package com.barbershop.backend.controller.publico;

import com.barbershop.backend.dto.ApiResponse;
import com.barbershop.backend.dto.LoginRequest;
import com.barbershop.backend.dto.LoginResponse;
import com.barbershop.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Login exitoso"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Token de refresh requerido", "INVALID_TOKEN"));
        }
        String refreshToken = authHeader.substring(7);
        LoginResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.ok(response, "Token renovado"));
    }

    /**
     * Logout con revocación de sesión (D2): revoca el sid del token
     * presentado (access o refresh) → access y refresh de esa sesión dejan
     * de autenticar de inmediato. IDEMPOTENTE: siempre 2xx, sin cabecera o
     * con token basura/ya revocado la respuesta es la misma (no se filtra
     * si la sesión existía).
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.ok(ApiResponse.ok(null, "Sesión cerrada"));
    }
}