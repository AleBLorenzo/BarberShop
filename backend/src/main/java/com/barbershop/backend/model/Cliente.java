package com.barbershop.backend.model;

import com.barbershop.backend.util.ClientNormalizer;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "clientes",
    indexes = {
        @Index(name = "idx_cliente_telefono", columnList = "telefono", unique = true),
        @Index(name = "idx_cliente_telefono_normalizado", columnList = "telefono_normalizado", unique = true)
    }
)
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, unique = true, length = 50)
    private String telefono;

    /**
     * Teléfono canónico (solo dígitos, sin prefijo 34): identidad indexable y
     * única (cambio dedupe-client-on-rebooking, D1). El índice único impide
     * físicamente el duplicado aunque dos procesos entren a la vez.
     * Se rellena también en el arranque por {@code ClientDedupeBackfill}.
     */
    @Column(name = "telefono_normalizado", unique = true, length = 50)
    private String telefonoNormalizado;

    @Column(length = 150)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Normalización al persistir (D3, defensa en profundidad): sea cual sea el
     * origen (reserva pública, panel del trabajador, admin, seed), el teléfono
     * se guarda canónico y el email en minúsculas. {@code telefonoNormalizado}
     * se recalcula SIEMPRE a partir del teléfono ya normalizado para que ambos
     * valores queden consistentes entre sí.
     */
    @PrePersist
    @PreUpdate
    void normalizarIdentidad() {
        this.telefono = ClientNormalizer.normalizarTelefono(this.telefono);
        this.email = ClientNormalizer.normalizarEmail(this.email);
        this.telefonoNormalizado = ClientNormalizer.normalizarTelefono(this.telefono);
    }
}