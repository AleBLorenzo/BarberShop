package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Vista previa de un cierre (D4): las citas activas que se cancelarían, sin
 * modificar nada. {@code yaExiste} avisa de que el cierre ya está creado (el
 * POST respondería 409).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CierrePreviewDTO {
    private LocalDate fecha;
    private Boolean general;
    private Long trabajadorId;
    private String trabajadorNombre;
    private Boolean yaExiste;
    private Integer total;
    private Integer porEmail;
    private Integer porTelefono;
    /** Citas afectadas ({@code tipo = "CIERRE"}). */
    private List<ReservaConflictoDTO> citas;
}
