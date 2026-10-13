package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaResponse {
    private Long id;
    private String clienteNombre;
    private String clienteTelefono;
    private String trabajadorNombre;
    private String servicioNombre;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String estado;
    /** "VISA" | "EFECTIVO"; null si no se registró método de pago. */
    private String metodoPago;
    private UUID tokenCancelacion;
    private BigDecimal precioTotal;
    private Integer duracionMinutos;
    private String mensaje;
}