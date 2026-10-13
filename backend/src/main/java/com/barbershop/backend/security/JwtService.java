package com.barbershop.backend.security;

import com.barbershop.backend.model.Trabajador;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secretString;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpiration;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Trabajador trabajador, UUID sid) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("trabajadorId", trabajador.getId());
        claims.put("email", trabajador.getEmail());
        claims.put("nombre", trabajador.getNombre());
        // type distingue access de refresh: el filtro solo autentica "access"
        // y /api/auth/refresh solo acepta "refresh" (auditoría B-MED-4)
        claims.put("type", "access");
        // sid = sesión de autenticación persistida (D2): el filtro la valida y
        // el logout la revoca → revocación inmediata de access y refresh.
        // Los tokens antiguos sin sid NO autentican (BREAKING: re-login).
        claims.put("sid", sid.toString());
        return buildToken(claims, trabajador.getEmail(), jwtExpiration);
    }

    public String generateRefreshToken(Trabajador trabajador, UUID sid) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        claims.put("sid", sid.toString());
        return buildToken(claims, trabajador.getEmail(), refreshExpiration);
    }

    /** Claim {@code sid} (sesión persistida); {@code null} en tokens legacy. */
    public String extractSid(String token) {
        return extractClaim(token, claims -> claims.get("sid", String.class));
    }

    /** Expiración del access token (ms), para el campo {@code expiresIn}. */
    public long getJwtExpiration() {
        return jwtExpiration;
    }

    /** Expiración del refresh token (ms) — ventana de la sesión (sid). */
    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    private String buildToken(Map<String, Object> claims, String subject, long expiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(secretKey)
                .compact();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractTrabajadorId(String token) {
        return extractClaim(token, claims -> claims.get("trabajadorId", Long.class));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }
}