package com.barbershop.backend.repository;

import com.barbershop.backend.model.Resena;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResenaRepository extends JpaRepository<Resena, Long> {
    /** Las más recientes primero para la portada. */
    List<Resena> findAllByOrderByCreatedAtDesc();

    long countByPuntuacionGreaterThanEqual(int puntuacion);
}
