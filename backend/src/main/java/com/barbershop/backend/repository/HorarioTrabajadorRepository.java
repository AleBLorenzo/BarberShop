package com.barbershop.backend.repository;

import com.barbershop.backend.model.HorarioTrabajador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;

@Repository
public interface HorarioTrabajadorRepository extends JpaRepository<HorarioTrabajador, Long> {

    List<HorarioTrabajador> findByTrabajador_Id(Long trabajadorId);

    /** Horarios propios de varios trabajadores de una vez (conflictos/avisos sin N+1). */
    @Query("SELECT h FROM HorarioTrabajador h JOIN FETCH h.trabajador WHERE h.trabajador.id IN :ids")
    List<HorarioTrabajador> findByTrabajadorIds(@Param("ids") List<Long> ids);

    /** Vuelve a heredar el horario general en ese día (borra el override). */
    @Modifying
    void deleteByTrabajador_IdAndDiaSemana(Long trabajadorId, DayOfWeek diaSemana);
}
