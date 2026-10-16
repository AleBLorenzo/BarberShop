package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Random;
import java.util.UUID;

import com.barbershop.backend.config.ClientDedupeBackfill;
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
 * Spec client-data-consistency — "Fusión desactivada por configuración":
 * con {@code app.client-dedupe.merge-duplicates=false} el runner normaliza
 * lo posible (la columna única la recibe como mucho UNA ficha del grupo),
 * pero NO elimina ninguna ficha, NO re-apunta reservas y NO completa huecos.
 * Contexto propio por las properties (BD H2 nueva vía {@code ddl-auto}).
 */
@SpringBootTest(properties = "app.client-dedupe.merge-duplicates=false")
class ClientDedupeBackfillDisabledTest {

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

    @Test
    void normalizaPeroNoEliminaNiReApunta() {
        String base = telefonoUnico();
        String emailConUna = "killswitch-" + UUID.randomUUID() + "@x.com";
        Trabajador trabajador = trabajadorDePrueba();
        Servicio servicio = servicioDePrueba();

        long idConTres = LegacyClientes.insertarCliente(jdbc, "Killswitch Tres", base, null);
        long idConUna = LegacyClientes.insertarCliente(jdbc, "Killswitch Una", "+34" + base, emailConUna);
        for (int i = 0; i < 3; i++) {
            insertarReservaLegacy(idConTres, trabajador, servicio, i);
        }
        insertarReservaLegacy(idConUna, trabajador, servicio, 3);

        backfill.run(new DefaultApplicationArguments());

        // Ninguna ficha eliminada: ambas siguen existiendo…
        var conservada = clienteRepository.findById(idConTres).orElseThrow();
        var noConservada = clienteRepository.findById(idConUna).orElseThrow();

        // …y las reservas siguen apuntando a su ficha original (sin re-apuntar)
        assertThat(reservaRepository.countByCliente_Id(idConTres)).isEqualTo(3);
        assertThat(reservaRepository.countByCliente_Id(idConUna)).isEqualTo(1);

        // La columna única la recibe SOLO la conservada (una por grupo);
        // la otra queda sin normalizar, sin fusión posible
        assertThat(conservada.getTelefonoNormalizado()).isEqualTo(base);
        assertThat(noConservada.getTelefonoNormalizado()).isNull();

        // Sin fusión tampoco se completan huecos ni se borra nada
        assertThat(conservada.getEmail()).isNull();
        assertThat(noConservada.getEmail()).isEqualTo(emailConUna);
        assertThat(noConservada.getTelefono()).isEqualTo("+34" + base);
    }

    // --- helpers ---

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
                .nombre("Killswitch Test " + sufijo)
                .email("killswitch-" + sufijo + "@barbershop.test")
                .passwordHash("killswitch-" + sufijo)
                .activo(true)
                .rol(Rol.WORKER)
                .build());
    }

    private Servicio servicioDePrueba() {
        return servicioRepository.save(Servicio.builder()
                .nombre("Corte killswitch " + UUID.randomUUID().toString().substring(0, 8))
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
