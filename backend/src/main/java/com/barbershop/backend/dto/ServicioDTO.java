package com.barbershop.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServicioDTO {
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String nombre;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0")
    private BigDecimal precio;

    @NotNull(message = "La duración es obligatoria")
    @DecimalMin(value = "1", message = "La duración debe ser al menos 1 minuto")
    private Integer duracionMinutos;

    private Boolean activo;

    /** Ruta pública de la foto, vacía o {@code null} si no tiene. */
    @Size(max = 255, message = "La ruta de la foto no puede exceder 255 caracteres")
    private String foto;

    // ====== Oferta temporal (opcional; la coherencia de ventana se valida en ServicioService) ======

    /** Porcentaje de descuento de la oferta, 1–99; {@code null} = sin oferta. */
    @Min(value = 1, message = "El porcentaje de oferta debe estar entre 1 y 99")
    @Max(value = 99, message = "El porcentaje de oferta debe estar entre 1 y 99")
    private Integer descuentoPorcentaje;

    /** Inicio de la ventana de oferta; requerido si hay porcentaje. */
    private java.time.LocalDateTime ofertaInicio;

    /** Fin de la ventana de oferta (exclusivo); requerido si hay porcentaje. */
    private java.time.LocalDateTime ofertaFin;

    /** Precio rebajado calculado en servidor (HALF_UP, 2 decimales). Solo lectura: se ignora en escritura. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal precioOferta;

    /** {@code true} si la oferta está vigente según el reloj del servidor. Solo lectura. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean ofertaActiva;

    private java.time.LocalDateTime createdAt;
}