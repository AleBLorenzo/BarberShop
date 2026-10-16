package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Spec bot-login-protection — {@code limit_req} nginx (D7, tarea 5.7). Test
 * UNITARIO sobre el TEXTO de {@code deploy/nginx-barbershop.conf} que guarda
 * contra regresiones de copy-paste: la zona existe FUERA del bloque server,
 * la {@code location = /api/auth/login} existe ANTES de {@code location /api/}
 * repite TODOS sus {@code proxy_set_header} (no los hereda) y lleva su
 * {@code limit_req}, y el resto de {@code /api/} queda SIN limit_req.
 *
 * <p>El fichero vive FUERA de {@code backend/}: la ruta se resuelve de forma
 * robusta subiendo desde {@code user.dir} (CI ejecuta en {@code backend/} =
 * un nivel por debajo de la raíz del repo) con varios candidatos; solo si no
 * aparece en ningún sitio el test FALLA con mensaje explicativo (nunca se
 * " salta" en silencio: la conf está versionada y debe existir).</p>
 */
class NginxConfTest {

    private static final String RUTA_RELATIVA = "deploy/nginx-barbershop.conf";

    private static String conf;

    @BeforeAll
    static void cargarConf() throws IOException {
        Path candidata = buscarConf();
        if (candidata == null) {
            fail("No se encontró " + RUTA_RELATIVA
                    + " subiendo desde user.dir=" + System.getProperty("user.dir")
                    + ". El test necesita la conf de nginx versionada en el repo"
                    + " (raíz del proyecto / deploy/).");
        }
        conf = Files.readString(candidata);
    }

    /**
     * Resolución robusta de la ruta: sube desde {@code user.dir} hasta la raíz
     * buscando {@code deploy/nginx-barbershop.conf} — funciona con cwd
     * {@code backend/} (CI, mvn) y con cwd en la raíz del repo (IDE).
     */
    private static Path buscarConf() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path posible = dir.resolve(RUTA_RELATIVA);
            if (Files.isRegularFile(posible)) {
                return posible;
            }
            dir = dir.getParent();
        }
        return null;
    }

    @Test
    void limitReqZoneDeclaradaFueraDelBloqueServer() {
        // Directiva de http (el fichero se incluye en http{} vía sites-enabled)
        assertThat(conf).contains(
                "limit_req_zone $binary_remote_addr zone=login:10m rate=5r/m;");
        int zona = conf.indexOf("limit_req_zone");
        int server = conf.indexOf("server {");
        assertThat(zona)
                .describedAs("limit_req_zone debe ir ANTES del bloque server (directiva de http)")
                .isGreaterThan(-1)
                .isLessThan(server);
    }

    @Test
    void locationLoginExisteAntesDeLocationApiYAplicaSuLimitReq() {
        Matcher login = locationExacta("location = /api/auth/login");
        Matcher api = locationPrefijo("location /api/");
        assertThat(login.find())
                .describedAs("debe existir location = /api/auth/login")
                .isTrue();
        assertThat(api.find())
                .describedAs("debe existir location /api/")
                .isTrue();
        assertThat(login.start())
                .describedAs("la location exacta del login debe declararse ANTES de location /api/")
                .isLessThan(api.start());

        String bloqueLogin = bloqueDe(conf, login.start());
        assertThat(bloqueLogin)
                .contains("limit_req zone=login burst=5 nodelay;")
                .contains("proxy_pass http://127.0.0.1:8080;");
    }

    @Test
    void locationLoginRepiteTodosLosProxySetHeaderDeLocationApi() {
        Matcher login = locationExacta("location = /api/auth/login");
        Matcher api = locationPrefijo("location /api/");
        assertThat(login.find()).isTrue();
        assertThat(api.find()).isTrue();
        String bloqueLogin = bloqueDe(conf, login.start());
        String bloqueApi = bloqueDe(conf, api.start());

        List<String> cabecerasLogin = proxySetHeaderDe(bloqueLogin);
        List<String> cabecerasApi = proxySetHeaderDe(bloqueApi);

        // La location del login NO hereda las de /api/ ⇒ las repite TODAS
        assertThat(cabecerasApi)
                .describedAs("location /api/ debe declarar sus 4 proxy_set_header")
                .hasSize(4);
        assertThat(cabecerasLogin)
                .describedAs("location = /api/auth/login debe repetir los MISMOS proxy_set_header")
                .containsExactlyInAnyOrderElementsOf(cabecerasApi);
    }

    @Test
    void elRestoDeLaApiNoAplicaLimitReq() {
        Matcher api = locationPrefijo("location /api/");
        assertThat(api.find()).isTrue();
        String bloqueApi = bloqueDe(conf, api.start());
        assertThat(bloqueApi)
                .describedAs("location /api/ NO debe llevar limit_req (rompería flujos legítimos)")
                .doesNotContain("limit_req");

        // En TODO el fichero hay una única limit_req activa (la del login):
        // ni duplicados ni copy-paste a otras locations.
        long limitReqActivas = contar(conf, "(?m)^\\s*limit_req\\s+zone=");
        assertThat(limitReqActivas)
                .describedAs("solo la location del login debe aplicar limit_req")
                .isEqualTo(1);
        long zonas = contar(conf, "(?m)^limit_req_zone\\b");
        assertThat(zonas).isEqualTo(1);
    }

    // ===================== helpers =====================

    /** Location exacta a línea de código (ignora comentarios). */
    private static Matcher locationExacta(String directiva) {
        return Pattern.compile("(?m)^\\s*" + Pattern.quote(directiva) + "\\s*\\{").matcher(conf);
    }

    /** Location por prefijo a línea de código (ignora comentarios). */
    private static Matcher locationPrefijo(String directiva) {
        return Pattern.compile("(?m)^\\s*" + Pattern.quote(directiva) + ".*\\{").matcher(conf);
    }

    /** Bloque {...} balanceado desde la posición de la apertura de la location. */
    private static String bloqueDe(String texto, int inicioLocation) {
        int apertura = texto.indexOf('{', inicioLocation);
        assertThat(apertura).isGreaterThan(-1);
        int nivel = 0;
        for (int i = apertura; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '{') {
                nivel++;
            } else if (c == '}') {
                nivel--;
                if (nivel == 0) {
                    return texto.substring(apertura, i + 1);
                }
            }
        }
        return texto.substring(apertura);
    }

    private static List<String> proxySetHeaderDe(String bloque) {
        return bloque.lines()
                .map(String::trim)
                .filter(l -> l.startsWith("proxy_set_header"))
                .toList();
    }

    private static long contar(String texto, String regex) {
        Matcher m = Pattern.compile(regex).matcher(texto);
        long n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
