package com.barbershop.backend.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carga el archivo {@code .env} (formato KEY=VALUE, no commiteado) como property
 * source de baja prioridad antes de que arranque la aplicacion.
 *
 * <p>Busca primero {@code ./.env} (ejecucion desde {@code backend/}) y despues
 * {@code ./backend/.env} (ejecucion desde la raiz del repositorio). Si el archivo
 * no existe, no hace nada: los despliegues pueden usar variables de entorno
 * nativas (siempre tienen prioridad sobre el archivo, pues este se anade al final).</p>
 *
 * <p>Resuelve el hueco previo: sin este cargador, claves como {@code MAIL_HOST} o
 * {@code JWT_SECRET} escritas en {@code backend/.env} nunca llegaban a Spring
 * (no hay soporte nativo de dotenv y {@code spring.config.import} ignora las
 * extensiones desconocidas como {@code .env}).</p>
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "dotenvFile";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envFile = resolveEnvFile();
        if (envFile == null) {
            return;
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.startsWith("export ")) {
                    trimmed = trimmed.substring("export ".length()).trim();
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                // Quita comillas envolventes si las hay (el resto del valor se respeta
                // tal cual: puede contener '#', ':', etc., tipico en contraseñas SMTP).
                if (value.length() >= 2) {
                    char first = value.charAt(0);
                    char last = value.charAt(value.length() - 1);
                    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                        value = value.substring(1, value.length() - 1);
                    }
                }
                properties.put(key, value);
            }
        } catch (IOException e) {
            // Mejor arrancar sin .env que fallar el arranque por un error de lectura.
            return;
        }
        if (!properties.isEmpty()) {
            // addLast: las variables de entorno reales del SO mandan sobre el archivo.
            environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, properties));
        }
    }

    private Path resolveEnvFile() {
        Path local = Paths.get(".env");
        if (Files.isRegularFile(local)) {
            return local;
        }
        Path nested = Paths.get("backend", ".env");
        return Files.isRegularFile(nested) ? nested : null;
    }
}
