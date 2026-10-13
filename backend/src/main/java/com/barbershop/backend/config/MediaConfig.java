package com.barbershop.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Almacenamiento de fotos locales (trabajadores, servicios y resultados).
 * <p>
 * Crea los directorios de subida al arrancar y publica su contenido bajo la
 * ruta pública {@code /uploads/**} para que el frontend pueda referenciar las
 * imágenes como {@code /uploads/{carpeta}/{nombre}}.
 */
@Configuration
public class MediaConfig implements WebMvcConfigurer {

    /** Directorio raíz de las subidas (relativo al directorio de trabajo). */
    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

    /** Carpetas válidas dentro del directorio de subidas. */
    public static final List<String> CARPETAS = List.of("trabajadores", "servicios", "resultados");

    /** Crea {@code uploads/trabajadores}, {@code uploads/servicios} y {@code uploads/resultados} si no existen. */
    @PostConstruct
    public void crearDirectoriosSubida() throws IOException {
        for (String carpeta : CARPETAS) {
            Files.createDirectories(Path.of(uploadsDir, carpeta));
        }
    }

    /** Sirve los archivos subidos en {@code /uploads/**} desde el disco. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadsDir + "/");
    }
}
