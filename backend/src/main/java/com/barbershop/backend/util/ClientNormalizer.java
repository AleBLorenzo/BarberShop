package com.barbershop.backend.util;

import java.util.Locale;

/**
 * Identidad canónica del cliente (cambio dedupe-client-on-rebooking, D1).
 * Única fuente de verdad de la normalización de teléfono/email, compartida por
 * la reserva pública, el panel del trabajador, el alta administrativa, el
 * sembrador de datos y la búsqueda "Mis citas" — así ningún llamador se
 * desincroniza del criterio.
 */
public final class ClientNormalizer {

    private ClientNormalizer() {
    }

    /**
     * Teléfono canónico: solo dígitos y sin prefijo de país {@code 34}.
     * El prefijo solo se elimina si el resultado mide <b>más de 9 dígitos</b>,
     * de modo que {@code +34612345678} → {@code 612345678} y {@code 34} + 9
     * dígitos (11) → 9 dígitos, pero un nacional de 9 dígitos que empieza por
     * 34 no se toca. Entrada null o en blanco se devuelve tal cual (los
     * llamadores deciden si ese valor cuenta como "sin teléfono").
     */
    public static String normalizarTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return telefono;
        }
        String soloDigitos = telefono.replaceAll("[^0-9]", "");
        if (soloDigitos.length() > 9 && soloDigitos.startsWith("34")) {
            soloDigitos = soloDigitos.substring(2);
        }
        return soloDigitos;
    }

    /**
     * Email canónico: sin espacios y en minúsculas ({@link Locale#ROOT}).
     * Null o en blanco → null (el email sigue sin ser clave única: D2/Non-Goals).
     */
    public static String normalizarEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
