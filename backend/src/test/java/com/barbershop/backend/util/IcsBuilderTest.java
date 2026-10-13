package com.barbershop.backend.util;

import com.barbershop.backend.dto.ReservaResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IcsBuilderTest {

    private final IcsBuilder icsBuilder = new IcsBuilder("TheTankBarber");

    private ReservaResponse reserva() {
        return ReservaResponse.builder()
                .id(42L)
                .fecha(LocalDate.of(2026, 10, 2))
                .horaInicio(LocalTime.of(18, 0))
                .horaFin(LocalTime.of(18, 45))
                .servicioNombre("Corte + barba")
                .trabajadorNombre("Ana, cortes; clásicos")
                .clienteNombre("Juan Pérez")
                .clienteTelefono("612345678")
                .precioTotal(new BigDecimal("22.50"))
                .estado("CONFIRMADA")
                .tokenCancelacion(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
                .build();
    }

    @Test
    void generaIcsConEstructuraValida() {
        String ics = icsBuilder.build(reserva());
        String plano = ics.replace("\r\n ", ""); // sin plegar, para las aserciones de contenido

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n");
        assertThat(ics).contains("VERSION:2.0");
        assertThat(ics).contains("UID:barbershop-reserva-42@barbershop");
        assertThat(ics).contains("PRODID:-//TheTankBarber//Reservas//ES");
        assertThat(ics).contains("DTSTART:20261002T180000");
        assertThat(ics).contains("DTEND:20261002T184500");
        assertThat(ics).contains("SUMMARY:Corte + barba · TheTankBarber");
        assertThat(ics).contains("LOCATION:TheTankBarber");
        assertThat(ics).contains("TRIGGER:-PT30M");
        assertThat(plano).contains("Token de cancelación: 550e8400-e29b-41d4-a716-446655440000");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");

        // Sin LF sueltos: todas las líneas terminadas en CRLF
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void escapaCaracteresSegunRfc5545() {
        String plano = icsBuilder.build(reserva()).replace("\r\n ", "");

        // coma y punto y coma escapadas con backslash
        assertThat(plano).contains("Ana\\, cortes\\; clásicos");
    }

    @Test
    void pliegaLineasLargas() {
        ReservaResponse r = reserva();
        r.setClienteNombre("Cliente con un nombre extraordinariamente largo ".repeat(3));
        String ics = icsBuilder.build(r);

        for (String linea : ics.split("\r\n")) {
            assertThat(linea.length()).isLessThanOrEqualTo(75);
        }
    }
}
