package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;

import com.barbershop.backend.config.ClientDedupeBackfill;
import com.barbershop.backend.model.Cliente;
import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.ClienteRepository;
import com.barbershop.backend.repository.ReservaRepository;
import com.barbershop.backend.repository.ServicioRepository;
import com.barbershop.backend.repository.TrabajadorRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spec client-data-consistency — "Duplicados históricos se fusionan de forma
 * controlada" (design D4): {@code ClientDedupeBackfill} es un
 * {@code ApplicationRunner} que ya se ejecuta al arrancar el contexto (sobre
 * una BD vacía). Los tests insertan datos LEGACY DESPUÉS del arranque y
 * vuelven a invocar {@code run(...)} explícitamente — esa segunda invocación
 * además prueba de paso la idempotencia.
 *
 * <p>{@code DataSeeder} NO se ejecuta en tests (está bajo {@code
 * @Profile("dev")}), pero otras clases de test comparten el mismo contexto
 * H2: por eso las afirmaciones usan deltas/conteos por ficha y datos propios
 * con teléfono aleatorio en lugar de números absolutos globales.</p>
 */
@SpringBootTest
class ClientDedupeBackfillTest {

    @Autowired
    private ClientDedupeBackfill backfill;

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

    /** Instantánea de una fila de clientes para comparar ejecuciones del runner. */
    private record ClienteFila(Long id, String nombre, String telefono,
                               String telefonoNormalizado, String email) {
    }

    // --- 5.5(a): fusión de duplicados históricos re-apuntando reservas ---

    @Test
    void fusionaDuplicadosHistoricosReApuntandoReservas() {
        String base = telefonoUnico();
        Trabajador trabajador = trabajadorDePrueba();
        Servicio servicio = servicioDePrueba();
        // Escenario de la spec: "612345678" con 3 reservas y "+34612345678"
        // con 1 reserva (aquí con base aleatoria para no pisar otros tests)
        long idConTres = LegacyClientes.insertarCliente(jdbc, "Legacy Tres", base, null);
        long idConUna = LegacyClientes.insertarCliente(jdbc, "Legacy Una", "+34" + base, "legacy-una@x.com");
        for (int i = 0; i < 3; i++) {
            insertarReservaLegacy(idConTres, trabajador, servicio, i);
        }
        insertarReservaLegacy(idConUna, trabajador, servicio, 3);

        ejecutarBackfill();

        // Se conserva UNA sola ficha y es la que tenía 3 reservas
        Cliente conservado = clienteRepository.findByTelefonoNormalizado(base).orElseThrow();
        assertThat(conservado.getId()).isEqualTo(idConTres);
        assertThat(clienteRepository.findById(idConUna)).isEmpty(); // la otra eliminada

        // Las 4 reservas quedan en el conservado (3 + 1 re-apuntada)
        assertThat(reservaRepository.countByCliente_Id(idConTres)).isEqualTo(4);
        assertThat(reservaRepository.countByCliente_Id(idConUna)).isZero();

        // Hueco completado desde la fusionada (B-MED-10: solo vacíos) y el
        // teléfono histórico del conservado se conserva tal cual
        assertThat(conservado.getNombre()).isEqualTo("Legacy Tres");
        assertThat(conservado.getEmail()).isEqualTo("legacy-una@x.com");
        assertThat(conservado.getTelefono()).isEqualTo(base);

        // Exactamente UNA fila en toda la tabla con esa identidad canónica
        long filasConIdentidad = clienteRepository.findAll().stream()
                .filter(c -> base.equals(c.getTelefonoNormalizado()))
                .count();
        assertThat(filasConIdentidad).isEqualTo(1);
    }

    // --- 5.5(b): segunda ejecución → idempotente ---

    @Test
    void segundaEjecucionNoModificaNada() {
        String base = telefonoUnico();
        Trabajador trabajador = trabajadorDePrueba();
        Servicio servicio = servicioDePrueba();
        long idPrincipal = LegacyClientes.insertarCliente(jdbc, "Idem Principal", base, "idem@x.com");
        long idDuplicada = LegacyClientes.insertarCliente(jdbc, "Idem Duplicada", "+34" + base, null);
        insertarReservaLegacy(idPrincipal, trabajador, servicio, 0);
        insertarReservaLegacy(idPrincipal, trabajador, servicio, 1);
        insertarReservaLegacy(idDuplicada, trabajador, servicio, 2);

        ejecutarBackfill(); // 1ª pasada: fusiona (también normaliza restos de otras clases)

        List<ClienteFila> clientesTrasFusion = instantaneaClientes();
        Map<Long, Long> reservasTrasFusion = instantaneaReservasPorCliente();

        ejecutarBackfill(); // 2ª pasada: nada debe cambiar

        assertThat(instantaneaClientes()).isEqualTo(clientesTrasFusion);
        assertThat(instantaneaReservasPorCliente()).isEqualTo(reservasTrasFusion);
    }

