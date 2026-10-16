package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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
import com.barbershop.backend.service.ClienteService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spec client-deduplication — "Reserva repetida reutiliza la ficha
 * existente": el mismo teléfono escrito de formas distintas ({@code
 * +34612345678}, {@code 612-345-678}, {@code 612 345 678}, con espacios) o un
 * email con distinta caja reutilizan SIEMPRE la misma fila de {@code clientes}.
 *
 * <p>Se drive por {@code ClienteService.findOrCreateByTelefono} con 3
 * argumentos: el punto de entrada compartido por la reserva pública
 * ({@code POST /api/public/reservas}) y la del panel del trabajador. Cada
 * variante además enlaza una reserva a la ficha devuelta para comprobar que
 * la cita queda asociada al MISMO cliente id.</p>
 */
@SpringBootTest
class ClientDedupeFormatVariantsTest {

    /** Identidad canónica del escenario de la spec. */
    private static final String BASE_NORMALIZADO = "612345678";

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @Autowired
    private ServicioRepository servicioRepository;

    private Trabajador trabajador;
    private Servicio servicio;
    private int secuenciaHoras;

    @BeforeEach
    void fixtures() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        trabajador = trabajadorRepository.save(Trabajador.builder()
                .nombre("Formatos Test " + sufijo)
                .email("formatos-" + sufijo + "@barbershop.test")
                .passwordHash("formatos-" + sufijo)
                .activo(true)
                .rol(Rol.WORKER)
                .build());
        servicio = servicioRepository.save(Servicio.builder()
                .nombre("Corte " + sufijo)
                .precio(new BigDecimal("15.00"))
                .duracionMinutos(30)
                .activo(true)
                .build());
        secuenciaHoras = 0;
    }

    @Test
    void variantesDeFormatoReutilizanLaMismaFilaYEnlazanLaReserva() {
        long antes = clienteRepository.count();

        // Alta inicial con el formato del escenario de la spec
        Cliente base = clienteService.findOrCreateByTelefono(
                "Cliente Formatos", "+34612345678", "cliente@ejemplo.com");
        assertThat(clienteRepository.count()).isEqualTo(antes + 1);

        String[][] variantes = {
                {"612-345-678", null},
                {"612 345 678", null},
                {" 612345678 ", null},
                {"+34 612 345 678", null},
                // caja distinta del email: la rama por teléfono gana y el
                // email en otra caja no debe crear una segunda ficha
                {"612345678", "CLIENTE@EJEMPLO.COM"},
                {"612345678", "Cliente@Ejemplo.Com"},
        };

        for (String[] variante : variantes) {
            Cliente resultado = clienteService.findOrCreateByTelefono(
                    "Cliente Formatos", variante[0], variante[1]);

            assertThat(resultado.getId())
                    .as("variante '%s' reutiliza la ficha base", variante[0])
                    .isEqualTo(base.getId());

            // La reserva enlazada apunta al MISMO cliente (choque único de
            // trabajador/fecha/hora evitado con hora creciente)
            int hora = 9 + secuenciaHoras++;
            Reserva reserva = reservaRepository.save(Reserva.builder()
                    .cliente(resultado)
                    .trabajador(trabajador)
                    .servicio(servicio)
                    .fecha(LocalDate.now().plusDays(2))
                    .horaInicio(java.time.LocalTime.of(hora, 0))
                    .horaFin(java.time.LocalTime.of(hora, 30))
                    .estado(ReservaEstado.CONFIRMADA)
                    .build());
            assertThat(reserva.getCliente().getId()).isEqualTo(base.getId());
        }

        // La tabla NO creció: todas las variantes cayeron en la misma fila
        assertThat(clienteRepository.count()).isEqualTo(antes + 1);
        var filasConIdentidad = clienteRepository.findAll().stream()
                .filter(c -> BASE_NORMALIZADO.equals(c.getTelefonoNormalizado()))
                .toList();
        assertThat(filasConIdentidad).hasSize(1);
        assertThat(filasConIdentidad.get(0).getId()).isEqualTo(base.getId());
        // Los datos almacenados no se pisaron con las variantes
        assertThat(filasConIdentidad.get(0).getNombre()).isEqualTo("Cliente Formatos");
        assertThat(filasConIdentidad.get(0).getEmail()).isEqualTo("cliente@ejemplo.com");
    }

    @Test
    void emailEnOtraCajaReutilizaLaFichaConTelefonoDesconocido() {
        // Teléfono aleatorio propio para que este método no dependa del
        // estado que deje el otro (comparten contexto/base H2)
        String telefonoBase = telefonoUnico();
        Cliente base = clienteService.findOrCreateByTelefono(
                "Cliente Caja", telefonoBase, "caja@ejemplo.com");
        long antes = clienteRepository.count();

        // Teléfono desconocido + email existente en otra caja → misma ficha
        Cliente resultado = clienteService.findOrCreateByTelefono(
                "Otra Persona", telefonoUnico(), "CAJA@EJEMPLO.COM");

        assertThat(resultado.getId()).isEqualTo(base.getId());
        assertThat(clienteRepository.count()).isEqualTo(antes); // sin fila nueva
        // El teléfono original se conserva como identificador
        assertThat(clienteRepository.findById(base.getId()).orElseThrow()
                .getTelefono()).isEqualTo(telefonoBase);
    }

    // --- helpers ---

    /** Teléfono canónico de 9 dígitos empezando por 6 (la normalización no lo recorta). */
    private static String telefonoUnico() {
        java.util.Random random = new java.util.Random();
        StringBuilder sb = new StringBuilder("6");
        for (int i = 0; i < 8; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
