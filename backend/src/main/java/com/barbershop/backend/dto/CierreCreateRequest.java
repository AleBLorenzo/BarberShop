package com.barbershop.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Cuerpo de {@code POST /api/admin/cierres} (D4): sin {@code trabajadorId} =
 * cierre de toda la barbería; con él, solo el día de ese trabajador.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CierreCreateRequest {

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    /** {@code null} = cierre general. */
    private Long trabajadorId;

    @Size(max = 255, message = "El motivo no puede exceder 255 caracteres")
    private String motivo;
}
