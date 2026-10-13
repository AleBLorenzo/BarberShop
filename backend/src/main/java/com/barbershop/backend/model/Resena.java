package com.barbershop.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Reseña de la web, gestionada por el administrador (no depende de la API de
 * Google Places: enlaces "Escríbenos en Google Maps" llevan al sitio real).
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "resenas")
public class Resena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String autor;

    /** Puntuación de 1 a 5 estrellas (validada en el DTO). */
    @Column(nullable = false)
    private Integer puntuacion;

    @Column(nullable = false, length = 1000)
    private String texto;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
