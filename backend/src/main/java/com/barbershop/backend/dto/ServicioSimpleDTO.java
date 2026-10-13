package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServicioSimpleDTO {
    private Long id;
    private String nombre;
    private BigDecimal precio;
    private Integer duracionMinutos;
    /** Ruta pública de la foto (opcional). */
    private String foto;
}