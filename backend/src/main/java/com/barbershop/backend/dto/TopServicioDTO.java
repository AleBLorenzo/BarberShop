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
public class TopServicioDTO {
    private Long servicioId;
    private String servicioNombre;
    private BigDecimal precio;
    private Long totalReservas;
    private BigDecimal ingresosGenerados;
}