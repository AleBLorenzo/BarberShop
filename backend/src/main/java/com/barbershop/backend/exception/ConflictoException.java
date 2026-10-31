package com.barbershop.backend.exception;

/**
 * 409 de negocio con detalle opcional en {@code data} del envoltorio
 * {@code ApiResponse} (D4, D5): cierre duplicado, bloqueo que pisa citas
 * activas u otro bloqueo (en ese caso {@code detalle} lista los conflictos).
 */
public class ConflictoException extends BusinessException {

    private final transient Object detalle;

    public ConflictoException(String message, String errorCode) {
        this(message, errorCode, null);
    }

    public ConflictoException(String message, String errorCode, Object detalle) {
        super(message, errorCode);
        this.detalle = detalle;
    }

    public Object getDetalle() {
        return detalle;
    }
}
