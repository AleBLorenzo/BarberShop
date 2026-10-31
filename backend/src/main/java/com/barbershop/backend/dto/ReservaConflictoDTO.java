package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Cita activa futura que no encaja en la agenda efectiva de su trabajador:
 * fuera de jornada, en un día cerrado o sobre un bloqueo (D2, D11).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaConflictoDTO {
    private Long reservaId;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String estado;
    private String clienteNombre;
    private String clienteTelefono;
    /** ¿El cliente dejó email? (decide el canal del aviso si se cancela). */
    private Boolean clienteTieneEmail;
    private Long trabajadorId;
    private String trabajadorNombre;
    private String servicioNombre;
    /** FUERA_DE_JORNADA | DIA_CERRADO | SOBRE_BLOQUEO */
    private String tipo;
    /** Explicación legible (incluye el motivo del cierre si lo hay). */
    private String motivo;
}
