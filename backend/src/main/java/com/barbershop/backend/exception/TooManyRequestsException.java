package com.barbershop.backend.exception;

import lombok.Getter;

/**
 * Rate limit superado (429) con el número de segundos que deben esperarse
 * antes de reintentar — se traduce a la cabecera {@code Retry-After} en
 * {@link GlobalExceptionHandler} (cambio harden-open-security-findings, D1).
 */
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }
}
