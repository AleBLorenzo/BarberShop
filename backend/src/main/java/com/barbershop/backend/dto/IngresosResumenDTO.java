package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Respuesta de {@code GET /api/admin/ingresos} (D10): una fila por trabajador y los totales. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngresosResumenDTO {
    private LocalDate inicio;
    private LocalDate fin;
    private List<IngresoTrabajadorDTO> filas;
    private IngresoTrabajadorDTO totales;
}
