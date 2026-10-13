package com.barbershop.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "servicios",
    indexes = {
        @Index(name = "idx_servicio_activo", columnList = "activo")
    }
)
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(name = "duracion_minutos", nullable = false)
    private Integer duracionMinutos;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    /** Ruta pública de la foto del servicio (p. ej. {@code /uploads/servicios/x.jpg}). */
    @Column(length = 255)
    private String foto;

    // ====== Oferta temporal (todo nullable: sin oferta = campos null) ======

    /** Porcentaje de descuento de la oferta (1–99); {@code null} = sin oferta. */
    @Column(name = "descuento_porcentaje")
    private Integer descuentoPorcentaje;

    /** Inicio de la ventana de oferta (reloj del servidor); {@code null} = sin oferta. */
    @Column(name = "oferta_inicio")
    private LocalDateTime ofertaInicio;

    /** Fin de la ventana de oferta (exclusivo: ahora < fin); {@code null} = sin oferta. */
    @Column(name = "oferta_fin")
    private LocalDateTime ofertaFin;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}