package com.barbershop.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResenaDTO {
    private Long id;

    @NotBlank(message = "El nombre del autor es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String autor;

    @NotNull(message = "La puntuación es obligatoria")
    @Min(value = 1, message = "La puntuación mínima es 1 estrella")
    @Max(value = 5, message = "La puntuación máxima es 5 estrellas")
    private Integer puntuacion;

    @NotBlank(message = "El texto de la reseña es obligatorio")
    @Size(max = 1000, message = "La reseña no puede exceder 1000 caracteres")
    private String texto;

    private LocalDateTime createdAt;
}
