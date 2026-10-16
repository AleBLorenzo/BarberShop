package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Spec sql-injection-hardening — convención verificable «SQL parametrizado
 * únicamente» (D1): test JUnit PURO (sin Spring, sin arrancar contexto) que
 * recorre {@code backend/src/main} en tiempo de test (cwd de surefire =
 * {@code backend/}) y FALLA nombrando la clase y la consulta si detecta SQL
 * dinámico no parametrizado:
 *
 * <ul>
 *   <li>{@code @Query} con concatenación {@code +} sobre parámetros/no literales
 *       (la concatenación de strings LITERALES —p. ej. {@code SesionAuthRepository}— está permitida),</li>
 *   <li>{@code String.format} dentro de un {@code @Query},</li>
 *   <li>{@code createNativeQuery} cuyo argumento no sea una consulta literal
 *       (concatenado o construido fuera),</li>
 *   <li>uso de {@code JdbcTemplate} o {@code Statement}/{@code PreparedStatement}
 *       (API de SQL crudo) en {@code src/main}.</li>
 * </ul>
 *
 * <p>El test PASA contra el código actual: 19 {@code @Query} con enlace
 * {@code :param}, incluida la única nativa ({@code ReservaRepository},
 * {@code IN (:antiguosIds)}) y cero API de SQL crudo. Los tests sintéticos de
 * este mismo fichero demuestran que el detector SÍ falla (y identifica
 * clase/consulta) ante una regresión futura.</p>
 */
class SqlInjectionRegressionTest {

    private static final Path SRC_MAIN = Paths.get("src/main");

    private static final Pattern PATRON_CLASE = Pattern.compile(
            "(?:class|interface|enum|record)\\s+(\\w+)");

    /** API de SQL crudo prohibida en src/main (el scan del detector es por fichero). */
    private static final Pattern PATRON_SQL_CRUDO = Pattern.compile(
            "\\b(?:Statement|PreparedStatement)\\b");

    // ===================== Scan del código real =====================

    @Test
    void srcMainNoContieneSqlDinamicoNoParametrizado() throws IOException {
        assertThat(Files.isDirectory(SRC_MAIN))
                .describedAs("No existe %s: el cwd del test debe ser backend/", SRC_MAIN.toAbsolutePath())
                .isTrue();

        List<String> violaciones = new ArrayList<>();
        try (Stream<Path> caminos = Files.walk(SRC_MAIN)) {
            for (Path fichero : caminos.filter(p -> p.toString().endsWith(".java")).toList()) {
                String fuente = Files.readString(fichero, StandardCharsets.UTF_8);
                String relativo = SRC_MAIN.relativize(fichero).toString().replace('\\', '/');
                violaciones.addAll(detectarViolaciones(fuente, relativo));
            }
        }

        assertThat(violaciones)
                .describedAs("Convención sql-injection-hardening: SQL dinámico no parametrizado prohibido en src/main")
                .isEmpty();
    }

    @Test
    void codigoActualTieneLas19QueryParametrizadasIncluidaLaNativa() throws IOException {
        assertThat(Files.isDirectory(SRC_MAIN)).isTrue();
        List<String> queries = queriesDeSrcMain();

        // Estado verificado de la auditoría (propuesta): 19 @Query, todas con :param.
        assertThat(queries)
                .describedAs("Se esperan al menos las 19 @Query auditadas en src/main — "
                        + "¿ha cambiado el layout o el scanner no está encontrando las queries?")
                .hasSizeGreaterThanOrEqualTo(19);
        assertThat(queries.stream().filter(q -> q.contains(":")).count())
                .describedAs("Las 19 @Query actuales usan enlace de parámetros :param")
                .isGreaterThanOrEqualTo(19);

        // La única nativa: ReservaRepository, parametrizada con IN (:antiguosIds).
        Path reservaRepo = SRC_MAIN.resolve("java/com/barbershop/backend/repository/ReservaRepository.java");
        assertThat(reservaRepo).exists();
        String fuente = Files.readString(reservaRepo, StandardCharsets.UTF_8);
        assertThat(fuente).contains("nativeQuery = true");
        assertThat(fuente).contains("IN (:antiguosIds)");
    }

    // ============ Regresión futura: el detector debe FALLAR ============

    @Test
    void detectorMarcaQueryConcatenadaIdentificandoClaseYConsulta() {
        String fuente = """
                package com.ejemplo;
                interface RepoEjemplo {
                    @Query("SELECT c FROM Cliente c WHERE c.nombre LIKE '%" + nombre + "%'")
                    List<Cliente> buscar(String nombre);
                }
                """;
        List<String> violaciones = detectarViolaciones(fuente, "RepoEjemplo.java");
        assertThat(violaciones)
                .describedAs("Un @Query concatenado con + sobre un parámetro DEBE fallar")
                .hasSize(1);
        assertThat(violaciones.get(0))
                .contains("RepoEjemplo")
                .contains("LIKE");
    }

