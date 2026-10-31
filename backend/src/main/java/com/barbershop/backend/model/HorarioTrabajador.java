package com.barbershop.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Horario propio de un trabajador para un día de la semana (D1). Si no hay
 * fila para ese día el trabajador hereda el horario general. {@code trabaja =
 * false} es su día libre semanal; si trabaja, el tramo debe quedar contenido
 * en el horario general (se valida al guardar y se intersecta al calcular).
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "horario_trabajador",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_horario_trabajador_dia", columnNames = {"trabajador_id", "dia_semana"})
    }
)
public class HorarioTrabajador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trabajador_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_horario_trabajador_trabajador"))
    private Trabajador trabajador;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 10)
    private DayOfWeek diaSemana;

    @Column(nullable = false)
    private Boolean trabaja;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fin")
    private LocalTime horaFin;
}
