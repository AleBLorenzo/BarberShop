package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.barbershop.backend.dto.ClienteDTO;
import com.barbershop.backend.model.Cliente;
import com.barbershop.backend.repository.ClienteRepository;
import com.barbershop.backend.service.ClienteService;
import com.barbershop.backend.util.ClientNormalizer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spec client-deduplication — "Ninguna vía de alta crea duplicados",
 * escenario "Alta administrativa con formato distinto": el ADMIN da de alta un
 * cliente con {@code 612 345-678} existiendo ya {@code +34612345678}.
 *
 * <p>Se drive por {@code ClienteService.create()/update()}, el servicio detrás
 * de {@code POST/PUT /api/admin/clientes} ({@code AdminController}).
 * <b>COMPORTAMIENTO REAL de la vía ADMIN ante un duplicado:</b> se RECHAZA —
 * {@code create()} normaliza el teléfono, lo compara contra
 * {@code telefono_normalizado} y lanza {@link IllegalArgumentException} ("Ya
 * existe un cliente con ese teléfono") ANTES de insertar;
 * {@code GlobalExceptionHandler} lo respondería como HTTP 400 en el endpoint.
 * NO resuelve hacia la ficha existente (esa resolución es la vía de la
 * reserva: {@code findOrCreateByTelefono}).</p>
 *
 * <p>La BD H2 {@code testdb} es compartida entre contextos de test, así que la
 * identidad del escenario ({@code 612345678}) se REUTILIZA si otra clase (o el
 * seeder demo) ya la dejó y se RETIRA al terminar si la creó esta clase: así
 * no queda fila residual que rompa el delta {@code antes + 1} de
 * {@link ClientDedupeFormatVariantsTest}. Todo el crecimiento se mide por
 * DELTAS de conteo (datos demo pueden haberse sembrado).</p>
 */
@SpringBootTest
class ClientAdminPathDedupeTest {

    /** Identidad canónica del escenario de la spec (teléfono del cliente ya existente). */
    private static final String ESCENARIO_NORMALIZADO = "612345678";
    /** El mismo número con el formato internacional del escenario. */
    private static final String ESCENARIO_TELEFONO = "+34612345678";

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private ClienteRepository clienteRepository;

    /** true si la ficha del escenario la creó ESTA clase (entonces hay que retirarla). */
    private boolean creadaPorEstaClase;

    @Test
    void altaAdministrativaConFormatoDistintoNoCreaSegundaFicha() {
        Cliente existente = fichaBaseDelEscenario();
        String nombreAntes = existente.getNombre();
        String emailAntes = existente.getEmail();
        try {
            long antes = clienteRepository.count();

            // El ADMIN escribe los MISMOS dígitos con otro formato
            ClienteDTO alta = ClienteDTO.builder()
                    .nombre("Alta Admin Distinta")
                    .telefono(conGuionesEspacios(ESCENARIO_NORMALIZADO)) // "612 345-678"
                    .email("alta-admin-" + UUID.randomUUID() + "@ejemplo.com")
                    .build();

            // COMPORTAMIENTO REAL: se rechaza como duplicado (no se resuelve
            // hacia la ficha existente) y no se inserta ninguna fila.
            assertThatThrownBy(() -> clienteService.create(alta))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("teléfono");

            // Ni fila nueva...
            assertThat(clienteRepository.count()).isEqualTo(antes);
            List<Cliente> filas = filasConIdentidadDelEscenario();
            assertThat(filas).hasSize(1);
            assertThat(filas.get(0).getId()).isEqualTo(existente.getId());
            // ...ni la ficha existente alterada por el intento de alta
            Cliente recargada = clienteRepository.findById(existente.getId()).orElseThrow();
            assertThat(recargada.getNombre()).isEqualTo(nombreAntes);
            assertThat(recargada.getEmail()).isEqualTo(emailAntes);
        } finally {
            // No dejar residuo para ClientDedupeFormatVariantsTest (mismo
            // contexto H2, afirma "antes + 1" sobre esta identidad fija)
            retirarFichaSiLaCreoEstaClase(existente);
        }
    }

    @Test
    void updateAlmacenaTelefonoYEmailEnFormatoCanonico() {
        String telefonoBase = telefonoUnico(); // 9 dígitos ya canónicos
        Cliente ficha = guardar("Cliente Edición",
                telefonoBase, "edicion-" + UUID.randomUUID() + "@x.com");
        long antes = clienteRepository.count();

        String telefonoEscrito = formatoInternacional(telefonoBase); // "+34 612 345 678"
        String emailEscrito = "  Edicion-" + UUID.randomUUID() + "@Example.COM ";

        ClienteDTO editada = ClienteDTO.builder()
                .nombre("Cliente Edición Actualizado")
                .telefono(telefonoEscrito)
                .email(emailEscrito)
                .build();

        ClienteDTO resultado = clienteService.update(ficha.getId(), editada);

        // update() no crea filas
        assertThat(clienteRepository.count()).isEqualTo(antes);

        Cliente recargada = clienteRepository.findById(ficha.getId()).orElseThrow();
        // Teléfono canónico almacenado: solo dígitos, sin "+34" ni separadores
        assertThat(recargada.getTelefono()).isEqualTo(telefonoBase);
        assertThat(recargada.getTelefonoNormalizado()).isEqualTo(telefonoBase);
        // Email canónico almacenado: sin espacios y en minúsculas
        assertThat(recargada.getEmail())
                .isEqualTo(emailEscrito.trim().toLowerCase(Locale.ROOT));
        assertThat(recargada.getNombre()).isEqualTo("Cliente Edición Actualizado");
        // Y el DTO devuelto refleja lo mismo
        assertThat(resultado.getTelefono()).isEqualTo(telefonoBase);
        assertThat(resultado.getEmail()).isEqualTo(emailEscrito.trim().toLowerCase(Locale.ROOT));
    }

    @Test
    void updateRechazaComoDuplicadoElTelefonoDeOtraFichaEnFormatoDistinto() {
        Cliente otra = guardar("Otra Ficha",
                telefonoUnico(), "otra-" + UUID.randomUUID() + "@x.com");
        Cliente propia = guardar("Ficha Propia",
                telefonoUnico(), "propia-" + UUID.randomUUID() + "@x.com");
        long antes = clienteRepository.count();

        // El teléfono de "otra" reescrito con formato internacional: la
        // normalización dentro de update() debe detectar el duplicado aunque
        // el texto escrito no coincida con lo almacenado.
        String varianteDeOtra = formatoInternacional(otra.getTelefono());

        ClienteDTO edicion = ClienteDTO.builder()
                .nombre("Ficha Propia Editada")
                .telefono(varianteDeOtra)
                .build();

        assertThatThrownBy(() -> clienteService.update(propia.getId(), edicion))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teléfono");

        assertThat(clienteRepository.count()).isEqualTo(antes);
        // Ninguna de las dos fichas cambió
        Cliente propiaRecargada = clienteRepository.findById(propia.getId()).orElseThrow();
        assertThat(propiaRecargada.getNombre()).isEqualTo("Ficha Propia");
        assertThat(propiaRecargada.getTelefono()).isEqualTo(propia.getTelefono());
        Cliente otraRecargada = clienteRepository.findById(otra.getId()).orElseThrow();
        assertThat(otraRecargada.getNombre()).isEqualTo("Otra Ficha");
        assertThat(otraRecargada.getTelefono()).isEqualTo(otra.getTelefono());
    }

    // --- helpers ---

    /**
     * Ficha con la identidad del escenario: se reutiliza la fila si otra clase
     * de test (o el seeder demo) ya la dejó; si no existe, se crea con el
     * formato internacional del escenario (el hook {@code @PrePersist} la
     * guarda canónica). {@link #creadaPorEstaClase} indica si hay que retirarla.
     */
    private Cliente fichaBaseDelEscenario() {
        var previa = clienteRepository.findAll().stream()
                .filter(c -> ESCENARIO_NORMALIZADO.equals(
                        ClientNormalizer.normalizarTelefono(c.getTelefono())))
                .findFirst();
        if (previa.isPresent()) {
            creadaPorEstaClase = false;
            return previa.get();
        }
        creadaPorEstaClase = true;
        return clienteRepository.save(Cliente.builder()
                .nombre("Cliente Escenario")
                .telefono(ESCENARIO_TELEFONO)
                .build());
    }

    /** Borra la ficha del escenario SOLO si la creó esta clase (evita filas residuales). */
    private void retirarFichaSiLaCreoEstaClase(Cliente ficha) {
        if (creadaPorEstaClase) {
            clienteRepository.deleteById(ficha.getId());
            creadaPorEstaClase = false;
        }
    }

    /** Todas las filas con la identidad canónica del escenario (tolerante a formatos crudos). */
    private List<Cliente> filasConIdentidadDelEscenario() {
        return clienteRepository.findAll().stream()
                .filter(c -> ESCENARIO_NORMALIZADO.equals(
                        ClientNormalizer.normalizarTelefono(c.getTelefono())))
                .toList();
    }

    private Cliente guardar(String nombre, String telefono, String email) {
        return clienteRepository.save(Cliente.builder()
                .nombre(nombre)
                .telefono(telefono)
                .email(email)
                .build());
    }

    /** Dígitos canónicos → "612 345-678" (formato del escenario para el alta admin). */
    private static String conGuionesEspacios(String canonico) {
        return canonico.substring(0, 3) + " "
                + canonico.substring(3, 6) + "-"
                + canonico.substring(6);
    }

    /** Dígitos canónicos → "+34 612 345 678" (formato internacional del escenario). */
    private static String formatoInternacional(String canonico) {
        return "+34 " + canonico.substring(0, 3) + " "
                + canonico.substring(3, 6) + " "
                + canonico.substring(6);
    }

    /**
     * Teléfono canónico de 9 dígitos empezando por 6 (la normalización no lo
     * recorta) y no colisiona con otros datos de prueba.
     */
    private static String telefonoUnico() {
        java.util.Random random = new java.util.Random();
        StringBuilder sb = new StringBuilder("6");
        for (int i = 0; i < 8; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
