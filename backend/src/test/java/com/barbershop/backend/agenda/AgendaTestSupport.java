package com.barbershop.backend.agenda;

import com.barbershop.backend.config.HorarioDefaultsInitializer;
import com.barbershop.backend.model.BloqueoHorario;
import com.barbershop.backend.model.CierreDia;
import com.barbershop.backend.model.Cliente;
import com.barbershop.backend.model.HorarioTrabajador;
import com.barbershop.backend.model.Reserva;
import com.barbershop.backend.model.ReservaEstado;
import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.BloqueoHorarioRepository;
import com.barbershop.backend.repository.CierreDiaRepository;
import com.barbershop.backend.repository.ClienteRepository;
import com.barbershop.backend.repository.HorarioBarberiaRepository;
import com.barbershop.backend.repository.HorarioTrabajadorRepository;
import com.barbershop.backend.repository.ReservaRepository;
import com.barbershop.backend.repository.ServicioRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.security.RateLimiterService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Utilidades comunes de los tests de agenda (cambio admin-agenda-y-cierres).
 *
 * <p>La H2 en memoria se comparte entre contextos de Spring: cada test crea
 * datos únicos (UUID) y en {@code @AfterEach} borra los cierres, bloqueos y
 * horarios propios que creó y restaura el horario general sembrado, para no
 * afectar a otros tests (un cierre general o un horario reducido sí lo
 * harían).</p>
 */
abstract class AgendaTestSupport {

    protected static final String PASSWORD = "AgendaTest12345";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected TrabajadorRepository trabajadorRepository;
    @Autowired protected ServicioRepository servicioRepository;
    @Autowired protected ClienteRepository clienteRepository;
    @Autowired protected ReservaRepository reservaRepository;
    @Autowired protected HorarioBarberiaRepository horarioBarberiaRepository;
    @Autowired protected HorarioTrabajadorRepository horarioTrabajadorRepository;
    @Autowired protected CierreDiaRepository cierreDiaRepository;
    @Autowired protected BloqueoHorarioRepository bloqueoHorarioRepository;
    @Autowired protected HorarioDefaultsInitializer horarioDefaultsInitializer;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected RateLimiterService rateLimiter;

    private final List<Long> cierresCreados = new ArrayList<>();
    private final List<Long> bloqueosCreados = new ArrayList<>();
    private final List<Long> trabajadoresCreados = new ArrayList<>();

    @BeforeEach
    void prepararAgenda() {
        rateLimiter.limpiarContadores();
        restaurarHorarioGeneral();
    }

    @AfterEach
    void limpiarAgenda() {
        cierreDiaRepository.deleteAllById(cierresCreados);
        bloqueoHorarioRepository.deleteAllById(bloqueosCreados);
        for (Long id : trabajadoresCreados) {
            horarioTrabajadorRepository.deleteAll(horarioTrabajadorRepository.findByTrabajador_Id(id));
        }
        cierresCreados.clear();
        bloqueosCreados.clear();
        trabajadoresCreados.clear();
        restaurarHorarioGeneral();
    }

    /** Horario general recién sembrado desde app.business.* (test: 09–21, domingo cerrado). */
    protected void restaurarHorarioGeneral() {
        horarioBarberiaRepository.deleteAll();
        horarioDefaultsInitializer.run(null);
    }

    // --- fixtures ---

    protected Trabajador crearTrabajador(String nombre, Rol rol) {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Trabajador t = trabajadorRepository.save(Trabajador.builder()
                .nombre(nombre + " " + sufijo)
                .email("agenda-" + sufijo + "@barbershop.test")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(rol)
                .build());
        trabajadoresCreados.add(t.getId());
        return t;
    }

    protected Servicio crearServicio(int duracion) {
        return servicioRepository.save(Servicio.builder()
                .nombre("Servicio agenda " + UUID.randomUUID().toString().substring(0, 8))
                .precio(new BigDecimal("15.00"))
                .duracionMinutos(duracion)
                .activo(true)
                .build());
    }

    protected Cliente crearCliente(String email) {
        String telefono = telefonoUnico();
        return clienteRepository.save(Cliente.builder()
                .nombre("Cliente Agenda")
                .telefono(telefono)
                .telefonoNormalizado(telefono)
                .email(email)
                .build());
    }

    protected Reserva crearReserva(Trabajador t, Servicio s, Cliente c, LocalDate fecha, LocalTime hora,
                                   ReservaEstado estado) {
        return reservaRepository.save(Reserva.builder()
                .trabajador(t)
                .servicio(s)
                .cliente(c)
                .fecha(fecha)
                .horaInicio(hora)
                .horaFin(hora.plusMinutes(s.getDuracionMinutos()))
                .estado(estado)
                .build());
    }

    protected CierreDia crearCierre(LocalDate fecha, Trabajador trabajador, String motivo) {
        CierreDia c = cierreDiaRepository.save(CierreDia.builder()
                .fecha(fecha)
                .trabajador(trabajador)
                .motivo(motivo)
                .build());
        cierresCreados.add(c.getId());
        return c;
    }

    protected BloqueoHorario crearBloqueo(Trabajador t, LocalDate fecha, LocalTime inicio, LocalTime fin) {
        BloqueoHorario b = bloqueoHorarioRepository.save(BloqueoHorario.builder()
                .trabajador(t)
                .fecha(fecha)
                .horaInicio(inicio)
                .horaFin(fin)
                .motivo("Médico")
                .build());
        bloqueosCreados.add(b.getId());
        return b;
    }

    /** Registra para el borrado de {@code @AfterEach} un cierre creado por API/servicio. */
    protected void registrarCierre(Long id) {
        if (id != null) {
            cierresCreados.add(id);
        }
    }

    /** Registra para el borrado de {@code @AfterEach} un bloqueo creado por API/servicio. */
    protected void registrarBloqueo(Long id) {
        if (id != null) {
            bloqueosCreados.add(id);
        }
    }

    protected HorarioTrabajador crearHorarioPropio(Trabajador t, DayOfWeek dia, boolean trabaja,
                                                   LocalTime inicio, LocalTime fin) {
        return horarioTrabajadorRepository.save(HorarioTrabajador.builder()
                .trabajador(t)
                .diaSemana(dia)
                .trabaja(trabaja)
                .horaInicio(inicio)
                .horaFin(fin)
                .build());
    }

    // --- fechas ---

    /** Próxima fecha con ese día de la semana a partir de dentro de una semana (siempre futura). */
    protected static LocalDate proximo(DayOfWeek dia) {
        return LocalDate.now().plusDays(7).with(TemporalAdjusters.nextOrSame(dia));
    }

    // --- peticiones ---

    protected String login(Trabajador t) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + t.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    protected String cuerpoReservaPublica(Long servicioId, Long trabajadorId, LocalDate fecha, String hora) {
        return "{\"servicioId\":" + servicioId
                + ",\"trabajadorId\":" + trabajadorId
                + ",\"fecha\":\"" + fecha + "\""
                + ",\"horaInicio\":\"" + hora + "\""
                + ",\"clienteNombre\":\"Cliente Agenda\""
                + ",\"clienteTelefono\":\"" + telefonoUnico() + "\"}";
    }

    protected JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    protected static String telefonoUnico() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder("6");
        for (int i = 0; i < 8; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
