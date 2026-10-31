package com.barbershop.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Cuerpo de {@code POST /api/worker/bloqueos} (D5). No tiene
 * {@code trabajadorId} a propósito: el trabajador sale SIEMPRE del JWT y un
 * {@code trabajadorId} en el JSON se ignora (Jackson descarta propiedades
 * desconocidas).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BloqueoCreateRequest {

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    @NotNull(message = "La hora de inicio es obligatoria")
    private LocalTime horaInicio;

    @NotNull(message = "La hora de fin es obligatoria")
    private LocalTime horaFin;

    @Size(max = 255, message = "El motivo no puede exceder 255 caracteres")
    private String motivo;
}
