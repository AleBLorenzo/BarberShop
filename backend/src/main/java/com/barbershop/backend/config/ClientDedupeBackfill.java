package com.barbershop.backend.config;

import com.barbershop.backend.model.Cliente;
import com.barbershop.backend.repository.ClienteRepository;
import com.barbershop.backend.repository.ReservaRepository;
import com.barbershop.backend.util.ClientNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Backfill idempotente de identidad canónica (design D4, cambio
 * dedupe-client-on-rebooking): al arrancar completa
 * {@code clientes.telefono_normalizado} en las filas existentes y fusiona los
 * duplicados históricos creados antes de la normalización (variantes de
 * formato del teléfono, p. ej. {@code 612345678} vs {@code +34612345678}).
 *
 * <p>Orden seguro respecto al índice único (Hibernate lo crea en el arranque
 * con {@code ddl-auto=update}, ANTES de este runner):
 * <ol>
 *   <li>(a) agrupar <b>en memoria</b> por teléfono normalizado — sin escribir nada;</li>
 *   <li>(b) fusionar primero: re-apuntar {@code reservas.cliente_id} y borrar
 *       los perdedores (ya no quedan dos filas con el mismo valor);</li>
 *   <li>(c) solo después escribir la columna de los supervivientes.</li>
 * </ol>
 * Así ninguna colisión del índice único puede ocurrir por construcción; el
 * {@code catch (DataIntegrityViolationException)} es la red de seguridad que
 * trata cualquier choque residual como OTRO grupo a fusionar en lugar de
 * tumbar el arranque.</p>
 *
 * <p>Controlado por {@code app.client-dedupe.merge-duplicates} (default
 * {@code true}): con {@code false} solo se normaliza lo posible, sin borrar ni
 * re-apuntar nada. Solo escribe la columna normalizada (consultas masivas que
 * no disparan los hooks de la entidad): el teléfono histórico tal como lo
 * escribió el usuario se conserva. Sin cambios si no hay nada que hacer, y
 * re-ejecutarlo no modifica datos de nuevo. Cada fusión queda en log con todos
 * los ids y sus conteos de reservas.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientDedupeBackfill implements ApplicationRunner {

    private final ClienteRepository clienteRepository;
    private final ReservaRepository reservaRepository;

    /** Kill-switch (D4): {@code false} = solo normalizar, nunca borrar ni re-apuntar. */
    @Value("${app.client-dedupe.merge-duplicates:true}")
    private boolean mergeDuplicates;

    /** Una fusión pendiente: conservado + perdedores (antes de re-apuntar). */
    private record Fusion(Cliente conservado, List<Cliente> perdedores) {
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Cliente> clientes = clienteRepository.findAll();
        if (clientes.isEmpty()) {
            return;
        }

        // Conteos de reservas ANTES de re-apuntar: criterio de conservación y
        // log de cada fusión con sus números previos (tabla pequeña: una
        // consulta por ficha).
        Map<Long, Long> reservasPrevias = new LinkedHashMap<>();
        for (Cliente cliente : clientes) {
            reservasPrevias.put(cliente.getId(), reservaRepository.countByCliente_Id(cliente.getId()));
        }

        // (a) Agrupar EN MEMORIA por teléfono normalizado: todavía no se escribe nada.
        Map<String, List<Cliente>> grupos = new LinkedHashMap<>();
        for (Cliente cliente : clientes) {
            String normalizado = ClientNormalizer.normalizarTelefono(cliente.getTelefono());
            if (normalizado == null || normalizado.isBlank()) {
                continue; // sin teléfono normalizable: fuera de la dedupe
            }
            grupos.computeIfAbsent(normalizado, clave -> new ArrayList<>()).add(cliente);
        }

        List<Fusion> fusiones = new ArrayList<>();
        List<Cliente> aNormalizar = new ArrayList<>();
        Map<String, List<Cliente>> gruposSinFusionar = new LinkedHashMap<>(); // merge-duplicates=false

        for (Map.Entry<String, List<Cliente>> grupo : grupos.entrySet()) {
            String normalizado = grupo.getKey();
            List<Cliente> miembros = grupo.getValue();

            if (miembros.size() == 1) {
                aNormalizar.add(miembros.get(0));
                continue;
            }

            Cliente conservado = elegirConservado(miembros, reservasPrevias);

            if (!mergeDuplicates) {
                // Solo normalizar: la columna es única, así que como mucho UNA
                // ficha del grupo puede recibir el valor; no se borra ni se
                // re-apunta nada (nunca se elimina ninguna ficha).
                boolean valorYaAsignado = miembros.stream()
                        .anyMatch(c -> normalizado.equals(c.getTelefonoNormalizado()));
                if (!valorYaAsignado) {
                    aNormalizar.add(conservado);
                }
                gruposSinFusionar.put(normalizado, miembros);
                continue;
            }

            List<Cliente> perdedores = miembros.stream()
                    .filter(c -> !c.getId().equals(conservado.getId()))
                    .toList();
            fusiones.add(new Fusion(conservado, perdedores));
            aNormalizar.add(conservado);
        }

        // (b1) Re-apuntar reservas de los perdedores a su conservado (ANTES de borrar: FK)
        for (Fusion fusion : fusiones) {
            List<Long> idsPerdedores = fusion.perdedores().stream().map(Cliente::getId).toList();
            reservaRepository.repuntarReservasACliente(fusion.conservado().getId(), idsPerdedores);
        }

        // (b2) Borrar perdedores y forzar el flush AHORA: los deletes se
        // ejecutan antes de cualquier escritura posterior (los updates no deben
        // adelantarse a los deletes).
        if (!fusiones.isEmpty()) {
            List<Cliente> todosPerdedores = fusiones.stream()
                    .flatMap(fusion -> fusion.perdedores().stream())
                    .toList();
            clienteRepository.deleteAll(todosPerdedores);
            clienteRepository.flush();
        }

        // Completado de huecos del conservado desde sus fusionadas (B-MED-10:
        // solo campos vacíos, nunca sobrescribir; gana el primero) + log de
        // cada fusión con todos los ids y sus conteos previos.
        for (Fusion fusion : fusiones) {
            Cliente conservado = fusion.conservado();
            List<Cliente> perdedores = fusion.perdedores();

            String nombre = conservado.getNombre();
            String email = conservado.getEmail();
            for (Cliente perdido : perdedores) {
                if (estaVacio(nombre) && !estaVacio(perdido.getNombre())) {
                    nombre = perdido.getNombre();
                }
                if (estaVacio(email) && !estaVacio(perdido.getEmail())) {
                    email = ClientNormalizer.normalizarEmail(perdido.getEmail());
                }
            }
            if (!Objects.equals(nombre, conservado.getNombre()) && !estaVacio(nombre)) {
                clienteRepository.actualizarNombre(conservado.getId(), nombre);
            }
            if (!Objects.equals(email, conservado.getEmail()) && !estaVacio(email)) {
                clienteRepository.actualizarEmail(conservado.getId(), email);
            }

            log.warn("Fusión de clientes duplicados (teléfono normalizado '{}'): se conserva id={} "
                            + "({} reservas); se eliminan ids=[{}] y sus reservas pasan al conservado",
                    ClientNormalizer.normalizarTelefono(conservado.getTelefono()),
                    conservado.getId(),
                    reservasPrevias.getOrDefault(conservado.getId(), 0L),
                    perdedores.stream()
                            .map(p -> p.getId() + " (" + reservasPrevias.getOrDefault(p.getId(), 0L) + " reservas)")
                            .collect(Collectors.joining(", ")));
        }

        // (c) Escribir telefono_normalizado de los supervivientes (solo si difiere)
        AtomicInteger filasNormalizadas = new AtomicInteger();
        for (Cliente cliente : aNormalizar) {
            String normalizado = ClientNormalizer.normalizarTelefono(cliente.getTelefono());
            if (normalizado == null || normalizado.isBlank()
                    || normalizado.equals(cliente.getTelefonoNormalizado())) {
                continue; // ya canónico: sin cambios (idempotencia)
            }
            aplicarNormalizado(cliente, normalizado, filasNormalizadas);
        }

        if (filasNormalizadas.get() == 0 && fusiones.isEmpty() && gruposSinFusionar.isEmpty()) {
            log.info("ClientDedupeBackfill: sin duplicados, 0 cambios");
        } else {
            log.info("ClientDedupeBackfill: {} fila(s) con telefono_normalizado actualizado; Clientes fusionados: {}",
                    filasNormalizadas.get(), fusiones.size());
        }
        gruposSinFusionar.forEach((normalizado, miembros) ->
                log.warn("ClientDedupeBackfill: '{}' compartido por las fichas ids={} sin fusionar "
                                + "(app.client-dedupe.merge-duplicates=false: no se borra ni re-apunta)",
                        normalizado, miembros.stream().map(Cliente::getId).toList()));
    }

    /**
     * Escribe la columna con red de seguridad (D4): si el índice único chocara,
     * esa colisión ES otro grupo de duplicados no detectado — se fusiona en el
     * momento (si la fusión está activa) en lugar de propagar el error.
     */
    private void aplicarNormalizado(Cliente cliente, String normalizado, AtomicInteger filasNormalizadas) {
        try {
            clienteRepository.actualizarTelefonoNormalizado(cliente.getId(), normalizado);
            filasNormalizadas.incrementAndGet();
        } catch (DataIntegrityViolationException e) {
            manejarColision(cliente, normalizado, filasNormalizadas, e);
        }
    }

    private void manejarColision(Cliente cliente, String normalizado,
                                 AtomicInteger filasNormalizadas, DataIntegrityViolationException e) {
        try {
            Optional<Cliente> ocupante = clienteRepository.findByTelefonoNormalizado(normalizado);
            if (ocupante.isEmpty() || ocupante.get().getId().equals(cliente.getId())) {
                log.warn("ClientDedupeBackfill: colisión de '{}' en id={} sin ocupante identificable; "
                        + "se omite en esta ejecución", normalizado, cliente.getId(), e);
                return;
            }
            if (!mergeDuplicates) {
                log.warn("ClientDedupeBackfill: '{}' ya pertenece a la ficha id={}; la ficha id={} "
                        + "no se normaliza (merge-duplicates=false: no se fusiona)", normalizado,
                        ocupante.get().getId(), cliente.getId());
                return;
            }
            // Grupo no detectado en la agrupación inicial → fusión conservadora
            // en caliente: completar huecos, re-apuntar, borrar y reintentar.
            Cliente perdido = ocupante.get();
            log.warn("ClientDedupeBackfill: colisión inesperada de '{}' → grupo de duplicados no detectado: "
                    + "fusionar id={} con id={}", normalizado, cliente.getId(), perdido.getId());
            if (estaVacio(cliente.getNombre()) && !estaVacio(perdido.getNombre())) {
                clienteRepository.actualizarNombre(cliente.getId(), perdido.getNombre());
            }
            if (estaVacio(cliente.getEmail()) && !estaVacio(perdido.getEmail())) {
                clienteRepository.actualizarEmail(cliente.getId(),
                        ClientNormalizer.normalizarEmail(perdido.getEmail()));
            }
            reservaRepository.repuntarReservasACliente(cliente.getId(), List.of(perdido.getId()));
            clienteRepository.delete(perdido);
            clienteRepository.flush();
            clienteRepository.actualizarTelefonoNormalizado(cliente.getId(), normalizado);
            filasNormalizadas.incrementAndGet();
            log.warn("ClientDedupeBackfill: fusión perezosa completada para '{}': ids=[{}, {}]",
                    normalizado, cliente.getId(), perdido.getId());
        } catch (Exception fallo) {
            // En PostgreSQL la transacción queda abortada tras el choque y los
            // pasos de recuperación pueden no poder ejecutarse: no se propaga
            // para no tumbar el arranque — se registra y la próxima ejecución
            // reintentará (el kill-switch de fusión sigue disponible).
            log.warn("ClientDedupeBackfill: no se pudo resolver la colisión de '{}' en id={}: {}",
                    normalizado, cliente.getId(), fallo.getMessage());
        }
    }

    /**
     * Ficha conservada (D4): más reservas → empate, {@code createdAt} menor →
     * id menor. Determinista para que re-ejecutar el runner decida igual.
     */
    private Cliente elegirConservado(List<Cliente> miembros, Map<Long, Long> reservasPrevias) {
        return miembros.stream()
                .min(Comparator
                        .comparingLong((Cliente c) -> reservasPrevias.getOrDefault(c.getId(), 0L)).reversed()
                        .thenComparing(Cliente::getCreatedAt,
                                Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
                        .thenComparing(Cliente::getId))
                .orElseThrow();
    }

    private static boolean estaVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
