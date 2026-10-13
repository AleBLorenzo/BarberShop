package com.barbershop.backend.event;

import com.barbershop.backend.dto.ReservaResponse;

/**
 * Publicada cuando una reserva pasa al estado COMPLETADA (transicion de
 * entrada: si se vuelve a marcar COMPLETADA no se reenvia). El listener
 * corre despues del commit ({@code AFTER_COMMIT}), como en la creacion.
 *
 * @param reserva      snapshot inmutable de la reserva completada
 * @param clienteEmail email del cliente (puede ser null: no se envia nada)
 */
public record ReservaCompletadaEvent(ReservaResponse reserva, String clienteEmail) {
}
