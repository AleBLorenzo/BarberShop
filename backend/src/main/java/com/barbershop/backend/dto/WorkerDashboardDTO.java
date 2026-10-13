package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerDashboardDTO {
    private Long trabajadorId;
    private String trabajadorNombre;
    private LocalDate fecha;
    private List<ReservaDTO> reservasDelDia;
    private Integer totalReservas;
    private Integer horasOcupadas;
    private BigDecimal ingresosDelDia;
    /** Ingreso de reservas COMPLETADA pagadas con visa en la fecha. */
    private BigDecimal ingresosVisa;
    /** Ingreso de reservas COMPLETADA pagadas en efectivo en la fecha. */
    private BigDecimal ingresosEfectivo;
}