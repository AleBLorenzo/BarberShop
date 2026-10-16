package com.barbershop.backend.security;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

import com.barbershop.backend.exception.BotChallengeException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Protección anti-bot del login (cambio harden-sql-injection-anti-bot, D5):
 * honeypot, reto ligero por cabecera, señales de fingerprint y Turnstile
 * opcional. Tres mecanismos "exigentes" (honeypot/reto/Turnstile) vienen con
 * flags de configuración package-private para que los tests del mismo paquete
 * los activen/desactiven SIN crear contextos Spring nuevos (patrón "un
 * contexto, datos aislados por unicidad" del design D8).
 *
 * <p>El orden de evaluación en {@code AuthService.login} es: honeypot →
 * reto → Turnstile → buckets de rate limit → autenticar. La auditoría de
 * cada rechazo (D6) la emite AuthService con su motivo.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotProtectionService {

    /** URL pública de verificación de tokens de Cloudflare Turnstile. */
    private static final String TURNSTILE_SITEVERIFY_URL =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    /**
     * Prefijo del reto: versiona el esquema {@code SHA-256(v1|email|minuto)}
     * para poder cambiarlo en el futuro sin aceptar valores de la versión
     * antigua.
     */
    private static final String RETO_VERSION = "v1";

    /** Tolerancia de reloj (minutos) al verificar la caducidad del reto. */
    private static final long RETO_TOLERANCIA_MINUTOS = 1L;

    /** Timeouts del siteverify de Turnstile (una llamada colgada no debe frenar el login). */
    private static final int TURNSTILE_TIMEOUT_MS = 3_000;

    private final ObjectMapper objectMapper;

    // --- Configuración (properties/entorno, defaults = capa barata ON, capas exigentes OFF) ---

    /** Honeypot ON por defecto: tolerante a la ausencia del campo ⇒ cero riesgo. */
    @Value("${app.security.bot.honeypot.enabled:true}")
    boolean honeypotActivo;

    /**
     * Demora artificial del rechazo por honeypot (~1 s en prod para no
     * delatar el motivo). Package-private y volátil: los tests la ponen a 0
     * SIN sleeps (patrón del reloj inyectable de RateLimiterService).
     */
    @Value("${app.security.bot.honeypot.delay-ms:1000}")
    volatile long demoraHoneypotMs;

    /** Reto por cabecera OFF por defecto: es lo que podría romper clientes API desconocidos. */
    @Value("${app.security.bot.challenge.enabled:false}")
    boolean retoActivo;

    /** Turnstile OFF por defecto (exige site-key/secret-key del propietario). */
    @Value("${app.security.turnstile.enabled:false}")
    boolean turnstileActivo;

    @Value("${app.security.turnstile.secret-key:}")
    String turnstileSecretKey;

    /**
     * Verificador de Turnstile sustituible en test (sin red real): si no se
     * fija, se usa el {@code siteverify} HTTP real. Interfaz funcional
     * package-private al estilo del {@code reloj} de RateLimiterService.
     */
    @FunctionalInterface
    interface VerificadorTurnstile {
        boolean verificar(String token);
    }

    volatile VerificadorTurnstile verificadorTurnstile;

    /** RestClient para siteverify con timeouts (una llamada colgada no debe frenar el login). */
    private final RestClient restClientTurnstile = RestClient.builder()
            .requestFactory(conTimeouts(TURNSTILE_TIMEOUT_MS))
            .build();

    private static SimpleClientHttpRequestFactory conTimeouts(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return factory;
    }

    // ===================== HONEYPOT =====================

    /** ¿El honeypot está habilitado por configuración? */
    public boolean honeypotActivo() {
        return honeypotActivo;
    }

    /** El campo honeypot llegó con cualquier valor no vacío (posible bot). */
    public boolean honeypotRelleno(String website) {
        return website != null && !website.isBlank();
    }

    /**
     * Demora artificial del rechazo por honeypot (~1 s): hace que la respuesta
     * del bot sea indistinguible en tiempo de un fallo normal de credenciales.
     * Con {@code delay-ms=0} (tests) no espera nada.
     */
    public void esperarDemoraHoneypot() {
        long ms = demoraHoneypotMs;
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ===================== RETO POR CABECERA =====================

    /**
     * Valida el reto {@code X-Login-Challenge} SOLO si está habilitado.
     * Con el reto ON, User-Agent ausente o cabecera ausente/inválida/caducada
     * → {@link BotChallengeException} (403 BOT_CHALLENGE) ANTES de
     * credenciales y rate limiter. Con el reto OFF no se exige ni se rechaza
     * nada por este motivo.
     *
     * <p>Esquema sin estado, verificable en ambos lados sin dependencias de
     * terceros: {@code X-Login-Challenge = hex(SHA-256("v1|<email>|<minuto>"))}
     * donde {@code minuto = epoch_ms / 60000}, válido mientras el reloj del
     * cliente esté dentro de ±1 minuto del del servidor (caducidad). Solo el
     * JS del frontend calcula el hash; un script que haga POST plano no lo
     * envía (es una capa barata de defensa en profundidad, no criptografía:
     * el algoritmo es público, como advierte el design D5).</p>
     */
    public void comprobarReto(String email) {
        if (!retoActivo) {
            return;
        }
        HttpServletRequest request = requestActual();
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            // Excepción del requisito de fingerprint (3.6): con el reto ON,
            // UA ausente cuenta como fallo de reto → 403 BOT_CHALLENGE
            throw new BotChallengeException("Reto anti-bot no superado");
        }
        String challenge = request == null ? null : request.getHeader("X-Login-Challenge");
        if (!retoValido(email, challenge)) {
            throw new BotChallengeException("Reto anti-bot no superado");
        }
    }

    /**
     * El valor enviado coincide con el esperado para el minuto actual o los
     * vecinos (tolerancia de reloj ±1 min ⇒ "caducado" a los 1-2 min).
     * Package-private: los tests verifican el esquema sin red.
     */
    boolean retoValido(String email, String challenge) {
        if (challenge == null || challenge.isBlank() || email == null) {
            return false;
        }
        long minuto = System.currentTimeMillis() / 60_000L;
        byte[] recibido = challenge.trim().getBytes(StandardCharsets.UTF_8);
        for (long desfase = -RETO_TOLERANCIA_MINUTOS; desfase <= RETO_TOLERANCIA_MINUTOS; desfase++) {
            byte[] esperado = retoEsperado(email, minuto + desfase)
                    .getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(esperado, recibido)) {
                return true;
            }
        }
        return false;
    }

    /** Valor esperado del reto: hex(SHA-256("v1|<email>|<minuto>")). */
    static String retoEsperado(String email, long minuto) {
        return sha256Hex(RETO_VERSION + "|" + email + "|" + minuto);
    }

    private static String sha256Hex(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    // ===================== TURNSTILE (opcional, OFF) =====================

    /**
     * Valida el token opcional de Turnstile SOLO cuando el mecanismo está
     * habilitado Y el campo viene presente (nunca exigido por `@Valid`):
     * token inválido/caducado → {@link BotChallengeException} (403) antes de
     * verificar credenciales. Deshabilitado → ni widget ni llamadas a
     * terceros (cero comportamiento).
     */
    public void comprobarTurnstile(String token) {
        if (!turnstileActivo || token == null || token.isBlank()) {
            return;
        }
        if (!verificarTurnstile(token)) {
            throw new BotChallengeException("Verificación anti-bot fallida");
        }
    }

    /**
     * {@code siteverify} con el verificador inyectado (tests, sin red) o la
     * llamada HTTP real (RestClient, sin dependencias nuevas). Un fallo de
     * red o de parseo se trata como token NO verificado pero se deja pasar
     * (fail-open con WARN): Turnstile es una capa opcional y una caída de
     * Cloudflare no debe bloquear los logins legítimos del propietario.
     */
    boolean verificarTurnstile(String token) {
        VerificadorTurnstile verificador = verificadorTurnstile;
        if (verificador != null) {
            return verificador.verificar(token);
        }
        try {
            String cuerpo = "secret=" + urlEncode(turnstileSecretKey) + "&response=" + urlEncode(token);
            String respuesta = restClientTurnstile.post()
                    .uri(TURNSTILE_SITEVERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(cuerpo)
                    .retrieve()
                    .body(String.class);
            return respuesta != null
                    && objectMapper.readTree(respuesta).path("success").asBoolean(false);
        } catch (Exception e) {
            log.warn("event=turnstile_siteverify_error detalle=\"{}\"", e.getMessage());
            return true; // fail-open documentado: la capa opcional no bloquea logins por un fallo transitorio
        }
    }

    private static String urlEncode(String valor) {
        return URLEncoder.encode(valor == null ? "" : valor, StandardCharsets.UTF_8);
    }

    // ===================== FINGERPRINT (informativo) =====================

    /** Cabecera de la petición actual ({@code null} fuera de request). */
    public String headerActual(String nombre) {
        HttpServletRequest request = requestActual();
        return request == null ? null : request.getHeader(nombre);
    }

    /**
     * Señal informativa (NO rechaza por sí sola, 3.6): User-Agent ausente/
     * vacío O Content-Type que no sea coherentemente {@code application/json}
     * → {@code suspect_ua=true} en la auditoría (D6). Con el reto habilitado
     * la ausencia total de UA sí rechaza, en {@link #comprobarReto}.
     */
    public boolean sospechosoFingerprint(String userAgent, String contentType) {
        boolean uaAusente = userAgent == null || userAgent.isBlank();
        boolean contentTypeRaro = contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json");
        return uaAusente || contentTypeRaro;
    }

    // ===================== NÚCLEO =====================

    private static HttpServletRequest requestActual() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }
}
