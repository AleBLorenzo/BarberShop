package com.barbershop.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.TrabajadorRepository;

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
 * <p>Cada método usa una IP propia (cabecera X-Real-IP o remoteAddr) para que
 * los buckets no se contaminen entre métodos ni con el resto de la suite.</p>
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

    private String emailValido;

    @BeforeEach
    void crearUsuario() {
        emailValido = "rl-" + UUID.randomUUID().toString().substring(0, 8) + "@barbershop.test";
        trabajadorRepository.save(Trabajador.builder()
                .nombre("Rate Limit Test")
                .email(emailValido)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(Rol.WORKER)
                .build());
    }

    @Test
    void sextoFalloResponde429ConRetryAfter() throws Exception {
        String ip = "203.0.113.10";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(emailValido, "contrasena-mala-" + i).with(xRealIp(ip)))
                    .andExpect(status().isUnauthorized());
        }
        // 6.º intento CON la contraseña correcta → tampoco emite tokens
        mockMvc.perform(login(emailValido, PASSWORD).with(xRealIp(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"));
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
        String email = "rl-xri-" + UUID.randomUUID().toString().substring(0, 8) + "@barbershop.test";
        trabajadorRepository.save(Trabajador.builder()
                .nombre("XRI Test").email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true).rol(Rol.WORKER).build());

        String ipBloqueada = "198.51.100.50";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(email, "mala").with(xRealIp(ipBloqueada)))
                    .andExpect(status().isUnauthorized());
        }
        // Misma cuenta + misma X-Real-IP → 429
        mockMvc.perform(login(email, PASSWORD).with(xRealIp(ipBloqueada)))
                .andExpect(status().isTooManyRequests());

        // Misma cuenta DESDE OTRA IP (sin la cabecera → remoteAddr) → 200:
        // el bucket usa la IP de la cabecera, no un valor fijo.
        mockMvc.perform(login(email, PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void usaRemoteAddrCuandoNoHayCabecerasDeProxy() throws Exception {
        String email = "rl-ra-" + UUID.randomUUID().toString().substring(0, 8) + "@barbershop.test";
        trabajadorRepository.save(Trabajador.builder()
                .nombre("RemoteAddr Test").email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true).rol(Rol.WORKER).build());

        RequestPostProcessor desdeIp = remoteAddr("198.51.100.60");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(email, "mala").with(desdeIp))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(email, PASSWORD).with(desdeIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        // Otra IP remota, sin cabeceras → bucket distinto → 200
        mockMvc.perform(login(email, PASSWORD).with(remoteAddr("198.51.100.61")))
                .andExpect(status().isOk());
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
