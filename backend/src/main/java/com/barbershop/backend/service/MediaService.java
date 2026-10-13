package com.barbershop.backend.service;

import com.barbershop.backend.config.MediaConfig;
import com.barbershop.backend.dto.MediaFileDTO;
import com.barbershop.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Gestión de fotos locales (subida, listado y borrado) dentro del directorio
 * de subidas configurado en {@code app.uploads.dir}.
 * <p>
 * Solo se admiten las carpetas de negocio y extensiones de imagen habituales.
 * Las validaciones de negocio se lanzan como {@link IllegalArgumentException}
 * (400) y los archivos inexistentes como {@link ResourceNotFoundException}
 * (404), igual que el resto de services del proyecto.
 */
@Service
@RequiredArgsConstructor
public class MediaService {

    /** Carpetas permitidas dentro del directorio de subidas. */
    private static final Set<String> CARPETAS = Set.of("trabajadores", "servicios", "resultados");

    /** Extensiones de imagen admitidas (se comparan en minúsculas).
     *  Sin SVG: puede llevar JS embebido y /uploads/** es permitAll (B-BAJO-1). */
    private static final Set<String> EXTENSIONES_PERMITIDAS =
            Set.of("jpg", "jpeg", "png", "webp", "gif", "avif");

    /** Tamaño máximo de cada archivo: 5 MB. */
    private static final long TAMANO_MAXIMO_BYTES = 5L * 1024 * 1024;

    /** Todo lo que no sea letra, dígito, punto, guion o guion bajo se elimina del nombre. */
    private static final String CARACTERES_INVALIDOS = "[^A-Za-z0-9._-]";

    /** Directorio raíz de las subidas (mismo valor que {@code MediaConfig}). */
    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

    /** Lista los archivos de imagen de una carpeta, ordenados por nombre. */
    public List<MediaFileDTO> list(String carpeta) {
        Path directorio = Path.of(validarCarpeta(carpeta));
        if (!Files.isDirectory(directorio)) {
            return List.of();
        }
        try (var flujo = Files.list(directorio)) {
            return flujo
                    .filter(Files::isRegularFile)
                    .filter(path -> extensionPermitida(path.getFileName().toString()))
                    .sorted()
                    .map(path -> toDTO(carpeta, path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo listar la carpeta " + carpeta, e);
        }
    }

    /** Valida y guarda un archivo subido, devolviendo su nombre y URL pública. */
    public MediaFileDTO upload(String carpeta, MultipartFile archivo) {
        String rutaCarpeta = validarCarpeta(carpeta);

        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_BYTES) {
            throw new IllegalArgumentException("El archivo no puede superar los 5 MB");
        }

        String nombreOriginal = nombreOriginal(archivo);
        String extension = extensionDe(nombreOriginal).toLowerCase(Locale.ROOT);
        if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Extensión no permitida. Usa una de: " + String.join(", ", EXTENSIONES_PERMITIDAS));
        }

        String nombre = sanitizarNombre(nombreOriginal, extension, rutaCarpeta);
        Path destino = Path.of(rutaCarpeta, nombre);
        try {
            Files.createDirectories(destino.getParent());
            Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo", e);
        }
        return toDTO(carpeta, nombre);
    }

    /** Elimina un archivo de la carpeta indicada. */
    public void delete(String carpeta, String nombre) {
        String rutaCarpeta = validarCarpeta(carpeta);

        if (nombre == null || nombre.isBlank()
                || nombre.contains("/") || nombre.contains("\\") || nombre.contains("..")) {
            throw new IllegalArgumentException("Nombre de archivo no válido");
        }

        Path archivo = Path.of(rutaCarpeta, nombre);
        if (!Files.isRegularFile(archivo)) {
            throw new ResourceNotFoundException("Archivo no encontrado: " + nombre);
        }
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo eliminar el archivo", e);
        }
    }

    /** Valida que la carpeta sea una de las permitidas y devuelve su ruta. */
    private String validarCarpeta(String carpeta) {
        if (carpeta == null || !CARPETAS.contains(carpeta)) {
            throw new IllegalArgumentException(
                    "Carpeta no válida. Usa una de: " + String.join(", ", CARPETAS));
        }
        return uploadsDir + "/" + carpeta;
    }

    /** Nombre original del archivo solo con su componente de nombre (sin rutas). */
    private String nombreOriginal(MultipartFile archivo) {
        String original = archivo.getOriginalFilename();
        if (original == null || original.isBlank()) {
            return "";
        }
        Path ruta = Paths.get(original);
        Path nombre = ruta.getFileName();
        return nombre == null ? "" : nombre.toString();
    }

    /** Extensión del archivo (sin punto) o cadena vacía si no tiene. */
    private String extensionDe(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto < 0 || punto == nombre.length() - 1 ? "" : nombre.substring(punto + 1);
    }

    /** True si la extensión del archivo está permitida (sin distinguir mayúsculas). */
    private boolean extensionPermitida(String nombre) {
        return EXTENSIONES_PERMITIDAS.contains(extensionDe(nombre).toLowerCase(Locale.ROOT));
    }

    /**
     * Espacios → {@code -}, elimina todo lo que no sea {@code [A-Za-z0-9._-]} y,
     * si no queda nada, usa {@code foto-<millis>.<ext>}. Si el nombre ya existe,
     * añade {@code -1}, {@code -2}… antes de la extensión.
     */
    private String sanitizarNombre(String nombreOriginal, String extension, String rutaCarpeta) {
        String limpio = nombreOriginal.replace(' ', '-').replaceAll(CARACTERES_INVALIDOS, "");
        if (limpio.isBlank() || limpio.equals(".")) {
            return "foto-" + System.currentTimeMillis() + "." + extension;
        }
        return hacerUnico(rutaCarpeta, limpio);
    }

    /** Añade sufijo {@code -1}, {@code -2}… antes de la extensión si ya existe. */
    private String hacerUnico(String rutaCarpeta, String nombre) {
        int punto = nombre.lastIndexOf('.');
        String base = punto > 0 ? nombre.substring(0, punto) : nombre;
        String ext = punto > 0 ? nombre.substring(punto) : "";

        String candidato = nombre;
        int intento = 1;
        while (Files.exists(Path.of(rutaCarpeta, candidato))) {
            candidato = base + "-" + intento + ext;
            intento++;
        }
        return candidato;
    }

    private MediaFileDTO toDTO(String carpeta, String nombre) {
        return MediaFileDTO.builder()
                .nombre(nombre)
                .url("/uploads/" + carpeta + "/" + nombre)
                .build();
    }
}