    // --- 5.5(c): sin duplicados → 0 modificaciones, 0 borrados ---

    @Test
    void sinDuplicadosNoModificaNada() {
        // 1ª pasada de apoyo: la BD puede contener restos legacy de otras
        // clases de test; la spec habla de una BD sin duplicados ni formatos
        // no canónicos, así que se canonicaliza antes de medir.
        ejecutarBackfill();

        // Datos nuevos ya canónicos (teléfonos aleatorios, sin relación)
        Trabajador trabajador = trabajadorDePrueba();
        Servicio servicio = servicioDePrueba();
        Cliente uno = clienteRepository.save(Cliente.builder()
                .nombre("Sin Duplicados Uno").telefono(telefonoUnico()).build());
        Cliente dos = clienteRepository.save(Cliente.builder()
                .nombre("Sin Duplicados Dos").telefono(telefonoUnico())
                .email("sin-dup@x.com").build());
        insertarReservaLegacy(uno.getId(), trabajador, servicio, 0);

        List<ClienteFila> clientesAntes = instantaneaClientes();
        Map<Long, Long> reservasAntes = instantaneaReservasPorCliente();

        ejecutarBackfill();

        assertThat(instantaneaClientes()).isEqualTo(clientesAntes);  // ninguna fila modificada
        assertThat(instantaneaReservasPorCliente()).isEqualTo(reservasAntes); // ninguna re-apuntada
        assertThat(clienteRepository.findById(dos.getId()).orElseThrow().getTelefonoNormalizado())
                .isEqualTo(dos.getTelefono()); // sigue intacta
    }

    // --- helpers ---

    private void ejecutarBackfill() {
        backfill.run(new DefaultApplicationArguments());
    }

    private List<ClienteFila> instantaneaClientes() {
        return jdbc.query(
                "SELECT id, nombre, telefono, telefono_normalizado, email FROM clientes ORDER BY id",
                (rs, i) -> new ClienteFila(
                        rs.getLong("id"),
                        rs.getString("nombre"),
                        rs.getString("telefono"),
                        rs.getString("telefono_normalizado"),
                        rs.getString("email")));
    }

    private Map<Long, Long> instantaneaReservasPorCliente() {
        Map<Long, Long> porCliente = new TreeMap<>();
        jdbc.query("SELECT cliente_id, COUNT(*) FROM reservas GROUP BY cliente_id", rs -> {
            porCliente.put(rs.getLong(1), rs.getLong(2));
        });
        return porCliente;
    }

    /** Reserva legacy: SQL directo, sin pasar por el servicio (evita validaciones de agenda). */
    private void insertarReservaLegacy(long clienteId, Trabajador trabajador, Servicio servicio, int indice) {
        jdbc.update("INSERT INTO reservas (cliente_id, trabajador_id, servicio_id, fecha, "
                        + "hora_inicio, hora_fin, estado, token_cancelacion, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'PENDIENTE', ?, ?)",
                clienteId, trabajador.getId(), servicio.getId(), LocalDate.now().plusDays(3),
                LocalTime.of(9 + indice, 0), LocalTime.of(9 + indice, 30),
                UUID.randomUUID().toString(), LocalDateTime.now());
    }

    private Trabajador trabajadorDePrueba() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        return trabajadorRepository.save(Trabajador.builder()
                .nombre("Backfill Test " + sufijo)
                .email("backfill-" + sufijo + "@barbershop.test")
                .passwordHash("backfill-" + sufijo)
                .activo(true)
                .rol(Rol.WORKER)
                .build());
    }

    private Servicio servicioDePrueba() {
        return servicioRepository.save(Servicio.builder()
                .nombre("Corte backfill " + UUID.randomUUID().toString().substring(0, 8))
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
