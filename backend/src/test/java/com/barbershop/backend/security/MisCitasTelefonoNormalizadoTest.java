package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.barbershop.backend.dto.ReservaDTO;
import com.barbershop.backend.exception.ResourceNotFoundException;
import com.barbershop.backend.model.Cliente;
import com.barbershop.backend.model.Reserva;
import com.barbershop.backend.model.ReservaEstado;
import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.ClienteRepository;
import com.barbershop.backend.repository.ReservaRepository;
import com.barbershop.backend.repository.ServicioRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.service.ReservaService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spec client-data-consistency — "Las consultas existentes usan la identidad
 * canónica": {@code ReservaService.buscarPorTelefonoYToken} ("Mis citas")
 * compara contra {@code telefono_normalizado} (con fallback a normalizar el
 * teléfono histórico), de modo que un cliente GUARDADO con {@code +34…} y
 * {@code telefono_normalizado} aún sin rellenar (fila previa al backfill)
 * localiza sus citas escribiendo el teléfono sin prefijo.
 */
@SpringBootTest
class MisCitasTelefonoNormalizadoTest {

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @Autowired
    private ServicioRepository servicioRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void consultaSinPrefijoEncuentraLasReservasDelCliente() {
        // Ficha LEGACY: guardada con el prefijo y sin telefono_normalizado.
        // SQL directo para saltarse los hooks @PrePersist (hoy la normalización
        // al persistir impediría guardar "+34…" tal cual).
        String base = telefonoUnico();
        long clienteId = LegacyClientes.insertarCliente(jdbc, "Mis Citas", "+34" + base, null);

        Reserva reserva = reservaRepository.save(Reserva.builder()
                .cliente(clienteRepository.findById(clienteId).orElseThrow())
                .trabajador(trabajadorDePrueba())
                .servicio(servicioDePrueba())
                .fecha(LocalDate.now().plusDays(2))
                .horaInicio(LocalTime.of(10, 0))
                .horaFin(LocalTime.of(10, 30))
                .estado(ReservaEstado.CONFIRMADA)
                .build());

        // Cliente guardado con "+34…"…
        Cliente guardado = clienteRepository.findById(clienteId).orElseThrow();
        assertThat(guardado.getTelefono()).isEqualTo("+34" + base);

        // …consultando SIN prefijo y con su token → encontrada
        List<ReservaDTO> encontradas =
                reservaService.buscarPorTelefonoYToken(base, reserva.getTokenCancelacion());
        assertThat(encontradas).hasSize(1);
        assertThat(encontradas.get(0).getId()).isEqualTo(reserva.getId());
    }

    @Test
    void tokenIncorrectoNoDevuelveReservas() {
        String base = telefonoUnico();
        long clienteId = LegacyClientes.insertarCliente(jdbc, "Mis Citas Token", "+34" + base, null);
        reservaRepository.save(Reserva.builder()
                .cliente(clienteRepository.findById(clienteId).orElseThrow())
                .trabajador(trabajadorDePrueba())
                .servicio(servicioDePrueba())
                .fecha(LocalDate.now().plusDays(2))
                .horaInicio(LocalTime.of(11, 0))
                .horaFin(LocalTime.of(11, 30))
                .estado(ReservaEstado.CONFIRMADA)
                .build());

        // Teléfono correcto + token inventado → no encontrada
        assertThatThrownBy(() -> reservaService.buscarPorTelefonoYToken(
                base, UUID.randomUUID().toString()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void telefonoDistintoConTokenValidoNoDevuelveReservas() {
        String base = telefonoUnico();
        long clienteId = LegacyClientes.insertarCliente(jdbc, "Mis Citas Telefono", "+34" + base, null);
        Reserva reserva = reservaRepository.save(Reserva.builder()
                .cliente(clienteRepository.findById(clienteId).orElseThrow())
                .trabajador(trabajadorDePrueba())
                .servicio(servicioDePrueba())
                .fecha(LocalDate.now().plusDays(2))
                .horaInicio(LocalTime.of(12, 0))
                .horaFin(LocalTime.of(12, 30))
                .estado(ReservaEstado.CONFIRMADA)
                .build());

        // Token válido + teléfono ajeno → no encontrada (el token basta para
        // cancelar, pero "Mis citas" exige también el teléfono del cliente)
        assertThatThrownBy(() -> reservaService.buscarPorTelefonoYToken(
                telefonoUnico(), reserva.getTokenCancelacion()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- helpers ---

    private Trabajador trabajadorDePrueba() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        return trabajadorRepository.save(Trabajador.builder()
                .nombre("Mis Citas Test " + sufijo)
                .email("mascitas-" + sufijo + "@barbershop.test")
                .passwordHash("mascitas-" + sufijo)
                .activo(true)
                .rol(Rol.WORKER)
                .build());
    }

    private Servicio servicioDePrueba() {
        return servicioRepository.save(Servicio.builder()
                .nombre("Corte mascitas " + UUID.randomUUID().toString().substring(0, 8))
                .precio(new BigDecimal("15.00"))
                .duracionMinutos(30)
                .activo(true)
                .build());
    }

    /** Teléfono canónico de 9 dígitos empezando por 6 (no lo recorta la normalización). */
    private static String telefonoUnico() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder("6");
        for (int i = 0; i < 8; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
