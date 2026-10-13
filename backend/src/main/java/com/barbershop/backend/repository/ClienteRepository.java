package com.barbershop.backend.repository;

import com.barbershop.backend.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByTelefono(String telefono);

    Optional<Cliente> findByEmail(String email);

    Optional<Cliente> findByNombreIgnoreCase(String nombre);

    boolean existsByTelefono(String telefono);

    @Query("SELECT c FROM Cliente c WHERE LOWER(c.nombre) LIKE LOWER(CONCAT('%', :nombre, '%'))")
    List<Cliente> findByNombreContainingIgnoreCase(@Param("nombre") String nombre);
}