package com.barbershop.backend.config;

import com.barbershop.backend.model.*;
import com.barbershop.backend.repository.ReservaRepository;
import com.barbershop.backend.repository.ResultadoRepository;
import com.barbershop.backend.repository.ServicioRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.service.ClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

/**
 * Development-only seeder (perfil dev, PostgreSQL local): creates demo workers,
 * services and reservations for the current week so every screen has realistic
 * data. Never runs with the "prod" profile.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final TrabajadorRepository trabajadorRepository;
    private final ServicioRepository servicioRepository;
    private final ClienteService clienteService;
    private final ReservaRepository reservaRepository;
    private final PasswordEncoder passwordEncoder;
    private final ResultadoRepository resultadoRepository;

    @Value("${app.business.closed-days:SUNDAY}")
    private String closedDaysProp;

    /**
     * Opt-in de credenciales demo en consola (auditoría B-BAJO-4): con el
     * default {@code false} el arranque no imprime contraseñas ni tokens.
     * Activar en local con DEMO_PRINT_CREDENTIALS=true (flujo cómodo).
     */
    @Value("${app.demo.print-credentials:false}")
    private boolean printCredentials;

    @Override
    public void run(String... args) {
        if (trabajadorRepository.count() > 0) {
            return; // already seeded
        }

        // --- Workers (login: email / password shown) ---
        Trabajador carlos = saveTrabajador("Carlos Mejía", "admin@barbershop.com", "admin12345", Rol.ADMIN);
        Trabajador ana = saveTrabajador("Ana Torres", "ana@barbershop.com", "ana123456", Rol.WORKER);
        Trabajador luis = saveTrabajador("Luis Rivas", "luis@barbershop.com", "luis123456", Rol.WORKER);

        // --- Services (catálogo real de la barbería: nombre / precio / duración) ---
        Servicio corte = saveServicio("Corte", "15.00", 30);
        Servicio corteBarba = saveServicio("Corte + barba", "20.00", 45);
        Servicio clasico = saveServicio("Corte clásico", "12.00", 30);
        Servicio arregloBarba = saveServicio("Arreglo de barba", "12.00", 30);
        Servicio ninos = saveServicio("Niños", "15.00", 30);
        Servicio afeitado = saveServicio("Afeitado tradicional", "10.00", 30);
        Servicio cejas = saveServicio("Cejas", "5.00", 15);
        Servicio clasicoCejas = saveServicio("Corte clásico con cejas", "17.00", 30);
        Servicio barbaCejas = saveServicio("Barba con cejas", "17.00", 30);
        List<Servicio> servicios = List.of(corte, corteBarba, clasico, arregloBarba, ninos,
                afeitado, cejas, clasicoCejas, barbaCejas);

        // --- Galería «Resultados»: fotos de ejemplo (el admin las sustituye por URL) ---
        // 480x480: tamaño real de la celda (~231px) con margen retina 2x
        String[][] fotos = {
                {"https://picsum.photos/seed/tankbarber1/480/480", "Corte de pelo"},
                {"https://picsum.photos/seed/tankbarber2/480/480", "Barba perfilada"},
                {"https://picsum.photos/seed/tankbarber3/480/480", "Corte clásico"},
                {"https://picsum.photos/seed/tankbarber4/480/480", "Afeitado tradicional"},
        };
        for (String[] foto : fotos) {
            resultadoRepository.save(Resultado.builder().url(foto[0]).titulo(foto[1]).build());
        }

        // --- Demo clients ---
        String[][] clientes = {
                {"Marta López", "612345678"},
                {"Javier Sánchez", "699887766"},
                {"Lucía Gómez", "633445566"},
                {"Pablo Martín", "655112233"},
                {"Sofía Ruiz", "688990011"},
                {"Diego Hernández", "677001122"},
                {"Elena Díaz", "644556677"},
        };

        // --- Reservations across the current week (Mon-Sun) ---
        LocalDate hoy = LocalDate.now();
        LocalDate lunes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Trabajador[] trabajadores = {carlos, ana, luis};
        int[] horas = {10, 11, 12, 16, 17, 18, 19};
        int seed = 0;

        for (int dias = 0; dias < 7; dias++) {
            LocalDate fecha = lunes.plusDays(dias);
            if (diaCerrado(fecha)) {
                continue; // días cerrados (domingos): no se puede reservar, no se siembra
            }
            int reservasDia = 2 + (fecha.getDayOfMonth() % 3); // 2-4 per day

            for (int i = 0; i < reservasDia; i++) {
                String[] cliente = clientes[seed % clientes.length];
                Trabajador trabajador = trabajadores[seed % trabajadores.length];
                Servicio servicio = servicios.get(seed % servicios.size());
                LocalTime hora = LocalTime.of(horas[(seed + i) % horas.length], 0);

                ReservaEstado estado;
                if (fecha.isBefore(hoy)) {
                    ReservaEstado[] pasados = {ReservaEstado.COMPLETADA, ReservaEstado.COMPLETADA,
                            ReservaEstado.NO_ASISTIO, ReservaEstado.CANCELADA};
                    estado = pasados[seed % pasados.length];
                } else if (fecha.isEqual(hoy)) {
                    estado = (i == 0) ? ReservaEstado.PENDIENTE : ReservaEstado.CONFIRMADA;
                } else {
                    estado = (seed % 3 == 0) ? ReservaEstado.PENDIENTE : ReservaEstado.CONFIRMADA;
                }

                reservaRepository.save(Reserva.builder()
                        .cliente(clienteService.findOrCreateByTelefono(cliente[0], cliente[1]))
                        .trabajador(trabajador)
                        .servicio(servicio)
                        .fecha(fecha)
                        .horaInicio(hora)
                        .horaFin(hora.plusMinutes(servicio.getDuracionMinutos()))
                        .estado(estado)
                        .build());
                seed++;
            }
        }

        // --- Direct-access demo reservation (phone + token for "Mis citas") ---
        LocalDate fechaDemo = hoy.plusDays(1);
        while (diaCerrado(fechaDemo)) {
            fechaDemo = fechaDemo.plusDays(1); // nunca en un día cerrado
        }
        Reserva demo = reservaRepository.save(Reserva.builder()
                .cliente(clienteService.findOrCreateByTelefono("Marta López", "612345678"))
                .trabajador(carlos)
                .servicio(corte)
                .fecha(fechaDemo)
                .horaInicio(LocalTime.of(15, 0))
                .horaFin(LocalTime.of(15, 30))
                .estado(ReservaEstado.CONFIRMADA)
                .build());
        // Credenciales/token demo solo bajo flag opt-in (B-BAJO-4): por
        // defecto el journal del arranque dev no contiene secretos.
        if (printCredentials) {
            System.out.println("[DataSeeder] Token demo 'Mis citas': " + demo.getTokenCancelacion()
                    + " (tel. 612345678)");
            System.out.println("[DataSeeder] Logins: admin@barbershop.com/admin12345 | "
                    + "ana@barbershop.com/ana123456 | luis@barbershop.com/luis123456");
        }
    }

    private Trabajador saveTrabajador(String nombre, String email, String password, Rol rol) {
        return trabajadorRepository.save(Trabajador.builder()
                .nombre(nombre)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .activo(true)
                .rol(rol)
                .build());
    }

    private Servicio saveServicio(String nombre, String precio, int duracion) {
        return servicioRepository.save(Servicio.builder()
                .nombre(nombre)
                .precio(new BigDecimal(precio))
                .duracionMinutos(duracion)
                .activo(true)
                .build());
    }

    // --- Días cerrados (app.business.closed-days, p. ej. SUNDAY) ---

    private boolean diaCerrado(LocalDate fecha) {
        if (closedDaysProp == null || closedDaysProp.isBlank()) {
            return false;
        }
        for (String parte : closedDaysProp.split(",")) {
            String limpio = parte.trim();
            if (!limpio.isEmpty()
                    && fecha.getDayOfWeek() == DayOfWeek.valueOf(limpio.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
