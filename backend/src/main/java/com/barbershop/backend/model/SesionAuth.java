package com.barbershop.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sesión de autenticación persistida (cambio harden-open-security-findings,
 * decisión D2 — spec jwt-session-revocation): su {@code id} (UUID) es el
 * claim {@code sid} que llevan access y refresh tokens. El logout marca
 * {@code revocada_en} y el filtro de autenticación rechaza tokens cuya
 * sesión no está activa → revocación inmediata de ambos tokens.
 *
 * <p>La tabla la crea {@code ddl-auto=update} ({@code sesion_auth}); sin
 * migración manual ni volcado de datos.</p>
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "sesion_auth")
public class SesionAuth {

    /** UUID del claim {@code sid} (asignado, no autoincremental). */
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trabajador_id", nullable = false)
    private Trabajador trabajador;

    /** Momento del login. */
    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    /** Fin de vida de la sesión (ventana del refresh; se desliza al refrescar). */
    @Column(name = "caduca_en", nullable = false)
    private LocalDateTime caducaEn;

    /** Momento del logout; {@code null} = sesión activa. */
    @Column(name = "revocada_en")
    private LocalDateTime revocadaEn;

    /** Momento del último refresh exitoso (observabilidad). */
    @Column(name = "ultimo_refresh_en")
    private LocalDateTime ultimoRefreshEn;

    /** ¿Sesión utilizable? (no revocada y no caducada) */
    public boolean estaActiva() {
        return revocadaEn == null && caducaEn.isAfter(LocalDateTime.now());
    }
}
