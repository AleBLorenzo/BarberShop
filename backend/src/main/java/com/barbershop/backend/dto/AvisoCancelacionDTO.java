package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Aviso de cancelación para la lista "Clientes por avisar" del admin (D7). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvisoCancelacionDTO {
    private Long id;
    private Long reservaId;
    private String clienteNombre;
    private String clienteTelefono;
    private String clienteEmail;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String servicioNombre;
    private String trabajadorNombre;
    private String motivo;
    /** EMAIL | TELEFONO */
    private String canal;
    /** PENDIENTE_ENVIO | ENVIADO | FALLIDO | PENDIENTE | AVISADO */
    private String estado;
    private String error;
    private LocalDateTime createdAt;
    private LocalDateTime avisadoAt;
    private String avisadoPorNombre;
}
