package com.barbershop.backend.config;

import com.barbershop.backend.model.HorarioBarberia;
import com.barbershop.backend.repository.HorarioBarberiaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;

/**
 * Siembra del horario general (D9), en todos los perfiles y antes de los
 * seeders ({@code DataSeeder}/{@code ProdSeeder} no declaran orden → van al
 * final). Si {@code horario_barberia} está vacía inserta los 7 días a partir
 * de {@link HorarioDefaults} (lun–sáb 09–20, domingo cerrado con la
 * configuración actual), de modo que el comportamiento no cambia hasta que el
 * admin lo toque. Idempotente: con filas existentes no hace nada (nunca
 * sobrescribe un horario personalizado).
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class HorarioDefaultsInitializer implements ApplicationRunner {

    private final HorarioBarberiaRepository horarioBarberiaRepository;
    private final HorarioDefaults horarioDefaults;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (horarioBarberiaRepository.count() > 0) {
            return;
        }
        List<HorarioBarberia> dias = Arrays.stream(DayOfWeek.values())
                .map(horarioDefaults::porDefecto)
                .toList();
        horarioBarberiaRepository.saveAll(dias);
        log.info("Horario general sembrado desde app.business.* ({} días)", dias.size());
    }
}
