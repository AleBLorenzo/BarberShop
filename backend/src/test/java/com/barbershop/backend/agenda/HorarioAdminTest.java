package com.barbershop.backend.agenda;

import com.barbershop.backend.model.HorarioBarberia;
import com.barbershop.backend.model.Reserva;
import com.barbershop.backend.model.ReservaEstado;
import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.model.Trabajador;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tarea 3.6: edición del horario por el ADMIN (D1, D11). Un horario inválido
 * no cambia nada (400); reducir el horario NO cancela citas, las devuelve
 * como conflictos; un horario de trabajador fuera del general es 400.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HorarioAdminTest extends AgendaTestSupport {

    private Trabajador admin;
    private Trabajador luis;
    private Servicio corte;
    private String tokenAdmin;

    @BeforeEach
    void fixtures() throws Exception {
        admin = crearTrabajador("Admin Horario", Rol.ADMIN);
        luis = crearTrabajador("Luis", Rol.WORKER);
        corte = crearServicio(30);
        tokenAdmin = login(admin);
    }

    @Test
    void horarioPublicoDevuelveLosSieteDias() throws Exception {
        mockMvc.perform(get("/api/public/horario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[0].diaSemana").value("MONDAY"))
                .andExpect(jsonPath("$.data[0].abierto").value(true))
                .andExpect(jsonPath("$.data[6].diaSemana").value("SUNDAY"))
                .andExpect(jsonPath("$.data[6].abierto").value(false));
    }

    @Test
    void horarioInvalidoResponde400YNoModificaNada() throws Exception {
        List<String> dias = semanaPorDefecto();
        dias.set(5, dia("SATURDAY", true, "14:00", "10:00")); // cierre anterior a la apertura

        mockMvc.perform(put("/api/admin/horario")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + String.join(",", dias) + "]"))
                .andExpect(status().isBadRequest());

        // Abierto sin horas y semana incompleta también son 400
        dias.set(5, "{\"diaSemana\":\"SATURDAY\",\"abierto\":true}");
        mockMvc.perform(put("/api/admin/horario")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + String.join(",", dias) + "]"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/horario")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + dia("MONDAY", true, "09:00", "20:00") + "]"))
                .andExpect(status().isBadRequest());

        HorarioBarberia sabado = horarioBarberiaRepository.findByDiaSemana(DayOfWeek.SATURDAY).orElseThrow();
        assertThat(sabado.getHoraApertura()).isEqualTo(LocalTime.of(9, 0));
        assertThat(sabado.getHoraCierre()).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    void reducirHorarioNoCancelaCitasYLasDevuelveComoConflicto() throws Exception {
        LocalDate viernes = proximo(DayOfWeek.FRIDAY);
        Reserva cita = crearReserva(luis, corte, crearCliente(null), viernes, LocalTime.of(19, 0),
                ReservaEstado.CONFIRMADA);

        List<String> dias = semanaPorDefecto();
        dias.set(4, dia("FRIDAY", true, "09:00", "18:00"));

        MvcResult result = mockMvc.perform(put("/api/admin/horario")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + String.join(",", dias) + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dias.length()").value(7))
                .andReturn();

        JsonNode conflictos = data(result).path("conflictos");
        assertThat(idsDeConflictos(conflictos)).contains(cita.getId());
        assertThat(reservaRepository.findById(cita.getId()).orElseThrow().getEstado())
                .isEqualTo(ReservaEstado.CONFIRMADA);

        // También en la consulta de conflictos bajo demanda
        MvcResult consulta = mockMvc.perform(get("/api/admin/agenda/conflictos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .param("desde", LocalDate.now().toString())
                        .param("hasta", viernes.toString()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(idsDeConflictos(data(consulta))).contains(cita.getId());

        // Y no se ofrecen huecos nuevos después de las 18:00
        MvcResult slots = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/public/disponibilidad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"servicioId\":" + corte.getId() + ",\"fecha\":\"" + viernes
                                + "\",\"trabajadorId\":" + luis.getId() + "}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode lista = data(slots);
        assertThat(lista.size()).isPositive();
        assertThat(lista.get(lista.size() - 1).path("horaFin").asText()).startsWith("18:00");
    }

    @Test
    void horarioDeTrabajadorFueraDelGeneralResponde400() throws Exception {
        mockMvc.perform(put("/api/admin/trabajadores/" + luis.getId() + "/horario")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + dia("TUESDAY", true, "08:00", "15:00") + "]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("dentro del horario de la barbería")));

        assertThat(horarioTrabajadorRepository.findByTrabajador_Id(luis.getId())).isEmpty();
    }

    @Test
    void horarioDeTrabajadorReemplazaYLosDiasAusentesHeredan() throws Exception {
        mockMvc.perform(put("/api/admin/trabajadores/" + luis.getId() + "/horario")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + dia("MONDAY", false, null, null) + ","
                                + dia("WEDNESDAY", true, "10:00", "14:00") + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dias.length()").value(2));

        // PUT reemplaza: quitar el miércoles lo devuelve a heredar el general
        mockMvc.perform(put("/api/admin/trabajadores/" + luis.getId() + "/horario")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + dia("MONDAY", false, null, null) + "]"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/trabajadores/" + luis.getId() + "/horario")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].diaSemana").value("MONDAY"))
                .andExpect(jsonPath("$.data[0].abierto").value(false));
    }

    @Test
    void unTrabajadorNoPuedeModificarHorarios() throws Exception {
        String tokenLuis = login(luis);
        mockMvc.perform(put("/api/admin/horario")
                        .header("Authorization", "Bearer " + tokenLuis)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + String.join(",", semanaPorDefecto()) + "]"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/trabajadores/" + luis.getId() + "/horario")
                        .header("Authorization", "Bearer " + tokenLuis)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private static List<String> semanaPorDefecto() {
        List<String> dias = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            dias.add(d == DayOfWeek.SUNDAY ? dia(d.name(), false, null, null) : dia(d.name(), true, "09:00", "21:00"));
        }
        return dias;
    }

    private static String dia(String dia, boolean abierto, String inicio, String fin) {
        return "{\"diaSemana\":\"" + dia + "\",\"abierto\":" + abierto
                + (inicio != null ? ",\"horaInicio\":\"" + inicio + "\"" : "")
                + (fin != null ? ",\"horaFin\":\"" + fin + "\"" : "") + "}";
    }

    private static List<Long> idsDeConflictos(JsonNode conflictos) {
        List<Long> ids = new ArrayList<>();
        conflictos.forEach(c -> ids.add(c.path("reservaId").asLong()));
        return ids;
    }
}
