package com.barbershop.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Bloqueo puntual de una franja propia de un trabajador (D5): descanso,
 * médico… A diferencia de {@link CierreDia}, un bloqueo nunca cancela citas:
 * no se puede crear sobre citas activas.
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "bloqueos_horario",
    indexes = {
        @Index(name = "idx_bloqueo_trabajador_fecha", columnList = "trabajador_id, fecha")
    }
)
public class BloqueoHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trabajador_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_bloqueo_trabajador"))
    private Trabajador trabajador;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(length = 255)
    private String motivo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
