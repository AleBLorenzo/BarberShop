package com.barbershop.backend.service;

import com.barbershop.backend.event.ReservaCanceladaPorNegocioEvent;
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
    private final AvisoCancelacionService avisoCancelacionService;

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

    /**
     * Aviso de cancelación por el negocio (D7): tras el commit y en otro hilo.
     * Con email registra ENVIADO/FALLIDO en una transacción nueva; sin email
     * no hay nada que enviar (el aviso TELEFONO/PENDIENTE ya existe).
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservaCanceladaPorNegocio(ReservaCanceladaPorNegocioEvent event) {
        boolean conEmail = event.clienteEmail() != null && !event.clienteEmail().isBlank();
        boolean enviado;
        String error = null;
        try {
            enviado = emailService.enviarCancelacion(event.reserva(), event.clienteEmail(), event.motivo());
            if (!enviado) {
                error = "No se pudo enviar el correo de cancelación";
            }
        } catch (Exception e) {
            enviado = false;
            error = e.getMessage();
            log.warn("No se pudo enviar el aviso de cancelacion de la reserva {}: {}",
                    event.reserva().getId(), e.getMessage());
        }
        if (!conEmail || event.avisoId() == null) {
            return;
        }
        try {
            avisoCancelacionService.marcarResultadoEnvio(event.avisoId(), enviado, error);
        } catch (Exception e) {
            // Queda en PENDIENTE_ENVIO: pasados 15 min se lista como pendiente
            log.warn("No se pudo registrar el resultado del aviso {}: {}", event.avisoId(), e.getMessage());
        }
    }
}
