package com.barbershop.backend.exception;

/**
 * Rechazo del reto anti-bot del login (403 con {@code code=BOT_CHALLENGE}):
 * cabecera {@code X-Login-Challenge} ausente/inválida/caducada, User-Agent
 * ausente con el reto habilitado, o token de Turnstile inválido (cambio
 * harden-sql-injection-anti-bot, D5/tareas 3.4 y 3.7).
 *
 * <p>Se lanza ANTES de verificar credenciales y ANTES del rate limiter de
 * credenciales, sin emitir tokens; se traduce a 403 en
 * {@link GlobalExceptionHandler} (mismo patrón que
 * {@link TooManyRequestsException} → 429). El motivo de auditoría asociado
 * es {@code CHALLENGE_FAILED}.</p>
 */
public class BotChallengeException extends RuntimeException {

    /** Código de negocio en el body del 403. */
    public static final String CODIGO_BOT_CHALLENGE = "BOT_CHALLENGE";

    public BotChallengeException(String message) {
        super(message);
    }
}
