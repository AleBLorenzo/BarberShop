package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.barbershop.backend.repository.TrabajadorRepository;

/**
 * B-BAJO-4 / spec dev-seeder-hygiene: con el default
 * {@code app.demo.print-credentials=false} el arranque del perfil dev siembra
 * la demo completa pero NO imprime contraseñas ni tokens en consola.
 *
 * <p>La captura de {@code System.out} arranca en {@code @BeforeAll}, antes de
 * que JUnit cree la instancia de test (la inyección de dependencias carga el
 * contexto y ejecuta {@code DataSeeder} dentro de esa ventana). El perfil
 * {@code dev} en test usa H2 (src/test/resources/application-dev.properties)
 * y no toca PostgreSQL.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
class DataSeederHygieneTest {

    private static final PrintStream STDOUT_ORIGINAL = System.out;
    private static final ByteArrayOutputStream CAPTURA = new ByteArrayOutputStream();

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @BeforeAll
    static void capturarSalida() {
        System.setOut(new PrintStream(CAPTURA, true, StandardCharsets.UTF_8));
    }

    @AfterAll
    static void restaurarSalida() {
        System.setOut(STDOUT_ORIGINAL);
    }

    @Test
    void arranqueDevSinFlagSiembraDemoSinImprimirCredenciales() {
        String salida = CAPTURA.toString(StandardCharsets.UTF_8);

        // La demo SÍ se siembra (el guard de "ya sembrado" no la esconde)
        assertThat(trabajadorRepository.count()).isGreaterThanOrEqualTo(3);

        // ... pero la consola no contiene secretos sin la flag opt-in
        assertThat(salida)
                .doesNotContain("admin12345")
                .doesNotContain("ana123456")
                .doesNotContain("luis123456")
                .doesNotContain("[DataSeeder] Token demo")
                .doesNotContain("[DataSeeder] Logins");
    }
}