    @Test
    void detectorMarcaStringFormatDentroDeQuery() {
        String fuente = """
                package com.ejemplo;
                interface RepoFormat {
                    @Query(String.format("SELECT c FROM Cliente c WHERE c.id = %s", id))
                    Cliente porId(String id);
                }
                """;
        List<String> violaciones = detectarViolaciones(fuente, "RepoFormat.java");
        assertThat(violaciones).hasSize(1);
        assertThat(violaciones.get(0))
                .contains("RepoFormat")
                .contains("String.format");
    }

    @Test
    void detectorMarcaCreateNativeQueryConcatenado() {
        String fuente = """
                package com.ejemplo;
                interface RepoNativo {
                    void borrar(jakarta.persistence.EntityManager em, String id) {
                        em.createNativeQuery("DELETE FROM trabajadores WHERE id = " + id);
                    }
                }
                """;
        List<String> violaciones = detectarViolaciones(fuente, "RepoNativo.java");
        assertThat(violaciones)
                .describedAs("createNativeQuery construido con input de usuario DEBE fallar")
                .hasSize(1);
        assertThat(violaciones.get(0))
                .contains("RepoNativo")
                .contains("createNativeQuery");
    }

    @Test
    void detectorMarcaJdbcTemplateYStatementCrudos() {
        String fuente = """
                package com.ejemplo;
                class RepoCrudo {
                    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
                    void ejecutar(java.sql.Statement st) throws Exception {
                        st.execute("DELETE FROM clientes");
                    }
                }
                """;
        List<String> violaciones = detectarViolaciones(fuente, "RepoCrudo.java");
        assertThat(violaciones)
                .describedAs("JdbcTemplate y Statement son SQL crudo: ambos DEBEN fallar")
                .hasSize(2);
        assertThat(violaciones.get(0)).contains("JdbcTemplate");
        assertThat(violaciones.get(1)).contains("Statement");
    }

    @Test
    void detectorAdmiteLoQueElCodigoActualHaceLiteral() {
        // Réplica de los patrones LEGÍTIMOS de hoy: CONCAT parametrizado,
        // concatenación de strings LITERALES (SesionAuthRepository) y la nativa
        // con IN (:antiguosIds) (ReservaRepository) — ninguno es SQL dinámico.
        String fuente = """
                package com.ejemplo;
                interface RepoLegal {
                    @Query("SELECT c FROM Cliente c WHERE LOWER(c.nombre) LIKE LOWER(CONCAT('%', :nombre, '%'))")
                    List<Cliente> buscar(org.springframework.data.repository.query.Param("nombre") String nombre);

                    @Query("UPDATE SesionAuth s SET s.revocadaEn = :ahora "
                            + "WHERE s.trabajador.id = :trabajadorId")
                    int revocar(org.springframework.data.repository.query.Param("ahora") java.time.LocalDateTime ahora);

                    @Query(value = "UPDATE reservas SET cliente_id = :nuevoClienteId WHERE cliente_id IN (:antiguosIds)", nativeQuery = true)
                    void repuntar(org.springframework.data.repository.query.Param("nuevoClienteId") Long nuevo,
                                  org.springframework.data.repository.query.Param("antiguosIds") java.util.List<Long> antiguos);
                }
                """;
        assertThat(detectarViolaciones(fuente, "RepoLegal.java"))
                .describedAs("Los patrones actuales del código son legales: parametrizados, sin SQL crudo")
                .isEmpty();
    }

    // ===================== Detector (reglas) =====================

    /** Detecta violaciones de la convención en UNA fuente; cada violación nombra clase y consulta. */
    static List<String> detectarViolaciones(String fuente, String fichero) {
        List<String> violaciones = new ArrayList<>();
        String clase = nombreDeClase(fuente, fichero);

        for (String query : extraerAnotaciones(fuente, "@Query")) {
            List<String> tokens = tokenizar(query);
            String extracto = extracto(query);
            if (tokens.stream().anyMatch(t -> t.startsWith("OTRO:") && t.contains("String.format"))) {
                violaciones.add(mensaje(fichero, clase, "String.format dentro de @Query", extracto));
            }
            if (hayConcatenacionNoLiteral(tokens)) {
                violaciones.add(mensaje(fichero, clase,
                        "@Query con concatenación + sobre parámetros/no literales", extracto));
            }
        }

        for (String args : extraerAnotaciones(fuente, "createNativeQuery")) {
            List<String> tokens = tokenizar(args);
            boolean soloLiteral = tokens.size() == 1 && tokens.get(0).startsWith("CADENA:");
            boolean formato = tokens.stream().anyMatch(t -> t.startsWith("OTRO:") && t.contains("String.format"));
            if (!soloLiteral || formato || hayConcatenacionNoLiteral(tokens)) {
                violaciones.add(mensaje(fichero, clase,
                        "createNativeQuery no es una consulta literal (SQL dinámico)", extracto(args)));
            }
        }

        if (fuente.contains("JdbcTemplate")) {
            violaciones.add(mensaje(fichero, clase, "JdbcTemplate (SQL crudo) prohibido en src/main", ""));
        }
        Matcher crudo = PATRON_SQL_CRUDO.matcher(fuente);
        if (crudo.find()) {
            violaciones.add(mensaje(fichero, clase,
                    "Statement/PreparedStatement (SQL crudo) prohibido en src/main: «" + crudo.group() + "»", ""));
        }
        return violaciones;
    }

