package com.barbershop.backend.repository;

import com.barbershop.backend.model.AvisoCancelacion;
import com.barbershop.backend.model.CanalAviso;
import com.barbershop.backend.model.EstadoAviso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Avisos de cancelación (D7). "Pendientes de acción" = TELEFONO/PENDIENTE +
 * EMAIL/FALLIDO + EMAIL/PENDIENTE_ENVIO creados antes de {@code limite} (el
 * proceso pudo morir entre el commit y el envío asíncrono).
 */
@Repository
public interface AvisoCancelacionRepository extends JpaRepository<AvisoCancelacion, Long> {

    Optional<AvisoCancelacion> findByReserva_Id(Long reservaId);

    @Query("SELECT a FROM AvisoCancelacion a JOIN FETCH a.reserva r JOIN FETCH r.cliente JOIN FETCH r.servicio JOIN FETCH r.trabajador "
            + "WHERE (a.canal = :telefono AND a.estado = :pendiente) "
            + "OR (a.canal = :email AND a.estado = :fallido) "
            + "OR (a.canal = :email AND a.estado = :pendienteEnvio AND a.createdAt < :limite) "
            + "ORDER BY r.fecha ASC, r.horaInicio ASC")
    List<AvisoCancelacion> findPendientesDeAccion(@Param("telefono") CanalAviso telefono,
                                                  @Param("email") CanalAviso email,
                                                  @Param("pendiente") EstadoAviso pendiente,
                                                  @Param("fallido") EstadoAviso fallido,
                                                  @Param("pendienteEnvio") EstadoAviso pendienteEnvio,
                                                  @Param("limite") LocalDateTime limite);

    @Query("SELECT COUNT(a) FROM AvisoCancelacion a "
            + "WHERE (a.canal = :telefono AND a.estado = :pendiente) "
            + "OR (a.canal = :email AND a.estado = :fallido) "
            + "OR (a.canal = :email AND a.estado = :pendienteEnvio AND a.createdAt < :limite)")
    long countPendientesDeAccion(@Param("telefono") CanalAviso telefono,
                                 @Param("email") CanalAviso email,
                                 @Param("pendiente") EstadoAviso pendiente,
                                 @Param("fallido") EstadoAviso fallido,
                                 @Param("pendienteEnvio") EstadoAviso pendienteEnvio,
                                 @Param("limite") LocalDateTime limite);

    /** Todos los avisos (los más recientes primero), para el histórico del admin. */
    @Query("SELECT a FROM AvisoCancelacion a JOIN FETCH a.reserva r JOIN FETCH r.cliente JOIN FETCH r.servicio JOIN FETCH r.trabajador "
            + "ORDER BY a.createdAt DESC")
    List<AvisoCancelacion> findAllConDetalle();
}
