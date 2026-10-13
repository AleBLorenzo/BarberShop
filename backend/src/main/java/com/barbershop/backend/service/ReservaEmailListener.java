package com.barbershop.backend.service;

import com.barbershop.backend.event.ReservaCreadaEvent;
import com.barbershop.backend.event.ReservaCompletadaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envia el correo de confirmacion DESPUES de que la reserva se haya
 * confirmado (commit) y de forma asincrona. Cualquier fallo se traga con un
 * log de advertencia: el correo nunca puede afectar a la reserva.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservaEmailListener {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservaCreada(ReservaCreadaEvent event) {
        try {
            emailService.enviarConfirmacion(event.reserva(), event.clienteEmail());
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo de confirmacion de la reserva {}: {}",
                    event.reserva().getId(), e.getMessage());
        }
    }

    /** Invitación a reseñar al marcar la cita COMPLETADA (una vez por transición). */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservaCompletada(ReservaCompletadaEvent event) {
        try {
            emailService.enviarInvitacionResena(event.reserva(), event.clienteEmail());
        } catch (Exception e) {
            log.warn("No se pudo enviar la invitacion a reseñar de la reserva {}: {}",
                    event.reserva().getId(), e.getMessage());
        }
    }
}
