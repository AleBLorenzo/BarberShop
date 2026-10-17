package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;

import com.barbershop.backend.model.Cliente;
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
 * Spec booking-contact-email-optional — "Validación de formato del email solo
 * cuando está presente": un email inválido → 400 con mensaje y sin crear
 * reserva ni ficha; un email válido con espacios/mayúsculas se persiste
 * normalizado ({@code trim} + minúsculas); un email de solo espacios se trata
 * como ausente (regresión del setter normalizador del DTO); nombre y teléfono
 * siguen siendo obligatorios.
 *
 * <p>H2 compartida: datos únicos por UUID y contadores de rate limit limpiados
 * en {@code @BeforeEach}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicBookingEmailValidationTest {

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
                .nombre("Validacion Email " + sufijo)
                .email("validacion-" + sufijo + "@barbershop.test")
                .passwordHash(passwordEncoder.encode("Validacion12345"))
                .activo(true)
                .rol(Rol.WORKER)
                .build());
        trabajadorId = trabajador.getId();

        Servicio servicio = servicioRepository.save(Servicio.builder()
                .nombre("Corte validacion " + sufijo)
                .precio(new java.math.BigDecimal("15.00"))
                .duracionMinutos(30)
                .activo(true)
                .build());
        servicioId = servicio.getId();

        fecha = diaHabilFuturo();
    }

    @Test
    void emailInvalidoDevuelve400ConMensajeYSinCrearNada() throws Exception {
        String telefono = telefonoUnico();
        long reservasAntes = reservaRepository.count();
        long clientesAntes = clienteRepository.count();

        mockMvc.perform(publica(telefono, "09:00", "\"no-es-un-email\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El email no tiene un formato válido"));

        assertThat(reservaRepository.count())
                .as("una validación de formato no crea reserva")
                .isEqualTo(reservasAntes);
        assertThat(clientesAntes).isEqualTo(clienteRepository.count());
    }

    @Test
    void emailValidoConEspaciosYMayusculasSePersisteNormalizado() throws Exception {
        String telefono = telefonoUnico();

        MvcResult result = mockMvc.perform(
                        publica(telefono, "09:00", "\"  Cliente@Ejemplo.COM  \""))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        Long reservaId = data.path("id").asLong();
        assertThat(reservaId).isPositive();

        Cliente cliente = clienteRepository.findByTelefonoNormalizado(telefono).orElseThrow();
        assertThat(cliente.getEmail())
                .as("el email se guarda sin espacios y en minúsculas")
                .isEqualTo("cliente@ejemplo.com");
    }

    @Test
    void emailSoloEspaciosSeTrataComoAusente() throws Exception {
        String telefono = telefonoUnico();

        mockMvc.perform(publica(telefono, "09:00", "\"   \""))
                .andExpect(status().isOk());

        Cliente cliente = clienteRepository.findByTelefonoNormalizado(telefono).orElseThrow();
        assertThat(cliente.getEmail()).isNull();
    }

    @Test
    void nombreYTelefonoSiguenSiendoObligatorios() throws Exception {
        // Nombre ausente → 400
        mockMvc.perform(publicaRaw(cuerpo(null, jsonString(telefonoUnico()), null, "09:00"), "198.20.20.21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("nombre del cliente es obligatorio")));

        // Teléfono vacío → 400
        mockMvc.perform(publicaRaw(cuerpo(jsonString("Cliente Validacion"), "\"\"", null, "09:00"), "198.20.20.22"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("teléfono")));

        // Teléfono solo espacios → 400
        mockMvc.perform(publicaRaw(cuerpo(jsonString("Cliente Validacion"), "\"   \"", null, "09:00"), "198.20.20.23"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("teléfono")));
    }

    // --- helpers ---

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder publica(
            String telefono, String hora, String emailField) {
        return publicaRaw(cuerpo(jsonString("Cliente Validacion"), jsonString(telefono), emailField, hora),
                "198.20.20.20");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder publicaRaw(
            String body, String ip) {
        return post("/api/public/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Real-IP", ip)
                .content(body);
    }

    /** Cuerpo JSON flexible; cada campo null se OMITE del payload. */
    private String cuerpo(String nombreRaw, String telefonoRaw, String emailRaw, String hora) {
        StringBuilder sb = new StringBuilder("{")
                .append("\"servicioId\":").append(servicioId).append(',')
                .append("\"trabajadorId\":").append(trabajadorId).append(',')
                .append("\"fecha\":\"").append(fecha).append("\",")
                .append("\"horaInicio\":\"").append(hora).append("\"");
        if (nombreRaw != null) {
            sb.append(",\"clienteNombre\":").append(nombreRaw);
        }
        if (telefonoRaw != null) {
            sb.append(",\"clienteTelefono\":").append(telefonoRaw);
        }
        if (emailRaw != null) {
            sb.append(",\"clienteEmail\":").append(emailRaw);
        }
        sb.append('}');
        return sb.toString();
    }

    private static String jsonString(String valor) {
        return "\"" + valor + "\"";
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
