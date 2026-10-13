package com.barbershop.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de {@code PUT /api/worker/perfil}: el trabajador edita su propio
 * nombre, email y foto. {@code foto} es opcional (si no viene se conserva la
 * actual); el path lo produce {@code POST /api/admin/media/trabajadores}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerPerfilUpdateRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String nombre;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    @Size(max = 150, message = "El email no puede exceder 150 caracteres")
    private String email;

    /** Ruta pública de la foto (p. ej. {@code /uploads/trabajadores/x.jpg}); opcional. */
    @Size(max = 255, message = "La ruta de la foto no puede exceder 255 caracteres")
    private String foto;

    /**
     * Descripción breve; opcional. {@code null} = conserva la actual,
     * cadena vacía = la borra, texto = la reemplaza.
     */
    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    private String descripcion;
}
