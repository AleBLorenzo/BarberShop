package com.barbershop.backend.repository;

import com.barbershop.backend.model.Resultado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResultadoRepository extends JpaRepository<Resultado, Long> {
    /** Galería de la portada: las más recientes primero. */
    List<Resultado> findAllByOrderByCreatedAtDesc();
}
