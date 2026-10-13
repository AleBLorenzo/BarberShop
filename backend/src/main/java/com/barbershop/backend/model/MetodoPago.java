package com.barbershop.backend.model;

/**
 * Método de pago registrado al completar una cita (se cobra en tienda;
 * solo se guarda cómo pagó el cliente). Nullable en reservas antiguas.
 */
public enum MetodoPago {
    VISA,
    EFECTIVO
}
