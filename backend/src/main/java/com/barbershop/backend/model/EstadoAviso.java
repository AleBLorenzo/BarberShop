package com.barbershop.backend.model;

/**
 * Estado de un aviso de cancelación (D7).
 * <ul>
 *   <li>EMAIL: {@code PENDIENTE_ENVIO} → {@code ENVIADO} | {@code FALLIDO}</li>
 *   <li>TELEFONO: {@code PENDIENTE}</li>
 *   <li>Cualquiera → {@code AVISADO} cuando el admin marca que avisó a mano.</li>
 * </ul>
 */
public enum EstadoAviso {
    PENDIENTE_ENVIO,
    ENVIADO,
    FALLIDO,
    PENDIENTE,
    AVISADO
}
