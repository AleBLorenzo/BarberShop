package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Ingresos de un trabajador en un rango (D10, solo ADMIN). Mismo criterio
 * que {@code /api/admin/dashboard}: {@code total} = citas COMPLETADA +
 * CONFIRMADA; {@code visa}/{@code efectivo}/{@code sinMetodo} = solo
 * COMPLETADA. En {@code IngresosResumenDTO.totales} los campos del
 * trabajador van a null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngresoTrabajadorDTO {
    private Long trabajadorId;
    private String nombre;
    /** ADMIN | WORKER */
    private String rol;
    private Boolean activo;
    private BigDecimal total;
    private BigDecimal visa;
    private BigDecimal efectivo;
    /** COMPLETADA sin método de pago registrado (reservas antiguas). */
    private BigDecimal sinMetodo;
    private Long completadas;
    /** CONFIRMADA (aún no cerradas): suman en el total, no en el desglose. */
    private Long confirmadas;
}
