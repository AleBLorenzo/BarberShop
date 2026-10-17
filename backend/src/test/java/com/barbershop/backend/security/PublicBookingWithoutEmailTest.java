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
 * Spec booking-contact-email-optional — "El email del cliente es opcional al
 * reservar" y "La identidad y la deduplicación no dependen del email":
 * la reserva PÚBLICA sin {@code clienteEmail} se crea (200, id + token), la
 * ficha {@code Cliente} nace sin email, el teléfono la reutiliza sin duplicar
 * y un email ya almacenado no se borra al reservar de nuevo sin email.
 *
 * <p>H2 {@code jdbc:h2:mem:testdb} es compartida entre clases: teléfonos y
 * emails únicos por UUID, {@code rateLimiter.limpiarContadores()} en
 * {@code @BeforeEach} (el techo diario global es clave compartida por diseño)
 * y sin dependencia del orden de ejecución.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicBookingWithoutEmailTest {

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

    @BeforeEach
    void fixtures() {
        rateLimiter.limpiarContadores();

        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Trabajador trabajador = trabajadorRepository.save(Trabajador.builder()
                .nombre("Sin Email Test " + sufijo)
                .email("sin-email-" + sufijo + "@barbershop.test")
                .passwordHash(passwordEncoder.encode("SinEmail12345"))
                .activo(true)
                .rol(Rol.WORKER)
                .build());
        trabajadorId = trabajador.getId();

        Servicio servicio = servicioRepository.save(Servicio.builder()
                .nombre("Corte sin email " + sufijo)
                .precio(new java.math.BigDecimal("15.00"))
                .duracionMinutos(30)
                .activo(true)
                .build());
        servicioId = servicio.getId();

        fecha = diaHabilFuturo();
    }

    @Test
    void reservaPublicaSinCampoEmailSeCreaYRespondeSinEmail() throws Exception {
        String telefono = telefonoUnico();

        MvcResult result = mockMvc.perform(publica(telefono, "09:00", null))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = data(result);
        assertThat(data.path("id").asLong()).isPositive();
        assertThat(data.path("tokenCancelacion").asText())
                .as("la respuesta incluye el token de cancelación")
                .isNotBlank();
        // La respuesta NO expone ningún campo de email (contrato intacto)
        assertThat(nombresDeCampo(data))
                .as("ReservaResponse no expone ningún campo de email")
                .noneMatch(nombre -> nombre.toLowerCase().contains("email"));

        Reserva reserva = reservaRepository.findById(data.path("id").asLong()).orElseThrow();
        assertThat(reserva.getId()).isPositive();
        // El cliente se lee por su teléfono (el proxy LAZY de la reserva no puede
        // inicializarse fuera de sesión con open-in-view=false)
        Cliente cliente = clienteRepository.findByTelefonoNormalizado(telefono).orElseThrow();
        assertThat(cliente.getEmail()).isNull();
    }

    @Test
    void emailAusenteNuloYSoloEspaciosCreanLaReserva() throws Exception {
        mockMvc.perform(publica(telefonoUnico(), "09:00", null))
                .andExpect(status().isOk());
        mockMvc.perform(publica(telefonoUnico(), "10:00", "null"))
                .andExpect(status().isOk());
        // Regresión del setter normalizador del DTO: "   " se trata como ausente
        mockMvc.perform(publica(telefonoUnico(), "11:00", "\"   \""))
                .andExpect(status().isOk());
    }

    @Test
    void fichaNuevaSinEmailYReutilizacionPorTelefonoNormalizado() throws Exception {
        String base = telefonoUnico();
        long clientesAntes = clienteRepository.count();

        MvcResult primera = mockMvc.perform(publica(base, "09:00", null))
                .andExpect(status().isOk()).andReturn();
        MvcResult segunda = mockMvc.perform(publica("+34" + base, "10:00", null))
                .andExpect(status().isOk()).andReturn();

        Cliente clientePrimera = clienteRepository.findByTelefonoNormalizado(base).orElseThrow();
        Reserva reservaPrimera = reservaDe(primera);
        Reserva reservaSegunda = reservaDe(segunda);

        // Ambas reservas comparten UNA sola ficha, sin email
        assertThat(reservaPrimera.getCliente().getId()).isEqualTo(clientePrimera.getId());
        assertThat(reservaSegunda.getCliente().getId()).isEqualTo(clientePrimera.getId());
        assertThat(clientePrimera.getTelefonoNormalizado()).isEqualTo(base);
        assertThat(clientePrimera.getEmail()).isNull();
        // `clientes` creció exactamente una fila (delta, no absoluto)
        assertThat(clienteRepository.count()).isEqualTo(clientesAntes + 1);
    }

    @Test
    void clienteExistenteConEmailConservaSuEmailAlReservarSinEmail() throws Exception {
        String base = telefonoUnico();
        String email = "conserva-" + UUID.randomUUID().toString().substring(0, 8) + "@ejemplo.com";

        // 1) Reserva CON email: la ficha nace con email
        MvcResult conEmail = mockMvc.perform(publica(base, "09:00", jsonString(email)))
                .andExpect(status().isOk()).andReturn();
        Long clienteId = reservaDe(conEmail).getCliente().getId();
        assertThat(clienteRepository.findById(clienteId).orElseThrow().getEmail()).isEqualTo(email);

        // 2) Misma persona reserva SIN email: la ficha se reutiliza y conserva el email
        MvcResult sinEmail = mockMvc.perform(publica(base, "10:00", null))
                .andExpect(status().isOk()).andReturn();

        assertThat(reservaDe(sinEmail).getCliente().getId()).isEqualTo(clienteId);
        assertThat(clienteRepository.findById(clienteId).orElseThrow().getEmail())
                .as("un email almacenado no se borra al reservar sin email")
                .isEqualTo(email);
    }

    // --- helpers ---

    /** Petición pública; {@code emailField} null omite el campo, si no va crudo (p. ej. "null"). */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder publica(
            String telefono, String hora, String emailField) {
        StringBuilder body = new StringBuilder("{")
                .append("\"servicioId\":").append(servicioId).append(',')
                .append("\"trabajadorId\":").append(trabajadorId).append(',')
                .append("\"fecha\":\"").append(fecha).append("\",")
                .append("\"horaInicio\":\"").append(hora).append("\",")
                .append("\"clienteNombre\":\"Cliente Sin Email\",")
                .append("\"clienteTelefono\":\"").append(telefono).append("\"");
        if (emailField != null) {
            body.append(",\"clienteEmail\":").append(emailField);
        }
        body.append('}');
        return post("/api/public/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Real-IP", "198.20.10.10")
                .content(body.toString());
    }

    private static String jsonString(String valor) {
        return "\"" + valor + "\"";
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private Reserva reservaDe(MvcResult result) throws Exception {
        Long id = data(result).path("id").asLong();
        assertThat(id).isPositive();
        return reservaRepository.findById(id).orElseThrow();
    }

    private static java.util.List<String> nombresDeCampo(JsonNode data) {
        java.util.List<String> nombres = new java.util.ArrayList<>();
        data.fieldNames().forEachRemaining(nombres::add);
        return nombres;
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
