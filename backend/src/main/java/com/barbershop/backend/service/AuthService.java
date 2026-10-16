package com.barbershop.backend.service;

import com.barbershop.backend.dto.LoginRequest;
import com.barbershop.backend.dto.LoginResponse;
import com.barbershop.backend.exception.BotChallengeException;
import com.barbershop.backend.exception.ResourceNotFoundException;
import com.barbershop.backend.exception.TooManyRequestsException;
import org.springframework.security.authentication.BadCredentialsException;
import com.barbershop.backend.mapper.TrabajadorMapper;
import com.barbershop.backend.model.SesionAuth;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.SesionAuthRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.security.BotProtectionService;
import com.barbershop.backend.security.JwtService;
import com.barbershop.backend.security.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TrabajadorRepository trabajadorRepository;
    private final TrabajadorMapper trabajadorMapper;
    private final RateLimiterService rateLimiter;
    private final BotProtectionService botProtection;
    private final SesionAuthRepository sesionAuthRepository;

    /**
     * Auditoría de fallos de login (D6, cambio harden-sql-injection-anti-bot):
     * activa por defecto; se apaga con {@code app.security.login.audit.enabled=false}
     * (env var, sin redeploy). Package-private para que los tests del paquete
     * la sobrescriban sin crear contextos nuevos.
     */
    @Value("${app.security.login.audit.enabled:true}")
    boolean auditoriaLoginActiva;

    /**
     * Login con protección anti-bot (D5) y auditoría de fallos (D6).
     *
     * <p>Orden de evaluación — cada salida que NO emite tokens emite EXACTAMENTE
     * UNA línea WARN {@code event=login_failed … reason=…} (auditoría):</p>
     * <ol>
     *   <li>Honeypot (PRIMERO, antes de buckets y credenciales): valor no
     *       vacío → demora artificial + 401 INVALID_CREDENTIALS genérico,
     *       sin AuthenticationManager ni buckets — motivo {@code HONEYPOT}.</li>
     *   <li>Reto por cabecera (si ON): ausente/inválido o UA ausente → 403
     *       BOT_CHALLENGE — motivo {@code CHALLENGE_FAILED}.</li>
     *   <li>Turnstile (si ON y token presente): inválido → 403 — motivo
     *       {@code CHALLENGE_FAILED}.</li>
     *   <li>Buckets de rate limit/lockout: 429 — motivos
     *       {@code ACCOUNT_LOCKED} o {@code RATE_LIMITED}.</li>
     *   <li>Autenticación: fallo → 401 — motivo {@code BAD_CREDENTIALS}.</li>
     * </ol>
     * <p>El login exitoso NO emite {@code login_failed}; emite INFO
     * {@code event=login_ok} con el email (contrapartida accionable).</p>
     */
    public LoginResponse login(LoginRequest request) {
        // IP real (proxy-aware) y señales de fingerprint (3.6): informativas,
        // viajan en la auditoría pero no rechazan por sí solas.
        final String ip = rateLimiter.ipReal();
        final String userAgent = botProtection.headerActual("User-Agent");
        final String contentType = botProtection.headerActual("Content-Type");
        final boolean suspectUa = botProtection.sospechosoFingerprint(userAgent, contentType);

        // 1) Honeypot ANTES de todo (3.1): sin AuthenticationManager ni buckets.
        //    El 401 genérico tras la demora es indistinguible de un fallo
        //    normal para el bot; el campo AUSENTE se tolera (clientes API).
        if (botProtection.honeypotActivo() && botProtection.honeypotRelleno(request.getWebsite())) {
            auditarFallo(request.getEmail(), "HONEYPOT", suspectUa, userAgent, ip);
            botProtection.esperarDemoraHoneypot();
            throw new BadCredentialsException("Credenciales inválidas");
        }

        // 2) Reto por cabecera (3.4): OFF por defecto; ON → 403 ANTES de
        //    credenciales y rate limiter, sin tokens.
        try {
            botProtection.comprobarReto(request.getEmail());
        } catch (BotChallengeException e) {
            auditarFallo(request.getEmail(), "CHALLENGE_FAILED", suspectUa, userAgent, ip);
            throw e;
        }

        // 3) Turnstile opcional (3.7): solo si ON y el token viene presente.
        try {
            botProtection.comprobarTurnstile(request.getTurnstileToken());
        } catch (BotChallengeException e) {
            auditarFallo(request.getEmail(), "CHALLENGE_FAILED", suspectUa, userAgent, ip);
            throw e;
        }

        // 4) Rate limit + lockout ANTES de autenticar (B-MED-5 / D4): el bucket
        // de lockout por cuenta (clave solo email, sin IP) y los buckets
        // email+IP/IP se comprueban antes de authenticationManager → una
        // cuenta bloqueada responde 429 + Retry-After + ACCOUNT_LOCKED sin
        // verificar BCrypt (sin timing-oracle) ni emitir tokens. Solo cuentan
        // FALLOS (registrarFalloLogin); acertar resetea los buckets de cuenta.
        try {
            rateLimiter.comprobarLogin(request.getEmail(), ip);
        } catch (TooManyRequestsException e) {
            String motivo = TooManyRequestsException.CODIGO_ACCOUNT_LOCKED.equals(e.getCodigo())
                    ? "ACCOUNT_LOCKED" : "RATE_LIMITED";
            auditarFallo(request.getEmail(), motivo, suspectUa, userAgent, ip);
            throw e;
        }

        // Authenticate
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (AuthenticationException e) {
            rateLimiter.registrarFalloLogin(request.getEmail(), ip);
            auditarFallo(request.getEmail(), "BAD_CREDENTIALS", suspectUa, userAgent, ip);
            throw e;
        }
        rateLimiter.resetearLogin(request.getEmail(), ip);

        // Generate tokens
        Trabajador trabajador = trabajadorRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado"));

        if (!trabajador.getActivo()) {
            throw new IllegalArgumentException("La cuenta está desactivada");
        }

        // Nueva sesión de autenticación (D2): su UUID es el claim que
        // llevan access y refresh; el filtro la valida en cada petición y
        // el logout la revoca.
        SesionAuth sesion = sesionAuthRepository.save(SesionAuth.builder()
                .id(UUID.randomUUID())
                .trabajador(trabajador)
                .creadaEn(LocalDateTime.now())
                .caducaEn(LocalDateTime.now().plusSeconds(jwtService.getRefreshExpiration() / 1000))
                .ultimoRefreshEn(LocalDateTime.now())
                .build());

        String accessToken = jwtService.generateToken(trabajador, sesion.getId());
        String refreshToken = jwtService.generateRefreshToken(trabajador, sesion.getId());

        // Contrapartida accionable de la auditoría de fallos (4.4): un login
        // exitoso se registra a INFO con el email, nunca como login_failed.
        // Lleva además suspect_ua (señal de fingerprint, 3.6): el operador ve
        // también en los aciertos las peticiones sin User-Agent.
        auditarExito(request.getEmail(), ip, suspectUa);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getJwtExpiration())
                .trabajador(trabajadorMapper.toDTO(trabajador))
                .build();
    }

    /**
     * Auditoría de un intento que NO terminó en tokens (D6): EXACTAMENTE UNA
     * línea WARN en formato {@code key=value}, grep-able por motivo/email/IP.
     * La IP usa la misma lógica proxy-aware del rate limiter (ipReal).
     * Sin {@code logback-spring.xml} nuevo: logger por defecto de Spring Boot.
     */
    private void auditarFallo(String email, String motivo, boolean suspectUa, String userAgent, String ip) {
        if (!auditoriaLoginActiva) {
            return;
        }
        String ua = userAgent == null || userAgent.isBlank() ? "-" : userAgent;
        log.warn("event=login_failed email={} ip={} reason={} suspect_ua={} path=/api/auth/login ua=\"{}\"",
                email, ip, motivo, suspectUa, ua);
    }

    /** Log INFO de login exitoso (contrapartida de la auditoría de fallos). */
    private void auditarExito(String email, String ip, boolean suspectUa) {
        if (!auditoriaLoginActiva) {
            return;
        }
        log.info("event=login_ok email={} ip={} suspect_ua={}", email, ip, suspectUa);
    }

    public LoginResponse refreshToken(String refreshToken) {
        // Firma y exp se validan dentro de extract*; las excepciones de JJWT
        // (token expirado/malformado) se traducen a 401 aquí en vez de caer
        // en el handler genérico como 500 (auditoría B-MED-4).
        final String tipo;
        try {
            jwtService.extractEmail(refreshToken);
            tipo = jwtService.extractClaim(refreshToken, claims -> claims.get("type", String.class));
        } catch (Exception e) {
            throw new BadCredentialsException("Token de refresh inválido");
        }
        // Solo un refresh token renueva sesión: un access token robado no debe
        // poder alargar la sesión indefinidamente
        if (!"refresh".equals(tipo)) {
            throw new BadCredentialsException("Token de refresh inválido");
        }
        String email = jwtService.extractEmail(refreshToken);
        Trabajador trabajador = trabajadorRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Token de refresh inválido"));
        if (!trabajador.getActivo()) {
            throw new BadCredentialsException("Token de refresh inválido");
        }

        // Validación de sesión (D2): debe existir, no estar revocada ni
        // caducada y pertenecer al subject del token. Refresh con sesión
        // revocada o token legacy sin sid → 401 (nunca 500).
        final UUID sid;
        try {
            sid = UUID.fromString(jwtService.extractSid(refreshToken));
        } catch (Exception e) {
            throw new BadCredentialsException("Token de refresh inválido");
        }
        SesionAuth sesion = sesionAuthRepository.findById(sid)
                .orElseThrow(() -> new BadCredentialsException("Token de refresh inválido"));
        if (!sesion.estaActiva() || !sesion.getTrabajador().getId().equals(trabajador.getId())) {
            throw new BadCredentialsException("Token de refresh inválido");
        }
        // Los nuevos tokens conservan el MISMO sid (rotación de refresh con
        // sesión persistida); la ventana de la sesión se desliza con cada
        // refresh legítimo.
        LocalDateTime ahora = LocalDateTime.now();
        sesion.setUltimoRefreshEn(ahora);
        sesion.setCaducaEn(ahora.plusSeconds(jwtService.getRefreshExpiration() / 1000));
        sesionAuthRepository.save(sesion);

        String newAccessToken = jwtService.generateToken(trabajador, sesion.getId());
        String newRefreshToken = jwtService.generateRefreshToken(trabajador, sesion.getId());

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtService.getJwtExpiration())
                .trabajador(trabajadorMapper.toDTO(trabajador))
                .build();
    }

    /**
     * Logout con revocación (D2 — spec jwt-session-revocation): revoca la
     * sesión del token presentado (access o refresh) → ambos dejan de
     * autenticar de inmediato. IDEMPOTENTE: con token ausente, inválido, sin
     * sid o ya revocado devuelve lo mismo (no se filtra estado).
     */
    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        final String sid;
        try {
            sid = jwtService.extractSid(authHeader.substring(7));
        } catch (Exception e) {
            return; // token basura o caducado: nada que revocar
        }
        if (sid == null) {
            return; // token legacy sin sid
        }
        try {
            UUID uuid = UUID.fromString(sid);
            sesionAuthRepository.findById(uuid).ifPresent(sesion -> {
                if (sesion.getRevocadaEn() == null) {
                    sesion.setRevocadaEn(LocalDateTime.now());
                    sesionAuthRepository.save(sesion);
                }
            });
        } catch (IllegalArgumentException e) {
            // sid no parseable: idempotente, sin error
        }
    }
}