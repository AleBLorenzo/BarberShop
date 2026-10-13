package com.barbershop.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.barbershop.backend.repository.TrabajadorRepository;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final TrabajadorRepository trabajadorRepository;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/api/public/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/api/auth/**",
                                "/uploads/**"
                        ).permitAll()
                        // Endpoints de trabajador: el rol ADMIN también los usa
                        // (restoreSession de sesión lee /api/worker/perfil).
                        .requestMatchers("/api/worker/**").hasAnyRole("WORKER", "ADMIN")
                        // Solo la SUBIDA de la propia foto del trabajador es
                        // legítima para WORKER (WorkerController#actualizarPerfil);
                        // el borrado (DELETE …/{nombre}) exige ADMIN (B-BAJO-2)
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/admin/media/trabajadores")
                                .hasAnyRole("WORKER", "ADMIN")
                        // Admin endpoints: solo rol ADMIN (OWASP A01)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // All other requests need authentication
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // Unauthenticated (missing/invalid/expired token) → 401 so the
                // frontend axios interceptor can run the refresh-token flow.
                // Authenticated but forbidden keeps returning 403.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":false,\"message\":\"No autenticado\",\"errorCode\":\"UNAUTHORIZED\"}");
                        })
                        // Escrito directamente (sin sendError): si se usara sendError, el
                        // ERROR dispatch a /error volvería a pasar por la seguridad como
                        // anónimo y el 403 acabaría convirtiéndose en 401.
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":false,\"message\":\"Acceso denegado: rol insuficiente\",\"errorCode\":\"FORBIDDEN\"}");
                        }))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // Sin consola H2 (migración migrate-h2-to-postgresql): se recupera el
        // DENY por defecto de X-Frame-Options (auditoría B-MED-8) y se conserva
        // la cabecera Referrer-Policy.
        //
        // Cabeceras completas de seguridad (B-BAJO-7, cambio
        // harden-open-security-findings D3):
        //  - Referrer-Policy: los enlaces de cancelación/reprogramación llevan
        //    el token en la URL (F-BAJO-2): el destino nunca debe recibirlo
        //    como cabecera Referer.
        //  - Permissions-Policy: APIs potencialmente peligrosas apagadas.
        //  - HSTS se envía SIEMPRE (sobre HTTP puro los navegadores lo
        //    ignoran; quedará activo cuando exista TLS tras nginx). El writer
        //    HSTS propio de Spring Security queda deshabilitado para no
        //    duplicar la cabecera en peticiones https.
        //  - CSP restrictiva SOLO en respuestas /api/** (son JSON y no deben
        //    renderizarse); Swagger UI y /uploads/** se quedan sin CSP para
        //    no romperlos en desarrollo.
        http.headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts.disable())
                .addHeaderWriter((request, response) -> {
                    response.setHeader("Referrer-Policy", "no-referrer");
                    response.setHeader("Permissions-Policy",
                            "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
                    response.setHeader("Strict-Transport-Security", "max-age=31536000");
                    String uri = request.getRequestURI();
                    if (uri != null && uri.startsWith("/api/")) {
                        response.setHeader("Content-Security-Policy",
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'; sandbox");
                    }
                }));

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return email -> trabajadorRepository.findByEmail(email)
                .map(trabajador -> org.springframework.security.core.userdetails.User
                        .withUsername(trabajador.getEmail())
                        .password(trabajador.getPasswordHash())
                        .authorities("ROLE_" + trabajador.getRol().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Trabajador no encontrado: " + email));
    }
}