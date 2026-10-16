package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.barbershop.backend.exception.TooManyRequestsException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * D3/D4 (spec account-lockout, fase 2): lockout por cuenta con clave SOLO el
 * email (sin IP) y backoff exponencial de la ventana —
 * {@code min(base·multiplier^(n − threshold), max)}.
 *
 * <p>JUnit puro sin Spring y con el reloj inyectable {@code RateLimiterService.reloj}:
 * cero {@code Thread.sleep}, estilo D8 pensado para convivir con la batería
 * de la fase 5. Emails únicos con UUID (H2/buckets compartidos).</p>
 */
class LockoutBackoffTest {

    private RateLimiterService rateLimiter;
    private AtomicLong reloj;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiterService();
        reloj = new AtomicLong(System.currentTimeMillis());
        rateLimiter.reloj = reloj::get;
        // Defaults de application.properties (tarea 2.7), fijados a mano porque
        // este test construye el bean sin Spring (@Value no inyecta nada)
        ReflectionTestUtils.setField(rateLimiter, "maxFallasPorCuenta", 5);
        ReflectionTestUtils.setField(rateLimiter, "maxFallasPorIp", 20);
        ReflectionTestUtils.setField(rateLimiter, "ventanaLoginSegundos", 900L);
        rateLimiter.umbralLockout = 5;
        rateLimiter.ventanaBaseLockoutSegundos = 900;
        rateLimiter.multiplicadorLockout = 2;
        rateLimiter.maxLockoutSegundos = 86400;
    }

    @Test
    void umbralDeCincoFallosBloqueaConVentanaBaseYsinOraculo() {
        String email = email();
        for (int i = 0; i < 5; i++) {
            // Cada fallo desde una IP distinta: ningún bucket con IP supera
            // su límite, solo el bucket por cuenta (sin IP)
            rateLimiter.registrarFalloLogin(email, "203.0.113." + i);
        }

        TooManyRequestsException bloqueo = assertThrows(
                TooManyRequestsException.class,
                () -> rateLimiter.comprobarLogin(email, "203.0.113.99"));
        assertThat(bloqueo.getCodigo()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(bloqueo.getRetryAfterSeconds()).isEqualTo(900L);
        assertThat(bloqueo.getMessage()).isEqualTo("Demasiados intentos. Inténtelo de nuevo más tarde.");

        // Otra cuenta distinta sigue operando con normalidad (lockout por cuenta)
        assertThatCode(() -> rateLimiter.comprobarLogin(email(), "203.0.113.99"))
                .doesNotThrowAnyException();
    }

    @Test
    void backoffExponencialReArmaLaVentanaYDecrementaConElTiempo() {
        String email = email();
        for (int i = 0; i < 5; i++) {
            rateLimiter.registrarFalloLogin(email, "198.51.100." + i);
        }
        assertThat(retryAfter(email)).isEqualTo(900L);      // 5 fallos → 15 min

        rateLimiter.registrarFalloLogin(email, "198.51.100.5");
        assertThat(retryAfter(email)).isEqualTo(1800L);     // 6.º fallo → 30 min

        rateLimiter.registrarFalloLogin(email, "198.51.100.6");
        assertThat(retryAfter(email)).isEqualTo(3600L);     // 7.º fallo → 1 h

        // Sin nuevos fallos, Retry-After = segundos restantes y decrece
        reloj.addAndGet(100_000L);
        assertThat(retryAfter(email)).isEqualTo(3500L);
    }

    @Test
    void laVentanaDeBackoffNuncaSuperElTopeDe24Horas() {
        assertThat(rateLimiter.ventanaLockoutSegundos(5)).isEqualTo(900L);
        assertThat(rateLimiter.ventanaLockoutSegundos(6)).isEqualTo(1800L);
        assertThat(rateLimiter.ventanaLockoutSegundos(7)).isEqualTo(3600L);
        assertThat(rateLimiter.ventanaLockoutSegundos(11)).isEqualTo(57600L);
        assertThat(rateLimiter.ventanaLockoutSegundos(12)).isEqualTo(86400L);  // tope
        assertThat(rateLimiter.ventanaLockoutSegundos(100)).isEqualTo(86400L);
        assertThat(rateLimiter.ventanaLockoutSegundos(Long.MAX_VALUE)).isEqualTo(86400L);

        String email = email();
        for (int i = 0; i < 20; i++) {
            rateLimiter.registrarFalloLogin(email, "192.0.2." + i);
        }
        assertThat(retryAfter(email)).isEqualTo(86400L);
    }

    @Test
    void elAciertoReseteaContadorYVentana() {
        String email = email();
        for (int i = 0; i < 5; i++) {
            rateLimiter.registrarFalloLogin(email, "203.0.114." + i);
        }
        assertThat(retryAfter(email)).isEqualTo(900L);

        // Éxito: limpia el bucket por cuenta (el por IP no se resetea) →
        // contador y ventana extendida vuelven a cero
        rateLimiter.resetearLogin(email, "203.0.114.0");
        assertThatCode(() -> rateLimiter.comprobarLogin(email, "203.0.114.99"))
                .doesNotThrowAnyException();

        // El siguiente ciclo empieza de nuevo con la ventana base de 15 min
        for (int i = 0; i < 5; i++) {
            rateLimiter.registrarFalloLogin(email, "203.0.114." + i);
        }
        assertThat(retryAfter(email)).isEqualTo(900L);
    }

    @Test
    void laVentanaSinActividadExpira() {
        String email = email();
        for (int i = 0; i < 5; i++) {
            rateLimiter.registrarFalloLogin(email, "203.0.115." + i);
        }
        assertThat(retryAfter(email)).isEqualTo(900L);

        // Sin peticiones durante la ventana efectiva → el ventaneado en
        // memoria la resetea: un login legítimo vuelve a ser posible
        reloj.addAndGet(901_000L);
        assertThatCode(() -> rateLimiter.comprobarLogin(email, "203.0.115.99"))
                .doesNotThrowAnyException();

        // Y un fallo nuevo reinicia el ciclo con la ventana base
        rateLimiter.registrarFalloLogin(email, "203.0.115.99");
        assertThatCode(() -> rateLimiter.comprobarLogin(email, "203.0.115.98"))
                .doesNotThrowAnyException();
    }

    // --- helpers ---

    private static String email() {
        return "lock-" + UUID.randomUUID() + "@barbershop.test";
    }

    private long retryAfter(String email) {
        TooManyRequestsException e = assertThrows(
                TooManyRequestsException.class,
                () -> rateLimiter.comprobarLogin(email, "203.0.113.200"));
        assertThat(e.getCodigo()).isEqualTo("ACCOUNT_LOCKED");
        return e.getRetryAfterSeconds();
    }
}
