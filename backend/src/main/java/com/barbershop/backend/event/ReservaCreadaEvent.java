package com.barbershop.backend.event;

import com.barbershop.backend.dto.ReservaResponse;

/**
 * Publicada cuando una reserva se crea. El listener corre despues del commit
 * ({@code AFTER_COMMIT}), nunca dentro de la transaccion de creacion.
 *
 * @param reserva       snapshot inmutable de la reserva creada (incluye token)
 * @param clienteEmail  email facilitado en la peticion (puede ser null)
 */
public record ReservaCreadaEvent(ReservaResponse reserva, String clienteEmail) {
}
