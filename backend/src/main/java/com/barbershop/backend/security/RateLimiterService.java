package com.barbershop.backend.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.barbershop.backend.exception.TooManyRequestsException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Rate limiting en memoria SIN dependencias nuevas (cambio
 * harden-open-security-findings, decisión D1): contadores de ventana fija con
 * expiración y limpieza periódica perezosa.
 *
 * <p>Buckets:</p>
 * <ul>
 *   <li>Login cuenta — {@code login:acct:{email}+{ip}}: fallos máximos por
 *       combinación email+IP (reset al acertar).</li>
 *   <li>Login IP — {@code login:ip:{ip}}: cubre fuerza bruta distribuida.</li>
 *   <li>Reserva IP — {@code book:ip:{ip}}: peticiones de reserva por IP.</li>
 *   <li>Reserva día — {@code book:day:{yyyy-MM-dd}}: techo diario global
 *       (límite de envíos de email).</li>
 * </ul>
 *
 * <p>Estado en memoria: el despliegue es una única instancia (Oracle Free);
 * al reiniciar se pierden los contadores (aceptable — documentado en D1).
 * Ventana fija con reset: suficiente y trivialmente testeable.</p>
 */
@Component
public class RateLimiterService {

    /** Cadencia de la limpieza perezosa de contadores expirados. */
    private static final long INTERVALO_LIMPIEZA_MS = 60_000L;
    private static final long SEGUNDOS_POR_DIA = 86_400L;

    /** Contador de ventana fija: inicio/fin en epoch ms y número de hits. */
    private static final class Ventana {
        private long fin;
        private long contador;

        private Ventana(long fin) {
            this.fin = fin;
        }
    }

    private final Map<String, Ventana> contadores = new ConcurrentHashMap<>();
    private volatile long ultimaLimpieza = System.currentTimeMillis();

    // --- Configuración (properties/entorno, todos con defaults sensatos) ---

    @Value("${app.security.login.max-attempts-per-account:5}")
    private int maxFallasPorCuenta;

    @Value("${app.security.login.max-attempts-per-ip:20}")
    private int maxFallasPorIp;

    @Value("${app.security.login.window-seconds:900}")
    private long ventanaLoginSegundos;

    @Value("${app.security.booking.max-per-ip:10}")
    private int maxReservasPorIp;

    @Value("${app.security.booking.window-seconds:3600}")
    private long ventanaReservaSegundos;

    @Value("${app.security.booking.max-per-day:100}")
    private int maxReservasPorDia;

    /** Confianza en X-Real-IP/X-Forwarded-For (true tras nginx). */
    @Value("${app.security.trust-proxy-headers:true}")
    private boolean confiarCabecerasProxy;

    // ===================== LOGIN =====================

    /**
     * Comprueba los buckets de login ANTES de autenticar (429 + Retry-After
     * sin llegar a validar credenciales). No incrementa: solo los fallos
     * cuentan, registrados en {@link #registrarFalloLogin}.
     */
    public void comprobarLogin(String email, String ip) {
        comprobar(claveLoginCuenta(email, ip), maxFallasPorCuenta, ventanaLoginSegundos);
        comprobar(claveLoginIp(ip), maxFallasPorIp, ventanaLoginSegundos);
    }

    /** Registra un intento fallido (llamar SOLO en fallo de autenticación). */
    public void registrarFalloLogin(String email, String ip) {
        incrementar(claveLoginCuenta(email, ip), ventanaLoginSegundos);
        incrementar(claveLoginIp(ip), ventanaLoginSegundos);
    }

    /** Reset del bucket de cuenta al acertar (el bucket por IP no se resetea). */
    public void resetearLogin(String email, String ip) {
        contadores.remove(claveLoginCuenta(email, ip));
    }

    // ===================== RESERVA PÚBLICA =====================

    /**
     * Registra y comprueba una petición de reserva pública: bucket por IP
     * (ventana configurable) + techo diario global. Si se supera cualquier
     * tope lanza {@link TooManyRequestsException} (429) ANTES de crear la
     * reserva ni publicar {@code ReservaCreadaEvent}.
     */
    public void registrarReserva(String ip) {
        incrementarYComprobar(claveReservaIp(ip), maxReservasPorIp, ventanaReservaSegundos);
        incrementarYComprobar(claveReservaDia(), maxReservasPorDia, SEGUNDOS_POR_DIA);
    }

