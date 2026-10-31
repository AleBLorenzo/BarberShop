package com.barbershop.backend.repository;

import com.barbershop.backend.model.Trabajador;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrabajadorRepository extends JpaRepository<Trabajador, Long> {
    Optional<Trabajador> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Trabajador> findByActivoTrue();

    /** Trabajadores visibles en la reserva pública (D8): activos y reservables. */
    List<Trabajador> findByActivoTrueAndReservableTrue();

    /**
     * Lock pesimista sobre la fila del trabajador (D3): serializa reservas,
     * cierres, bloqueos y cambios de horario del mismo trabajador sin depender
     * de que existan filas de reservas que bloquear.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Trabajador t WHERE t.id = :id")
    Optional<Trabajador> findByIdForUpdate(@Param("id") Long id);

    /**
     * Lock de todos los trabajadores activos en orden ascendente de id (D3):
     * el orden fijo evita interbloqueos con los locks individuales.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Trabajador t WHERE t.activo = true ORDER BY t.id ASC")
    List<Trabajador> findAllForUpdateOrderById();

    /**
     * Lock de TODOS los trabajadores, también los inactivos, en orden
     * ascendente de id (D3). Lo usa el cierre general: cancela también las
     * citas futuras que siguen activas de trabajadores dados de baja, y así
     * serializa además con una reactivación concurrente del trabajador
     * (el UPDATE de {@code TrabajadorService.update} espera a este lock).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Trabajador t ORDER BY t.id ASC")
    List<Trabajador> findAllIncluyendoInactivosForUpdateOrderById();

    // ESCAPE declarativo (D2): ver ClienteRepository — H2/PostgreSQL no escapan '\' por defecto.
    @Query("SELECT t FROM Trabajador t WHERE t.activo = true AND LOWER(t.nombre) LIKE LOWER(CONCAT('%', :nombre, '%')) ESCAPE '\\'")
    List<Trabajador> findActivosByNombreContainingIgnoreCase(@Param("nombre") String nombre);

    @Query("SELECT t FROM Trabajador t WHERE t.activo = true AND t.id NOT IN (SELECT r.trabajador.id FROM Reserva r WHERE r.fecha = :fecha AND r.estado IN ('PENDIENTE', 'CONFIRMADA'))")
    List<Trabajador> findDisponiblesEnFecha(@Param("fecha") java.time.LocalDate fecha);

    long countByActivoTrue();
}