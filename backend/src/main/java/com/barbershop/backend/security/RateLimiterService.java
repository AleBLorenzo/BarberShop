package com.barbershop.backend.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

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
 *   <li>Login lockout — {@code login:acct:{email}}: umbral de fallos SOLO por
 *       email normalizado (SIN la IP en la clave) con backoff exponencial:
 *       la rotación de IPs de un botnet no reinicia el presupuesto de la
 *       cuenta (cambio harden-sql-injection-anti-bot, D3/D4).</li>
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

    /**
     * Reloj inyectable (package-private): los tests del mismo paquete lo
     * sustituyen para manipular el tiempo SIN {@code Thread.sleep} — la
     * ventana/backoff se prueba desplazando el reloj (design D8, tarea 2.4).
     */
    volatile LongSupplier reloj = System::currentTimeMillis;

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

    // --- Lockout progresivo por cuenta (D3/D4, change harden-sql-injection-anti-bot) ---
    // Campos package-private a propósito: los tests ajustan umbral/ventana y
    // leen la fórmula SIN crear contextos Spring nuevos (sin @TestPropertySource).

    @Value("${app.security.login.lockout.threshold:5}")
    int umbralLockout;

    @Value("${app.security.login.lockout.base-seconds:900}")
    long ventanaBaseLockoutSegundos;

    @Value("${app.security.login.lockout.multiplier:2}")
    double multiplicadorLockout;

    @Value("${app.security.login.lockout.max-seconds:86400}")
    long maxLockoutSegundos;

    // ===================== LOGIN =====================

    /**
     * Comprueba los buckets de login ANTES de autenticar (429 + Retry-After
     * sin llegar a validar credenciales). No incrementa: solo los fallos
     * cuentan, registrados en {@link #registrarFalloLogin}.
     *
     * <p>Orden: primero el lockout por cuenta (clave solo email, sin IP) →
     * {@code ACCOUNT_LOCKED}; después los buckets preexistentes email+IP e
     * IP → {@code TOO_MANY_REQUESTS}. Al llegar primero, una cuenta
     * bloqueada recibe siempre {@code ACCOUNT_LOCKED} mientras el rate limit
     * genérico (p. ej. 20 cuentas distintas desde una IP) conserva su código.</p>
     */
    public void comprobarLogin(String email, String ip) {
        comprobarLockout(email);
        comprobar(claveLoginCuenta(email, ip), maxFallasPorCuenta, ventanaLoginSegundos);
        comprobar(claveLoginIp(ip), maxFallasPorIp, ventanaLoginSegundos);
    }

    /** Registra un intento fallido (llamar SOLO en fallo de autenticación). */
    public void registrarFalloLogin(String email, String ip) {
        incrementarLockout(email);
        incrementar(claveLoginCuenta(email, ip), ventanaLoginSegundos);
        incrementar(claveLoginIp(ip), ventanaLoginSegundos);
    }

    /** Reset de los buckets de cuenta al acertar (el bucket por IP no se resetea). */
    public void resetearLogin(String email, String ip) {
        contadores.remove(claveLoginCuenta(email, ip));
        contadores.remove(claveCuenta(email));
    }

    // ===================== LOCKOUT POR CUENTA (SIN IP) =====================

    /**
     * Comprueba el bucket de lockout {@code login:acct:{email}} — clave SOLO
     * el email normalizado, sin la IP. Alcanzado el umbral → 429 con código
     * {@code ACCOUNT_LOCKED} y {@code Retry-After} en segundos restantes,
     * ANTES de autenticar (sin tocar BCrypt, sin emitir tokens) y con
     * mensaje genérico idéntico para cuentas inexistentes (sin oráculo de
     * enumeración).
     */
    private void comprobarLockout(String email) {
        String clave = claveCuenta(email);
        long ahora = ahora();
        Ventana ventana = ventanaActual(clave, ventanaBaseLockoutSegundos, ahora);
        synchronized (ventana) {
            if (ventana.contador >= umbralLockout) {
                throw new TooManyRequestsException(
                        "Demasiados intentos. Inténtelo de nuevo más tarde.",
                        segundosRestantes(ventana, ahora),
                        TooManyRequestsException.CODIGO_ACCOUNT_LOCKED);
            }
        }
    }

    /**
     * Incrementa el bucket de lockout y, al alcanzar el umbral, RE-ARMALA la
     * ventana con backoff exponencial: cada fallo adicional emitido DURANTE
     * el bloqueo la alarga según {@code min(base·multiplier^(n−threshold),
     * max)} (5 fallos → 15 min, 6.º → 30 min, 7.º → 1 h, … tope 24 h); el
     * atacante no "gasta" la espera fallando. Un acierto la limpia
     * ({@link #resetearLogin}) y sin actividad la ventana expira (el
     * ventaneado en memoria ya existente la resetea a cero).
     */
    private void incrementarLockout(String email) {
        String clave = claveCuenta(email);
        long ahora = ahora();
        Ventana ventana = ventanaActual(clave, ventanaBaseLockoutSegundos, ahora);
        synchronized (ventana) {
            ventana.contador++;
            if (ventana.contador >= umbralLockout) {
                ventana.fin = ahora + ventanaLockoutSegundos(ventana.contador) * 1000L;
            }
        }
    }

    /**
     * Ventana efectiva de lockout para {@code fallos} acumulados:
     * {@code min(base-seconds · multiplier^(fallos − threshold), max-seconds)}.
     * Package-private: los tests verifican la fórmula y el tope sin sleeps.
     */
    long ventanaLockoutSegundos(long fallos) {
        long exponente = Math.max(0L, fallos - umbralLockout);
        double segundos = ventanaBaseLockoutSegundos * Math.pow(multiplicadorLockout, exponente);
        // No finito (overflow de la potencia) o por encima del tope → tope
        if (!Double.isFinite(segundos) || segundos >= maxLockoutSegundos) {
            return maxLockoutSegundos;
        }
        return Math.max(1L, (long) segundos);
    }

    /** Instante actual según el reloj inyectable (los tests lo manipulan). */
    private long ahora() {
        return reloj.getAsLong();
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
        long ahora = ahora();
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
        long ahora = ahora();
        Ventana ventana = ventanaActual(clave, ventanaSegundos, ahora);
        synchronized (ventana) {
            ventana.contador++;
        }
    }

    /** Incrementa y lanza 429 si el nuevo valor supera el máximo (uso: reservas). */
    private void incrementarYComprobar(String clave, int max, long ventanaSegundos) {
        long ahora = ahora();
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

    private static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String claveLoginCuenta(String email, String ip) {
        return "login:acct:" + normalizarEmail(email) + "+" + ip;
    }

    /** Bucket de lockout: clave SOLO el email normalizado, SIN la IP. */
    private static String claveCuenta(String email) {
        return "login:acct:" + normalizarEmail(email);
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
