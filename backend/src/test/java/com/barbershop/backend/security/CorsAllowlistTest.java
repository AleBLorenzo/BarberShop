package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.barbershop.backend.config.CorsConfig;

/**
 * B-BAJO-6 / spec cors-origin-allowlist: la allowlist vive en
 * {@code app.cors.allowed-origins} (env {@code CORS_ALLOWED_ORIGINS}), solo se
 * responde con ACAO a orígenes listados y el fail-fast de {@code CorsConfig}
 * (vacío o {@code *}) se conserva.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsAllowlistTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightOrigenListadoRecibeAccessControlAllowOrigin() throws Exception {
        mockMvc.perform(options("/api/public/servicios")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void preflightOrigenNoListadoNoRecibeAccessControlAllowOrigin() throws Exception {
        MvcResult result = mockMvc.perform(options("/api/public/servicios")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andReturn();

        // Origen fuera de la allowlist: la respuesta NO lleva ACAO (spec scenario
        // "Origen no permitido"). El status puede variar (403/404) — no se assertiona.
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void corsConfigFallaAlArrancarSinOrigenes() {
        assertThatThrownBy(() -> new CorsConfig().corsConfigurationSource(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorsConfig().corsConfigurationSource(" , "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void corsConfigFallaAlArrancarConComodin() {
        assertThatThrownBy(() -> new CorsConfig().corsConfigurationSource("*"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
