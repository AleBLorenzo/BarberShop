package com.barbershop.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaReprogramarRequest {
    @NotNull(message = "El token es obligatorio")
    private UUID token;

    @NotNull(message = "La nueva fecha es obligatoria")
    private LocalDate nuevaFecha;

    @NotNull(message = "La nueva hora de inicio es obligatoria")
    private LocalTime nuevaHoraInicio;
}