package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Detalle ({@code data}) del 409 al crear un bloqueo que pisa citas activas u otros bloqueos (D5). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BloqueoConflictoDTO {
    /** Citas PENDIENTE/CONFIRMADA en la franja ({@code tipo = "SOLAPA_BLOQUEO"}). */
    private List<ReservaConflictoDTO> citas;
    /** Bloqueos propios ya existentes que se solapan. */
    private List<BloqueoDTO> bloqueos;
}
