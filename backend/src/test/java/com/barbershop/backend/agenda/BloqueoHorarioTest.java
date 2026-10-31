package com.barbershop.backend.agenda;

import com.barbershop.backend.dto.DisponibilidadRequest;
import com.barbershop.backend.dto.DisponibilidadSlotDTO;
import com.barbershop.backend.model.BloqueoHorario;
import com.barbershop.backend.model.Reserva;
import com.barbershop.backend.model.ReservaEstado;
import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.service.ReservaService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tarea 6.5: bloqueos de horas libres (D5) y agenda del trabajador (D11).
 * El trabajador del bloqueo sale siempre del JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BloqueoHorarioTest extends AgendaTestSupport {

    @Autowired private ReservaService reservaService;

    private Trabajador admin;
    private Trabajador ana;
    private Trabajador luis;
    private Servicio corte;
    private String tokenAna;
    private String tokenLuis;
    private String tokenAdmin;
    private LocalDate dia;

    @BeforeEach
    void fixtures() throws Exception {
        admin = crearTrabajador("Admin Bloqueos", Rol.ADMIN);
        ana = crearTrabajador("Ana", Rol.WORKER);
        luis = crearTrabajador("Luis", Rol.WORKER);
        corte = crearServicio(30);
        tokenAna = login(ana);
        tokenLuis = login(luis);
        tokenAdmin = login(admin);
        dia = proximo(DayOfWeek.WEDNESDAY);
    }

    @Test
    void bloqueoCreaHuecoNoDisponibleEIgnoraElTrabajadorIdDelCuerpo() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/worker/bloqueos")
                        .header("Authorization", "Bearer " + tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(dia, "13:00", "14:00", "Comida", luis.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trabajadorId").value(ana.getId()))
                .andExpect(jsonPath("$.data.motivo").value("Comida"))
                .andReturn();
        long id = data(result).path("id").asLong();
        registrarBloqueo(id);

        BloqueoHorario guardado = bloqueoHorarioRepository.findById(id).orElseThrow();
        assertThat(guardado.getTrabajador().getId()).isEqualTo(ana.getId());
        assertThat(bloqueoHorarioRepository.findByTrabajador_IdAndFechaOrderByHoraInicioAsc(luis.getId(), dia)).isEmpty();

        List<DisponibilidadSlotDTO> slots = disponibilidad(dia, ana.getId());
        assertThat(slots).filteredOn(s -> s.getHoraInicio().isBefore(LocalTime.of(14, 0))
                        && s.getHoraFin().isAfter(LocalTime.of(13, 0)))
                .isNotEmpty()
                .allSatisfy(s -> assertThat(s.getDisponible()).isFalse());
        // Luis no se ve afectado
        assertThat(disponibilidad(dia, luis.getId())).filteredOn(s -> s.getHoraInicio().equals(LocalTime.of(13, 0)))
                .allSatisfy(s -> assertThat(s.getDisponible()).isTrue());
    }

    @Test
    void bloqueoSobreCitaActivaResponde409ConLaCita() throws Exception {
        Reserva cita = crearReserva(luis, corte, crearCliente(null), dia, LocalTime.of(11, 0), ReservaEstado.CONFIRMADA);

        mockMvc.perform(post("/api/worker/bloqueos")
                        .header("Authorization", "Bearer " + tokenLuis)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(dia, "10:00", "12:00", null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("BLOQUEO_CONFLICTO"))
                .andExpect(jsonPath("$.data.citas[0].reservaId").value(cita.getId()))
                .andExpect(jsonPath("$.data.citas[0].horaInicio").value("11:00:00"));

        assertThat(bloqueoHorarioRepository.findByTrabajador_IdAndFechaOrderByHoraInicioAsc(luis.getId(), dia)).isEmpty();
    }

    @Test
    void bloqueoSobreOtroBloqueoResponde409() throws Exception {
        crearBloqueo(ana, dia, LocalTime.of(16, 0), LocalTime.of(17, 0));

        mockMvc.perform(post("/api/worker/bloqueos")
                        .header("Authorization", "Bearer " + tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(dia, "16:30", "18:00", null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.bloqueos[0].horaInicio").value("16:00:00"));
    }

    @Test
    void rangoInvalidoFechaPasadaYDiaCerradoResponden400() throws Exception {
        mockMvc.perform(post("/api/worker/bloqueos")
                        .header("Authorization", "Bearer " + tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(dia, "14:00", "13:00", null, null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/worker/bloqueos")
                        .header("Authorization", "Bearer " + tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(LocalDate.now().minusDays(1), "10:00", "11:00", null, null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/worker/bloqueos")
                        .header("Authorization", "Bearer " + tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(proximo(DayOfWeek.SUNDAY), "10:00", "11:00", null, null)))
                .andExpect(status().isBadRequest());
        // Fuera de la jornada sembrada (09–21)
        mockMvc.perform(post("/api/worker/bloqueos")
                        .header("Authorization", "Bearer " + tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(dia, "07:00", "10:00", null, null)))
                .andExpect(status().isBadRequest());
        assertThat(bloqueoHorarioRepository.findByTrabajador_IdAndFechaOrderByHoraInicioAsc(ana.getId(), dia)).isEmpty();
    }

    @Test
    void borrarBloqueoAjenoResponde404YNoLoBorra() throws Exception {
        BloqueoHorario deLuis = crearBloqueo(luis, dia, LocalTime.of(10, 0), LocalTime.of(11, 0));

        mockMvc.perform(delete("/api/worker/bloqueos/" + deLuis.getId())
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isNotFound());
        assertThat(bloqueoHorarioRepository.findById(deLuis.getId())).isPresent();

        // Inexistente: mismo 404
        mockMvc.perform(delete("/api/worker/bloqueos/999999999")
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isNotFound());

        // El propietario sí puede
        mockMvc.perform(delete("/api/worker/bloqueos/" + deLuis.getId())
                        .header("Authorization", "Bearer " + tokenLuis))
                .andExpect(status().isOk());
        assertThat(bloqueoHorarioRepository.findById(deLuis.getId())).isEmpty();
    }

    @Test
    void listadoPropioSoloDevuelveLosBloqueosDelTrabajador() throws Exception {
        BloqueoHorario deAna = crearBloqueo(ana, dia, LocalTime.of(10, 0), LocalTime.of(11, 0));
        crearBloqueo(luis, dia, LocalTime.of(10, 0), LocalTime.of(11, 0));

        MvcResult result = mockMvc.perform(get("/api/worker/bloqueos")
                        .param("desde", dia.toString())
                        .param("hasta", dia.toString())
                        .param("trabajadorId", luis.getId().toString())
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode lista = data(result);
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).path("id").asLong()).isEqualTo(deAna.getId());
    }

    @Test
    void adminCreaSuBloqueoPropioDesdeElPanelYGestionaLosAjenos() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/worker/bloqueos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(dia, "18:00", "19:00", "Gestoría", ana.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.trabajadorId").value(admin.getId()))
                .andReturn();
        registrarBloqueo(data(result).path("id").asLong());

        BloqueoHorario deAna = crearBloqueo(ana, dia, LocalTime.of(10, 0), LocalTime.of(11, 0));
        mockMvc.perform(get("/api/admin/bloqueos")
                        .param("trabajadorId", ana.getId().toString())
                        .param("desde", dia.toString())
                        .param("hasta", dia.toString())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(deAna.getId()));
        mockMvc.perform(delete("/api/admin/bloqueos/" + deAna.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
        assertThat(bloqueoHorarioRepository.findById(deAna.getId())).isEmpty();

        // Un WORKER no puede usar los endpoints de admin
        mockMvc.perform(get("/api/admin/bloqueos").header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isForbidden());
    }

    @Test
    void agendaMarcaDiasCerradosConMotivoBloqueosYDomingo() throws Exception {
        LocalDate lunes = dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate jueves = lunes.plusDays(3);
        LocalDate domingo = lunes.plusDays(6);
        crearCierre(jueves, null, "Festivo");
        crearBloqueo(ana, dia, LocalTime.of(13, 0), LocalTime.of(14, 0));

        MvcResult result = mockMvc.perform(get("/api/worker/agenda")
                        .param("desde", lunes.toString())
                        .param("hasta", domingo.toString())
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode dias = data(result);
        assertThat(dias).hasSize(7);

        JsonNode miercoles = dias.get(2);
        assertThat(miercoles.path("fecha").asText()).isEqualTo(dia.toString());
        assertThat(miercoles.path("trabaja").asBoolean()).isTrue();
        assertThat(miercoles.path("inicio").asText()).isEqualTo("09:00:00");
        assertThat(miercoles.path("bloqueos")).hasSize(1);
        assertThat(miercoles.path("bloqueos").get(0).path("horaInicio").asText()).isEqualTo("13:00:00");

        JsonNode juevesDto = dias.get(3);
        assertThat(juevesDto.path("trabaja").asBoolean()).isFalse();
        assertThat(juevesDto.path("causa").asText()).isEqualTo("CIERRE_GENERAL");
        assertThat(juevesDto.path("cerradoBarberia").asBoolean()).isTrue();
        assertThat(juevesDto.path("motivoCierre").asText()).isEqualTo("Festivo");

        JsonNode domingoDto = dias.get(6);
        assertThat(domingoDto.path("trabaja").asBoolean()).isFalse();
        assertThat(domingoDto.path("causa").asText()).isEqualTo("BARBERIA_CERRADA");

        // Rango de más de 62 días → 400
        mockMvc.perform(get("/api/worker/agenda")
                        .param("desde", lunes.toString())
                        .param("hasta", lunes.plusDays(80).toString())
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservasSemanaPorDefectoIncluyeElDomingo() throws Exception {
        LocalDate domingo = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(6);
        Reserva enDomingo = crearReserva(ana, corte, crearCliente(null), domingo, LocalTime.of(10, 0), ReservaEstado.CONFIRMADA);

        mockMvc.perform(get("/api/worker/reservas/semana")
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + enDomingo.getId() + ")]").exists());
    }

    // --- helpers ---

    private static String cuerpo(LocalDate fecha, String inicio, String fin, String motivo, Long trabajadorId) {
        return "{\"fecha\":\"" + fecha + "\""
                + ",\"horaInicio\":\"" + inicio + "\""
                + ",\"horaFin\":\"" + fin + "\""
                + (motivo != null ? ",\"motivo\":\"" + motivo + "\"" : "")
                + (trabajadorId != null ? ",\"trabajadorId\":" + trabajadorId : "")
                + "}";
    }

    private List<DisponibilidadSlotDTO> disponibilidad(LocalDate fecha, Long trabajadorId) {
        DisponibilidadRequest req = new DisponibilidadRequest();
        req.setServicioId(corte.getId());
        req.setFecha(fecha);
        req.setTrabajadorId(trabajadorId);
        return reservaService.consultarDisponibilidad(req);
    }
}
