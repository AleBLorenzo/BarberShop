package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * B-BAJO-7 / spec security-headers: cabeceras completas en toda respuesta del
 * backend (Referrer-Policy, Permissions-Policy, X-Frame-Options DENY, HSTS),
 * CSP restrictiva {@code default-src 'none'} SOLO en respuestas {@code /api/**}
 * y SIN CSP en Swagger UI (para no romperlo en dev).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityHeadersTest {

    private static final String EMAIL = "headers-test@barbershop.test";
    private static final String PASSWORD = "HeadersTest12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void crearTrabajadorDePrueba() {
        if (!trabajadorRepository.existsByEmail(EMAIL)) {
            trabajadorRepository.save(Trabajador.builder()
                    .nombre("Headers Test")
                    .email(EMAIL)
                    .passwordHash(passwordEncoder.encode(PASSWORD))
                    .activo(true)
                    .rol(Rol.WORKER)
                    .build());
        }
    }

    @Test
    void endpointPublicoLlevaCabecerasDeSeguridad() throws Exception {
        mockMvc.perform(get("/api/public/servicios"))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().string("Permissions-Policy", containsString("geolocation=()")))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));
    }

    @Test
    void endpointAutenticadoLlevaCabecerasDeSeguridad() throws Exception {
        String accessToken = login();

        MvcResult result = mockMvc.perform(get("/api/worker/perfil")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andReturn();

        assertThat(result.getResponse().getContentType()).contains("application/json");
    }

    @Test
    void swaggerUiNoLlevaCspParaNoRomperlo() throws Exception {
        // La CSP restrictiva es solo para /api/**; Swagger se sirve fuera de
        // ese prefijo y debe cargar sin ser bloqueado.
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(header().doesNotExist("Content-Security-Policy"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Content-Security-Policy"));
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("accessToken").asText();
    }
}
