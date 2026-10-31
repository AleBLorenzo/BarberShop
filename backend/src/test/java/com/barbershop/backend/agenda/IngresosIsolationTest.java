package com.barbershop.backend.agenda;

import com.barbershop.backend.model.MetodoPago;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tarea 7.5: aislamiento de ingresos (D10). Ana (VISA 15 €), Luis (VISA
 * 20 €) y el admin (efectivo 30 €) completan una cita el mismo día: cada uno
 * ve en {@code /api/worker/**} solo lo suyo, un {@code ?trabajadorId=} ajeno
 * se ignora y {@code /api/admin/ingresos} cuadra con {@code /api/admin/dashboard}.
 *
 * <p>Fecha pasada fija que no usa ningún otro test: los totales globales del
 * día son exactamente los de este escenario.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class IngresosIsolationTest extends AgendaTestSupport {

    private static final LocalDate DIA = LocalDate.of(2004, 2, 17);

    private Trabajador admin;
    private Trabajador ana;
    private Trabajador luis;
    private String tokenAna;
    private String tokenAdmin;
    private Reserva citaAna;

    @BeforeEach
    void fixtures() throws Exception {
        // Idempotente si la H2 compartida ya tiene citas de una ejecución previa del escenario
        reservaRepository.deleteAll(reservaRepository.findByFechaBetweenOrderByFechaAscHoraInicioAsc(DIA, DIA));

        admin = crearTrabajador("Admin Ingresos", Rol.ADMIN);
        ana = crearTrabajador("Ana", Rol.WORKER);
        luis = crearTrabajador("Luis", Rol.WORKER);
        tokenAna = login(ana);
        tokenAdmin = login(admin);

        citaAna = completada(ana, servicio("15.00"), MetodoPago.VISA, "10:00");
        completada(luis, servicio("20.00"), MetodoPago.VISA, "10:00");
        completada(admin, servicio("30.00"), MetodoPago.EFECTIVO, "10:00");
    }

    @Test
    void dashboardDelTrabajadorSoloSumaSusCitas() throws Exception {
        JsonNode dashboard = data(mockMvc.perform(get("/api/worker/dashboard")
                        .param("fecha", DIA.toString())
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(dashboard.path("trabajadorId").asLong()).isEqualTo(ana.getId());
        assertDinero(dashboard.path("ingresosVisa"), "15");
        assertDinero(dashboard.path("ingresosEfectivo"), "0");
        assertDinero(dashboard.path("ingresosDelDia"), "15");
    }

    @Test
    void elAdminEnSuPanelSoloVeSusIngresos() throws Exception {
        JsonNode dashboard = data(mockMvc.perform(get("/api/worker/dashboard")
                        .param("fecha", DIA.toString())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn());

        assertDinero(dashboard.path("ingresosVisa"), "0");
        assertDinero(dashboard.path("ingresosEfectivo"), "30");
        assertDinero(dashboard.path("ingresosDelDia"), "30");
    }

    @Test
    void trabajadorIdAjenoSeIgnoraEnDashboardYSemana() throws Exception {
        JsonNode dashboard = data(mockMvc.perform(get("/api/worker/dashboard")
                        .param("fecha", DIA.toString())
                        .param("trabajadorId", luis.getId().toString())
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(dashboard.path("trabajadorId").asLong()).isEqualTo(ana.getId());
        assertDinero(dashboard.path("ingresosVisa"), "15");
        assertThat(dashboard.path("reservasDelDia")).hasSize(1);

        JsonNode semana = data(mockMvc.perform(get("/api/worker/reservas/semana")
                        .param("fechaInicio", DIA.toString())
                        .param("fechaFin", DIA.toString())
                        .param("trabajadorId", luis.getId().toString())
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(semana).hasSize(1);
        assertThat(semana.get(0).path("id").asLong()).isEqualTo(citaAna.getId());
    }

    @Test
    void ingresosPorTrabajadorSuman65YCuadranConElDashboard() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/ingresos")
                        .param("inicio", DIA.toString())
                        .param("fin", DIA.toString())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode resumen = data(result);
        JsonNode totales = resumen.path("totales");
        assertDinero(totales.path("total"), "65");
        assertDinero(totales.path("visa"), "35");
        assertDinero(totales.path("efectivo"), "30");
        assertThat(totales.path("completadas").asLong()).isEqualTo(3);

        assertDinero(fila(resumen, ana).path("visa"), "15");
        assertDinero(fila(resumen, luis).path("visa"), "20");
        assertDinero(fila(resumen, admin).path("efectivo"), "30");
        assertThat(fila(resumen, admin).path("rol").asText()).isEqualTo("ADMIN");
        assertThat(fila(resumen, ana).path("completadas").asLong()).isEqualTo(1);

        BigDecimal sumaFilas = BigDecimal.ZERO;
        for (JsonNode f : resumen.path("filas")) {
            sumaFilas = sumaFilas.add(f.path("total").decimalValue());
        }
        assertThat(sumaFilas).isEqualByComparingTo("65");

        JsonNode dashboard = data(mockMvc.perform(get("/api/admin/dashboard")
                        .param("inicio", DIA.toString())
                        .param("fin", DIA.toString())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(dashboard.path("ingresosTotales").decimalValue()).isEqualByComparingTo(totales.path("total").decimalValue());
        assertThat(dashboard.path("ingresosVisa").decimalValue()).isEqualByComparingTo(totales.path("visa").decimalValue());
        assertThat(dashboard.path("ingresosEfectivo").decimalValue()).isEqualByComparingTo(totales.path("efectivo").decimalValue());
    }

    @Test
    void workerNoAccedeAIngresosDeAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/ingresos")
                        .param("inicio", DIA.toString())
                        .param("fin", DIA.toString())
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/ingresos"))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private Servicio servicio(String precio) {
        return servicioRepository.save(Servicio.builder()
                .nombre("Servicio ingresos " + UUID.randomUUID().toString().substring(0, 8))
                .precio(new BigDecimal(precio))
                .duracionMinutos(30)
                .activo(true)
                .build());
    }

    private Reserva completada(Trabajador t, Servicio s, MetodoPago metodo, String hora) {
        LocalTime inicio = LocalTime.parse(hora);
        return reservaRepository.save(Reserva.builder()
                .trabajador(t)
                .servicio(s)
                .cliente(crearCliente(null))
                .fecha(DIA)
                .horaInicio(inicio)
                .horaFin(inicio.plusMinutes(s.getDuracionMinutos()))
                .estado(ReservaEstado.COMPLETADA)
                .metodoPago(metodo)
                .build());
    }

    private static JsonNode fila(JsonNode resumen, Trabajador t) {
        for (JsonNode f : resumen.path("filas")) {
            if (f.path("trabajadorId").asLong() == t.getId()) {
                return f;
            }
        }
        throw new AssertionError("Sin fila de ingresos para " + t.getNombre());
    }

    private static void assertDinero(JsonNode valor, String esperado) {
        assertThat(valor.isMissingNode() || valor.isNull()).as("importe presente").isFalse();
        assertThat(valor.decimalValue()).isEqualByComparingTo(esperado);
    }
}
