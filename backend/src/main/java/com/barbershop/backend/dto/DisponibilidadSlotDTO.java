package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisponibilidadSlotDTO {
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private Boolean disponible;
    private Long trabajadorId;      // worker this slot belongs to (needed to book "any worker")
    private String trabajadorNombre; // if specific worker
}