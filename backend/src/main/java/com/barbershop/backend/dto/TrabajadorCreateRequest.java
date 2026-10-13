package com.barbershop.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrabajadorCreateRequest {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String nombre;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    @Size(max = 150, message = "El email no puede exceder 150 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
    private String password;

    private Boolean activo = true;

    /** Ruta pública de la foto (opcional). */
    @Size(max = 255, message = "La ruta de la foto no puede exceder 255 caracteres")
    private String foto;

    /** Descripción breve (especialidades/experiencia), opcional. */
    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    private String descripcion;

    /** Rol de seguridad (ADMIN/WORKER); si no viene, se crea como WORKER. */
    private com.barbershop.backend.model.Rol rol;
}