package com.barbershop.backend.config;

import com.barbershop.backend.model.HorarioBarberia;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Horario general por defecto a partir de {@code app.business.opening-hour},
 * {@code closing-hour} y {@code closed-days} (D9). Estas propiedades son SOLO
 * valores de siembra: la fuente de verdad es la tabla {@code horario_barberia}.
 * {@code AgendaService} lo usa además como red de seguridad si falta la fila
 * de un día (p. ej. tabla vaciada a mano), para no dejar la barbería cerrada.
 */
@Component
public class HorarioDefaults {

    @Value("${app.business.opening-hour:9}")
    private int openingHour;

    @Value("${app.business.closing-hour:20}")
    private int closingHour;

    @Value("${app.business.closed-days:SUNDAY}")
    private String closedDaysProp;

    /** Fila por defecto (sin id) para un día de la semana. */
    public HorarioBarberia porDefecto(DayOfWeek dia) {
        boolean abierto = !diasCerrados().contains(dia);
        return HorarioBarberia.builder()
                .diaSemana(dia)
                .abierto(abierto)
                .horaApertura(abierto ? LocalTime.of(openingHour, 0) : null)
                .horaCierre(abierto ? LocalTime.of(closingHour, 0) : null)
                .build();
    }

    /** Días cerrados configurados (p. ej. SUNDAY), lista separada por comas. */
    public Set<DayOfWeek> diasCerrados() {
        Set<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        if (closedDaysProp == null || closedDaysProp.isBlank()) {
            return dias;
        }
        for (String parte : closedDaysProp.split(",")) {
            String limpio = parte.trim();
            if (!limpio.isEmpty()) {
                dias.add(DayOfWeek.valueOf(limpio.toUpperCase(Locale.ROOT)));
            }
        }
        return dias;
    }
}
