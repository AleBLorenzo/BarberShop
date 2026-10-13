package com.barbershop.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Foto de la galería «Resultados» (trabajos a clientes), gestionada por el
 * administrador mediante URL externa o ruta local {@code /uploads/...} (el
 * binario se guarda en disco; ver {@code MediaService}).
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "resultados")
public class Resultado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** URL http(s) de la imagen (validada en el DTO). */
    @Column(nullable = false, length = 500)
    private String url;

    /** Etiqueta opcional del trabajo. */
    @Column(length = 100)
    private String titulo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
