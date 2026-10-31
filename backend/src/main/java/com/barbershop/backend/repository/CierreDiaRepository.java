package com.barbershop.backend.repository;

import com.barbershop.backend.model.CierreDia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Cierres de día (D4). Las consultas "aplicables a un trabajador" usan
 * LEFT JOIN: con {@code c.trabajador.id} (join implícito = INNER) se
 * perderían los cierres generales ({@code trabajador IS NULL}).
 */
@Repository
public interface CierreDiaRepository extends JpaRepository<CierreDia, Long> {

    List<CierreDia> findByFecha(LocalDate fecha);

    @Query("SELECT c FROM CierreDia c LEFT JOIN FETCH c.trabajador WHERE c.fecha BETWEEN :desde AND :hasta ORDER BY c.fecha ASC, c.id ASC")
    List<CierreDia> findByFechaBetween(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /** Cierre general (toda la barbería) de una fecha, si existe. */
    Optional<CierreDia> findFirstByFechaAndTrabajadorIsNull(LocalDate fecha);

    /** Cierre de un trabajador concreto en una fecha, si existe. */
    Optional<CierreDia> findFirstByFechaAndTrabajador_Id(LocalDate fecha, Long trabajadorId);

    /** Cierres que afectan a un trabajador en una fecha: el general y el suyo. */
    @Query("SELECT c FROM CierreDia c LEFT JOIN c.trabajador t WHERE c.fecha = :fecha AND (t IS NULL OR t.id = :trabajadorId)")
    List<CierreDia> findAplicables(@Param("fecha") LocalDate fecha, @Param("trabajadorId") Long trabajadorId);

    /** Cierres que afectan a un trabajador en un rango (agenda en bloque, sin N+1). */
    @Query("SELECT c FROM CierreDia c LEFT JOIN c.trabajador t WHERE c.fecha BETWEEN :desde AND :hasta AND (t IS NULL OR t.id = :trabajadorId)")
    List<CierreDia> findAplicablesEnRango(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
                                          @Param("trabajadorId") Long trabajadorId);
}
