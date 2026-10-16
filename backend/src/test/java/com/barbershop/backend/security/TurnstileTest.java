package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.barbershop.backend.dto.LoginRequest;
import com.barbershop.backend.exception.BotChallengeException;
import com.barbershop.backend.mapper.TrabajadorMapper;
import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.SesionAuthRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec bot-login-protection — CAPTCHA Turnstile opcional OFF por defecto
 * (tarea 5.5). Complementa {@code BotProtectionSmokeTest} (que cubre el 403
 * con token inválido y la tolerancia del campo ausente) verificando que
 * deshabilitado NO se realiza NINGUNA llamada de verificación a terceros
 * (verificador mockeable, sin red), que el rechazo ocurre ANTES de
 * autenticar (Mockito a nivel de servicio) y que el campo {@code turnstileToken}
 * no lo exige {@code @Valid}. El token inválido usa el mock de siteverify
 * ({@code verificadorTurnstile}), nunca la red real.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TurnstileTest {

    private static final String PASSWORD = "TurnstileTest12345";

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
    void aislamiento() {
        rateLimiter.limpiarContadores();
        emailValido = "turnstile-" + UUID.randomUUID().toString().substring(0, 8)
                + "@barbershop.test";
        trabajadorRepository.save(Trabajador.builder()
                .nombre("Turnstile Test")
                .email(emailValido)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(Rol.WORKER)
                .build());
    }

    @AfterEach
    void restaurar() {
        botProtection.turnstileActivo = false;
        botProtection.verificadorTurnstile = null;
        rateLimiter.limpiarContadores();
    }

    @Test
    void deshabilitadoPorDefectoNoRealizaNingunaLlamadaATerceros() throws Exception {
        // Verificador que contaría cualquier llamada al siteverify: con el
        // mecanismo OFF por defecto NI EL VERIFICADOR se invoca — cero
        // dependencias de terceros en el flujo de login.
        AtomicInteger llamadas = new AtomicInteger();
        botProtection.verificadorTurnstile = token -> {
            llamadas.incrementAndGet();
            return true;
        };
        // turnstileActivo queda en su default false (application.properties)
        mockMvc.perform(login()
                        .content("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD
                                + "\",\"turnstileToken\":\"token-cualquiera\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        assertThat(llamadas.get()).isZero();
    }

    @Test
    void habilitadoConTokenInvalidoRechazaAntesDeAutenticar() throws Exception {
        botProtection.turnstileActivo = true;
        AtomicReference<String> tokenRecibido = new AtomicReference<>();
        botProtection.verificadorTurnstile = token -> {
            tokenRecibido.set(token);
            return false; // siteverify mockeado: token inválido, sin red real
        };
        mockMvc.perform(login()
                        .content("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD
                                + "\",\"turnstileToken\":\"token-invalido\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("BOT_CHALLENGE"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
        assertThat(tokenRecibido.get()).isEqualTo("token-invalido");

        // A nivel de servicio: la verificación fallida impide llegar a
        // authenticationManager (403 antes de verificar credenciales).
        AuthServiceStubs.AuthenticationManagerContador authenticationManager =
                AuthServiceStubs.authenticationManagerContador();
        BotProtectionService botReal = new BotProtectionService(new ObjectMapper());
        botReal.turnstileActivo = true;
        botReal.verificadorTurnstile = token -> false;
        AuthService authService = new AuthService(
                authenticationManager,
                new JwtService(),
                AuthServiceStubs.prohibirLlamadas(TrabajadorRepository.class),
                AuthServiceStubs.prohibirLlamadas(TrabajadorMapper.class),
                new AuthServiceStubs.RateLimiterEspia(), // buckets intactos (no se tocan en este camino)
                botReal,
                AuthServiceStubs.prohibirLlamadas(SesionAuthRepository.class));
        assertThatThrownBy(() -> authService.login(LoginRequest.builder()
                .email(emailValido).password(PASSWORD).turnstileToken("token-invalido").build()))
                .isInstanceOf(BotChallengeException.class);
        assertThat(authenticationManager.llamadas())
                .as("el 403 de Turnstile debe ocurrir ANTES de verificar credenciales")
                .isZero();
    }

    @Test
    void habilitadoConTokenValidoSigueFuncionando() throws Exception {
        botProtection.turnstileActivo = true;
        botProtection.verificadorTurnstile = token -> "token-valido".equals(token);
        mockMvc.perform(login()
                        .content("{\"email\":\"" + emailValido + "\",\"password\":\"" + PASSWORD
                                + "\",\"turnstileToken\":\"token-valido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    void elCampoTurnstileTokenNoEsExigidoPorValidacion() {
        // Contrato @Valid del DTO: el campo es OPCIONAL — un body sin él (ni
        // honeypot) supera la validación Bean Validation sin una sola
        // violación; un cliente que no lo envía nunca recibe 400.
        try (jakarta.validation.ValidatorFactory factory =
                     Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            LoginRequest sinCampo = LoginRequest.builder()
                    .email("api@barbershop.test").password(PASSWORD).build();
            Set<jakarta.validation.ConstraintViolation<LoginRequest>> fallos =
                    validator.validate(sinCampo);
            assertThat(fallos).isEmpty();

            LoginRequest enBlanco = LoginRequest.builder()
                    .email("api@barbershop.test").password(PASSWORD)
                    .turnstileToken("").website("").build();
            assertThat(validator.validate(enBlanco)).isEmpty();
        }
    }

    // ===================== helpers =====================

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login() {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "Mozilla/5.0 (TurnstileTest)");
    }
}
