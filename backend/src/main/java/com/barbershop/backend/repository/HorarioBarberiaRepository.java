package com.barbershop.backend.repository;

import com.barbershop.backend.model.HorarioBarberia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.Optional;

@Repository
public interface HorarioBarberiaRepository extends JpaRepository<HorarioBarberia, Long> {

    Optional<HorarioBarberia> findByDiaSemana(DayOfWeek diaSemana);
}
