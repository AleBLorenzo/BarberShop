package com.barbershop.backend.security;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import com.barbershop.backend.exception.TooManyRequestsException;

/**
 * Dobles de test MANUALES para construir {@code AuthService} fuera de Spring
 * (tareas 5.1/5.4/5.5). Se evita Mockito a propósito: el mock maker inline no
 * puede instrumentar clases concretas en el JVM de CI (Java 25) y estos tests
 * solo necesitan tres comportamientos — un {@code AuthenticationManager} que
 * cuenta invocaciones, un {@code RateLimiterService} que registra/lanza y
 * repositorios que NUNCA deben ser tocados (los caminos bajo prueba salen
 * antes de la persistencia).
 */
final class AuthServiceStubs {

    private AuthServiceStubs() {
    }

    /** {@code AuthenticationManager} que cuenta cuántas veces se autentica. */
    static AuthenticationManagerContador authenticationManagerContador() {
        return new AuthenticationCount();
    }

    interface AuthenticationManagerContador
            extends org.springframework.security.authentication.AuthenticationManager {
        int llamadas();
    }

    private static final class AuthenticationCount implements AuthenticationManagerContador {
        private final AtomicInteger contador = new AtomicInteger();

        @Override
        public org.springframework.security.core.Authentication authenticate(
                org.springframework.security.core.Authentication authentication) {
            contador.incrementAndGet();
            return authentication;
        }

        @Override
        public int llamadas() {
            return contador.get();
        }
    }

    /**
     * Stub de interfaz (repositorio/mapper) que FALLA el test si AuthService
     * llega a invocarlo: los caminos honeypot/reto/lockout/turnstile salen
     * ANTES de tocar persistencia.
     */
    static <T> T prohibirLlamadas(Class<T> tipo) {
        return tipo.cast(Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo},
                (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) {
                        return "stub-prohibido:" + tipo.getSimpleName();
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new AssertionError(
                            "AuthService NO debe invocar " + tipo.getSimpleName() + "." + method.getName());
                }));
    }

    /**
     * {@code RateLimiterService} que registra las llamadas y puede lanzar un
     * 429 simulado desde {@code comprobarLogin} (subclase del mismo paquete:
     * los métodos públicos relevantes se sobrescriben; los buckets reales no
     * se tocan).
     */
    static final class RateLimiterEspia extends RateLimiterService {

        final AtomicInteger comprobaciones = new AtomicInteger();
        final AtomicInteger fallos = new AtomicInteger();
        final AtomicInteger resets = new AtomicInteger();

        /** Si no es {@code null}, {@code comprobarLogin} la lanza (lockout simulado). */
        volatile TooManyRequestsException excepcionAlComprobar;

        @Override
        public void comprobarLogin(String email, String ip) {
            comprobaciones.incrementAndGet();
            TooManyRequestsException e = excepcionAlComprobar;
            if (e != null) {
                throw e;
            }
        }

        @Override
        public void registrarFalloLogin(String email, String ip) {
            fallos.incrementAndGet();
        }

        @Override
        public void resetearLogin(String email, String ip) {
            resets.incrementAndGet();
        }
    }
}
