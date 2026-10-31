package com.barbershop.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Horario semanal general de la barbería: una fila por día de la semana
 * (D1). Es la envolvente de los horarios por trabajador: un día cerrado aquí
 * deja sin jornada a todos los trabajadores aunque tengan horario propio.
 * Si {@code abierto = false}, las horas quedan a {@code null}.
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "horario_barberia",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_horario_barberia_dia", columnNames = {"dia_semana"})
    }
)
public class HorarioBarberia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 10)
    private DayOfWeek diaSemana;

    @Column(nullable = false)
    private Boolean abierto;

    @Column(name = "hora_apertura")
    private LocalTime horaApertura;

    @Column(name = "hora_cierre")
    private LocalTime horaCierre;
}
