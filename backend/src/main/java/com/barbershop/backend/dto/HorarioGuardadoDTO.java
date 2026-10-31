package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Respuesta de los PUT de horario (D11): el horario guardado, las citas
 * activas futuras que quedan fuera (NO se cancelan: decide el admin) y avisos
 * informativos (p. ej. horarios de trabajador que ya no caben en el general y
 * se recortarán por intersección, D1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorarioGuardadoDTO {
    private List<HorarioDiaDTO> dias;
    private List<ReservaConflictoDTO> conflictos;
    private List<String> avisos;
}
