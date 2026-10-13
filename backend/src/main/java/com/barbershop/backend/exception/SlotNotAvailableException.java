package com.barbershop.backend.exception;

public class SlotNotAvailableException extends BusinessException {
    public SlotNotAvailableException(String message) {
        super(message, "SLOT_NOT_AVAILABLE");
    }
}