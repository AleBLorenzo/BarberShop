package com.barbershop.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Aviso al cliente de una cita cancelada por el negocio (D7): cola
 * gestionable que recoge los emails (enviados/fallidos) y los avisos
 * telefónicos pendientes, y audita quién y cuándo avisó a mano.
 * Exactamente uno por reserva ({@code reserva_id} único).
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "avisos_cancelacion",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_aviso_reserva", columnNames = {"reserva_id"})
    },
    indexes = {
        @Index(name = "idx_aviso_estado", columnList = "estado")
    }
)
public class AvisoCancelacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reserva_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_aviso_reserva"))
    private Reserva reserva;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CanalAviso canal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoAviso estado;

    @Column(length = 255)
    private String motivo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "avisado_at")
    private LocalDateTime avisadoAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avisado_por_id",
        foreignKey = @ForeignKey(name = "fk_aviso_avisado_por"))
    private Trabajador avisadoPor;

    /** Último error de envío (SMTP), si lo hubo. */
    @Column(length = 255)
    private String error;
}
