package com.barbershop.backend.agenda;

import com.barbershop.backend.config.HorarioDefaultsInitializer;
import com.barbershop.backend.model.HorarioBarberia;
import com.barbershop.backend.repository.HorarioBarberiaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Siembra del horario general (D9, tarea 1.9): con la tabla vacía se insertan
 * los 7 días desde {@code app.business.*}; un segundo arranque no duplica ni
 * sobrescribe un horario ya personalizado.
 *
 * <p>La H2 en memoria es compartida entre contextos de test, así que el
 * "arranque con BD vacía" se simula vaciando la tabla y ejecutando el runner;
 * al terminar se restaura la siembra para no afectar a otros tests.</p>
 */
@SpringBootTest
class HorarioDefaultsInitializerTest {

    @Autowired
    private HorarioBarberiaRepository horarioBarberiaRepository;

    @Autowired
    private HorarioDefaultsInitializer initializer;

    @AfterEach
    void restaurar() {
        horarioBarberiaRepository.deleteAll();
        initializer.run(null);
    }

    @Test
    void arranqueConTablaVaciaSiembraSieteDias() {
        horarioBarberiaRepository.deleteAll();

        initializer.run(null);

        assertThat(horarioBarberiaRepository.count()).isEqualTo(7);
        HorarioBarberia martes = horarioBarberiaRepository.findByDiaSemana(DayOfWeek.TUESDAY).orElseThrow();
        assertThat(martes.getAbierto()).isTrue();
        assertThat(martes.getHoraApertura()).isEqualTo(LocalTime.of(9, 0));
        // Propiedades de test: app.business.closing-hour=21 (en prod es 20)
        assertThat(martes.getHoraCierre()).isEqualTo(LocalTime.of(21, 0));
        HorarioBarberia domingo = horarioBarberiaRepository.findByDiaSemana(DayOfWeek.SUNDAY).orElseThrow();
        assertThat(domingo.getAbierto()).isFalse();
        assertThat(domingo.getHoraApertura()).isNull();
        assertThat(domingo.getHoraCierre()).isNull();
    }

    @Test
    void segundoArranqueNoDuplicaNiSobrescribe() {
        horarioBarberiaRepository.deleteAll();
        initializer.run(null);

        HorarioBarberia sabado = horarioBarberiaRepository.findByDiaSemana(DayOfWeek.SATURDAY).orElseThrow();
        sabado.setHoraApertura(LocalTime.of(10, 0));
        sabado.setHoraCierre(LocalTime.of(14, 0));
        horarioBarberiaRepository.save(sabado);

        initializer.run(null);

        assertThat(horarioBarberiaRepository.count()).isEqualTo(7);
        HorarioBarberia guardado = horarioBarberiaRepository.findByDiaSemana(DayOfWeek.SATURDAY).orElseThrow();
        assertThat(guardado.getHoraApertura()).isEqualTo(LocalTime.of(10, 0));
        assertThat(guardado.getHoraCierre()).isEqualTo(LocalTime.of(14, 0));
    }
}
