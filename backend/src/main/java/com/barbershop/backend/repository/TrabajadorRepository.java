package com.barbershop.backend.repository;

import com.barbershop.backend.model.Trabajador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrabajadorRepository extends JpaRepository<Trabajador, Long> {
    Optional<Trabajador> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Trabajador> findByActivoTrue();

    @Query("SELECT t FROM Trabajador t WHERE t.activo = true AND LOWER(t.nombre) LIKE LOWER(CONCAT('%', :nombre, '%'))")
    List<Trabajador> findActivosByNombreContainingIgnoreCase(@Param("nombre") String nombre);

    @Query("SELECT t FROM Trabajador t WHERE t.activo = true AND t.id NOT IN (SELECT r.trabajador.id FROM Reserva r WHERE r.fecha = :fecha AND r.estado IN ('PENDIENTE', 'CONFIRMADA'))")
    List<Trabajador> findDisponiblesEnFecha(@Param("fecha") java.time.LocalDate fecha);

    long countByActivoTrue();
}