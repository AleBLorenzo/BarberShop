package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

import com.barbershop.backend.model.Cliente;
import com.barbershop.backend.model.Reserva;
import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.ClienteRepository;
import com.barbershop.backend.repository.ReservaRepository;
import com.barbershop.backend.repository.ServicioRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 * Spec client-deduplication — "Ninguna vía de alta crea duplicados":
 * verificación a nivel de ENDPOINT de que una reserva creada por el público
 * ({@code POST /api/public/reservas} con {@code +34…}) y otra creada desde el
 * panel del trabajador ({@code POST /api/worker/reservas} con los MISMOS
 * dígitos separados por espacios y el mismo email en otra caja) comparten UNA
 * SOLA ficha en {@code clientes} — el escenario de la spec con
 * {@code +34612345678} vs {@code 612 345 678}, pero con dígitos aleatorios
 * porque la BD H2 {@code testdb} es nominal y compartida entre contextos de
 * test (un {@code 612345678} fijo dejaría fila residual para otras clases).
 *
 * <p>Se drive por HTTP real contra los controllers (MockMvc), con login JWT
 * del trabajador como en {@link AuthorizationMatrixTest}: ninguna llamada de
 * servicio directa. El crecimiento de {@code clientes} se mide por DELTAS de
 * conteo (el {@code DataSeeder} puede haber poblado datos demo).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClientDedupeEndpointChannelsTest {

    private static final String PASSWORD = "DedupeChannels12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @Autowired
    private ServicioRepository servicioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimiterService rateLimiter;

    private Long trabajadorId;
    private Long servicioId;
    private LocalDate fecha;

    /** Identidad aleatoria del escenario (teléfono normalizado + email). */
    private String telefonoNormalizado;
    private String telefonoPublico;
    private String telefonoPanel;
    private String email;
    private String emailOtraCaja;

    @BeforeEach
    void fixtures() {
        // El techo diario global es clave compartida por diseño: se limpia para
        // no depender del orden respecto a otras clases de este contexto.
        rateLimiter.limpiarContadores();

        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Trabajador trabajador = trabajadorRepository.save(Trabajador.builder()
                .nombre("Canales Dedupe " + sufijo)
                .email("canales-" + sufijo + "@barbershop.test")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(Rol.WORKER)
                .build());
        trabajadorId = trabajador.getId();

        Servicio servicio = servicioRepository.save(Servicio.builder()
                .nombre("Corte " + sufijo)
                .precio(new java.math.BigDecimal("15.00"))
                .duracionMinutos(30)
                .activo(true)
                .build());
        servicioId = servicio.getId();

        fecha = diaHabilFuturo();

        // Escenario de la spec con dígitos únicos: público con "+34…",
        // panel con los mismos dígitos separados por espacios.
        telefonoNormalizado = telefonoUnico();
        telefonoPublico = "+34" + telefonoNormalizado;
        telefonoPanel = telefonoNormalizado.substring(0, 3) + " "
                + telefonoNormalizado.substring(3, 6) + " "
                + telefonoNormalizado.substring(6);
        email = "canal-" + sufijo + "@ejemplo.com";
        emailOtraCaja = email.toUpperCase(java.util.Locale.ROOT);
    }

    @Test
    void reservaPublicaYDelPanelCompartenUnaSoloFichaDeCliente() throws Exception {
        long clientesAntes = clienteRepository.count();

        // 1) Reserva PÚBLICA con el teléfono en formato internacional
        MvcResult publica = mockMvc.perform(post("/api/public/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Real-IP", "198.19.7.7")
                        .content(reservaJson(telefonoPublico, email, "10:00")))
                .andExpect(status().isOk())
                .andReturn();
        Long reservaPublicaId = idDesdeRespuesta(publica);

        // 2) Reserva del PANEL del trabajador (login JWT) con el mismo teléfono
        //    escrito con espacios y el mismo email en otra caja
        String token = loginWorker();
        MvcResult panel = mockMvc.perform(post("/api/worker/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(reservaJson(telefonoPanel, emailOtraCaja, "11:00")))
                .andExpect(status().isOk())
                .andReturn();
        Long reservaPanelId = idDesdeRespuesta(panel);

        // Ambas reservas apuntan al MISMO cliente_id
        Cliente clientePublica = clienteDeReserva(reservaPublicaId);
        Cliente clientePanel = clienteDeReserva(reservaPanelId);
        assertThat(clientePanel.getId())
                .as("la reserva del panel reutiliza la ficha creada por la reserva pública")
                .isEqualTo(clientePublica.getId());

        // La tabla `clientes` creció EXACTAMENTE una fila (deltas, no absolutos)
        assertThat(clienteRepository.count()).isEqualTo(clientesAntes + 1);

        // Y esa única fila es la identidad canónica del escenario
        var filasConIdentidad = clienteRepository.findAll().stream()
                .filter(c -> telefonoNormalizado.equals(c.getTelefonoNormalizado()))
                .toList();
        assertThat(filasConIdentidad).hasSize(1);
        assertThat(filasConIdentidad.get(0).getId()).isEqualTo(clientePublica.getId());
    }

    // --- helpers ---

    /** Cuerpo JSON común a ambos endpoints (mismo DTO {@code ReservaCreateRequest}). */
    private String reservaJson(String telefono, String email, String hora) {
        return "{"
                + "\"servicioId\":" + servicioId + ","
                + "\"trabajadorId\":" + trabajadorId + ","
                + "\"fecha\":\"" + fecha + "\","
                + "\"horaInicio\":\"" + hora + "\","
                + "\"clienteNombre\":\"Cliente Canales\","
                + "\"clienteTelefono\":\"" + telefono + "\","
                + "\"clienteEmail\":\"" + email + "\""
                + "}";
    }

    /** Login del trabajador y extracción del access token (patrón AuthorizationMatrixTest). */
    private String loginWorker() throws Exception {
        String email = trabajadorRepository.findById(trabajadorId).orElseThrow().getEmail();
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return data.path("accessToken").asText();
    }

    /** {@code data.id} de la respuesta {@code ApiResponse<ReservaResponse>}. */
    private Long idDesdeRespuesta(MvcResult result) throws Exception {
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("id").asLong()).isPositive();
        return data.path("id").asLong();
    }

    private Cliente clienteDeReserva(Long reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId).orElseThrow();
        assertThat(reserva.getCliente()).isNotNull();
        return reserva.getCliente();
    }

    /** Teléfono canónico de 9 dígitos empezando por 6 (la normalización no lo recorta). */
    private static String telefonoUnico() {
        java.util.Random random = new java.util.Random();
        StringBuilder sb = new StringBuilder("6");
        for (int i = 0; i < 8; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /** Próximo día hábil futuro (sin domingos: día cerrado por negocio). */
    private static LocalDate diaHabilFuturo() {
        LocalDate dia = LocalDate.now().plusDays(2);
        while (dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            dia = dia.plusDays(1);
        }
        return dia;
    }
}
