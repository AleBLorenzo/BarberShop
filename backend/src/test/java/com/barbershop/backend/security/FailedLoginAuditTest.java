package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.service.AuthService;

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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Spec failed-login-audit (D6, tarea 5.6): auditoría {@code key=value} de
 * cada intento de login con {@code OutputCaptureExtension}. Complementa el
 * smoke test (1 fallo → 1 línea y login_ok) con la cobertura completa de la
 * spec: línea exacta con timestamp y todos los campos, 7 fallos → 7 líneas,
 * los 4 motivos de rechazo, greps por motivo/email/ip, {@code suspect_ua=true}
 * sin UA y contrato HTTP idéntico con la auditoría activada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class FailedLoginAuditTest {

    private static final String UA = "Mozilla/5.0 (AuditTest)";

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

    @Autowired
    private AuthService authService;

    private String emailValido;
    private int baseIp;
    private final AtomicInteger secuenciaIp = new AtomicInteger(10);

    @BeforeEach
    void aislamiento() {
        rateLimiter.limpiarContadores();
        botProtection.demoraHoneypotMs = 0L;
        baseIp = 60 + (int) (Math.random() * 40);
        secuenciaIp.set(10);
        emailValido = "audit-" + UUID.randomUUID().toString().substring(0, 8)
                + "@barbershop.test";
        trabajadorRepository.save(Trabajador.builder()
                .nombre("Failed Login Audit Test")
                .email(emailValido)
                .passwordHash(passwordEncoder.encode("AuditTest12345"))
                .activo(true)
                .rol(Rol.WORKER)
                .build());
        ReflectionTestUtils.setField(authService, "auditoriaLoginActiva", true);
    }

    @AfterEach
    void restaurar() {
        botProtection.honeypotActivo = true;
        botProtection.demoraHoneypotMs = 1000L;
        botProtection.retoActivo = false;
        botProtection.turnstileActivo = false;
        botProtection.verificadorTurnstile = null;
        ReflectionTestUtils.setField(authService, "auditoriaLoginActiva", true);
        rateLimiter.limpiarContadores();
    }

    // ===================== Formato de la línea =====================

    @Test
    void unFalloEmiteExactamenteUnaLineaWarnCompleta(CapturedOutput output) throws Exception {
        String ip = ip();
        mockMvc.perform(loginConUa(emailValido, "password-mala", ip))
                .andExpect(status().isUnauthorized());

        List<String> lineas = lineasLoginFailed(output).toList();
        assertThat(lineas).hasSize(1);
        String linea = lineas.get(0);
        // Timestamp del patrón por defecto de Spring Boot (logback sin config nueva)
        assertThat(linea).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
        assertThat(linea).contains("event=login_failed");
        assertThat(linea).contains("email=" + emailValido);
        assertThat(linea).contains("ip=" + ip);
        assertThat(linea).contains("reason=BAD_CREDENTIALS");
        assertThat(linea).contains("suspect_ua=false");
        assertThat(linea).contains("path=/api/auth/login");
        assertThat(linea).contains("ua=\"" + UA + "\"");
    }

    @Test
    void sieteFallosEmitenSieteLineasUnaPorIntento(CapturedOutput output) throws Exception {
        for (int i = 0; i < 7; i++) {
            mockMvc.perform(loginConUa(emailValido, "mala-" + i, ip()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .isIn(401, 429)); // 6.º/7.º ya bloqueados por el lockout
        }
        List<String> lineas = lineasLoginFailed(output).toList();
        assertThat(lineas).hasSize(7); // UNA línea por intento, ni más ni menos
        long badCredentials = lineas.stream().filter(l -> l.contains("reason=BAD_CREDENTIALS")).count();
        long accountLocked = lineas.stream().filter(l -> l.contains("reason=ACCOUNT_LOCKED")).count();
        assertThat(badCredentials).isEqualTo(5);
        assertThat(accountLocked).isEqualTo(2);
        // Emails e IPs correspondientes a cada intento
        assertThat(lineas).allMatch(l -> l.contains("email=" + emailValido));
        assertThat(lineas.stream().map(FailedLoginAuditTest::ipDe).distinct()).hasSize(7);
    }

    // ===================== Motivos de rechazo =====================

    @Test
    void losCuatroMotivosDeRechazoSeAuditan(CapturedOutput output) throws Exception {
        // HONEYPOT: rechazo silencioso 401 antes de credenciales
        String ipHoneypot = ip();
        mockMvc.perform(loginConUa(emailValido, "AuditTest12345", ipHoneypot)
                        .content("{\"email\":\"" + emailValido + "\",\"password\":\"AuditTest12345\","
                                + "\"website\":\"http://bot.example\"}"))
                .andExpect(status().isUnauthorized());

        // CHALLENGE_FAILED: reto habilitado, cabecera ausente → 403
        botProtection.retoActivo = true;
        String ipReto = ip();
        mockMvc.perform(loginConUa(emailValido, "AuditTest12345", ipReto))
                .andExpect(status().isForbidden());
        botProtection.retoActivo = false;

        // ACCOUNT_LOCKED: 5 fallos + 6.º intento → 429
        String emailBloqueada = "audit-lock-" + UUID.randomUUID().toString().substring(0, 8)
                + "@barbershop.test";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(loginConUa(emailBloqueada, "mala-" + i, ip()))
                    .andExpect(status().isUnauthorized());
        }
        String ipBloqueo = ip();
        mockMvc.perform(loginConUa(emailBloqueada, "mala", ipBloqueo))
                .andExpect(status().isTooManyRequests());

        // RATE_LIMITED: IP sobre su límite preexistente (20/15 min) → 429
        String ipLimitada = ip();
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(loginConUa("audit-ip-" + sufijo + "-" + i + "@barbershop.test",
                            "mala", ipLimitada))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(loginConUa("audit-ip-" + sufijo + "-final@barbershop.test",
                        "mala", ipLimitada))
                .andExpect(status().isTooManyRequests());

        String todo = output.toString();
        assertThat(todo).contains("reason=HONEYPOT");
        assertThat(todo).contains("reason=CHALLENGE_FAILED");
        assertThat(todo).contains("reason=ACCOUNT_LOCKED");
        assertThat(todo).contains("reason=RATE_LIMITED");
        // Cada motivo con su IP de origen correspondiente
        assertThat(lineasLoginFailed(output).anyMatch(l ->
                l.contains("reason=HONEYPOT") && l.contains("ip=" + ipHoneypot) && l.contains(emailValido)))
                .as("línea HONEYPOT con la cuenta y la IP del intento").isTrue();
        assertThat(lineasLoginFailed(output).anyMatch(l ->
                l.contains("reason=CHALLENGE_FAILED") && l.contains("ip=" + ipReto)))
                .as("línea CHALLENGE_FAILED con la IP del origen").isTrue();
        assertThat(lineasLoginFailed(output).anyMatch(l ->
                l.contains("reason=ACCOUNT_LOCKED") && l.contains("ip=" + ipBloqueo)
                        && l.contains(emailBloqueada)))
                .as("línea ACCOUNT_LOCKED con la cuenta y la IP").isTrue();
        assertThat(lineasLoginFailed(output).anyMatch(l ->
                l.contains("reason=RATE_LIMITED") && l.contains("ip=" + ipLimitada)))
                .as("línea RATE_LIMITED con la IP limitada").isTrue();
    }

    // ===================== Grep-abilidad =====================

    @Test
    void losRegistrosSeFiltranPorMotivo(CapturedOutput output) throws Exception {
        // 3 fallos de credenciales + 1 rechazo por honeypot (motivos distintos)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(loginConUa(emailValido, "mala-" + i, ip()))
                    .andExpect(status().isUnauthorized());
        }
        String emailBot = "audit-bot-" + UUID.randomUUID().toString().substring(0, 8)
                + "@barbershop.test";
        mockMvc.perform(loginConUa(emailBot, "x", ip())
                        .content("{\"email\":\"" + emailBot + "\",\"password\":\"x\","
                                + "\"website\":\"bot\"}"))
                .andExpect(status().isUnauthorized());

        // Filtro por motivo: exactamente los intentos de ese motivo, y cada
        // línea con todos los campos event=/email=/ip=/reason=/path=
        List<String> porMotivo = lineasLoginFailed(output)
                .filter(l -> l.contains("reason=BAD_CREDENTIALS"))
                .toList();
        assertThat(porMotivo).hasSize(3);
        assertThat(porMotivo).allMatch(l -> l.contains("event=login_failed"));
        assertThat(porMotivo).allMatch(l -> l.contains("email=" + emailValido));
        assertThat(porMotivo).allMatch(l -> l.contains("ip="));
        assertThat(porMotivo).allMatch(l -> l.contains("path=/api/auth/login"));

        List<String> honeypot = lineasLoginFailed(output)
                .filter(l -> l.contains("reason=HONEYPOT"))
                .toList();
        assertThat(honeypot).hasSize(1);
        assertThat(honeypot.get(0)).contains("email=" + emailBot);
    }

    @Test
    void ataqueDistribuidoSobreUnaCuentaEsReconstruible(CapturedOutput output) throws Exception {
        String victima = "audit-victima-" + UUID.randomUUID().toString().substring(0, 8)
                + "@barbershop.test";
        // 10 intentos fallidos sobre la MISMA cuenta desde 10 IPs distintas
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(loginConUa(victima, "mala-" + i, ip()))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .isIn(401, 429));
        }
        // El operador filtra por email y reconstruye el ataque: 10 líneas,
        // cada una con su IP y su timestamp.
        List<String> delAtaque = lineasLoginFailed(output)
                .filter(l -> l.contains("email=" + victima))
                .toList();
        assertThat(delAtaque).hasSize(10);
        assertThat(delAtaque.stream().map(FailedLoginAuditTest::ipDe).distinct()).hasSize(10);
        assertThat(delAtaque).allMatch(l -> l.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"));
    }

    @Test
    void losRegistrosSeFiltranPorIp(CapturedOutput output) throws Exception {
        String ipOrigen = ip();
        // 3 intentos fallidos desde la MISMA IP con emails distintos
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(loginConUa("audit-ipgrep-" + i + "-" + UUID.randomUUID()
                            .toString().substring(0, 8) + "@barbershop.test", "mala", ipOrigen))
                    .andExpect(status().isUnauthorized());
        }
        // …y uno desde otra IP para asegurar el filtrado
        mockMvc.perform(loginConUa(emailValido, "mala", ip()))
                .andExpect(status().isUnauthorized());

        List<String> porIp = lineasLoginFailed(output)
                .filter(l -> l.contains("ip=" + ipOrigen + " "))
                .toList();
        assertThat(porIp).hasSize(3);
        assertThat(porIp).allMatch(l -> l.contains("reason=BAD_CREDENTIALS"));
    }

    // ===================== Fingerprint y contrato =====================

    @Test
    void lineaSinUserAgentIncluyeSuspectUaTrue(CapturedOutput output) throws Exception {
        // Petición SIN User-Agent: la línea marca la señal de fingerprint
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + emailValido + "\",\"password\":\"mala-sin-ua\"}")
                        .with(xRealIp(ip())))
                .andExpect(status().isUnauthorized());

        assertThat(lineasLoginFailed(output).anyMatch(l -> l.contains("suspect_ua=true")
                && l.contains("email=" + emailValido)))
                .as("la línea de un intento sin User-Agent debe marcar suspect_ua=true")
                .isTrue();
    }

    @Test
    void elContratoHttpNoCambiaConLaAuditoria(CapturedOutput output) throws Exception {
        // Respuesta CON auditoría activa
        String ipConAuditoria = ip();
        String cuerpoCon = mockMvc.perform(loginConUa(emailValido, "mala", ipConAuditoria))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(lineasLoginFailed(output).anyMatch(l -> l.contains("email=" + emailValido)))
                .isTrue();

        // Misma petición con la auditoría DESACTIVADA por env var: código y
        // cuerpo IDÉNTICOS (la auditoría solo añade la línea de log)
        ReflectionTestUtils.setField(authService, "auditoriaLoginActiva", false);
        String emailSinAuditoria = "audit-off-" + UUID.randomUUID().toString().substring(0, 8)
                + "@barbershop.test";
        trabajadorRepository.save(Trabajador.builder()
                .nombre("Audit Off")
                .email(emailSinAuditoria)
                .passwordHash(passwordEncoder.encode("AuditTest12345"))
                .activo(true)
                .rol(Rol.WORKER)
                .build());
        String cuerpoSin = mockMvc.perform(loginConUa(emailSinAuditoria, "mala", ip()))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpoSin).isEqualTo(cuerpoCon);
        assertThat(lineasLoginFailed(output).anyMatch(l -> l.contains("email=" + emailSinAuditoria)))
                .as("con la auditoría apagada NO debe emitirse la línea")
                .isFalse();
    }

    // ===================== helpers =====================

    private static Stream<String> lineasLoginFailed(CapturedOutput output) {
        return output.toString().lines().filter(l -> l.contains("event=login_failed"));
    }

    private static String ipDe(String linea) {
        int i = linea.indexOf(" ip=");
        int fin = linea.indexOf(' ', i + 4);
        return linea.substring(i + 4, fin < 0 ? linea.length() : fin);
    }

    private MockHttpServletRequestBuilder loginConUa(String email, String password, String ip) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", UA)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .with(xRealIp(ip));
    }

    /** IP única por intento dentro del rango propio del método. */
    private String ip() {
        return "198.51." + baseIp + "." + secuenciaIp.getAndIncrement();
    }

    private static RequestPostProcessor xRealIp(String ip) {
        return request -> {
            request.addHeader("X-Real-IP", ip);
            return request;
        };
    }
}
