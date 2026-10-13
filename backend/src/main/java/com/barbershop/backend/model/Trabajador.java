package com.barbershop.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "trabajadores",
    indexes = {
        @Index(name = "idx_trabajador_email", columnList = "email", unique = true),
        @Index(name = "idx_trabajador_activo", columnList = "activo")
    }
)
public class Trabajador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    /** Ruta pública de la foto del trabajador (p. ej. {@code /uploads/trabajadores/x.jpg}). */
    @Column(length = 255)
    private String foto;

    /** Descripción breve del trabajador (especialidades/experiencia), opcional. */
    @Column(columnDefinition = "text")
    private String descripcion;

    /**
     * Rol de seguridad; por defecto {@code WORKER}. {@code @Builder.Default} es
     * necesario porque Lombok ignora los inicializadores de campo en el builder.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol = Rol.WORKER;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}