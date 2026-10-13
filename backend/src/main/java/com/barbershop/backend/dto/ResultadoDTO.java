package com.barbershop.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class ResultadoDTO {
    private Long id;

    @NotBlank(message = "La URL de la imagen es obligatoria")
    @Size(max = 500, message = "La URL no puede exceder 500 caracteres")
    @Pattern(regexp = "^(https?://.+|/uploads/.+)$",
            message = "Debe ser una URL http(s) o una ruta /uploads/...")
    private String url;

    @Size(max = 100, message = "El título no puede exceder 100 caracteres")
    private String titulo;

    private LocalDateTime createdAt;
}
