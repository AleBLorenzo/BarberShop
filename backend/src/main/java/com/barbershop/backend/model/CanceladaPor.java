package com.barbershop.backend.model;

/** Quién canceló una reserva (D6); {@code null} en reservas no canceladas o antiguas. */
public enum CanceladaPor {
    CLIENTE,
    ADMIN
}
