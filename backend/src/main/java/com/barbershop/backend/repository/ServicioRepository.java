package com.barbershop.backend.repository;

import com.barbershop.backend.model.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServicioRepository extends JpaRepository<Servicio, Long> {
    Optional<Servicio> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    List<Servicio> findByActivoTrue();

    @Query("SELECT s FROM Servicio s WHERE s.activo = true AND LOWER(s.nombre) LIKE LOWER(CONCAT('%', :nombre, '%'))")
    List<Servicio> findActivosByNombreContainingIgnoreCase(@Param("nombre") String nombre);
}