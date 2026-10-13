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
 */
class SlotGeneratorTest {

    private SlotGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SlotGenerator();
        ReflectionTestUtils.setField(generator, "openingHour", 9);
        ReflectionTestUtils.setField(generator, "closingHour", 20);
        ReflectionTestUtils.setField(generator, "slotIntervalMinutes", 30);
    }

    @Test
    void servicioDe30MinUltimaCita1930() {
        List<TimeSlot> slots = generator.generateSlots(30);

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
        List<TimeSlot> slots = generator.generateSlots(45);

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
        List<TimeSlot> slots = generator.generateSlots(60);

        assertThat(slots.get(0).getInicio()).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots.get(slots.size() - 1).getInicio()).isEqualTo(LocalTime.of(19, 0));
        assertThat(slots).allSatisfy(s ->
                assertThat(s.getFin()).isBeforeOrEqualTo(LocalTime.of(20, 0)));
    }

    @Test
    void servicioQueNoCabeEnLaJornadaNoGeneraHuecos() {
        assertThat(generator.generateSlots(15 * 60)).isEmpty();
    }
}
