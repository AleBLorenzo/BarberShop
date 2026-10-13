package com.barbershop.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.ReservaRepository;
import com.barbershop.backend.repository.ServicioRepository;
import com.barbershop.backend.repository.TrabajadorRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * B-MED-6 / spec public-booking-abuse-prevention: {@code POST
 * /api/public/reservas} limita las peticiones por IP (10/hora) y aplica un
 * techo diario global (12 en esta prueba), devolviendo 429 con
 * {@code Retry-After} sin crear la reserva (⇒ sin publicar
 * {@code ReservaCreadaEvent} ni enviar email). Las IPs distintas no se
 * afectan entre sí.
 *
 * <p>Los topes se bajan vía {@code @SpringBootTest(properties=...)} y cada
 * método limpia los contadores ({@link RateLimiterService#limpiarContadores()})
 * para que el techo diario —clave compartida por diseño— no contamine a los
 * demás métodos. Los huecos de reserva se toman de una bolsa estática de
 * slots futuros para evitar solapes de agenda.</p>
 */
@SpringBootTest(
        properties = {
                "app.security.booking.max-per-ip=10",
                "app.security.booking.max-per-day=12"
        })
@AutoConfigureMockMvc
class BookingSpamTest {

    private static final String TELEFONO = "699111222";

    /** Bolsa estática de [fecha, hora] futuros, sin domingos (día cerrado). */
    private static final List<String[]> SLOTS = new ArrayList<>();
    private static final AtomicInteger SLOT_IDX = new AtomicInteger();

    static {
        LocalDate dia = LocalDate.now().plusDays(1);
        while (SLOTS.size() < 60) {
            if (dia.getDayOfWeek() != DayOfWeek.SUNDAY) {
                for (int k = 0; k < 20; k++) { // 09:00 … 18:30 cada 30 min
                    String hora = String.format("%02d:%02d", 9 + k / 2, (k % 2) * 30);
                    SLOTS.add(new String[]{dia.toString(), hora});
                }
            }
            dia = dia.plusDays(1);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @Autowired
    private ServicioRepository servicioRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimiterService rateLimiter;

    private Long trabajadorId;
    private Long servicioId;

    @BeforeEach
    void fixtures() {
        rateLimiter.limpiarContadores();
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Trabajador trabajador = trabajadorRepository.save(Trabajador.builder()
                .nombre("Spam Test " + sufijo)
                .email("spam-" + sufijo + "@barbershop.test")
                .passwordHash(passwordEncoder.encode("BookingSpam12345"))
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
    }

    @Test
    void onceavaPeticionMismaIpResponde429() throws Exception {
        String ip = "198.18.0.10";
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(reserva().with(xRealIp(ip)))
                    .andExpect(status().isOk());
        }
        long creadas = reservaRepository.count();

        mockMvc.perform(reserva().with(xRealIp(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        // 429: no se crea la reserva (⇒ no se publica ReservaCreadaEvent)
        org.assertj.core.api.Assertions.assertThat(reservaRepository.count()).isEqualTo(creadas);
    }

    @Test
    void techoDiarioGlobalResponde429() throws Exception {
        // IPs frescas: el bucket por IP nunca se llena; manda el techo diario.
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(reserva().with(xRealIp("198.18.1." + (i + 1))))
                    .andExpect(status().isOk());
        }
        long creadas = reservaRepository.count();

        mockMvc.perform(reserva().with(xRealIp("198.18.1.99")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        org.assertj.core.api.Assertions.assertThat(reservaRepository.count()).isEqualTo(creadas);
    }

    @Test
    void ipsDistintasNoSeAfectanEntreSi() throws Exception {
        String ipSaturada = "198.18.2.10";
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(reserva().with(xRealIp(ipSaturada)))
                    .andExpect(status().isOk());
        }
        // La IP saturada recibe 429…
        mockMvc.perform(reserva().with(xRealIp(ipSaturada)))
                .andExpect(status().isTooManyRequests());

        // …mientras otra IP sigue reservando con normalidad
        mockMvc.perform(reserva().with(xRealIp("198.18.2.20")))
                .andExpect(status().isOk());
    }

    // --- helpers ---

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder reserva() {
        String[] slot = SLOTS.get(SLOT_IDX.getAndIncrement() % SLOTS.size());
        String body = "{"
                + "\"servicioId\":" + servicioId + ","
                + "\"trabajadorId\":" + trabajadorId + ","
                + "\"fecha\":\"" + slot[0] + "\","
                + "\"horaInicio\":\"" + slot[1] + "\","
                + "\"clienteNombre\":\"Cliente Anti-Spam\","
                + "\"clienteTelefono\":\"" + TELEFONO + "\","
                + "\"clienteEmail\":\"antispam@client.test\""
                + "}";
        return post("/api/public/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor xRealIp(String ip) {
        return request -> {
            request.addHeader("X-Real-IP", ip);
            return request;
        };
    }
}
