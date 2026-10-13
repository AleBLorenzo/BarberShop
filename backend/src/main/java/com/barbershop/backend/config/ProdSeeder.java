package com.barbershop.backend.config;

import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.ServicioRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bootstrap minimo de produccion (perfil {@code prod}), SIEMPRE condicionado a
 * que la base de datos este vacia:
 *
 * <ul>
 *   <li>Primer administrador: solo si {@code SEED_ADMIN_EMAIL} y
 *       {@code SEED_ADMIN_PASSWORD} estan definidos. Si faltan, se avisa por
 *       log y no se crea nada (nunca credenciales por defecto en el codigo —
 *       sin un ADMIN la tabla trabajadores quedaria vacia y no habria con
 *       que entrar en {@code POST /api/admin/trabajadores}).</li>
 *   <li>Catalogo real de servicios: solo si la tabla de servicios esta vacia
 *       (mismos datos que {@link DataSeeder}).</li>
 *   <li>NUNCA datos de demostracion (clientes, reservas o fotos): en produccion
 *       solo datos reales del negocio.</li>
 * </ul>
 *
 * Con la BD ya poblada no toca nada: cada guard es un {@code count() == 0},
 * de modo que los reinicios del backend conservan los datos intactos.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProdSeeder implements CommandLineRunner {

    private final TrabajadorRepository trabajadorRepository;
    private final ServicioRepository servicioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${SEED_ADMIN_EMAIL:}")
    private String seedAdminEmail;

    @Value("${SEED_ADMIN_PASSWORD:}")
    private String seedAdminPassword;

    @Override
    public void run(String... args) {
        if (trabajadorRepository.count() == 0) {
            String email = seedAdminEmail == null ? "" : seedAdminEmail.trim();
            String password = seedAdminPassword == null ? "" : seedAdminPassword;
            if (email.isEmpty() || password.isEmpty()) {
                System.out.println("[ProdSeeder] BD sin administrador: define SEED_ADMIN_EMAIL "
                        + "y SEED_ADMIN_PASSWORD (solo necesario en el primer arranque) "
                        + "para crear el primer admin.");
            } else {
                trabajadorRepository.save(Trabajador.builder()
                        .nombre("Administrador")
                        .email(email)
                        .passwordHash(passwordEncoder.encode(password))
                        .activo(true)
                        .rol(Rol.ADMIN)
                        .build());
                // Nunca se imprime la contraseña por log.
                System.out.println("[ProdSeeder] Administrador inicial creado: " + email);
            }
        }

        if (servicioRepository.count() == 0) {
            servicioRepository.saveAll(catalogoReal());
            System.out.println("[ProdSeeder] Catalogo de servicios sembrado ("
                    + servicioRepository.count() + " servicios).");
        }
    }

    /** Catalogo real de la barberia (misma lista que DataSeeder, sin datos demo). */
    private List<Servicio> catalogoReal() {
        return List.of(
                servicio("Corte", "15.00", 30),
                servicio("Corte + barba", "20.00", 45),
                servicio("Corte clásico", "12.00", 30),
                servicio("Arreglo de barba", "12.00", 30),
                servicio("Niños", "15.00", 30),
                servicio("Afeitado tradicional", "10.00", 30),
                servicio("Cejas", "5.00", 15),
                servicio("Corte clásico con cejas", "17.00", 30),
                servicio("Barba con cejas", "17.00", 30));
    }

    private Servicio servicio(String nombre, String precio, int duracionMinutos) {
        return Servicio.builder()
                .nombre(nombre)
                .precio(new BigDecimal(precio))
                .duracionMinutos(duracionMinutos)
                .activo(true)
                .build();
    }
}
