package com.barbershop.backend.service;

import com.barbershop.backend.dto.LoginRequest;
import com.barbershop.backend.dto.LoginResponse;
import com.barbershop.backend.exception.ResourceNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import com.barbershop.backend.mapper.TrabajadorMapper;
import com.barbershop.backend.model.SesionAuth;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.SesionAuthRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.security.JwtService;
import com.barbershop.backend.security.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TrabajadorRepository trabajadorRepository;
    private final TrabajadorMapper trabajadorMapper;
    private final RateLimiterService rateLimiter;
    private final SesionAuthRepository sesionAuthRepository;

    public LoginResponse login(LoginRequest request) {
        // Rate limit ANTES de autenticar (B-MED-5): tras el máximo de fallos
        // por cuenta+IP o por IP en la ventana → 429 + Retry-After sin
        // validar credenciales. Solo cuentan FALLOS (registrarFalloLogin);
        // acertar resetea el bucket de la cuenta.
        final String ip = rateLimiter.ipReal();
        rateLimiter.comprobarLogin(request.getEmail(), ip);

        // Authenticate
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (AuthenticationException e) {
            rateLimiter.registrarFalloLogin(request.getEmail(), ip);
            throw e;
        }
        rateLimiter.resetearLogin(request.getEmail(), ip);

        // Generate tokens
        Trabajador trabajador = trabajadorRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado"));

        if (!trabajador.getActivo()) {
            throw new IllegalArgumentException("La cuenta está desactivada");
        }

        // Nueva sesión de autenticación (D2): su UUID es el claim sid que
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

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getJwtExpiration())
                .trabajador(trabajadorMapper.toDTO(trabajador))
                .build();
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