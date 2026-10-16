package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.barbershop.backend.dto.LoginRequest;
import com.barbershop.backend.mapper.TrabajadorMapper;
import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.SesionAuthRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Spec bot-login-protection — batería completa del honeypot y huella de
 * fingerprint (tarea 5.4). Complementa {@code BotProtectionSmokeTest} SOLO en
 * lo que ese smoke no cubre: la auditoría motivo {@code HONEYPOT}, la no
 * invocación de {@code AuthenticationManager} (dobladajes manuales, sin
 * Mockito — ver {@code AuthServiceStubs}) y
 * el escenario "sin User-Agent con reto OFF → 200 con {@code suspect_ua=true}
 * en la auditoría". Los escenarios de compatibilidad (honeypot ausente → 200,
 * reto OFF/ON, reto con UA ausente, Turnstile) ya los cubre el smoke test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class BotHoneypotTest {

    private static final String PASSWORD = "BotHoneypotTest12345";
    private static final String UA = "Mozilla/5.0 (HoneypotTest)";

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
    private int baseIp;

    @BeforeEach
    void aislamiento() {
        rateLimiter.limpiarContadores();
        baseIp = 110 + (int) (Math.random() * 20);
        emailValido = "honeypot-" + UUID.randomUUID().toString().substring(0, 8)
                + "@barbershop.test";
        trabajadorRepository.save(Trabajador.builder()
                .nombre("Bot Honeypot Test")
                .email(emailValido)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(Rol.WORKER)
                .build());
        // Sin esperas artificiales: la demora del honeypot a 0 en tests
        botProtection.demoraHoneypotMs = 0L;
    }

    @AfterEach
    void restaurar() {
        botProtection.honeypotActivo = true;
        botProtection.demoraHoneypotMs = 1000L;
        botProtection.retoActivo = false;
        botProtection.turnstileActivo = false;
        botProtection.verificadorTurnstile = null;
        rateLimiter.limpiarContadores();
    }

    @Test
    void honeypotConCredencialesValidasRechazaSinTokensYAuditaHoneypot(CapturedOutput output)
            throws Exception {
        String ip = ip(50);
        // Credenciales VÁLIDAS + honeypot relleno → 401 genérico, sin tokens
        mockMvc.perform(login(PASSWORD)
                        .with(xRealIp(ip))
                        .header("User-Agent", UA)
                        .content("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD
                                + "\",\"website\":\"http://bot.example\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());

        // La auditoría registra el motivo real HONEYPOT (indistinguible para
        // el bot, accionable para el operador) con la IP del origen
        String[] lineas = output.toString().lines()
                .filter(l -> l.contains("event=login_failed") && l.contains(emailValido))
                .toArray(String[]::new);
        assertThat(lineas).hasSize(1);
        assertThat(lineas[0]).contains("reason=HONEYPOT").contains("ip=" + ip);
    }

    @Test
    void elHoneypotNoLlamaAlAuthenticationManagerNiAlRateLimiter() {
        // Doblajes manuales (sin Mockito): con honeypot relleno, AuthService
        // sale por el 401 ANTES de tocar credenciales (sin BCrypt) y ANTES de
        // los buckets de rate limit (sin consumir presupuesto legítimo).
        AuthServiceStubs.AuthenticationManagerContador authenticationManager =
                AuthServiceStubs.authenticationManagerContador();
        AuthServiceStubs.RateLimiterEspia rateLimiterEspia = new AuthServiceStubs.RateLimiterEspia();
        BotProtectionService botReal = new BotProtectionService(new ObjectMapper());
        botReal.honeypotActivo = true;
        botReal.demoraHoneypotMs = 0L;
        AuthService authService = new AuthService(
                authenticationManager,
                new JwtService(),
                AuthServiceStubs.prohibirLlamadas(TrabajadorRepository.class),
                AuthServiceStubs.prohibirLlamadas(TrabajadorMapper.class),
                rateLimiterEspia,
                botReal,
                AuthServiceStubs.prohibirLlamadas(SesionAuthRepository.class));

        assertThatThrownBy(() -> authService.login(LoginRequest.builder()
                .email("bot@barbershop.test")
                .password(PASSWORD) // contraseña correcta: irrelevant siquiera mirarla
                .website("http://spam.example")
                .build()))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(authenticationManager.llamadas())
                .as("el honeypot debe rechazar ANTES de verificar la contraseña")
                .isZero();
        assertThat(rateLimiterEspia.comprobaciones.get())
                .as("el honeypot no debe tocar los buckets de rate limit")
                .isZero();
        assertThat(rateLimiterEspia.fallos.get()).isZero();
        assertThat(rateLimiterEspia.resets.get()).isZero();
    }

    @Test
    void sinUserAgentConRetoDeshabilitadoDevuelve200MarcandoSuspectUa(CapturedOutput output)
            throws Exception {
        // Reto OFF (default) y petición SIN User-Agent: el fingerprint observa
        // pero NO bloquea — 200 con tokens…
        LoggingSystem.get(getClass().getClassLoader()).setLogLevel("com.barbershop.backend", LogLevel.INFO);
        try {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD + "\"}")
                            .with(xRealIp(ip(60))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        } finally {
            LoggingSystem.get(getClass().getClassLoader()).setLogLevel("com.barbershop.backend", null);
        }
        // …y la auditoría del acierto marca la señal suspect_ua=true
        String[] ok = output.toString().lines()
                .filter(l -> l.contains("event=login_ok") && l.contains(emailValido))
                .toArray(String[]::new);
        assertThat(ok).hasSize(1);
        assertThat(ok[0]).contains("suspect_ua=true");
    }

    // ===================== helpers =====================

    /** Login por defecto (sin honeypot, con UA); el body puede sobreponerse. */
    private MockHttpServletRequestBuilder login(String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", UA)
                .content("{\"email\":\"" + emailValido + "\",\"password\":\"" + password + "\"}");
    }

    private String ip(int n) {
        return "203.0." + baseIp + "." + n;
    }

    private static RequestPostProcessor xRealIp(String ip) {
        return request -> {
            request.addHeader("X-Real-IP", ip);
            return request;
        };
    }
}
