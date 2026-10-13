package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Archivo de imagen almacenado en el directorio local de subidas.
 * {@code url} es la ruta pública bajo la que Spring sirve el archivo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaFileDTO {
    private String nombre;
    private String url;
}
