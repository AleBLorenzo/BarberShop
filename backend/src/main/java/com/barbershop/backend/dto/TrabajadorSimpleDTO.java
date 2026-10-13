package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrabajadorSimpleDTO {
    private Long id;
    private String nombre;
    private Boolean activo;
    /** Ruta pública de la foto (opcional). */
    private String foto;
}