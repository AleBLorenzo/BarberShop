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
public class DashboardStatsDTO {
    private Long totalReservas;
    private Long reservasConfirmadas;
    private Long reservasCompletadas;
    private Long reservasCanceladas;
    private Long noShows;
    private BigDecimal ingresosTotales;
    private BigDecimal ingresoPromedioPorReserva;
    /** Ingreso de reservas COMPLETADA pagadas con visa en el rango. */
    private BigDecimal ingresosVisa;
    /** Ingreso de reservas COMPLETADA pagadas en efectivo en el rango. */
    private BigDecimal ingresosEfectivo;
    private Double tasaOcupacion; // percentage
    private Double tasaNoShow; // percentage
}