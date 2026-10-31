package com.barbershop.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cierre puntual de un día (D4): general (toda la barbería, {@code trabajador
 * = null}) o de un trabajador concreto. La unicidad (un general por fecha, uno
 * por trabajador y fecha) se garantiza en el servicio bajo el lock de D3: un
 * UNIQUE con columna nullable no la cubriría en PostgreSQL.
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "cierres_dia",
    indexes = {
        @Index(name = "idx_cierre_fecha", columnList = "fecha")
    }
)
public class CierreDia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    /** {@code null} = cierre general de la barbería. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trabajador_id",
        foreignKey = @ForeignKey(name = "fk_cierre_trabajador"))
    private Trabajador trabajador;

    @Column(length = 255)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id",
        foreignKey = @ForeignKey(name = "fk_cierre_creado_por"))
    private Trabajador creadoPor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
