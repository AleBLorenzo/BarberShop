package com.barbershop.backend.exception;

public class TokenInvalidoException extends BusinessException {
    public TokenInvalidoException(String message) {
        super(message, "TOKEN_INVALIDO");
    }
}