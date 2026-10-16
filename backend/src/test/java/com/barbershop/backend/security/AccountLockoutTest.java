package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.barbershop.backend.dto.LoginRequest;
import com.barbershop.backend.exception.TooManyRequestsException;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Spec account-lockout (D3/D4) a nivel HTTP — {@code POST /api/auth/login}:
 * lockout por cuenta SIN la IP en la clave (tarea 5.1) y backoff exponencial
 * con el reloj inyectable {@code RateLimiterService.reloj}, SIN ningún
 * {@code Thread.sleep} (tarea 5.2).
 *
 * <p>Patrón D8 "un contexto, datos aislados por unicidad": emails únicos con
 * {@code UUID}, {@code limpiarContadores()} en {@code @BeforeEach}/
 * {@code @AfterEach}, IP única por método (cabecera {@code X-Real-IP}) y
 * reloj restaurado tras cada método. Los tests de backoff registran los
 * fallos directamente vía {@code registrarFalloLogin} (mismo paquete) cuando
 * el intento HTTP ya responde 429 — el check de lockout ocurre ANTES de
 * autenticar, así que un intento bloqueado no puede incrementar el bucket; la
 * fórmula de extensión se verifica entonces sobre la respuesta HTTP real.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountLockoutTest {

    private static final String PASSWORD = "AccountLockoutTest12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimiterService rateLimiter;

    /** Reloj controlable por test: se inyecta en el bean compartido y se restaura. */
    private final AtomicLong reloj = new AtomicLong(System.currentTimeMillis());

    private String emailVictima;
    private int baseIp;

    @BeforeEach
    void aislamiento() {
        rateLimiter.limpiarContadores();
        reloj.set(System.currentTimeMillis());
        rateLimiter.reloj = reloj::get;
        // Tercer octeto propio del método: todas sus peticiones se quedan en un
        // rango de IP que ningún otro test usa.
        baseIp = 1 + (int) (Math.random() * 200);
        emailVictima = "lockout-" + UUID.randomUUID().toString().substring(0, 8)
                + "@barbershop.test";
        trabajadorRepository.save(Trabajador.builder()
                .nombre("Account Lockout Test")
                .email(emailVictima)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(Rol.WORKER)
                .build());
    }

    @AfterEach
    void restaurar() {
        rateLimiter.reloj = System::currentTimeMillis;
        rateLimiter.limpiarContadores();
    }

    // ===================== 5.1 — lockout por cuenta =====================

    @Test
    void rotarIpsNoEvitaElLockoutPorCuenta() throws Exception {
        // 5 fallos con X-Real-IP ROTANTE: ningún bucket email+IP ni ninguna IP
        // individual superan su propio límite; solo el bucket por cuenta (sin
        // IP) acumula 5.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(emailVictima, "mala-" + i).with(xRealIp(ip(i))))
                    .andExpect(status().isUnauthorized());
        }
        // 6.º intento desde una IP NUEVA y con la contraseña CORRECTA: sigue
        // siendo 429 — rotar IPs ya no ayuda. Sin tokens y sin verificar
        // credenciales (la comprobación es anterior a authenticate).
        mockMvc.perform(login(emailVictima, PASSWORD).with(xRealIp(ip(250))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    void elBloqueoAfectaSoloALaCuentaObjetivo() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(emailVictima, "mala-" + i).with(xRealIp(ip(i))))
                    .andExpect(status().isUnauthorized());
        }
        // Otra cuenta no bloqueada → 200 con tokens: el lockout es por cuenta,
        // no global.
        String emailOtra = "lockout-otra-" + UUID.randomUUID().toString().substring(0, 8)
                + "@barbershop.test";
        trabajadorRepository.save(Trabajador.builder()
                .nombre("Cuenta Sana")
                .email(emailOtra)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .rol(Rol.WORKER)
                .build());
        mockMvc.perform(login(emailOtra, PASSWORD).with(xRealIp(ip(90))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void cuatroFallosMasCredencialesValidasDan200YReseteanElContador() throws Exception {
        // 4 fallos (< umbral 5) con IPs rotantes…
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(login(emailVictima, "mala-" + i).with(xRealIp(ip(i))))
                    .andExpect(status().isUnauthorized());
        }
        // …y credenciales válidas → 200 con tokens.
        mockMvc.perform(login(emailVictima, PASSWORD).with(xRealIp(ip(80))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // Si el acierto NO hubiera reseteado el contador (4+4=8 ≥ umbral), este
        // segundo ciclo terminaría bloqueado: el 200 final demuestra el reset.
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(login(emailVictima, "otra-mala-" + i).with(xRealIp(ip(10 + i))))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(emailVictima, PASSWORD).with(xRealIp(ip(81))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void ipQueSuperaSuLimitePreexistenteSigueRespondiendoTooManyRequests() throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String ipFija = ip(5);
        // 20 fallos desde MISMA IP con 20 emails distintos: ninguna cuenta
        // alcanza el umbral de lockout (1 fallo cada una), pero la IP llega a
        // su límite preexistente (20/15 min).
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(login("rl-pre-" + sufijo + "-" + i + "@barbershop.test", "mala")
                            .with(xRealIp(ipFija)))
                    .andExpect(status().isUnauthorized());
        }
        // 21.º → el bucket por IP preexistente responde 429 con su CÓDIGO
        // original: el lockout por cuenta lo complementa sin alterarlo.
        mockMvc.perform(login("rl-pre-" + sufijo + "-final@barbershop.test", "mala")
                        .with(xRealIp(ipFija)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void emailNoRegistradoAcumulaIgualSinOraculoDeEnumeracion() throws Exception {
        String fantasma = "ghost-" + UUID.randomUUID().toString().substring(0, 8)
                + "@barbershop.test";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(fantasma, "mala-" + i).with(xRealIp(ip(i))))
                    .andExpect(status().isUnauthorized());
        }
        MvcResult fantasma429 = mockMvc.perform(login(fantasma, "cualquiera").with(xRealIp(ip(90))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"))
                .andReturn();

        // Misma situación con una cuenta REAL bloqueada…
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(emailVictima, "mala-" + i).with(xRealIp(ip(30 + i))))
                    .andExpect(status().isUnauthorized());
        }
        MvcResult real429 = mockMvc.perform(login(emailVictima, "cualquiera").with(xRealIp(ip(91))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"))
                .andReturn();

        // …y el mensaje/cuerpo es IDÉNTICO: el lockout no revela si la cuenta
        // existe (sin oráculo de enumeración).
        ObjectMapper mapper = new ObjectMapper();
        String cuerpoFantasma = fantasma429.getResponse().getContentAsString();
        String cuerpoReal = real429.getResponse().getContentAsString();
        String mensajeFantasma = mapper.readTree(cuerpoFantasma).path("message").asText();
        String mensajeReal = mapper.readTree(cuerpoReal).path("message").asText();
        assertThat(mensajeFantasma).isEqualTo(mensajeReal);
        assertThat(cuerpoFantasma).isEqualTo(cuerpoReal);
    }

    @Test
    void elBloqueoRespondeAntesDeVerificarCredenciales() {
        // Nivel de servicio (sin Spring): AuthService debe lanzar el 429 del
        // lockout sin llegar a authenticationManager (sin BCrypt, sin
        // timing-oracle, sin tokens) — aun con la contraseña CORRECTA.
        AuthServiceStubs.AuthenticationManagerContador authenticationManager =
                AuthServiceStubs.authenticationManagerContador();
        AuthServiceStubs.RateLimiterEspia rateLimiterEspia =
                new AuthServiceStubs.RateLimiterEspia();
        rateLimiterEspia.excepcionAlComprobar = new TooManyRequestsException(
                "Demasiados intentos. Inténtelo de nuevo más tarde.", 900,
                TooManyRequestsException.CODIGO_ACCOUNT_LOCKED);
        AuthService authService = new AuthService(
                authenticationManager,
                new JwtService(),
                AuthServiceStubs.prohibirLlamadas(TrabajadorRepository.class),
                AuthServiceStubs.prohibirLlamadas(TrabajadorMapper.class),
                rateLimiterEspia,
                new BotProtectionService(new ObjectMapper()),
                AuthServiceStubs.prohibirLlamadas(SesionAuthRepository.class));

        TooManyRequestsException e = org.junit.jupiter.api.Assertions.assertThrows(
                TooManyRequestsException.class,
                () -> authService.login(LoginRequest.builder()
                        .email("bloqueada@barbershop.test")
                        .password(PASSWORD)
                        .build()));
        assertThat(e.getCodigo()).isEqualTo(TooManyRequestsException.CODIGO_ACCOUNT_LOCKED);
        assertThat(authenticationManager.llamadas())
                .as("el lockout debe responder ANTES de verificar credenciales")
                .isZero();
        assertThat(rateLimiterEspia.fallos.get())
                .as("un rechazo bloqueado no debe registrar fallo nuevo")
                .isZero();
    }

    // ===================== 5.2 — backoff con reloj inyectable (sin sleep) =====================

    @Test
    void cadaFalloAdicionalReArmaLaVentanaA1800Y3600Segundos() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(emailVictima, "mala-" + i).with(xRealIp(ip(i))))
                    .andExpect(status().isUnauthorized());
        }
        // 5 fallos → ventana base: Retry-After 900 s
        mockMvc.perform(login(emailVictima, "mala-5").with(xRealIp(ip(60))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"));

        // 6.º fallo DURANTE el bloqueo (rechazado por el check, registrado
        // directamente en el bucket) → re-armala a 30 min
        rateLimiter.registrarFalloLogin(emailVictima, ip(61));
        mockMvc.perform(login(emailVictima, "mala-6").with(xRealIp(ip(62))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1800"));

        // 7.º fallo → 1 h: Retry-After estrictamente creciente
        rateLimiter.registrarFalloLogin(emailVictima, ip(63));
        mockMvc.perform(login(emailVictima, "mala-7").with(xRealIp(ip(64))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));
    }

    @Test
    void laVentanaDeLockoutNuncaSuperaElTopeMaximo() throws Exception {
        // Fallos continuados muy por encima del umbral (n grande)
        for (int i = 0; i < 30; i++) {
            rateLimiter.registrarFalloLogin(emailVictima, ip(70 + (i % 20)));
        }
        // La ventana efectiva se topa en lockout.max-seconds (86400 = 24 h)
        mockMvc.perform(login(emailVictima, "mala").with(xRealIp(ip(90))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "86400"))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));
    }

    @Test
    void retryAfterSonLosSegundosRestantesYDecreceConElTiempo() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(emailVictima, "mala-" + i).with(xRealIp(ip(i))))
                    .andExpect(status().isUnauthorized());
        }
        rateLimiter.registrarFalloLogin(emailVictima, ip(61));
        rateLimiter.registrarFalloLogin(emailVictima, ip(62)); // ventana 3600 s

        mockMvc.perform(login(emailVictima, "mala").with(xRealIp(ip(90))))
                .andExpect(header().string("Retry-After", "3600"));

        // El reloj avanza SIN dormir: Retry-After = segundos restantes
        reloj.addAndGet(60_000L);
        mockMvc.perform(login(emailVictima, "mala").with(xRealIp(ip(91))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3540"));

        reloj.addAndGet(300_000L);
        mockMvc.perform(login(emailVictima, "mala").with(xRealIp(ip(92))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3240"));
    }

    @Test
    void loginValidoReiniciaElCicloConLaVentanaBase() throws Exception {
        // 4 fallos (sin bloqueo) → acierto → 200 y reset del bucket de cuenta
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(login(emailVictima, "mala-" + i).with(xRealIp(ip(i))))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(emailVictima, PASSWORD).with(xRealIp(ip(80))))
                .andExpect(status().isOk());

        // El siguiente ciclo empieza DE NUEVO con la ventana base: si el reset
        // no hubiera funcionado, el contador acumulado produciría una ventana
        // extendida (> 900 s) en vez de los 900 s base.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(emailVictima, "ciclo2-mala-" + i).with(xRealIp(ip(100 + i))))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(emailVictima, "ciclo2-intento").with(xRealIp(ip(90))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"));
    }

    @Test
    void laVentanaSinActividadExpiraYVuelveASerPosibleElLogin() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(emailVictima, "mala-" + i).with(xRealIp(ip(i))))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(emailVictima, PASSWORD).with(xRealIp(ip(90))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"));

        // Sin peticiones durante la ventana efectiva (reloj desplazado, sin
        // dormir): el ventaneado en memoria resetea el bucket → 200 con tokens
        reloj.addAndGet(901_000L);
        mockMvc.perform(login(emailVictima, PASSWORD).with(xRealIp(ip(91))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    // ===================== helpers =====================

    private MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    /** IP del método: tercer octeto propio + cuarto octeto distinto por petición. */
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
