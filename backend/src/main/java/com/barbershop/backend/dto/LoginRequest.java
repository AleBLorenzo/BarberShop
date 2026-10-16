package com.barbershop.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    /**
     * Campo honeypot anti-bot (cambio harden-sql-injection-anti-bot, D5):
     * el frontend lo envía SIEMPRE vacío; el usuario real no puede verlo ni
     * enfocarlo. Nombre engañoso a propósito (NO "honeypot"). Opcional y SIN
     * validación: tolerado si falta (clientes API legítimos siguen
     * funcionando); si llega no vacío AuthService lo rechaza ANTES de
     * verificar credenciales y antes de tocar los buckets de rate limit.
     */
    private String website;

    /**
     * Token opcional de Cloudflare Turnstile (CAPTCHA de terceros, OFF por
     * defecto — `app.security.turnstile.enabled=false`). NUNCA exigido por
     * `@Valid`: el backend solo lo valida cuando el campo viene presente y
     * el mecanismo está habilitado.
     */
    private String turnstileToken;
}