    /** Extrae los argumentos (texto entre paréntesis equilibrados) de cada aparición de la marca. */
    static List<String> extraerAnotaciones(String fuente, String marca) {
        List<String> extracciones = new ArrayList<>();
        int desde = 0;
        while (true) {
            int i = fuente.indexOf(marca, desde);
            if (i < 0) {
                return extracciones;
            }
            int j = i + marca.length();
            while (j < fuente.length() && Character.isWhitespace(fuente.charAt(j))) {
                j++;
            }
            if (j < fuente.length() && fuente.charAt(j) == '(') {
                String args = parentesisEquilibrado(fuente, j);
                if (args != null) {
                    extracciones.add(args);
                }
            }
            desde = i + marca.length();
        }
    }

    /** Devuelve el contenido entre paréntesis desde {@code inicio} (== '('), ignorando dentro de "...". */
    private static String parentesisEquilibrado(String s, int inicio) {
        int profundidad = 0;
        boolean enCadena = false;
        for (int k = inicio; k < s.length(); k++) {
            char c = s.charAt(k);
            if (enCadena) {
                if (c == '\\') {
                    k++; // escape dentro de la cadena Java
                } else if (c == '"') {
                    enCadena = false;
                }
                continue;
            }
            if (c == '"') {
                enCadena = true;
            } else if (c == '(') {
                profundidad++;
            } else if (c == ')') {
                profundidad--;
                if (profundidad == 0) {
                    return s.substring(inicio + 1, k);
                }
            }
        }
        return null;
    }

    /**
     * Tokeniza una expresión: {@code CADENA:...} (literal entre comillas),
     * {@code MAS} (el operador {@code +}) y {@code OTRO:...} (cualquier otra cosa).
     */
    static List<String> tokenizar(String expr) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        int n = expr.length();
        while (i < n) {
            char c = expr.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '"') {
                StringBuilder sb = new StringBuilder("CADENA:");
                i++;
                while (i < n) {
                    char d = expr.charAt(i);
                    if (d == '\\') {
                        sb.append(d);
                        if (i + 1 < n) {
                            sb.append(expr.charAt(i + 1));
                        }
                        i += 2;
                    } else if (d == '"') {
                        i++;
                        break;
                    } else {
                        sb.append(d);
                        i++;
                    }
                }
                tokens.add(sb.toString());
            } else if (c == '+') {
                tokens.add("MAS");
                i++;
            } else {
                int ini = i;
                while (i < n && !Character.isWhitespace(expr.charAt(i))
                        && expr.charAt(i) != '"' && expr.charAt(i) != '+') {
                    i++;
                }
                tokens.add("OTRO:" + expr.substring(ini, i));
            }
        }
        return tokens;
    }

    /** Un {@code +} solo es lícito si une DOS literales (p. ej. partir una query en varias líneas). */
    private static boolean hayConcatenacionNoLiteral(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            if (!"MAS".equals(tokens.get(i))) {
                continue;
            }
            boolean izqLiteral = i > 0 && tokens.get(i - 1).startsWith("CADENA:");
            boolean derLiteral = i + 1 < tokens.size() && tokens.get(i + 1).startsWith("CADENA:");
            if (!izqLiteral || !derLiteral) {
                return true;
            }
        }
        return false;
    }

    static String nombreDeClase(String fuente, String fichero) {
        Matcher m = PATRON_CLASE.matcher(fuente);
        return m.find() ? m.group(1) : fichero;
    }

    private static String extracto(String texto) {
        String plano = texto.replaceAll("\\s+", " ").trim();
        return plano.length() > 140 ? plano.substring(0, 140) + "…" : plano;
    }

    private static String mensaje(String fichero, String clase, String motivo, String extracto) {
        return "SQL dinámico prohibido en " + fichero + " (clase " + clase + "): " + motivo
                + (extracto.isEmpty() ? "" : " → «" + extracto + "»");
    }

    private static List<String> queriesDeSrcMain() throws IOException {
        List<String> queries = new ArrayList<>();
        try (Stream<Path> caminos = Files.walk(SRC_MAIN)) {
            for (Path fichero : caminos.filter(p -> p.toString().endsWith(".java")).toList()) {
                String fuente = Files.readString(fichero, StandardCharsets.UTF_8);
                queries.addAll(extraerAnotaciones(fuente, "@Query"));
            }
        }
        return queries;
    }
}
