package com.barbershop.backend.util;

import com.barbershop.backend.dto.ReservaResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera un fichero iCalendar (.ics) con la cita: mismo formato que
 * frontend/src/utils/ics.js (hora local flotante, escapado RFC 5545,
 * plegado de lineas, alarma de 30 minutos).
 */
@Component
public class IcsBuilder {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final String nombreNegocio;

    /** @param nombreNegocio marca visible (app.business.name), p. ej. TheTankBarber */
    public IcsBuilder(@Value("${app.business.name:TheTankBarber}") String nombreNegocio) {
        this.nombreNegocio = nombreNegocio;
    }

    public String build(ReservaResponse r) {
        String fecha = r.getFecha().format(DATE);
        String dtStart = fecha + "T" + r.getHoraInicio().format(TIME);
        String dtEnd = fecha + "T" + r.getHoraFin().format(TIME);
        String dtStamp = OffsetDateTime.now(ZoneOffset.UTC).format(STAMP);

        List<String> descLineas = new ArrayList<>();
        add(descLineas, "Cliente", r.getClienteNombre());
        add(descLineas, "Teléfono", r.getClienteTelefono());
        add(descLineas, "Trabajador", r.getTrabajadorNombre());
        add(descLineas, "Servicio", r.getServicioNombre());
        if (r.getPrecioTotal() != null) {
            descLineas.add("Precio: " + r.getPrecioTotal() + " €");
        }
        if (r.getTokenCancelacion() != null) {
            descLineas.add("Token de cancelación: " + r.getTokenCancelacion());
        }
        String descripcion = escapeIcs(String.join("\n", descLineas));

        List<String> lineas = List.of(
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//" + nombreNegocio + "//Reservas//ES",
                "CALSCALE:GREGORIAN",
                "METHOD:PUBLISH",
                "BEGIN:VEVENT",
                "UID:barbershop-reserva-" + (r.getId() != null ? r.getId() : "x") + "@barbershop",
                "DTSTAMP:" + dtStamp,
                "DTSTART:" + dtStart,
                "DTEND:" + dtEnd,
                "SUMMARY:" + escapeIcs(nullSafe(r.getServicioNombre()) + " · " + nombreNegocio),
                "DESCRIPTION:" + descripcion,
                "LOCATION:" + escapeIcs(nombreNegocio),
                "BEGIN:VALARM",
                "TRIGGER:-PT30M",
                "ACTION:DISPLAY",
                "DESCRIPTION:" + escapeIcs("Cita en " + nombreNegocio + " en 30 minutos"),
                "END:VALARM",
                "END:VEVENT",
                "END:VCALENDAR"
        );

        StringBuilder sb = new StringBuilder();
        for (String linea : lineas) {
            sb.append(foldLine(linea)).append("\r\n");
        }
        return sb.toString();
    }

    private static void add(List<String> lineas, String label, String valor) {
        if (valor != null && !valor.isBlank()) {
            lineas.add(label + ": " + valor);
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    /** Escapado de texto RFC 5545 (se backslash primero). */
    static String escapeIcs(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\n")
                // CR suelto: evita inyectar líneas iCalendar extra (B-BAJO-8)
                .replace("\r", "\n")
                .replace("\n", "\\n");
    }

    /** Plegado de lineas largas en ~75 octetos (RFC 5545 §3.1). */
    static String foldLine(String line) {
        if (line.length() <= 74) {
            return line;
        }
        StringBuilder sb = new StringBuilder(line.substring(0, 74));
        String rest = line.substring(74);
        while (rest.length() > 73) {
            sb.append("\r\n ").append(rest, 0, 73);
            rest = rest.substring(73);
        }
        if (!rest.isEmpty()) {
            sb.append("\r\n ").append(rest);
        }
        return sb.toString();
    }
}
