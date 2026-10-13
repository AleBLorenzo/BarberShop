package com.barbershop.backend.model;

/**
 * Rol de seguridad de un trabajador.
 *
 * <p>{@code ADMIN}: propietario/encargado; acceso a {@code /api/admin/**}.
 * {@code WORKER}: peluquero de plantilla; solo sus propios endpoints de
 * {@code /api/worker/**} (más la subida de su propia foto).</p>
 */
public enum Rol {
    ADMIN,
    WORKER
}
