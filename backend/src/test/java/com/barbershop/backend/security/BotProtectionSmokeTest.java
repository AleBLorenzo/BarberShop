package com.barbershop.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.TrabajadorRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;

/**
 * Verificación mínima de las fases 3 y 4 (protección anti-bot y auditoría de
 * login) del cambio harden-sql-injection-anti-bot. La batería completa es la
 * Fase 5 (BotHoneypotTest/FailedLoginAuditTest); este smoke test deja
 * comprobado el cableado sin sleeps, con emails únicos UUID y los flags de
 * BotProtectionService sobrescritos directamente (mismo paquete, un solo
 * contexto Spring — patrón D8).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class BotProtectionSmokeTest {

    private static final String PASSWORD = "BotSmokeTest12345";
    private static final String UA = "Mozilla/5.0 (SmokeTest)";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimiterService rateLimiter;

    @Autowired
    private BotProtectionService botProtection;

    private String emailValido;

    @BeforeEach
    void crearUsuario() {
        emailValido = "smoke-" + UUID.randomUUID().toString().substring(0, 8) + "@barbershop.test";
        trabajadorRepository.save(Trabajador.builder()
                .nombre("Bot Smoke Test")
                .email(emailValido)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(Rol.WORKER)
                .build());
        rateLimiter.limpiarContadores();
        // Sin esperas artificiales ni sleeps: la demora del honeypot a 0 en tests
        botProtection.demoraHoneypotMs = 0L;
    }

    @AfterEach
    void restaurarFlags() {
        // Defaults de producción: honeypot ON, reto/Turnstile OFF, verificador real
        botProtection.honeypotActivo = true;
        botProtection.demoraHoneypotMs = 1000L;
        botProtection.retoActivo = false;
        botProtection.turnstileActivo = false;
        botProtection.verificadorTurnstile = null;
        rateLimiter.limpiarContadores();
    }

    // --- 3.1 Honeypot ---

    @Test
    void honeypotRellenoConCredencialesValidasDevuelve401SinTokens() throws Exception {
        mockMvc.perform(login("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD
                        + "\",\"website\":\"http://bot.example\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void honeypotNoConsumeBucketsNiRompeAlUsuarioReal() throws Exception {
        // 5 rechazos por honeypot (el umbral del lockout): si consumieran
        // buckets, el login posterior fallaría con 429 en vez de 200
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD
                            + "\",\"website\":\"bot-" + i + "\"}"))
                    .andExpect(status().isUnauthorized());
        }
        // Campo AUSENTE (tolerado, clientes API legítimos) → 200 con tokens
        mockMvc.perform(login("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    // --- 3.4/3.6 Reto por cabecera ---

    @Test
    void retoDeshabilitadoPorDefectoNoExigeNada() throws Exception {
        // Sin X-Login-Challenge y sin UA: con el reto OFF nada se rechaza
        mockMvc.perform(login("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void retoHabilitadoSinCabeceraResponde403() throws Exception {
        botProtection.retoActivo = true;
        mockMvc.perform(login("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD + "\"}")
                        .header("User-Agent", UA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("BOT_CHALLENGE"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void retoHabilitadoConCabeceraValidaYUaSuperaElReto() throws Exception {
        botProtection.retoActivo = true;
        long minuto = System.currentTimeMillis() / 60_000L;
        mockMvc.perform(login("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD + "\"}")
                        .header("User-Agent", UA)
                        .header("X-Login-Challenge", BotProtectionService.retoEsperado(emailValido, minuto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void retoHabilitadoConValorBasuraResponde403() throws Exception {
        botProtection.retoActivo = true;
        mockMvc.perform(login("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD + "\"}")
                        .header("User-Agent", UA)
                        .header("X-Login-Challenge", "valor-basura"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("BOT_CHALLENGE"));
    }

    @Test
    void retoHabilitadoSinUserAgentResponde403() throws Exception {
        botProtection.retoActivo = true;
        long minuto = System.currentTimeMillis() / 60_000L;
        // Cabecera de reto VÁLIDA pero sin UA: con el reto ON, la ausencia
        // total de UA cuenta como fallo de reto (3.6)
        mockMvc.perform(login("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD + "\"}")
                        .header("X-Login-Challenge", BotProtectionService.retoEsperado(emailValido, minuto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("BOT_CHALLENGE"));
    }

    // --- 3.7 Turnstile ---

    @Test
    void turnstileHabilitadoConTokenInvalidoResponde403AntesDeAuth() throws Exception {
        botProtection.turnstileActivo = true;
        botProtection.verificadorTurnstile = token -> false;
        mockMvc.perform(login("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD
                        + "\",\"turnstileToken\":\"token-invalido\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("BOT_CHALLENGE"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void turnstileSinTokenEnElBodyEsTolerado() throws Exception {
        botProtection.turnstileActivo = true;
        botProtection.verificadorTurnstile = token -> false;
        // El campo NUNCA es exigido por @Valid: sin él, el login normal funciona
        mockMvc.perform(login("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void turnstileDeshabilitadoIgnoraElToken() throws Exception {
        // OFF por defecto: con token presente pero disabled, ni widget ni
        // llamadas a terceros — el login no se ve afectado
        mockMvc.perform(login("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD
                        + "\",\"turnstileToken\":\"cualquiera\"}"))
                .andExpect(status().isOk());
    }

    // --- 4.2-4.4 Auditoría ---

    @Test
    void falloEmiteUnaUnicaLineaWarnConElFormatoKeyValue(CapturedOutput output) throws Exception {
        String ip = "203.0.113.77";
        mockMvc.perform(login("{\"email\":\"" + emailValido + "\",\"password\":\"mala\"}")
                        .with(xRealIp(ip)))
                .andExpect(status().isUnauthorized());

        String[] lineas = output.toString().lines()
                .filter(l -> l.contains("event=login_failed") && l.contains(emailValido))
                .toArray(String[]::new);
        org.junit.jupiter.api.Assertions.assertEquals(1, lineas.length,
                "debe haber EXACTAMENTE UNA línea event=login_failed por intento fallido");
        org.junit.jupiter.api.Assertions.assertTrue(lineas[0].contains("ip=" + ip), lineas[0]);
        org.junit.jupiter.api.Assertions.assertTrue(lineas[0].contains("reason=BAD_CREDENTIALS"), lineas[0]);
        org.junit.jupiter.api.Assertions.assertTrue(lineas[0].contains("suspect_ua="), lineas[0]);
        org.junit.jupiter.api.Assertions.assertTrue(lineas[0].contains("path=/api/auth/login"), lineas[0]);
        org.junit.jupiter.api.Assertions.assertTrue(lineas[0].contains("ua="), lineas[0]);
    }

    @Test
    void loginExitosoEmiteLoginOkSinLoginFailed(CapturedOutput output) throws Exception {
        LoggingSystem.get(getClass().getClassLoader()).setLogLevel("com.barbershop.backend", LogLevel.INFO);
        try {
            mockMvc.perform(login("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD + "\"}")
                            .header("User-Agent", UA))
                    .andExpect(status().isOk());
        } finally {
            LoggingSystem.get(getClass().getClassLoader()).setLogLevel("com.barbershop.backend", null);
        }
        org.junit.jupiter.api.Assertions.assertTrue(
                output.toString().contains("event=login_ok email=" + emailValido),
                "el login exitoso debe registrar event=login_ok con el email");
        org.junit.jupiter.api.Assertions.assertFalse(
                output.toString().lines().anyMatch(l -> l.contains("event=login_failed") && l.contains(emailValido)),
                "el login exitoso NO debe emitir event=login_failed");
    }

    // --- helpers ---

    private static MockHttpServletRequestBuilder login(String body) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static RequestPostProcessor xRealIp(String ip) {
        return request -> {
            request.addHeader("X-Real-IP", ip);
            return request;
        };
    }
}
