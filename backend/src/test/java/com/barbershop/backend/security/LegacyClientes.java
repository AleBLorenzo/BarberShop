package com.barbershop.backend.security;

import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Fixtures de filas LEGACY (previas al cambio dedupe-client-on-rebooking):
 * se insertan con SQL directo para saltarse los hooks {@code
 * @PrePersist/@PreUpdate} de {@code Cliente}. Una fila con teléfono {@code
 * +34…} ya no podría guardarse hoy vía JPA (el hook la normalizaría), y
 * {@code telefono_normalizado} queda {@code NULL} como en la BD real antes
 * del backfill.
 */
final class LegacyClientes {

    private LegacyClientes() {
    }

    /** Inserta una ficha legacy y devuelve su id. {@code telefono} debe ser único en la BD. */
    static long insertarCliente(JdbcTemplate jdbc, String nombre, String telefono, String email) {
        jdbc.update("INSERT INTO clientes (nombre, telefono, telefono_normalizado, email, created_at) "
                        + "VALUES (?, ?, NULL, ?, ?)",
                nombre, telefono, email, LocalDateTime.now());
        return jdbc.queryForObject(
                "SELECT MAX(id) FROM clientes WHERE telefono = ?", Long.class, telefono);
    }
}
