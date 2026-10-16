package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.barbershop.backend.model.Cliente;
import com.barbershop.backend.repository.ClienteRepository;
import com.barbershop.backend.service.ClienteService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * B-MED-10 / spec client-record-consolidation: la reserva pública consolida
 * fichas con la regla "completar vacíos, nunca sobrescribir" — se eliminó la
 * regla "último gana" que permitía pisar PII con conocer el teléfono.
 */
@SpringBootTest
class ClientConsolidationTest {

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private ClienteRepository clienteRepository;

    @Test
    void mismoTelefonoConservaNombreYEmailExistentes() {
        String telefono = telefonoUnico();
        Cliente existente = guardar("Marta López", telefono, "a@x.com");

        Cliente resultado = clienteService.findOrCreateByTelefono("Marta L.", telefono, "b@x.com");

        assertThat(resultado.getId()).isEqualTo(existente.getId());
        assertThat(resultado.getNombre()).isEqualTo("Marta López");   // no sobrescribe
        assertThat(resultado.getEmail()).isEqualTo("a@x.com");        // no sobrescribe
    }

    @Test
    void campoVacioSeCompletaConElPrimerValor() {
        String telefono = telefonoUnico();
        Cliente existente = guardar("Cliente Sin Email", telefono, null);

        Cliente resultado = clienteService.findOrCreateByTelefono(
                "Otro Nombre", telefono, "rellenado@x.com");

        assertThat(resultado.getId()).isEqualTo(existente.getId());
        assertThat(resultado.getEmail()).isEqualTo("rellenado@x.com"); // vacío → se completa
        assertThat(resultado.getNombre()).isEqualTo("Cliente Sin Email"); // no vacío → se conserva
    }

    @Test
    void ramaReutilizacionPorEmailNoSobrescribeElNombre() {
        String email = "reutiliza-" + UUID.randomUUID() + "@x.com";
        Cliente existente = guardar("Nombre Original", telefonoUnico(), email);

        // Teléfono desconocido pero email existente → se reutiliza la ficha
        Cliente resultado = clienteService.findOrCreateByTelefono(
                "Nombre Invitado", telefonoUnico(), email);

        assertThat(resultado.getId()).isEqualTo(existente.getId());
        assertThat(resultado.getNombre()).isEqualTo("Nombre Original"); // nunca sobrescribir
    }

    @Test
    void clienteNuevoSeCreaConLosDatosRecibidos() {
        String telefono = telefonoUnico();

        Cliente resultado = clienteService.findOrCreateByTelefono(
                "Cliente Nuevo", telefono, "nuevo-" + UUID.randomUUID() + "@x.com");

        assertThat(resultado.getId()).isNotNull();
        assertThat(resultado.getNombre()).isEqualTo("Cliente Nuevo");
        assertThat(existeClienteConTelefono(telefono)).isTrue();
    }

    // Spec client-deduplication — "Coincidencia cruzada prioriza el teléfono"
    // (design D2.5): teléfono → ficha A, email → ficha B ⇒ gana el TELÉFONO:
    // se completa el hueco de A, B queda intacta y no se crea fila nueva.
    @Test
    void conflictoCruzadoGanaElTelefonoYNoTocaLaOtraFicha() {
        String telefonoA = telefonoUnico();
        String telefonoB = telefonoUnico();
        String emailB = "carlos-" + UUID.randomUUID() + "@ejemplo.com";
        Cliente fichaA = guardar("Cliente A", telefonoA, null);        // sin email
        Cliente fichaB = guardar("Cliente B", telefonoB, emailB);
        long antes = clienteRepository.count();

        // Teléfono de A + email de B en la misma petición
        Cliente resultado = clienteService.findOrCreateByTelefono(
                "Nombre Nuevo", telefonoA, emailB);

        assertThat(resultado.getId()).isEqualTo(fichaA.getId());   // gana el teléfono
        assertThat(clienteRepository.count()).isEqualTo(antes);    // sin fila nueva

        Cliente aRecargada = clienteRepository.findById(fichaA.getId()).orElseThrow();
        assertThat(aRecargada.getEmail()).isEqualTo(emailB);       // hueco completado
        assertThat(aRecargada.getNombre()).isEqualTo("Cliente A"); // nunca sobrescribir

        Cliente bRecargada = clienteRepository.findById(fichaB.getId()).orElseThrow();
        assertThat(bRecargada.getNombre()).isEqualTo("Cliente B"); // intacta
        assertThat(bRecargada.getTelefono()).isEqualTo(telefonoB);
        assertThat(bRecargada.getEmail()).isEqualTo(emailB);
    }

    // --- helpers ---

    private Cliente guardar(String nombre, String telefono, String email) {
        return clienteRepository.save(Cliente.builder()
                .nombre(nombre)
                .telefono(telefono)
                .email(email)
                .build());
    }

    private boolean existeClienteConTelefono(String telefono) {
        return clienteRepository.findByTelefono(telefono).isPresent();
    }

    /**
     * Teléfono único, SOLO dígitos: la spec client-data-consistency exige que
     * {@code clientes.telefono} se persista canónico (dígitos, sin prefijo 34),
     * así que el valor de prueba debe ser ya canónico para que una búsqueda
     * exacta {@code findByTelefono} lo encuentre (el hook @PrePersist normaliza
     * antes de guardar). Empieza por 6 → la normalización no lo recorta.
     */
    private static String telefonoUnico() {
        // no colisiona con los datos demo (DataSeeder) ni entre pruebas
        java.util.Random random = new java.util.Random();
        StringBuilder sb = new StringBuilder("6");
        for (int i = 0; i < 8; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
