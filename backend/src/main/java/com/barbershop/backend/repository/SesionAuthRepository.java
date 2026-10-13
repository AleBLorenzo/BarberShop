package com.barbershop.backend.repository;

import com.barbershop.backend.model.SesionAuth;
import com.barbershop.backend.model.Trabajador;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SesionAuthRepository extends JpaRepository<SesionAuth, UUID> {

    /**
     * Revoca todas las sesiones activas del trabajador (logout global al
     * desactivar la cuenta desde admin — tarea 6.6). Devuelve el nº de
     * sesiones revocadas; {@code @Modifying} requiere transacción (los
     * servicios llamantes ya son {@code @Transactional}).
     */
    @Modifying
    @Query("UPDATE SesionAuth s SET s.revocadaEn = :ahora "
            + "WHERE s.trabajador.id = :trabajadorId AND s.revocadaEn IS NULL")
    int revocarActivas(@Param("trabajadorId") Long trabajadorId,
                       @Param("ahora") LocalDateTime ahora);

    /** Borra las sesiones antes de un borrado físico del trabajador (FK). */
    void deleteByTrabajador(Trabajador trabajador);

    long countByTrabajadorId(Long trabajadorId);
}
