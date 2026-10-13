package com.barbershop.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
 * Regresión de la auditoría §5 (matriz de autorización): 401 sin token o
 * inválido, 403 con rol insuficiente, 200 con rol adecuado; un access token
 * no sirve como refresh; el token basura responde 401 (nunca 500).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationMatrixTest {

    private static final String PASSWORD = "AuthMatrixTest12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String workerEmail;
    private String adminEmail;

    @BeforeEach
    void crearUsuarios() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        workerEmail = "matrix-worker-" + sufijo + "@barbershop.test";
        adminEmail = "matrix-admin-" + sufijo + "@barbershop.test";
        guardar(workerEmail, Rol.WORKER);
        guardar(adminEmail, Rol.ADMIN);
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/admin/trabajadores"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/worker/perfil"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void workerEnRutaAdminResponde403() throws Exception {
        String token = login(workerEmail);
        mockMvc.perform(get("/api/admin/trabajadores")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEnRutaAdminResponde200() throws Exception {
        String token = login(adminEmail);
        mockMvc.perform(get("/api/admin/trabajadores")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void accessTokenNoSirveComoRefresh() throws Exception {
        String accessToken = login(workerEmail);
        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenBasuraResponde401YNo500() throws Exception {
        mockMvc.perform(get("/api/worker/perfil")
                        .header("Authorization", "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private void guardar(String email, Rol rol) {
        trabajadorRepository.save(Trabajador.builder()
                .nombre("Matrix " + rol)
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(rol)
                .build());
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return data.path("accessToken").asText();
    }
}