    // ===================== IP DEL CLIENTE =====================

    /**
     * IP real del cliente: {@code X-Real-IP} (nginx lo sobrescribe con la IP
     * real ⇒ no manipulable) → última entrada de {@code X-Forwarded-For} →
     * {@code remoteAddr}. Con {@code app.security.trust-proxy-headers=false}
     * solo se usa {@code remoteAddr} (sin proxy delante).
     */
    public String ipReal() {
        if (!confiarCabecerasProxy) {
            return remoteAddr();
        }
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }
        HttpServletRequest request = attrs.getRequest();

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] entradas = forwardedFor.split(",");
            return entradas[entradas.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    private String remoteAddr() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? "unknown" : attrs.getRequest().getRemoteAddr();
    }

    // ===================== NÚCLEO =====================

    /** Lanza 429 si el bucket ya alcanzó el máximo (sin incrementar). */
    private void comprobar(String clave, int max, long ventanaSegundos) {
        long ahora = System.currentTimeMillis();
        Ventana ventana = ventanaActual(clave, ventanaSegundos, ahora);
        synchronized (ventana) {
            if (ventana.contador >= max) {
                throw new TooManyRequestsException(
                        "Demasiados intentos. Inténtelo de nuevo más tarde.",
                        segundosRestantes(ventana, ahora));
            }
        }
    }

    /** Incrementa el contador sin lanzar (uso: fallos de login). */
    private void incrementar(String clave, long ventanaSegundos) {
        long ahora = System.currentTimeMillis();
        Ventana ventana = ventanaActual(clave, ventanaSegundos, ahora);
        synchronized (ventana) {
            ventana.contador++;
        }
    }

    /** Incrementa y lanza 429 si el nuevo valor supera el máximo (uso: reservas). */
    private void incrementarYComprobar(String clave, int max, long ventanaSegundos) {
        long ahora = System.currentTimeMillis();
        Ventana ventana = ventanaActual(clave, ventanaSegundos, ahora);
        synchronized (ventana) {
            ventana.contador++;
            if (ventana.contador > max) {
                throw new TooManyRequestsException(
                        "Demasiadas peticiones. Inténtelo de nuevo más tarde.",
                        segundosRestantes(ventana, ahora));
            }
        }
    }

    /** Devuelve la ventana vigente de la clave, reiniciándola si expiró. */
    private Ventana ventanaActual(String clave, long ventanaSegundos, long ahora) {
        limpiarSiProcede(ahora);
        Ventana ventana = contadores.computeIfAbsent(clave,
                k -> new Ventana(ahora + ventanaSegundos * 1000L));
        synchronized (ventana) {
            if (ventana.fin <= ahora) {
                ventana.fin = ahora + ventanaSegundos * 1000L;
                ventana.contador = 0;
            }
        }
        return ventana;
    }

    private static long segundosRestantes(Ventana ventana, long ahora) {
        return Math.max(1L, (ventana.fin - ahora + 999L) / 1000L);
    }

    /** Limpieza periódica (cada INTERVALO_LIMPIEZA_MS) de ventanas expiradas. */
    private void limpiarSiProcede(long ahora) {
        if (ahora - ultimaLimpieza < INTERVALO_LIMPIEZA_MS) {
            return;
        }
        synchronized (this) {
            if (ahora - ultimaLimpieza < INTERVALO_LIMPIEZA_MS) {
                return;
            }
            contadores.entrySet().removeIf(e -> e.getValue().fin <= ahora);
            ultimaLimpieza = ahora;
        }
    }

    // ===================== CLAVES =====================

    private static String claveLoginCuenta(String email, String ip) {
        String normalizado = email == null ? "" : email.toLowerCase(java.util.Locale.ROOT);
        return "login:acct:" + normalizado + "+" + ip;
    }

    private static String claveLoginIp(String ip) {
        return "login:ip:" + ip;
    }

    private static String claveReservaIp(String ip) {
        return "book:ip:" + ip;
    }

    private static String claveReservaDia() {
        return "book:day:" + java.time.LocalDate.now();
    }

    /**
     * Vacía todos los contadores. Uso exclusivo de tests (aislamiento entre
     * métodos: la clave diaria de reservas es compartida por diseño).
     */
    public void limpiarContadores() {
        contadores.clear();
    }
}
