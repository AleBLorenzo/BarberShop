package com.barbershop.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * B-INFO-1 / spec api-docs-hardening: con el perfil por defecto (dev)
 * Swagger/OpenAPI sigue disponible — {@code GET /v3/api-docs} → 200.
 * Complemento de {@code ApiDocsProdTest} (el perfil se fija por clase).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiDocsDevTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsDisponibleEnDev() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
