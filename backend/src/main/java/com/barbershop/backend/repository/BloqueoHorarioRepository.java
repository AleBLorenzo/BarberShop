package com.barbershop.backend.repository;

import com.barbershop.backend.model.BloqueoHorario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface BloqueoHorarioRepository extends JpaRepository<BloqueoHorario, Long> {

    List<BloqueoHorario> findByTrabajador_IdAndFechaOrderByHoraInicioAsc(Long trabajadorId, LocalDate fecha);

    List<BloqueoHorario> findByTrabajador_IdAndFechaBetweenOrderByFechaAscHoraInicioAsc(
            Long trabajadorId, LocalDate desde, LocalDate hasta);

    /** Listado del admin: todos los trabajadores en el rango. */
    @Query("SELECT b FROM BloqueoHorario b JOIN FETCH b.trabajador WHERE b.fecha BETWEEN :desde AND :hasta ORDER BY b.fecha ASC, b.horaInicio ASC")
    List<BloqueoHorario> findByFechaBetween(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /** Bloqueos del trabajador que se solapan con [horaInicio, horaFin) en la fecha. */
    @Query("SELECT b FROM BloqueoHorario b WHERE b.trabajador.id = :trabajadorId AND b.fecha = :fecha AND b.horaInicio < :horaFin AND b.horaFin > :horaInicio ORDER BY b.horaInicio ASC")
    List<BloqueoHorario> findSolapados(@Param("trabajadorId") Long trabajadorId, @Param("fecha") LocalDate fecha,
                                       @Param("horaInicio") LocalTime horaInicio, @Param("horaFin") LocalTime horaFin);
}
