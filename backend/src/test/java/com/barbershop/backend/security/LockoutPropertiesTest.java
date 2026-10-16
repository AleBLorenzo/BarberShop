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
 * Spec account-lockout — "Umbrales y tiempos configurables por properties"
 * (tarea 5.3), SIN crear contextos Spring nuevos.
 *
 * <p><b>Elección documentada:</b> los parámetros {@code app.security.login.lockout.*}
 * se leen con {@code @Value} en la construcción del bean, así que en el único
 * contexto compartido se sobrescriben directamente los campos package-private
 * de {@link RateLimiterService} ({@code umbralLockout}/
 * {@code ventanaBaseLockoutSegundos}) — patrón D8 "un contexto, datos aislados
 * por unicidad". La alternativa ({@code @TestPropertySource} por set de
 * properties) crearía un contexto Spring nuevo por test y relentizaría toda la
 * suite; los defaults de producción se restauran en {@code @AfterEach}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class LockoutPropertiesTest {

    private static final String PASSWORD = "LockoutPropsTest12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimiterService rateLimiter;

    private String email;

    @BeforeEach
    void aislamiento() {
        rateLimiter.limpiarContadores();
        email = "lockprops-" + UUID.randomUUID().toString().substring(0, 8)
                + "@barbershop.test";
        trabajadorRepository.save(Trabajador.builder()
                .nombre("Lockout Properties Test")
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(Rol.WORKER)
                .build());
    }

    @AfterEach
    void restaurarDefaults() {
        // Defaults de application.properties (tarea 2.7): threshold=5,
        // base-seconds=900 — el bean del contexto compartido vuelve a prod.
        rateLimiter.umbralLockout = 5;
        rateLimiter.ventanaBaseLockoutSegundos = 900;
        rateLimiter.limpiarContadores();
    }

    @Test
    void umbralSobreescribibleTresFallosBloqueanEnElCuartoIntento() throws Exception {
        rateLimiter.umbralLockout = 3; // app.security.login.lockout.threshold=3

        // 3 fallos con IPs rotantes (menos que el default 5: sin el override
        // estos fallos NO bloquearían)…
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login("mala-" + i).with(xRealIp("198.51.101." + (10 + i))))
                    .andExpect(status().isUnauthorized());
        }
        // …y el 4.º intento —incluso con la contraseña correcta y desde IP
        // nueva— recibe 429 con Retry-After, sin cambios de código.
        mockMvc.perform(login(PASSWORD).with(xRealIp("198.51.101.99")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void ventanaBaseSobreescribibleTrescientosSegundos() throws Exception {
        rateLimiter.ventanaBaseLockoutSegundos = 300; // app.security.login.lockout.base-seconds=300

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login("mala-" + i).with(xRealIp("198.51.102." + (10 + i))))
                    .andExpect(status().isUnauthorized());
        }
        // El primer bloqueo (en el umbral) dura 300 s y Retry-After lo refleja
        mockMvc.perform(login("intento").with(xRealIp("198.51.102.99")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "300"))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));
    }

    // ===================== helpers =====================

    private MockHttpServletRequestBuilder login(String password) {
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
}
