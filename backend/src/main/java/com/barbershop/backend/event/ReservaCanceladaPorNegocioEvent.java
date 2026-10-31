package com.barbershop.backend.event;

import com.barbershop.backend.dto.ReservaResponse;

/**
 * Publicada cuando el negocio (ADMIN o un cierre) cancela una cita (D6). El
 * listener corre después del commit ({@code AFTER_COMMIT}): si la cancelación
 * hace rollback no se envía nada.
 *
 * @param reserva      snapshot inmutable de la reserva cancelada
 * @param clienteEmail email del cliente (null/blank → canal TELEFONO, no se envía)
 * @param avisoId      aviso asociado, para registrar ENVIADO/FALLIDO (D7)
 * @param motivo       motivo de la cancelación (puede ser null)
 */
public record ReservaCanceladaPorNegocioEvent(ReservaResponse reserva, String clienteEmail,
                                              Long avisoId, String motivo) {
}
