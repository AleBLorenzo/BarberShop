package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Respuesta de la creación de un cierre (D4): el cierre y el resumen de las
 * citas canceladas (cuántas se avisan por email y cuántas quedan pendientes
 * de aviso telefónico, con sus avisos para mostrarlos sin otra petición).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CierreResultadoDTO {
    private CierreDTO cierre;
    private Integer canceladas;
    private Integer porEmail;
    private Integer porTelefono;
    private List<AvisoCancelacionDTO> avisos;
}
