package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.barbershop.backend.controller.admin.AdminController;
import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.TrabajadorRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @Autowired
    private AdminController adminController;

    private String workerEmail;
    private String adminEmail;
    private Long adminId;

    @BeforeEach
    void crearUsuarios() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        workerEmail = "matrix-worker-" + sufijo + "@barbershop.test";
        adminEmail = "matrix-admin-" + sufijo + "@barbershop.test";
        guardar(workerEmail, Rol.WORKER);
        adminId = guardar(adminEmail, Rol.ADMIN).getId();
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

    // --- admin-agenda-y-cierres (tarea 7.6) ---

    /** Rutas de administración nuevas: método + ruta. */
    private List<Object[]> rutasAdminNuevas() {
        return List.of(
                new Object[]{HttpMethod.GET, "/api/admin/ingresos"},
                new Object[]{HttpMethod.GET, "/api/admin/dashboard"},
                new Object[]{HttpMethod.GET, "/api/admin/horario"},
                new Object[]{HttpMethod.GET, "/api/admin/cierres"},
                new Object[]{HttpMethod.GET, "/api/admin/avisos"},
                new Object[]{HttpMethod.GET, "/api/admin/bloqueos"},
                new Object[]{HttpMethod.POST, "/api/admin/reservas/999999999/cancelar"},
                new Object[]{HttpMethod.GET, "/api/admin/trabajadores/" + adminId + "/horario"});
    }

    @Test
    void rutasAdminNuevasSinTokenResponden401() throws Exception {
        for (Object[] ruta : rutasAdminNuevas()) {
            mockMvc.perform(request((HttpMethod) ruta[0], (String) ruta[1]))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void rutasAdminNuevasConWorkerResponden403() throws Exception {
        String token = login(workerEmail);
        for (Object[] ruta : rutasAdminNuevas()) {
            mockMvc.perform(request((HttpMethod) ruta[0], (String) ruta[1])
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void rutasAdminNuevasConAdminResponden200() throws Exception {
        String token = login(adminEmail);
        for (Object[] ruta : rutasAdminNuevas()) {
            if (ruta[0] != HttpMethod.GET) {
                continue; // la cancelación de una reserva inexistente es 404, no un tema de rol
            }
            mockMvc.perform(request((HttpMethod) ruta[0], (String) ruta[1])
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/admin/reservas/999999999/cancelar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void agendaYBloqueosDelTrabajadorAccesiblesParaWorkerYAdmin() throws Exception {
        for (String email : List.of(workerEmail, adminEmail)) {
            String token = login(email);
            mockMvc.perform(get("/api/worker/bloqueos").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/worker/agenda").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/worker/bloqueos")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/worker/agenda")).andExpect(status().isUnauthorized());
    }

    @Test
    void horarioYDiasCerradosPublicosSinToken() throws Exception {
        LocalDate hoy = LocalDate.now();
        mockMvc.perform(get("/api/public/horario")).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/dias-cerrados")
                        .param("desde", hoy.toString())
                        .param("hasta", hoy.plusDays(30).toString()))
                .andExpect(status().isOk());
    }

    /**
     * {@code @PreAuthorize} de clase (D10, 7.4): la subida de la foto propia
     * sigue abierta a WORKER (llega a la validación del archivo → 400, no
     * 403), pero el resto de carpetas no.
     */
    @Test
    void workerSigueSubiendoSuFotoPeroNoAOtrasCarpetas() throws Exception {
        String token = login(workerEmail);
        MockMultipartFile noImagen = new MockMultipartFile("archivo", "nota.txt", "text/plain", "hola".getBytes());
        mockMvc.perform(multipart("/api/admin/media/trabajadores").file(noImagen)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/admin/media/servicios").file(noImagen)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /** La seguridad de método está activa: sin pasar por la regla de URL, un WORKER es rechazado. */
    @Test
    void preAuthorizeDeAdminControllerRechazaAWorkerAunqueSeLlameDirecto() {
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    workerEmail, null, List.of(new SimpleGrantedAuthority("ROLE_WORKER"))));
            assertThatThrownBy(() -> adminController.getIngresos(null, null))
                    .isInstanceOf(AuthorizationDeniedException.class);

            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    adminEmail, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            assertThat(adminController.getIngresos(null, null).getStatusCode().is2xxSuccessful()).isTrue();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // --- helpers ---

    private Trabajador guardar(String email, Rol rol) {
        return trabajadorRepository.save(Trabajador.builder()
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
