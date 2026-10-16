package com.barbershop.backend.repository;

import com.barbershop.backend.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByTelefono(String telefono);

    /** Identidad canónica (D1): teléfono ya normalizado, respaldado por índice único. */
    Optional<Cliente> findByTelefonoNormalizado(String telefonoNormalizado);

    /** Email en minúsculas (D1): el email no es clave única, pero la caja sí se ignora. */
    Optional<Cliente> findByEmailIgnoreCase(String email);

    Optional<Cliente> findByEmail(String email);

    Optional<Cliente> findByNombreIgnoreCase(String nombre);

    boolean existsByTelefono(String telefono);

    // ESCAPE declarativo (D2): H2 (y PostgreSQL) NO usan '\' como carácter de
    // escape por defecto en LIKE; el patrón lo escapa LikeEscaper en la capa
    // de servicio y esta cláusula hace que se interprete literalmente.
    @Query("SELECT c FROM Cliente c WHERE LOWER(c.nombre) LIKE LOWER(CONCAT('%', :nombre, '%')) ESCAPE '\\'")
    List<Cliente> findByNombreContainingIgnoreCase(@Param("nombre") String nombre);

    // ========== Backfill de dedupe (ClientDedupeBackfill) ==========
    // Actualizaciones masivas a propósito: NO disparan @PrePersist/@PreUpdate,
    // de modo que la tarea de arranque solo escribe la columna normalizada
    // (preserva el teléfono histórico tal cual lo escribió el usuario, D1c).

    /** Escribe la identidad canónica calculada sin tocar el teléfono original. */
    @Modifying
    @Query("UPDATE Cliente c SET c.telefonoNormalizado = :valor WHERE c.id = :id")
    void actualizarTelefonoNormalizado(@Param("id") Long id, @Param("valor") String valor);

    /** Completa un hueco vacío durante una fusión (el llamador verifica que esté vacío, B-MED-10). */
    @Modifying
    @Query("UPDATE Cliente c SET c.nombre = :nombre WHERE c.id = :id")
    void actualizarNombre(@Param("id") Long id, @Param("nombre") String nombre);

    /** Completa un hueco vacío durante una fusión (el llamador verifica que esté vacío, B-MED-10). */
    @Modifying
    @Query("UPDATE Cliente c SET c.email = :email WHERE c.id = :id")
    void actualizarEmail(@Param("id") Long id, @Param("email") String email);
}