package com.barbershop.backend.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class SlotGenerator {

    @Value("${app.business.opening-hour:9}")
    private int openingHour;

    @Value("${app.business.closing-hour:20}")
    private int closingHour;

    @Value("${app.business.slot-interval-minutes:30}")
    private int slotIntervalMinutes;

    public List<TimeSlot> generateSlots(int servicioDuracionMinutos) {
        List<TimeSlot> slots = new ArrayList<>();
        LocalTime apertura = LocalTime.of(openingHour, 0);
        LocalTime cierre = LocalTime.of(closingHour, 0);

        // Rejilla alineada hacia atrás desde el cierre: la última cita empieza
        // exactamente en cierre - duración (20:00 → 19:30 con 30 min, 19:15 con
        // 45 min, 19:00 con 1 h) y ningún hueco termina después del cierre
        // ni empieza antes de la apertura.
        LocalTime inicio = cierre.minusMinutes(servicioDuracionMinutos);
        if (inicio.isBefore(apertura)) {
            return slots; // el servicio no cabe en la jornada
        }

        // El paso entre slots es la duración del servicio (30 min → huecos de 30,
        // 45 min → de 45, 1 h → de 1 h), sin bajar nunca del intervalo de la rejilla.
        int paso = Math.max(servicioDuracionMinutos, slotIntervalMinutes);

        while (!inicio.isBefore(apertura)) {
            LocalTime fin = inicio.plusMinutes(servicioDuracionMinutos);
            slots.add(TimeSlot.builder()
                    .inicio(inicio)
                    .fin(fin)
                    .disponible(true)
                    .build());
            inicio = inicio.minusMinutes(paso);
        }
        Collections.reverse(slots);
        return slots;
    }

    public List<TimeSlot> generateSlotsForWorker(int servicioDuracionMinutos, List<ReservaSlot> reservasOcupadas) {
        List<TimeSlot> allSlots = generateSlots(servicioDuracionMinutos);

        // Mark occupied slots
        for (TimeSlot slot : allSlots) {
            for (ReservaSlot reserva : reservasOcupadas) {
                if (slotOverlaps(slot, reserva)) {
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