package com.barbershop.backend.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Cuerpo de {@code POST /api/admin/reservas/{id}/cancelar} (D6). Motivo opcional. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelarReservaRequest {

    @Size(max = 255, message = "El motivo no puede exceder 255 caracteres")
    private String motivo;
}
