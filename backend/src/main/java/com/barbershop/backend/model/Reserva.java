package com.barbershop.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "reservas",
    indexes = {
        @Index(name = "idx_reserva_fecha", columnList = "fecha"),
        @Index(name = "idx_reserva_trabajador_fecha", columnList = "trabajador_id, fecha"),
        @Index(name = "idx_reserva_estado", columnList = "estado"),
        @Index(name = "idx_reserva_token", columnList = "token_cancelacion", unique = true),
        @Index(name = "idx_reserva_cliente", columnList = "cliente_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_reserva_no_double_booking",
            columnNames = {"trabajador_id", "fecha", "hora_inicio"}
        )
    }
)
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_reserva_cliente"))
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trabajador_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_reserva_trabajador"))
    private Trabajador trabajador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servicio_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_reserva_servicio"))
    private Servicio servicio;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservaEstado estado = ReservaEstado.PENDIENTE;

    /** Método de pago usado al completar (visa/efectivo); null en reservas antiguas o no completadas. */
    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", length = 20)
    private MetodoPago metodoPago;

    @Column(name = "token_cancelacion", nullable = false, unique = true, length = 36)
    private String tokenCancelacion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (tokenCancelacion == null) {
            tokenCancelacion = UUID.randomUUID().toString();
        }
        if (estado == null) {
            estado = ReservaEstado.PENDIENTE;
        }
    }

    // Helper method to check if reservation overlaps with another
    public boolean overlaps(Reserva other) {
        if (!this.fecha.equals(other.fecha)) return false;
        if (!this.trabajador.getId().equals(other.trabajador.getId())) return false;
        // Check time overlap: this.start < other.end && this.end > other.start
        return this.horaInicio.isBefore(other.horaFin) && this.horaFin.isAfter(other.horaInicio);
    }
}