package com.barbershop.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
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

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * B-MED-3 / spec jwt-session-revocation: login crea una sesión persistida
 * (claim {@code sid}); {@code POST /api/auth/logout} revoca la sesión y deja
 * access+refresh sin autenticar de inmediato; las demás sesiones del mismo
 * usuario siguen vivas; el endpoint es idempotente; los tokens legacy sin
 * {@code sid} no autentican (BREAKING aceptado: re-login tras desplegar).
 */
@SpringBootTest
@AutoConfigureMockMvc
class JwtLogoutRevocationTest {

    private static final String TEST_SECRET =
            "testSecretKeyForBarberShopThatIsAtLeast256BitsLongForHS256AlgorithmTest";
    private static final String PASSWORD = "JwtLogoutTest12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String email;
    private Long trabajadorId;

    @BeforeEach
    void crearUsuario() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        email = "logout-" + sufijo + "@barbershop.test";
        Trabajador trabajador = trabajadorRepository.save(Trabajador.builder()
                .nombre("Logout Test")
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(Rol.WORKER)
                .build());
        trabajadorId = trabajador.getId();
    }

    @Test
    void logoutInvalidaAccessYRefreshDeLaSesion() throws Exception {
        Tokens tokens = login();

        // Antes del logout: el access autentica
        mockMvc.perform(perfilCon(tokens.access))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + tokens.refresh))
                .andExpect(status().isOk());

        // Mismo access → 401 (sesión revocada)
        mockMvc.perform(perfilCon(tokens.access))
                .andExpect(status().isUnauthorized());

        // Refresh de la sesión revocada → 401
        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer " + tokens.refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutSoloRevocaLaSesionDelTokenPresentado() throws Exception {
        Tokens sesionA = login();
        Tokens sesionB = login();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + sesionA.refresh))
                .andExpect(status().isOk());

        // La otra sesión del mismo trabajador sigue viva
        mockMvc.perform(perfilCon(sesionB.access))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer " + sesionB.refresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void logoutEsIdempotenteYNoFiltraEstado() throws Exception {
        Tokens tokens = login();

        // Sin cabecera Authorization → 2xx
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());

        // Token basura → 2xx (misma respuesta, no revela estado)
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer basura.del.tokn"))
                .andExpect(status().isOk());

        // Logout válido → 200
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + tokens.refresh))
                .andExpect(status().isOk());

        // Segundo logout con el mismo token (ya revocado) → sigue siendo 2xx
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + tokens.refresh))
                .andExpect(status().isOk());
    }

    @Test
    void tokenLegacySinSidNoAutentica() throws Exception {
        // Access token válido y firmado, pero emitido "antes del cambio"
        // (sin claim sid): debe responder 401 y obligar a re-login.
        String legacy = tokenLegacySinSid();

        mockMvc.perform(perfilCon(legacy))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutConAccessTokenTambienRevoca() throws Exception {
        Tokens tokens = login();

        // El logout acepta access o refresh (ambos llevan sid)
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + tokens.access))
                .andExpect(status().isOk());

        mockMvc.perform(perfilCon(tokens.access))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer " + tokens.refresh))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private record Tokens(String access, String refresh) {}

    private Tokens login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new Tokens(data.path("accessToken").asText(), data.path("refreshToken").asText());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder perfilCon(
            String accessToken) {
        return get("/api/worker/perfil").header("Authorization", "Bearer " + accessToken);
    }

    /** Firma un access token con el mismo secreto de test pero SIN claim sid. */
    private String tokenLegacySinSid() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("trabajadorId", trabajadorId);
        claims.put("email", email);
        claims.put("nombre", "Logout Test");
        claims.put("type", "access");
        return Jwts.builder()
                .claims(claims)
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
