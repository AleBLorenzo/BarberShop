package com.barbershop.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * B-INFO-1 / spec api-docs-hardening: con el perfil {@code prod} springdoc
 * está deshabilitado ({@code springdoc.api-docs.enabled=false}) y las rutas de
 * documentación responden 404. El rasgo de prod se replica en
 * {@code src/test/resources/application-prod.properties} (sombra la de
 * src/main, que apunta a PostgreSQL; los tests usan H2).
 *
 * <p>La comprobación del perfil por defecto (dev → 200) vive en
 * {@code ApiDocsDevTest}, porque el perfil se fija a nivel de clase.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ApiDocsProdTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsResponde404EnProd() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerUiResponde404EnProd() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerUiIndexResponde404EnProd() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isNotFound());
    }
}
