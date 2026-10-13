package com.barbershop.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteDTO {
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String nombre;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = "^[+]?[0-9\\s-]{9,15}$", message = "Formato de teléfono inválido")
    @Size(max = 50, message = "El teléfono no puede exceder 50 caracteres")
    private String telefono;

    /** Opcional: facilitado al reservar (nunca clave única). */
    @Size(max = 150, message = "El email no puede exceder 150 caracteres")
    private String email;

    private java.time.LocalDateTime createdAt;
}