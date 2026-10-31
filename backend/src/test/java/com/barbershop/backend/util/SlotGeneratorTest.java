package com.barbershop.backend.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rejilla alineada hacia atrás desde el cierre: la última cita empieza en
 * cierre − duración (20:00 → 19:30 / 19:15 / 19:00) y nada se sale de 9:00–20:00.
 * La apertura y el cierre llegan ahora de la jornada efectiva (D2), no de
 * propiedades: también se cubre una jornada reducida 09:00–14:00.
 */
class SlotGeneratorTest {

    private static final LocalTime APERTURA = LocalTime.of(9, 0);
    private static final LocalTime CIERRE = LocalTime.of(20, 0);

    private SlotGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SlotGenerator();
        ReflectionTestUtils.setField(generator, "slotIntervalMinutes", 30);
    }

    @Test
    void servicioDe30MinUltimaCita1930() {
        List<TimeSlot> slots = generator.generateSlots(APERTURA, CIERRE, 30);

        assertThat(slots).isNotEmpty();
        assertThat(slots.get(0).getInicio()).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots.get(slots.size() - 1).getInicio()).isEqualTo(LocalTime.of(19, 30));
        assertThat(slots).allSatisfy(s -> {
            assertThat(s.getInicio()).isAfterOrEqualTo(LocalTime.of(9, 0));
            assertThat(s.getFin()).isBeforeOrEqualTo(LocalTime.of(20, 0));
        });
        assertThat(slots).isSortedAccordingTo(Comparator.comparing(TimeSlot::getInicio));
    }

    @Test
    void servicioDe45MinUltimaCita1915() {
        List<TimeSlot> slots = generator.generateSlots(APERTURA, CIERRE, 45);

        assertThat(slots).isNotEmpty();
        assertThat(slots.get(slots.size() - 1).getInicio()).isEqualTo(LocalTime.of(19, 15));
        assertThat(slots).allSatisfy(s -> {
            assertThat(s.getInicio()).isAfterOrEqualTo(LocalTime.of(9, 0));
            assertThat(s.getFin()).isBeforeOrEqualTo(LocalTime.of(20, 0));
        });
        assertThat(slots).isSortedAccordingTo(Comparator.comparing(TimeSlot::getInicio));
    }

    @Test
    void servicioDe60MinUltimaCita1900() {
        List<TimeSlot> slots = generator.generateSlots(APERTURA, CIERRE, 60);

        assertThat(slots.get(0).getInicio()).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots.get(slots.size() - 1).getInicio()).isEqualTo(LocalTime.of(19, 0));
        assertThat(slots).allSatisfy(s ->
                assertThat(s.getFin()).isBeforeOrEqualTo(LocalTime.of(20, 0)));
    }

    @Test
    void servicioQueNoCabeEnLaJornadaNoGeneraHuecos() {
        assertThat(generator.generateSlots(APERTURA, CIERRE, 15 * 60)).isEmpty();
    }

    @Test
    void jornadaReducidaUltimaCita1330() {
        List<TimeSlot> slots = generator.generateSlots(LocalTime.of(9, 0), LocalTime.of(14, 0), 30);

        assertThat(slots.get(0).getInicio()).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots.get(slots.size() - 1).getInicio()).isEqualTo(LocalTime.of(13, 30));
        assertThat(slots.get(slots.size() - 1).getFin()).isEqualTo(LocalTime.of(14, 0));
        assertThat(slots).hasSize(10);
    }

    @Test
    void jornadaReducidaCon45MinAlineadaHaciaAtras() {
        List<TimeSlot> slots = generator.generateSlots(LocalTime.of(9, 0), LocalTime.of(14, 0), 45);

        assertThat(slots.get(slots.size() - 1).getInicio()).isEqualTo(LocalTime.of(13, 15));
        assertThat(slots).allSatisfy(s -> {
            assertThat(s.getInicio()).isAfterOrEqualTo(LocalTime.of(9, 0));
            assertThat(s.getFin()).isBeforeOrEqualTo(LocalTime.of(14, 0));
        });
    }

    @Test
    void ocupadosMarcanHuecosNoDisponibles() {
        List<TimeSlot> slots = generator.generateSlotsForWorker(LocalTime.of(9, 0), LocalTime.of(14, 0), 30,
                List.of(SlotGenerator.ReservaSlot.builder()
                        .inicio(LocalTime.of(13, 0)).fin(LocalTime.of(14, 0)).build()));

        assertThat(slots).filteredOn(s -> !s.getInicio().isBefore(LocalTime.of(13, 0)))
                .allSatisfy(s -> assertThat(s.isDisponible()).isFalse());
        assertThat(slots).filteredOn(s -> s.getInicio().isBefore(LocalTime.of(13, 0)))
                .allSatisfy(s -> assertThat(s.isDisponible()).isTrue());
    }

    @Test
    void sinJornadaNoHayHuecos() {
        assertThat(generator.generateSlots(null, null, 30)).isEmpty();
        assertThat(generator.generateSlots(LocalTime.of(14, 0), LocalTime.of(14, 0), 30)).isEmpty();
    }
}
