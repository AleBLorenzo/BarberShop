package com.barbershop.backend.agenda;

import com.barbershop.backend.dto.CierreCreateRequest;
import com.barbershop.backend.dto.DisponibilidadRequest;
import com.barbershop.backend.dto.DisponibilidadSlotDTO;
import com.barbershop.backend.dto.ReservaCreateRequest;
import com.barbershop.backend.model.CanceladaPor;
import com.barbershop.backend.model.Reserva;
import com.barbershop.backend.model.ReservaEstado;
import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.service.CierreService;
import com.barbershop.backend.service.ReservaService;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tarea 5.5: cierres de día (D4) — resumen de cancelaciones, alcance general
 * vs. trabajador, estados terminales intactos, 409/400, reapertura sin
 * reactivar citas y concurrencia reserva vs. cierre (lock D3).
 *
 * <p>El cierre general cancela TODAS las citas activas de la fecha en la H2
 * compartida: los tests que cuentan citas usan fechas que no usa ningún otro
 * test (desplazamientos de 41–58 días, de dos en dos para que el salto del domingo no las haga coincidir) y comprueban con la vista previa que la
 * fecha parte vacía.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class CierreDiaTest extends AgendaTestSupport {

    @Autowired private CierreService cierreService;
    @Autowired private ReservaService reservaService;

    private Trabajador admin;
    private Trabajador ana;
    private Trabajador luis;
    private Servicio corte;
    private String tokenAdmin;

    @BeforeEach
    void fixtures() throws Exception {
        admin = crearTrabajador("Admin Cierres", Rol.ADMIN);
        ana = crearTrabajador("Ana", Rol.WORKER);
        luis = crearTrabajador("Luis", Rol.WORKER);
        corte = crearServicio(30);
        tokenAdmin = login(admin);
    }

    @Test
    void cierreGeneralConTresCitasDevuelveResumen3_2_1() throws Exception {
        LocalDate dia = laborable(41);
        assertThat(cierreService.preview(dia, null).getTotal()).as("la fecha parte sin citas activas").isZero();

        Reserva r1 = crearReserva(ana, corte, crearCliente(emailUnico()), dia, LocalTime.of(10, 0), ReservaEstado.CONFIRMADA);
        Reserva r2 = crearReserva(luis, corte, crearCliente(emailUnico()), dia, LocalTime.of(11, 0), ReservaEstado.CONFIRMADA);
        Reserva r3 = crearReserva(ana, corte, crearCliente(null), dia, LocalTime.of(12, 0), ReservaEstado.CONFIRMADA);

        // Vista previa: no modifica nada
        mockMvc.perform(get("/api/admin/cierres/preview")
                        .param("fecha", dia.toString())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.porEmail").value(2))
                .andExpect(jsonPath("$.data.porTelefono").value(1))
                .andExpect(jsonPath("$.data.general").value(true))
                .andExpect(jsonPath("$.data.yaExiste").value(false));
        assertThat(reservaRepository.findById(r1.getId()).orElseThrow().getEstado()).isEqualTo(ReservaEstado.CONFIRMADA);

        MvcResult result = mockMvc.perform(post("/api/admin/cierres")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\":\"" + dia + "\",\"motivo\":\"Festivo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.canceladas").value(3))
                .andExpect(jsonPath("$.data.porEmail").value(2))
                .andExpect(jsonPath("$.data.porTelefono").value(1))
                .andExpect(jsonPath("$.data.cierre.general").value(true))
                .andExpect(jsonPath("$.data.cierre.motivo").value("Festivo"))
                .andReturn();
        registrarCierre(data(result).path("cierre").path("id").asLong());

        for (Reserva r : List.of(r1, r2, r3)) {
            Reserva tras = reservaRepository.findById(r.getId()).orElseThrow();
            assertThat(tras.getEstado()).isEqualTo(ReservaEstado.CANCELADA);
            assertThat(tras.getCanceladaPor()).isEqualTo(CanceladaPor.ADMIN);
            assertThat(tras.getMotivoCancelacion()).isEqualTo("Festivo");
        }
        // Disponibilidad vacía para todos tras el cierre
        assertThat(disponibilidad(dia, null)).isEmpty();
        assertThat(disponibilidad(dia, ana.getId())).isEmpty();
    }

    @Test
    void cierreDeTrabajadorSoloCancelaSusCitasYNoAfectaAOtros() throws Exception {
        LocalDate dia = laborable(43);
        Reserva deAna = crearReserva(ana, corte, crearCliente(null), dia, LocalTime.of(10, 0), ReservaEstado.CONFIRMADA);
        Reserva deLuis = crearReserva(luis, corte, crearCliente(null), dia, LocalTime.of(10, 0), ReservaEstado.CONFIRMADA);

        MvcResult result = mockMvc.perform(post("/api/admin/cierres")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\":\"" + dia + "\",\"trabajadorId\":" + ana.getId()
                                + ",\"motivo\":\"Asuntos propios\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.canceladas").value(1))
                .andExpect(jsonPath("$.data.porTelefono").value(1))
                .andExpect(jsonPath("$.data.cierre.general").value(false))
                .andExpect(jsonPath("$.data.cierre.trabajadorId").value(ana.getId()))
                .andReturn();
        registrarCierre(data(result).path("cierre").path("id").asLong());

        assertThat(reservaRepository.findById(deAna.getId()).orElseThrow().getEstado()).isEqualTo(ReservaEstado.CANCELADA);
        assertThat(reservaRepository.findById(deLuis.getId()).orElseThrow().getEstado()).isEqualTo(ReservaEstado.CONFIRMADA);
        assertThat(disponibilidad(dia, ana.getId())).isEmpty();
        assertThat(disponibilidad(dia, luis.getId())).isNotEmpty();
    }

    @Test
    void citasEnEstadoTerminalNoSeTocan() throws Exception {
        LocalDate dia = laborable(45);
        Reserva completada = crearReserva(ana, corte, crearCliente(null), dia, LocalTime.of(10, 0), ReservaEstado.COMPLETADA);
        Reserva noAsistio = crearReserva(ana, corte, crearCliente(null), dia, LocalTime.of(11, 0), ReservaEstado.NO_ASISTIO);
        Reserva cancelada = crearReserva(ana, corte, crearCliente(null), dia, LocalTime.of(12, 0), ReservaEstado.CANCELADA);

        MvcResult result = mockMvc.perform(post("/api/admin/cierres")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\":\"" + dia + "\",\"trabajadorId\":" + ana.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.canceladas").value(0))
                .andReturn();
        registrarCierre(data(result).path("cierre").path("id").asLong());

        assertThat(reservaRepository.findById(completada.getId()).orElseThrow().getEstado()).isEqualTo(ReservaEstado.COMPLETADA);
        assertThat(reservaRepository.findById(noAsistio.getId()).orElseThrow().getEstado()).isEqualTo(ReservaEstado.NO_ASISTIO);
        Reserva trasCancelada = reservaRepository.findById(cancelada.getId()).orElseThrow();
        assertThat(trasCancelada.getEstado()).isEqualTo(ReservaEstado.CANCELADA);
        assertThat(trasCancelada.getCanceladaPor()).isNull();
    }

    @Test
    void cierreDuplicadoResponde409() throws Exception {
        LocalDate dia = laborable(47);
        String cuerpo = "{\"fecha\":\"" + dia + "\",\"trabajadorId\":" + luis.getId() + "}";
        MvcResult result = mockMvc.perform(post("/api/admin/cierres")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn();
        registrarCierre(data(result).path("cierre").path("id").asLong());

        mockMvc.perform(post("/api/admin/cierres")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CIERRE_DUPLICADO"));

        // General duplicado también 409
        crearCierre(dia, null, "Festivo");
        mockMvc.perform(post("/api/admin/cierres")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\":\"" + dia + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void fechaPasadaResponde400() throws Exception {
        mockMvc.perform(post("/api/admin/cierres")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\":\"" + LocalDate.now().minusDays(1) + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(cierreDiaRepository.findFirstByFechaAndTrabajadorIsNull(LocalDate.now().minusDays(1))).isEmpty();
    }

    @Test
    void reabrirRestauraLaDisponibilidadPeroNoReactivaCitas() throws Exception {
        LocalDate dia = laborable(49);
        Reserva cita = crearReserva(luis, corte, crearCliente(null), dia, LocalTime.of(10, 0), ReservaEstado.CONFIRMADA);
        MvcResult result = mockMvc.perform(post("/api/admin/cierres")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\":\"" + dia + "\",\"trabajadorId\":" + luis.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        long cierreId = data(result).path("cierre").path("id").asLong();
        registrarCierre(cierreId);
        assertThat(disponibilidad(dia, luis.getId())).isEmpty();

        mockMvc.perform(get("/api/admin/cierres")
                        .param("desde", dia.toString())
                        .param("hasta", dia.toString())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + cierreId + ")]").exists());

        mockMvc.perform(delete("/api/admin/cierres/" + cierreId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());

        assertThat(cierreDiaRepository.findById(cierreId)).isEmpty();
        assertThat(disponibilidad(dia, luis.getId())).isNotEmpty();
        assertThat(reservaRepository.findById(cita.getId()).orElseThrow().getEstado()).isEqualTo(ReservaEstado.CANCELADA);
    }

    @Test
    void eliminarCierrePasadoResponde400() throws Exception {
        var pasado = crearCierre(LocalDate.now().minusDays(3), ana, "Antiguo");
        mockMvc.perform(delete("/api/admin/cierres/" + pasado.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isBadRequest());
        assertThat(cierreDiaRepository.findById(pasado.getId())).isPresent();
    }

    /**
     * Reserva y cierre general simultáneos (D3): sea cual sea el orden en
     * que obtienen el lock, nunca queda una cita activa en el día cerrado.
     */
    @Test
    void reservaYCierreConcurrentesNuncaDejanCitaActivaEnDiaCerrado() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 4; i++) {
                LocalDate dia = laborable(51 + 2 * i);
                CountDownLatch salida = new CountDownLatch(1);

                Future<?> reserva = pool.submit(() -> {
                    salida.await();
                    ReservaCreateRequest req = new ReservaCreateRequest();
                    req.setServicioId(corte.getId());
                    req.setFecha(dia);
                    req.setHoraInicio(LocalTime.of(10, 0));
                    req.setClienteNombre("Cliente Carrera");
                    req.setClienteTelefono(telefonoUnico());
                    return reservaService.crearReservaParaTrabajador(req, ana.getId());
                });
                Future<?> cierre = pool.submit(() -> {
                    salida.await();
                    CierreCreateRequest req = new CierreCreateRequest(dia, null, "Carrera");
                    return cierreService.crear(req, admin.getId());
                });
                salida.countDown();

                // Cualquiera de los dos puede fallar (400 por día cerrado, timeout de lock…):
                // lo que se comprueba es el invariante.
                esperar(reserva);
                esperar(cierre);

                var creado = cierreDiaRepository.findFirstByFechaAndTrabajadorIsNull(dia);
                creado.ifPresent(c -> registrarCierre(c.getId()));
                if (creado.isPresent()) {
                    List<Reserva> activas = reservaRepository.findByFechaAndEstadoInOrderByHoraInicioAsc(
                            dia, List.of(ReservaEstado.PENDIENTE, ReservaEstado.CONFIRMADA));
                    assertThat(activas).as("día %s cerrado sin citas activas", dia).isEmpty();
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // --- helpers ---

    /** Laborable (no domingo) a {@code dias} días vista: fechas que no usan otros tests. */
    private static LocalDate laborable(int dias) {
        LocalDate d = LocalDate.now().plusDays(dias);
        return d.getDayOfWeek() == DayOfWeek.SUNDAY ? d.plusDays(1) : d;
    }

    private static void esperar(Future<?> f) {
        try {
            f.get(30, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // el fallo de una de las dos operaciones es un resultado válido
        }
    }

    private static String emailUnico() {
        return "cierre-" + UUID.randomUUID().toString().substring(0, 8) + "@cliente.test";
    }

    private List<DisponibilidadSlotDTO> disponibilidad(LocalDate fecha, Long trabajadorId) {
        DisponibilidadRequest req = new DisponibilidadRequest();
        req.setServicioId(corte.getId());
        req.setFecha(fecha);
        req.setTrabajadorId(trabajadorId);
        return reservaService.consultarDisponibilidad(req);
    }
}
