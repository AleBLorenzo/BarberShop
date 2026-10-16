package com.barbershop.backend.exception;

import lombok.Getter;

/**
 * Rate limit superado (429) con el número de segundos que deben esperarse
 * antes de reintentar — se traduce a la cabecera {@code Retry-After} en
 * {@link GlobalExceptionHandler} (cambio harden-open-security-findings, D1).
 *
 * <p>Lleva además un {@code codigo} de negocio en el body para distinguir el
 * rate limit genérico ({@link #CODIGO_RATE_LIMIT}) del lockout de cuenta
 * ({@link #CODIGO_ACCOUNT_LOCKED}), sin cambiar el status 429 y sin
 * introducir 423 (cambio harden-sql-injection-anti-bot, D4/tarea 2.6).</p>
 */
@Getter
public class TooManyRequestsException extends RuntimeException {

    /** Rate limit genérico (buckets por IP / email+IP, reservas). */
    public static final String CODIGO_RATE_LIMIT = "TOO_MANY_REQUESTS";

    /** Lockout de cuenta por el bucket sin IP (login:acct:{email}). */
    public static final String CODIGO_ACCOUNT_LOCKED = "ACCOUNT_LOCKED";

    private final long retryAfterSeconds;
    private final String codigo;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        this(message, retryAfterSeconds, CODIGO_RATE_LIMIT);
    }

    public TooManyRequestsException(String message, long retryAfterSeconds, String codigo) {
        super(message);
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
        this.codigo = codigo;
    }
}
