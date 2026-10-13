package com.barbershop.backend.repository;

import com.barbershop.backend.model.Cliente;
import com.barbershop.backend.model.Reserva;
import com.barbershop.backend.model.ReservaEstado;
import com.barbershop.backend.model.Trabajador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    // Find by token for cancel/rebook
    Optional<Reserva> findByTokenCancelacion(String tokenCancelacion);

    // Find reservations for a client
    List<Reserva> findByClienteOrderByFechaDescHoraInicioDesc(Cliente cliente);
    List<Reserva> findByClienteAndEstadoIn(Cliente cliente, List<ReservaEstado> estados);

    // Find reservations for a worker on a date
    List<Reserva> findByTrabajadorAndFechaOrderByHoraInicioAsc(Trabajador trabajador, LocalDate fecha);
    List<Reserva> findByTrabajadorAndFechaBetweenOrderByHoraInicioAsc(Trabajador trabajador, LocalDate start, LocalDate end);
    List<Reserva> findByTrabajadorAndFechaAndEstadoIn(Trabajador trabajador, LocalDate fecha, List<ReservaEstado> estados);

    // Find all reservations for a date range
    List<Reserva> findByFechaBetweenOrderByFechaAscHoraInicioAsc(LocalDate start, LocalDate end);

    // Count by estado in date range
    @Query("SELECT COUNT(r) FROM Reserva r WHERE r.fecha BETWEEN :start AND :end AND r.estado IN :estados")
    long countByFechaBetweenAndEstadoIn(@Param("start") LocalDate start, @Param("end") LocalDate end, @Param("estados") List<ReservaEstado> estados);

    // Critical: Lock rows for update to prevent double-booking
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reserva r WHERE r.trabajador = :trabajador AND r.fecha = :fecha AND r.estado IN ('PENDIENTE', 'CONFIRMADA')")
    List<Reserva> findConflictingReservasForUpdate(@Param("trabajador") Trabajador trabajador, @Param("fecha") LocalDate fecha);

    // Check if a specific slot is available (for quick check before locking)
    @Query("SELECT COUNT(r) > 0 FROM Reserva r WHERE r.trabajador = :trabajador AND r.fecha = :fecha AND r.horaInicio < :horaFin AND r.horaFin > :horaInicio AND r.estado IN ('PENDIENTE', 'CONFIRMADA')")
    boolean existsConflictingReserva(@Param("trabajador") Trabajador trabajador, @Param("fecha") LocalDate fecha, @Param("horaInicio") LocalTime horaInicio, @Param("horaFin") LocalTime horaFin);

    // Stats queries
    @Query("SELECT COUNT(r) FROM Reserva r WHERE r.fecha BETWEEN :start AND :end AND r.estado IN ('CONFIRMADA', 'COMPLETADA')")
    long countCompletedReservasBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COUNT(r) FROM Reserva r WHERE r.fecha BETWEEN :start AND :end AND r.estado = 'NO_ASISTIO'")
    long countNoShowsBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT SUM(s.precio) FROM Reserva r JOIN r.servicio s WHERE r.fecha BETWEEN :start AND :end AND r.estado IN ('CONFIRMADA', 'COMPLETADA')")
    java.math.BigDecimal sumIngresosBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    // ========== Ingresos por método de pago (solo COMPLETADA) ==========

    /** Ingreso total de reservas COMPLETADA en el rango (incluye las sin método de pago registrado). */
    @Query("SELECT SUM(s.precio) FROM Reserva r JOIN r.servicio s WHERE r.fecha BETWEEN :start AND :end AND r.estado = 'COMPLETADA'")
    java.math.BigDecimal sumIngresosCompletadasBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Ingreso COMPLETADA agrupado por método de pago: filas [metodoPago, suma].
     * El grupo con metodoPago = null (reservas antiguas) entra en el total pero
     * no en ninguna franja visa/efectivo; el caller lo omite.
     */
    @Query("SELECT r.metodoPago, SUM(s.precio) FROM Reserva r JOIN r.servicio s WHERE r.fecha BETWEEN :start AND :end AND r.estado = 'COMPLETADA' GROUP BY r.metodoPago")
    List<Object[]> sumIngresosCompletadasPorMetodoPagoBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** ¿Existen reservas que referencian este servicio/trabajador? (borrado seguro) */
    boolean existsByServicio_Id(Long id);
    boolean existsByTrabajador_Id(Long id);

    @Query("SELECT r.trabajador, COUNT(r) FROM Reserva r WHERE r.fecha BETWEEN :start AND :end AND r.estado IN ('CONFIRMADA', 'COMPLETADA') GROUP BY r.trabajador ORDER BY COUNT(r) DESC")
    List<Object[]> countReservasByTrabajadorBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT r.servicio, COUNT(r) FROM Reserva r WHERE r.fecha BETWEEN :start AND :end AND r.estado IN ('CONFIRMADA', 'COMPLETADA') GROUP BY r.servicio ORDER BY COUNT(r) DESC")
    List<Object[]> countReservasByServicioBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}