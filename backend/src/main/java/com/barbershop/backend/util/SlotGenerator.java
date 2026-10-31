package com.barbershop.backend.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rejilla de huecos de una jornada (D2). Ya no lee el horario de las
 * propiedades: la apertura y el cierre llegan de la jornada efectiva que
 * calcula {@code AgendaService}. {@code slot-interval-minutes} sigue siendo
 * propiedad (tamaño mínimo del paso de la rejilla).
 */
@Component
public class SlotGenerator {

    @Value("${app.business.slot-interval-minutes:30}")
    private int slotIntervalMinutes;

    public List<TimeSlot> generateSlots(LocalTime apertura, LocalTime cierre, int servicioDuracionMinutos) {
        List<TimeSlot> slots = new ArrayList<>();
        if (apertura == null || cierre == null || !cierre.isAfter(apertura)) {
            return slots; // sin jornada
        }

        // Rejilla alineada hacia atrás desde el cierre: la última cita empieza
        // exactamente en cierre - duración (20:00 → 19:30 con 30 min, 19:15 con
        // 45 min, 19:00 con 1 h) y ningún hueco termina después del cierre
        // ni empieza antes de la apertura.
        int minutosJornada = (int) Duration.between(apertura, cierre).toMinutes();
        if (servicioDuracionMinutos <= 0 || servicioDuracionMinutos > minutosJornada) {
            return slots; // el servicio no cabe en la jornada
        }

        // El paso entre slots es la duración del servicio (30 min → huecos de 30,
        // 45 min → de 45, 1 h → de 1 h), sin bajar nunca del intervalo de la rejilla.
        int paso = Math.max(servicioDuracionMinutos, slotIntervalMinutes);

        // Se cuenta en minutos desde la apertura para no dar la vuelta a medianoche
        // con LocalTime.minusMinutes en jornadas que empiezan a primera hora.
        int offset = minutosJornada - servicioDuracionMinutos;
        while (offset >= 0) {
            LocalTime inicio = apertura.plusMinutes(offset);
            slots.add(TimeSlot.builder()
                    .inicio(inicio)
                    .fin(inicio.plusMinutes(servicioDuracionMinutos))
                    .disponible(true)
                    .build());
            offset -= paso;
        }
        Collections.reverse(slots);
        return slots;
    }

    /**
     * Huecos de la jornada marcando como no disponibles los que se solapan con
     * {@code ocupados} (citas activas y bloqueos del trabajador, D2).
     */
    public List<TimeSlot> generateSlotsForWorker(LocalTime apertura, LocalTime cierre, int servicioDuracionMinutos,
                                                 List<ReservaSlot> ocupados) {
        List<TimeSlot> allSlots = generateSlots(apertura, cierre, servicioDuracionMinutos);

        for (TimeSlot slot : allSlots) {
            for (ReservaSlot ocupado : ocupados) {
                if (slotOverlaps(slot, ocupado)) {
                    slot.setDisponible(false);
                    break;
                }
            }
        }
        return allSlots;
    }

    private boolean slotOverlaps(TimeSlot slot, ReservaSlot reserva) {
        return slot.getInicio().isBefore(reserva.getFin()) && slot.getFin().isAfter(reserva.getInicio());
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservaSlot {
        private LocalTime inicio;
        private LocalTime fin;
    }
}