package com.barbershop.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Un día del horario semanal (D1, D11). Se usa para el horario general
 * ({@code abierto} = la barbería abre) y para el horario propio de un
 * trabajador ({@code abierto} = el trabajador trabaja; {@code false} = día
 * libre semanal). Si {@code abierto = false} las horas se ignoran.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorarioDiaDTO {

    @NotNull(message = "El día de la semana es obligatorio")
    private DayOfWeek diaSemana;

    @NotNull(message = "Indica si el día está abierto")
    private Boolean abierto;

    private LocalTime horaInicio;

    private LocalTime horaFin;
}
