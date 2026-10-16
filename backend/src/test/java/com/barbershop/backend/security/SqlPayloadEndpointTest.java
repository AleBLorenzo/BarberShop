package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.barbershop.backend.model.Cliente;
import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.ClienteRepository;
import com.barbershop.backend.repository.ServicioRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.service.ServicioService;
import com.barbershop.backend.service.TrabajadorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Spec sql-injection-hardening — regresión de payloads en los puntos de
 * entrada reales (D1): payloads clásicos de SQL injection contra
 * {@code GET /api/admin/clientes?search=}, {@code GET /api/admin/trabajadores}
 * y {@code GET /api/admin/servicios}, {@code POST /api/auth/login},
 * {@code POST /api/public/reservas} y {@code GET /api/public/reservas} →
 * siempre 200/401/4xx controlado, NUNCA 500, con el payload tratado como
 * texto literal. Después de la andanada se verifica la INTEGRIDAD (tarea 1.5):
 * las tablas siguen consultables, la búsqueda literal de un cliente de control
 * lo sigue devolviendo (NO se devuelven todos) y un usuario de control sigue
 * logueándose — detecta DDL/DML efectivo aunque el scan de fuentes falle.
 *
 * <p>Nota: los endpoints admin de trabajadores/servicios no aceptan hoy ningún
 * parámetro {@code search} (verificado en los controladores reales); los payloads
 * se envían igualmente como query param —deben ser ignorados sin 500— y las
 * rutas LIKE reales de {@code TrabajadorService}/{@code ServicioService} se ejercitan
 * además directamente en el nivel de servicio.</p>
 *
 * <p>Aislamiento (H2 {@code testdb} compartido entre contextos): emails y
 * teléfonos únicos con {@code UUID} por método, {@code limpiarContadores()} en
 * {@code @BeforeEach} (buckets de login/reserva por IP y clave diaria global),
 * IP única por petición vía {@code X-Real-IP} y fixtures propios por método.
 * Sin dependencia de orden.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class SqlPayloadEndpointTest {

    private static final String PASSWORD = "SqlPayloadTest12345";

    /**
     * Arsenal de payloads clásicos (incluye unicode y %00 como texto literal:
     * MockMvc entrega los params ya decodificados, así que el byte NUL no se
     * simula; el literal "%00" cubre el caso de uso sin depender del soporte
     * de NUL de cada motor).
     */
    private static final List<String> PAYLOADS = List.of(
            "' OR '1'='1",
            "'; DROP TABLE trabajadores; --",
            "%' OR 1=1 --",
            "' OR '1'='1'--",
            "' OR 1=1 -- 𝔘𝔫𝔦𝔠𝔬𝔡𝔢 😀",
            "%00",
            "or 1=1 --");

    /** Bolsa estática de slots futuros sin domingos (patrón BookingSpamTest). */
    private static final List<String[]> SLOTS = new ArrayList<>();

    static {
        LocalDate dia = LocalDate.now().plusDays(1);
        while (SLOTS.size() < 40) {
            if (dia.getDayOfWeek() != DayOfWeek.SUNDAY) {
                for (int k = 0; k < 20; k++) {
                    SLOTS.add(new String[]{dia.toString(), String.format("%02d:%02d", 9 + k / 2, (k % 2) * 30)});
                }
            }
            dia = dia.plusDays(1);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ServicioRepository servicioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimiterService rateLimiter;

    @Autowired
    private TrabajadorService trabajadorService;

    @Autowired
    private ServicioService servicioService;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;
    private String controlEmail;
    private String controlNombre;
    private String controlTelefono;
    private Long trabajadorId;
    private Long servicioId;
    private int secuenciaIp;
    private int secuenciaSlot;

    @BeforeEach
    void fixtures() throws Exception {
        rateLimiter.limpiarContadores();
        secuenciaIp = 10;
        secuenciaSlot = 0;
        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        // Admin propio por método (token para las rutas /api/admin/**)
        String adminEmail = "sqli-admin-" + sufijo + "@barbershop.test";
        guardarTrabajador(adminEmail, Rol.ADMIN);

        // Usuario de control para verificar que el login sigue funcionando
        controlEmail = "sqli-ctrl-" + sufijo + "@barbershop.test";
        guardarTrabajador(controlEmail, Rol.WORKER);

        // Cliente de control con nombre/teléfono únicos
        controlNombre = "CtrlSqli-" + sufijo;
        controlTelefono = telefonoUnico();
        clienteRepository.save(Cliente.builder()
                .nombre(controlNombre)
                .telefono(controlTelefono)
                .build());

        // Fixtures de reserva pública (trabajador + servicio propios por método)
        trabajadorId = trabajadorRepository.save(Trabajador.builder()
                .nombre("SQLi Trab " + sufijo)
                .email("sqli-trab-" + sufijo + "@barbershop.test")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(Rol.WORKER)
                .build()).getId();
        servicioId = servicioRepository.save(Servicio.builder()
                .nombre("SQLi Servicio " + sufijo)
                .precio(new BigDecimal("15.00"))
                .duracionMinutos(30)
                .activo(true)
                .build()).getId();

        adminToken = loginYDevuelveToken(adminEmail, PASSWORD);
    }

    @AfterEach
    void limpiarResiduo() {
        // Deja los buckets limpios para las clases siguientes: los payloads de
        // login dejan buckets por IP/email en memoria (tarea 5.8).
        rateLimiter.limpiarContadores();
    }

    // ===================== Tarea 1.4: payloads =====================

    @Test
    void payloadsEnBusquedaDeClientesNoFallanNiDevuelvenTodos() throws Exception {
        int totalClientes = nombres(dataClientes(null)).size();

        for (String payload : PAYLOADS) {
            JsonNode data = dataClientes(payload);
            assertThat(data.isArray()).isTrue();
            List<String> nombres = nombres(data);
            // Payload tratado como texto literal: NO devuelve «todos los clientes»
            assertThat(nombres)
                    .describedAs("El payload %s no debe actuar como tautología", payload)
                    .doesNotContain(controlNombre);
            assertThat(nombres.size())
                    .describedAs("El payload %s no debe devolver todos los clientes", payload)
                    .isLessThan(totalClientes);
        }
    }

    @Test
    void payloadsEnTrabajadoresYServiciosNoProducen500() throws Exception {
        for (String payload : PAYLOADS) {
            mockMvc.perform(get("/api/admin/trabajadores").param("search", payload)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/admin/servicios").param("search", payload)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }
        // Las rutas LIKE reales de estos dos services (hoy sin parámetro search
        // en el endpoint) reciben el mismo arsenal directamente, sin excepción.
        for (String payload : PAYLOADS) {
            assertThat(trabajadorService.searchActivosByNombre(payload)).isNotNull();
            assertThat(servicioService.searchActivosByNombre(payload)).isNotNull();
        }
    }

    @Test
    void payloadsEnLoginNoAutenticanNiProducen500() throws Exception {
        // Payload crudo: lo rechaza la validación @Email del DTO (4xx controlado)
        for (String payload : PAYLOADS) {
            MvcResult resultado = mockMvc.perform(loginRequest(payload, "cualquier-password"))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .describedAs("Login con payload %s nunca 500", payload)
                            .isBetween(400, 499))
                    .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                    .andReturn();
            assertThat(resultado.getResponse().getContentAsString())
                    .doesNotContain("accessToken");
        }
        // Payload con formato de email válido (supera @Email): llega a la
        // autenticación y responde 401 INVALID_CREDENTIALS sin emitir tokens.
        MvcResult resultado = mockMvc.perform(loginRequest("x'or'1'='1'--@evil.com", "password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andReturn();
        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("accessToken");
    }

    @Test
    void payloadsEnReservaPublicaNoRompenElFlujo() throws Exception {
        // Nombre con payload + teléfono válido → creación 200, texto literal
        List<String> nombresPayload = List.of(
                "' OR '1'='1",
                "'; DROP TABLE trabajadores; --",
                "Reserva 𝔘𝔫𝔦𝔠𝔬𝔡𝔢 😀");
        for (String nombre : nombresPayload) {
            mockMvc.perform(reservaPublica(nombre, telefonoUnico()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .describedAs("Reserva con nombre payload %s", nombre)
                            .isEqualTo(200))
                    .andExpect(jsonPath("$.success").value(true));
        }
        // El payload queda PERSISTIDO como texto literal (no interpreta DDL)
        assertThat(clienteRepository.findByNombreIgnoreCase("'; DROP TABLE trabajadores; --"))
                .isPresent();

        // Teléfono con payload → validación 4xx controlada (nunca 500)
        for (String payload : List.of("' OR '1'='1", "'; DROP TABLE trabajadores; --")) {
            mockMvc.perform(reservaPublica("Cliente SQLi", payload))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .describedAs("Reserva con teléfono payload %s", payload)
                            .isBetween(400, 499))
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void payloadsEnMisCitasPublicoNoProducen500() throws Exception {
        for (String payload : PAYLOADS) {
            // Teléfono con payload + token inventado → 404 del flujo normal
            mockMvc.perform(get("/api/public/reservas")
                            .param("telefono", payload)
                            .param("token", UUID.randomUUID().toString()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .describedAs("Mis citas con teléfono payload %s", payload)
                            .isEqualTo(404));
            // Teléfono de control + token con payload → 404 (no filtra nada)
            mockMvc.perform(get("/api/public/reservas")
                            .param("telefono", controlTelefono)
                            .param("token", payload))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .describedAs("Mis citas con token payload %s", payload)
                            .isEqualTo(404));
        }
    }

    // ===================== Tarea 1.5: integridad tras los payloads =====================

    @Test
    void integridadDeLaBaseYUsuariosTrasLosPayloads() throws Exception {
        int totalClientes = nombres(dataClientes(null)).size();

        // Andanada completa de payloads por todos los puntos de entrada
        for (String payload : PAYLOADS) {
            dataClientes(payload);                                                     // búsqueda clientes
            mockMvc.perform(get("/api/admin/trabajadores").param("search", payload)
                    .header("Authorization", "Bearer " + adminToken)).andReturn();     // trabajadores
            mockMvc.perform(get("/api/admin/servicios").param("search", payload)
                    .header("Authorization", "Bearer " + adminToken)).andReturn();     // servicios
            mockMvc.perform(loginRequest(payload, "mala")).andReturn();                 // login
            mockMvc.perform(get("/api/public/reservas").param("telefono", payload)
                    .param("token", UUID.randomUUID().toString())).andReturn();         // mis citas
        }
        for (String nombre : List.of("' OR '1'='1", "'; DROP TABLE trabajadores; --")) {
            mockMvc.perform(reservaPublica(nombre, telefonoUnico())).andReturn();       // reserva pública
        }

        // Las tablas siguen existiendo y siendo consultables (detecta DDL efectivo)
        for (String tabla : List.of("clientes", "trabajadores", "servicios", "reservas")) {
            Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class);
            assertThat(n).describedAs("La tabla %s sigue consultable", tabla).isNotNull();
        }
        assertThat(trabajadorRepository.count())
                .describedAs("Los trabajadores de control siguen existiendo")
                .isGreaterThanOrEqualTo(2);

        // La búsqueda LITERAL del cliente de control lo sigue devolviendo…
        assertThat(nombres(dataClientes(controlNombre))).containsExactly(controlNombre);

        // …y el payload NO devuelve todos los clientes (ni siquiera al control)
        assertThat(nombres(dataClientes("' OR '1'='1")))
                .describedAs("Tras los payloads la búsqueda por tautología no devuelve todo")
                .doesNotContain(controlNombre)
                .hasSizeLessThan(totalClientes);

        // El usuario de control sigue logueándose con normalidad
        mockMvc.perform(loginRequest(controlEmail, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    // ===================== helpers =====================

    private void guardarTrabajador(String email, Rol rol) {
        trabajadorRepository.save(Trabajador.builder()
                .nombre("SQLi " + rol)
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(rol)
                .build());
    }

    /** Campo {@code data} de un GET /api/admin/clientes (con search o sin él), validando 200. */
    private JsonNode dataClientes(String search) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/admin/clientes")
                .header("Authorization", "Bearer " + adminToken);
        if (search != null) {
            request.param("search", search);
        }
        MvcResult resultado = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
    }

    private static List<String> nombres(JsonNode data) {
        List<String> nombres = new ArrayList<>();
        for (JsonNode nodo : data) {
            nombres.add(nodo.path("nombre").asText());
        }
        return nombres;
    }

    /** Petición de login con IP propia (bucket por IP aislado por petición). */
    private MockHttpServletRequestBuilder loginRequest(String email, String password) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("email", email);
        body.put("password", password);
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .with(xRealIp("198.51.100." + secuenciaIp++));
    }

    /** Reserva pública con slot de la bolsa y teléfono siempre único. */
    private MockHttpServletRequestBuilder reservaPublica(String nombre, String telefono) throws Exception {
        String[] slot = SLOTS.get(secuenciaSlot++ % SLOTS.size());
        ObjectNode body = objectMapper.createObjectNode();
        body.put("servicioId", servicioId);
        body.put("trabajadorId", trabajadorId);
        body.put("fecha", slot[0]);
        body.put("horaInicio", slot[1]);
        body.put("clienteNombre", nombre);
        body.put("clienteTelefono", telefono);
        return post("/api/public/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .with(xRealIp("203.0.113." + (secuenciaIp++ % 250 + 1)));
    }

    private String loginYDevuelveToken(String email, String password) throws Exception {
        MvcResult resultado = mockMvc.perform(loginRequest(email, password))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private static RequestPostProcessor xRealIp(String ip) {
        return request -> {
            request.addHeader("X-Real-IP", ip);
            return request;
        };
    }

    /** Teléfono canónico de 9 dígitos empezando por 6 (no lo recorta la normalización). */
    private static String telefonoUnico() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder("6");
        for (int i = 0; i < 8; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
