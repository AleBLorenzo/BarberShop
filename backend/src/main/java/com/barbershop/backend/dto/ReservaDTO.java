package com.barbershop.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaDTO {
    private Long id;

    private ClienteSimpleDTO cliente;
    private TrabajadorSimpleDTO trabajador;
    private ServicioSimpleDTO servicio;

    private LocalDate fecha;
    private DayOfWeek diaSemana;
    private LocalTime horaInicio;
    private LocalTime horaFin;

    private String estado;
    /** "VISA" | "EFECTIVO"; null si la reserva no está completada o es anterior al cambio. */
    private String metodoPago;
    private UUID tokenCancelacion;

    private LocalDateTime createdAt;

    // Computed fields
    private BigDecimal precioTotal;
    private Integer duracionMinutos;
}