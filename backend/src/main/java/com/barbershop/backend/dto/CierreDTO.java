package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Cierre de día (D4). {@code general = true} ⇔ {@code trabajadorId == null}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CierreDTO {
    private Long id;
    private LocalDate fecha;
    private Boolean general;
    private Long trabajadorId;
    private String trabajadorNombre;
    private String motivo;
    private String creadoPorNombre;
    private LocalDateTime createdAt;
}
