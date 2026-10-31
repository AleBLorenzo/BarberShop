package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Bloqueo de horas libres de un trabajador (D5). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BloqueoDTO {
    private Long id;
    private Long trabajadorId;
    private String trabajadorNombre;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String motivo;
    private LocalDateTime createdAt;
}
