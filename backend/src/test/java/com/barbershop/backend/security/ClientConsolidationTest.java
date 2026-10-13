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

    private static String telefonoUnico() {
        // formato válido pero no colisiona con los datos demo (DataSeeder)
        return "6" + UUID.randomUUID().toString().replace("-", "").substring(0, 9);
    }
}
