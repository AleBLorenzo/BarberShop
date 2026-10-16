package com.barbershop.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Spec client-data-consistency — "Prefijo de país solo se elimina cuando
 * aplica": la identidad canónica (D1) recorta el prefijo {@code 34} SOLO
 * cuando el resultado tiene más de 9 dígitos, de modo que un número nacional
 * de 9 dígitos que empieza por 34 no se toca. Test plano (sin contexto
 * Spring): {@code ClientNormalizer} es una utilidad pura.
 */
class ClientNormalizerTest {

    @Test
    void prefijoDePaisSeEliminaSoloSiHayMasDeNueveDigitos() {
        // +34 y 34 + 9 dígitos = 11 dígitos → se recorta a los 9 últimos
        assertThat(ClientNormalizer.normalizarTelefono("+34612345678")).isEqualTo("612345678");
        assertThat(ClientNormalizer.normalizarTelefono("34 612 345 678")).isEqualTo("612345678");

        // 9 dígitos que empiezan por 34: es un número NACIONAL, se conserva íntegro
        assertThat(ClientNormalizer.normalizarTelefono("341234567")).isEqualTo("341234567");
        assertThat(ClientNormalizer.normalizarTelefono("+34 123 456 7")).isEqualTo("341234567");
    }

    @Test
    void separadoresYPrefijoPlusSeEliminan() {
        assertThat(ClientNormalizer.normalizarTelefono("612-345-678")).isEqualTo("612345678");
        assertThat(ClientNormalizer.normalizarTelefono("612 345 678")).isEqualTo("612345678");
        assertThat(ClientNormalizer.normalizarTelefono(" 612345678 ")).isEqualTo("612345678");
        assertThat(ClientNormalizer.normalizarTelefono("612.345.678")).isEqualTo("612345678");
    }

    @Test
    void entradaNulaOEnBlancoSeDevuelveTalCual() {
        // Los llamadores deciden si el valor cuenta como "sin teléfono"
        assertThat(ClientNormalizer.normalizarTelefono(null)).isNull();
        assertThat(ClientNormalizer.normalizarTelefono("")).isEmpty();
        assertThat(ClientNormalizer.normalizarTelefono("   ")).isEqualTo("   ");
    }

    @Test
    void emailSeGuardaEnMinusculasYSinEspacios() {
        assertThat(ClientNormalizer.normalizarEmail("  Foo@Bar.COM ")).isEqualTo("foo@bar.com");
        assertThat(ClientNormalizer.normalizarEmail("cliente@ejemplo.com")).isEqualTo("cliente@ejemplo.com");
        assertThat(ClientNormalizer.normalizarEmail(null)).isNull();
        assertThat(ClientNormalizer.normalizarEmail("  ")).isNull();
    }
}
