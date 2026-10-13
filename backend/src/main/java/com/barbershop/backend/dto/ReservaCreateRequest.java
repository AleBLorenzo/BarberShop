package com.barbershop.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaCreateRequest {
    @NotNull(message = "El ID del servicio es obligatorio")
    private Long servicioId;

    @NotNull(message = "El ID del trabajador es obligatorio")
    private Long trabajadorId;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    @NotNull(message = "La hora de inicio es obligatoria")
    private LocalTime horaInicio;

    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String clienteNombre;

    @NotBlank(message = "El teléfono del cliente es obligatorio")
    @Pattern(regexp = "^[+]?[0-9\\s-]{9,15}$", message = "Formato de teléfono inválido")
    @Size(max = 50, message = "El teléfono no puede exceder 50 caracteres")
    private String clienteTelefono;

    @Email(message = "El email no tiene un formato válido")
    @Size(max = 150, message = "El email no puede exceder 150 caracteres")
    private String clienteEmail;
}