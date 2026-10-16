package com.barbershop.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.TrabajadorRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * B-MED-5 / spec login-rate-limiting: {@code POST /api/auth/login} limita los
 * intentos FALLIDOS por cuenta+IP (5/15 min) y por IP (20/15 min), con
 * 429 + {@code Retry-After}, reset al acertar y extracción de IP real
 * (X-Real-IP → XFF → remoteAddr).
 *
 * <p>Con el lockout por cuenta (spec account-lockout, sin IP en la clave) el
 * 6.º fallo de una misma cuenta responde ya {@code ACCOUNT_LOCKED} aunque la
 * IP rote; los buckets preexistentes por IP siguen devolviendo
 * {@code TOO_MANY_REQUESTS} (20 cuentas distintas desde un mismo origen).</p>
 *
 * <p>Cada método usa una IP propia (cabecera X-Real-IP o remoteAddr) para que
 * los buckets no se contaminen entre métodos ni con el resto de la suite; las
 * IPs de este test son FIJAS, así que {@code limpiarContadores()} corre en
 * {@code @BeforeEach} Y en {@code @AfterEach} (aislamiento tarea 5.8).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginRateLimitTest {

    private static final String PASSWORD = "LoginRateTest12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimiterService rateLimiter;

    private String emailValido;

    @BeforeEach
    void crearUsuario() {
        // Aislamiento (tarea 5.8): este test usa IPs FIJAS, así que limpia
        // cualquier bucket residuo de otras clases ANTES de cada método.
        rateLimiter.limpiarContadores();
        emailValido = "rl-" + UUID.randomUUID().toString().substring(0, 8) + "@barbershop.test";
        trabajadorRepository.save(Trabajador.builder()
                .nombre("Rate Limit Test")
                .email(emailValido)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(Rol.WORKER)
                .build());
    }

    @AfterEach
    void limpiarResiduo() {
        // Las IPs de este test son FIJAS y quedan saturadas (20 fallos/IP):
        // se limpian para no contaminar a las clases siguientes (tarea 5.8).
        rateLimiter.limpiarContadores();
    }

    @Test
    void sextoFalloResponde429ConRetryAfter() throws Exception {
        String ip = "203.0.113.10";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(emailValido, "contrasena-mala-" + i).with(xRealIp(ip)))
                    .andExpect(status().isUnauthorized());
        }
        // 6.º intento CON la contraseña correcta → tampoco emite tokens: la
        // cuenta superó el umbral del lockout por cuenta (bucket SIN IP) →
        // 429 + Retry-After + ACCOUNT_LOCKED (spec account-lockout, D3/D4)
        mockMvc.perform(login(emailValido, PASSWORD).with(xRealIp(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));
    }

    @Test
    void aciertoReseteaElContadorDeLaCuenta() throws Exception {
        String ip = "203.0.113.11";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login(emailValido, "mala-" + i).with(xRealIp(ip)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(emailValido, PASSWORD).with(xRealIp(ip)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // Si el reset no hubiera funcionado, el contador (3+4=7) superaría el
        // máximo y este acierto recibiría 429 en vez de 200.
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(login(emailValido, "otra-mala-" + i).with(xRealIp(ip)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(emailValido, PASSWORD).with(xRealIp(ip)))
                .andExpect(status().isOk());
    }

    @Test
    void bucketPorIpConCuentasDistintas() throws Exception {
        String ip = "203.0.113.12";
        // 20 fallos alternando 20 emails distintos: ninguna cuenta supera su
        // propio límite, pero la IP llega a 20.
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(login("nunca-existe-" + i + "@barbershop.test", "mala")
                            .with(xRealIp(ip)))
                    .andExpect(status().isUnauthorized());
        }
        // 21.º fallo desde esa IP → 429 aunque la cuenta no estuviera bloqueada
        mockMvc.perform(login("nunca-existe-final@barbershop.test", "mala")
                        .with(xRealIp(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void dentroDelLimiteConCredencialesValidasDevuelve200() throws Exception {
        mockMvc.perform(login(emailValido, PASSWORD).with(xRealIp("203.0.113.13")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    void usaCabeceraXRealIPComoClaveDelBucket() throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String ipBloqueada = "198.51.100.50";
        // 20 fallos desde MISMA X-Real-IP con emails distintos: ninguna
        // cuenta alcanza el umbral de lockout, pero la IP de la CABECERA
        // llega a su límite preexistente (20/15 min) → 429 genérico
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(login("rl-xri-" + sufijo + "-" + i + "@barbershop.test", "mala")
                            .with(xRealIp(ipBloqueada)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login("rl-xri-" + sufijo + "-final@barbershop.test", "mala")
                        .with(xRealIp(ipBloqueada)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"));

        // Misma situación DESDE OTRA X-Real-IP → bucket distinto → NO 429:
        // la clave del bucket usa la cabecera, no remoteAddr. (La rotación de
        // IP ya NO resetea la cuenta: eso lo cubre el lockout por cuenta sin
        // IP del cambio harden-sql-injection-anti-bot.)
        mockMvc.perform(login("rl-xri-" + sufijo + "-otra@barbershop.test", "mala")
                        .with(xRealIp("198.51.101.51")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usaRemoteAddrCuandoNoHayCabecerasDeProxy() throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String ipBloqueada = "198.51.100.60";
        // Sin cabeceras de proxy la clave del bucket por IP es remoteAddr:
        // 20 fallos con emails distintos desde la MISMA remoteAddr → 429
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(login("rl-ra-" + sufijo + "-" + i + "@barbershop.test", "mala")
                            .with(remoteAddr(ipBloqueada)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login("rl-ra-" + sufijo + "-final@barbershop.test", "mala")
                        .with(remoteAddr(ipBloqueada)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"));

        // Otra remoteAddr, sin cabeceras → bucket distinto → NO 429: la clave
        // es la IP remota real, no un valor fijo de cabecera ausente
        mockMvc.perform(login("rl-ra-" + sufijo + "-otra@barbershop.test", "mala")
                        .with(remoteAddr("198.51.100.61")))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private static RequestPostProcessor xRealIp(String ip) {
        return request -> {
            request.addHeader("X-Real-IP", ip);
            return request;
        };
    }

    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
