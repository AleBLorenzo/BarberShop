package com.barbershop.backend.agenda;

import com.barbershop.backend.dto.DisponibilidadRequest;
import com.barbershop.backend.dto.DisponibilidadSlotDTO;
import com.barbershop.backend.dto.ReservaCreateRequest;
import com.barbershop.backend.model.Reserva;
import com.barbershop.backend.model.ReservaEstado;
import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.service.AgendaService;
import com.barbershop.backend.service.ReservaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tarea 2.9: jornada efectiva (D2) y su uso en disponibilidad, reserva y
 * reprogramación. Horario de test sembrado: lun–sáb 09:00–21:00 (en
 * producción 09:00–20:00), domingo cerrado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgendaDisponibilidadTest extends AgendaTestSupport {

    @Autowired
    private AgendaService agendaService;

    @Autowired
    private ReservaService reservaService;

    private Trabajador ana;
    private Trabajador luis;
    private Servicio corte;

    @BeforeEach
    void fixtures() {
        ana = crearTrabajador("Ana", Rol.WORKER);
        luis = crearTrabajador("Luis", Rol.WORKER);
        corte = crearServicio(30);
    }

    // ---------- AgendaService ----------

    @Test
    void martesPorDefectoEsLaJornadaSembrada() {
        Optional<AgendaService.Jornada> jornada = agendaService.jornadaEfectiva(ana, proximo(DayOfWeek.TUESDAY));

        assertThat(jornada).isPresent();
        assertThat(jornada.get().inicio()).isEqualTo(LocalTime.of(9, 0));
        assertThat(jornada.get().fin()).isEqualTo(LocalTime.of(21, 0));
        assertThat(jornada.get().bloqueos()).isEmpty();
    }

    @Test
    void domingoSinJornadaNiHuecos() {
        LocalDate domingo = proximo(DayOfWeek.SUNDAY);

        assertThat(agendaService.jornadaEfectiva(ana, domingo)).isEmpty();
        assertThat(agendaService.dia(ana, domingo).causa()).isEqualTo(AgendaService.CausaSinJornada.BARBERIA_CERRADA);
        assertThat(disponibilidad(domingo, ana.getId())).isEmpty();
    }

    @Test
    void horarioPropioReducidoLimitaLosHuecos() {
        crearHorarioPropio(luis, DayOfWeek.THURSDAY, true, LocalTime.of(9, 0), LocalTime.of(14, 0));

        List<DisponibilidadSlotDTO> slots = disponibilidad(proximo(DayOfWeek.THURSDAY), luis.getId());

        assertThat(slots).isNotEmpty();
        DisponibilidadSlotDTO ultimo = slots.get(slots.size() - 1);
        assertThat(ultimo.getHoraInicio()).isEqualTo(LocalTime.of(13, 30));
        assertThat(ultimo.getHoraFin()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    void horarioPropioFueraDelGeneralSeIntersecta() {
        // Override "antiguo" que ya no cabe (p. ej. el general se redujo después)
        crearHorarioPropio(luis, DayOfWeek.WEDNESDAY, true, LocalTime.of(8, 0), LocalTime.of(23, 0));

        AgendaService.Jornada jornada = agendaService.jornadaEfectiva(luis, proximo(DayOfWeek.WEDNESDAY)).orElseThrow();

        assertThat(jornada.inicio()).isEqualTo(LocalTime.of(9, 0));
        assertThat(jornada.fin()).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    void diaLibreSemanalSoloAfectaAlTrabajador() {
        crearHorarioPropio(ana, DayOfWeek.MONDAY, false, null, null);
        LocalDate lunes = proximo(DayOfWeek.MONDAY);

        assertThat(agendaService.jornadaEfectiva(ana, lunes)).isEmpty();
        assertThat(agendaService.dia(ana, lunes).causa()).isEqualTo(AgendaService.CausaSinJornada.DIA_LIBRE);
        assertThat(disponibilidad(lunes, ana.getId())).isEmpty();
        assertThat(disponibilidad(lunes, luis.getId())).isNotEmpty();
    }

    @Test
    void cierreGeneralVaciaLaDisponibilidadDeTodos() {
        LocalDate dia = proximo(DayOfWeek.FRIDAY);
        crearCierre(dia, null, "Festivo");

        assertThat(agendaService.jornadaEfectiva(ana, dia)).isEmpty();
        assertThat(agendaService.jornadaEfectiva(luis, dia)).isEmpty();
        assertThat(agendaService.dia(ana, dia).motivoCierre()).isEqualTo("Festivo");
        assertThat(disponibilidad(dia, null)).isEmpty();
        assertThat(agendaService.diasCerrados(dia, dia, null)).containsExactly(dia);
    }

    @Test
    void cierreDeTrabajadorNoAfectaAlResto() {
        LocalDate dia = proximo(DayOfWeek.FRIDAY);
        crearCierre(dia, ana, "Asuntos propios");

        assertThat(agendaService.jornadaEfectiva(ana, dia)).isEmpty();
        assertThat(agendaService.dia(ana, dia).causa()).isEqualTo(AgendaService.CausaSinJornada.CIERRE_TRABAJADOR);
        assertThat(agendaService.jornadaEfectiva(luis, dia)).isPresent();
        assertThat(disponibilidad(dia, luis.getId())).isNotEmpty();
        assertThat(agendaService.diasCerrados(dia, dia, ana.getId())).containsExactly(dia);
        assertThat(agendaService.diasCerrados(dia, dia, luis.getId())).isEmpty();
    }

    @Test
    void bloqueoMarcaHuecosNoDisponibles() {
        LocalDate dia = proximo(DayOfWeek.TUESDAY);
        crearBloqueo(ana, dia, LocalTime.of(13, 0), LocalTime.of(14, 0));

        List<DisponibilidadSlotDTO> slots = disponibilidad(dia, ana.getId());

        assertThat(slots).filteredOn(s -> s.getHoraInicio().isBefore(LocalTime.of(14, 0))
                        && s.getHoraFin().isAfter(LocalTime.of(13, 0)))
                .isNotEmpty()
                .allSatisfy(s -> assertThat(s.getDisponible()).isFalse());
        assertThat(slots).filteredOn(s -> s.getHoraInicio().equals(LocalTime.of(10, 0)))
                .allSatisfy(s -> assertThat(s.getDisponible()).isTrue());
    }

    @Test
    void diasCerradosRechazaRangosDeMasDe62Dias() throws Exception {
        LocalDate desde = LocalDate.now();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/public/dias-cerrados")
                        .param("desde", desde.toString())
                        .param("hasta", desde.plusDays(70).toString()))
                .andExpect(status().isBadRequest());
    }

    // ---------- ReservaService ----------

    @Test
    void reservaPublicaEnDomingoResponde400() throws Exception {
        mockMvc.perform(post("/api/public/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Real-IP", "198.51.100.21")
                        .content(cuerpoReservaPublica(corte.getId(), ana.getId(), proximo(DayOfWeek.SUNDAY), "10:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("La barbería está cerrada")));
    }

    @Test
    void reservaPublicaEnDiaCerradoDelTrabajadorResponde400() throws Exception {
        LocalDate dia = proximo(DayOfWeek.FRIDAY);
        crearCierre(dia, ana, null);
        long antes = reservaRepository.count();

        mockMvc.perform(post("/api/public/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Real-IP", "198.51.100.22")
                        .content(cuerpoReservaPublica(corte.getId(), ana.getId(), dia, "10:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El trabajador no trabaja ese día"));

        assertThat(reservaRepository.count()).isEqualTo(antes);
    }

    @Test
    void reservaFueraDeJornadaPropiaResponde400() throws Exception {
        crearHorarioPropio(luis, DayOfWeek.THURSDAY, true, LocalTime.of(9, 0), LocalTime.of(14, 0));

        mockMvc.perform(post("/api/public/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Real-IP", "198.51.100.23")
                        .content(cuerpoReservaPublica(corte.getId(), luis.getId(), proximo(DayOfWeek.THURSDAY), "13:45")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservaPublicaSobreBloqueoResponde409() throws Exception {
        LocalDate dia = proximo(DayOfWeek.TUESDAY);
        crearBloqueo(ana, dia, LocalTime.of(13, 0), LocalTime.of(14, 0));

        mockMvc.perform(post("/api/public/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Real-IP", "198.51.100.24")
                        .content(cuerpoReservaPublica(corte.getId(), ana.getId(), dia, "13:30")))
                .andExpect(status().isConflict());
    }

    @Test
    void reprogramarADiaCerradoResponde400YNoCambiaLaCita() throws Exception {
        LocalDate original = proximo(DayOfWeek.TUESDAY);
        LocalDate cerrado = proximo(DayOfWeek.FRIDAY);
        crearCierre(cerrado, null, "Festivo");
        Reserva reserva = crearReserva(ana, corte, crearCliente(null), original, LocalTime.of(10, 0),
                ReservaEstado.CONFIRMADA);

        mockMvc.perform(post("/api/public/reservas/reprogramar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + reserva.getTokenCancelacion() + "\",\"nuevaFecha\":\"" + cerrado
                                + "\",\"nuevaHoraInicio\":\"11:00\"}"))
                .andExpect(status().isBadRequest());

        Reserva tras = reservaRepository.findById(reserva.getId()).orElseThrow();
        assertThat(tras.getFecha()).isEqualTo(original);
        assertThat(tras.getHoraInicio()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void reprogramarSobreBloqueoResponde409YNoCambiaLaCita() throws Exception {
        LocalDate original = proximo(DayOfWeek.TUESDAY);
        LocalDate destino = proximo(DayOfWeek.WEDNESDAY);
        crearBloqueo(ana, destino, LocalTime.of(13, 0), LocalTime.of(14, 0));
        Reserva reserva = crearReserva(ana, corte, crearCliente(null), original, LocalTime.of(10, 0),
                ReservaEstado.CONFIRMADA);

        mockMvc.perform(post("/api/public/reservas/reprogramar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + reserva.getTokenCancelacion() + "\",\"nuevaFecha\":\"" + destino
                                + "\",\"nuevaHoraInicio\":\"13:30\"}"))
                .andExpect(status().isConflict());

        Reserva tras = reservaRepository.findById(reserva.getId()).orElseThrow();
        assertThat(tras.getFecha()).isEqualTo(original);
        assertThat(tras.getHoraInicio()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void trabajadorNoReservableRechazaReservaPublicaPeroNoLaDelPanel() throws Exception {
        luis.setReservable(false);
        trabajadorRepository.save(luis);
        LocalDate dia = proximo(DayOfWeek.TUESDAY);

        mockMvc.perform(post("/api/public/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Real-IP", "198.51.100.25")
                        .content(cuerpoReservaPublica(corte.getId(), luis.getId(), dia, "10:00")))
                .andExpect(status().isBadRequest());

        // Fuera de la disponibilidad pública sin trabajador
        assertThat(disponibilidad(dia, null)).noneMatch(s -> s.getTrabajadorId().equals(luis.getId()));

        // El panel del trabajador sí puede crear sus propias citas (D8)
        ReservaCreateRequest req = new ReservaCreateRequest();
        req.setServicioId(corte.getId());
        req.setFecha(dia);
        req.setHoraInicio(LocalTime.of(11, 0));
        req.setClienteNombre("Cliente Panel");
        req.setClienteTelefono(telefonoUnico());
        assertThat(reservaService.crearReservaParaTrabajador(req, luis.getId()).getId()).isPositive();
    }

    private List<DisponibilidadSlotDTO> disponibilidad(LocalDate fecha, Long trabajadorId) {
        DisponibilidadRequest req = new DisponibilidadRequest();
        req.setServicioId(corte.getId());
        req.setFecha(fecha);
        req.setTrabajadorId(trabajadorId);
        return reservaService.consultarDisponibilidad(req);
    }
}
