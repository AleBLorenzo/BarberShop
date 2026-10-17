package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Random;
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
 * Spec booking-contact-email-optional — "Reserva creada por el trabajador sin
 * email del cliente": {@code POST /api/worker/reservas} con el token del
 * trabajador y sin {@code clienteEmail} → 200, la cita queda asignada al
 * trabajador del JWT (no al {@code trabajadorId} del cuerpo, que aquí se omite)
 * y la ficha del cliente se crea sin email.
 *
 * <p>H2 compartida: datos únicos por UUID y limpieza de contadores en
 * {@code @BeforeEach}. Este endpoint NO usa {@code @Valid} (WorkerController),
 * así que la validación es la del servicio — prueba también de esa capa.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkerBookingWithoutEmailTest {

    private static final String PASSWORD = "WorkerSinEmail12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private ClienteRepository clienteRepository;

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

    @BeforeEach
    void fixtures() {
        rateLimiter.limpiarContadores();

        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Trabajador trabajador = trabajadorRepository.save(Trabajador.builder()
                .nombre("Worker Sin Email " + sufijo)
                .email("worker-sin-email-" + sufijo + "@barbershop.test")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(Rol.WORKER)
                .build());
        trabajadorId = trabajador.getId();

        Servicio servicio = servicioRepository.save(Servicio.builder()
                .nombre("Corte worker " + sufijo)
                .precio(new java.math.BigDecimal("15.00"))
                .duracionMinutos(30)
                .activo(true)
                .build());
        servicioId = servicio.getId();

        fecha = diaHabilFuturo();
    }

    @Test
    void reservaDelTrabajadorSinEmailSeAsignaAlTrabajadorDelJwt() throws Exception {
        String token = loginWorker();
        String telefono = telefonoUnico();

        MvcResult result = mockMvc.perform(post("/api/worker/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(cuerpoSinEmail(telefono, "09:00", null)))
                .andExpect(status().isOk())
                .andReturn();

        Long reservaId = idDesdeRespuesta(result);
        Reserva reserva = reservaRepository.findById(reservaId).orElseThrow();

        assertThat(reserva.getTrabajador().getId())
                .as("el trabajador destino sale del JWT, no del cuerpo")
                .isEqualTo(trabajadorId);
        assertThat(reserva.getTokenCancelacion()).isNotNull();

        // El cliente se lee por teléfono (el proxy LAZY no se inicializa sin sesión)
        Cliente cliente = clienteRepository.findByTelefonoNormalizado(telefono).orElseThrow();
        assertThat(cliente.getEmail())
                .as("sin email, la ficha se crea sin correo")
                .isNull();
    }

    @Test
    void emailSoloEspaciosTambienSeTrataComoAusenteEnElPanel() throws Exception {
        String token = loginWorker();
        String telefono = telefonoUnico();

        MvcResult result = mockMvc.perform(post("/api/worker/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(cuerpoSinEmail(telefono, "10:00", "\"   \"")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(reservaRepository.findById(idDesdeRespuesta(result))).isPresent();
        Cliente cliente = clienteRepository.findByTelefonoNormalizado(telefono).orElseThrow();
        assertThat(cliente.getEmail()).isNull();
    }

    // --- helpers ---

    /** Cuerpo del panel SIN el campo del trabajador (lo aporta el JWT). */
    private String cuerpoSinEmail(String telefono, String hora, String emailRaw) {
        StringBuilder sb = new StringBuilder("{")
                .append("\"servicioId\":").append(servicioId).append(',')
                .append("\"fecha\":\"").append(fecha).append("\",")
                .append("\"horaInicio\":\"").append(hora).append("\",")
                .append("\"clienteNombre\":\"Cliente Del Panel\",")
                .append("\"clienteTelefono\":\"").append(telefono).append("\"");
        if (emailRaw != null) {
            sb.append(",\"clienteEmail\":").append(emailRaw);
        }
        sb.append('}');
        return sb.toString();
    }

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

    private Long idDesdeRespuesta(MvcResult result) throws Exception {
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("id").asLong()).isPositive();
        return data.path("id").asLong();
    }

    /** Teléfono canónico de 9 dígitos empezando por 6 (la normalización no lo recorta). */
    private static String telefonoUnico() {
        Random random = new Random();
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
