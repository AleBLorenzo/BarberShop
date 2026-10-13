package com.barbershop.backend.security;

import com.barbershop.backend.model.SesionAuth;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.SesionAuthRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TrabajadorRepository trabajadorRepository;
    private final SesionAuthRepository sesionAuthRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String email;
        final String tipo;
        final String sid;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            email = jwtService.extractEmail(jwt);
            tipo = jwtService.extractClaim(jwt, claims -> claims.get("type", String.class));
            sid = jwtService.extractSid(jwt);
        } catch (Exception e) {
            // Invalid token
            filterChain.doFilter(request, response);
            return;
        }

        // Solo tokens de tipo "access" autentican peticiones de API: un refresh
        // (vida más larga) no debe servir como credencial (B-MED-4)
        if (!"access".equals(tipo)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<Trabajador> trabajadorOpt = trabajadorRepository.findByEmail(email);
            if (trabajadorOpt.isPresent()) {
                Trabajador trabajador = trabajadorOpt.get();
                if (!trabajador.getActivo()) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // Sesión persistida (D2): sin claim sid (token legacy) o con
                // sesión revocada/inexistente/caducada → NO autentica → 401.
                // El logout revoca la sesión y surte efecto inmediato.
                if (sid == null || !sesionActiva(sid, trabajador)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                UserDetails userDetails = org.springframework.security.core.userdetails.User
                        .withUsername(trabajador.getEmail())
                        .password(trabajador.getPasswordHash())
                        .authorities("ROLE_" + trabajador.getRol().name())
                        .build();

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /** La sesión del claim {@code sid} existe, está activa y pertenece al trabajador. */
    private boolean sesionActiva(String sid, Trabajador trabajador) {
        try {
            Optional<SesionAuth> sesion = sesionAuthRepository.findById(UUID.fromString(sid));
            return sesion.map(s -> s.estaActiva()
                    && s.getTrabajador().getId().equals(trabajador.getId()))
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            return false; // sid no parseable
        }
    }
}
