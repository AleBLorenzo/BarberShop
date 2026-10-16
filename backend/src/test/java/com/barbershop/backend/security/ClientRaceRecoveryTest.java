package com.barbershop.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.Random;

import com.barbershop.backend.model.Cliente;
import com.barbershop.backend.repository.ClienteRepository;
import com.barbershop.backend.service.ClienteService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Spec client-data-consistency — "La identidad se resuelve sin error bajo
 * concurrencia" (design D5): cuando el índice único {@code
 * telefono_normalizado} choca durante el {@code saveAndFlush}, el servicio
 * NO propaga el error: reintenta la búsqueda UNA vez y devuelve la ficha ya
 * existente.
 *
 * <p><b>Qué demuestra exactamente este test:</b> se dobla el repositorio con
 * un {@code Proxy} dinámico que (1) hace que la pre-comprobación
 * {@code findByTelefonoNormalizado} NO vea la fila ajena la primera vez
 * (simula el otro proceso aún sin commitear) y (2) hace que el guardado
 * lance el {@code DataIntegrityViolationException} real que JPA traduce del
 * choque con el índice único. El servicio debe capturarlo, reintentar la
 * búsqueda (ahora sí delega en el repositorio real y ve la fila) y devolver
 * la ficha existente sin lanzar nada. En H2 (runtime de tests) el reintento
 * opera a plenimiento; en PostgreSQL la transacción quedaría abortada tras
 * el choque y el servicio repropaga el 409 documentado — caveat conocido
 * del diseño.</p>
 */
@SpringBootTest
class ClientRaceRecoveryTest {

    @Autowired
    private ClienteService clienteService;

    /** El primario en este contexto es el doble de carrera (ver {@link ConfigCarrera}). */
    @Autowired
    private ClienteRepository clienteRepository;

    @Test
    void choqueDeIndiceUnicoSeRecuperaDevolviendoLaFichaExistente() {
        String telefono = telefonoUnico();
        Cliente existente = clienteRepository.save(Cliente.builder()
                .nombre("Cliente Carrera")
                .telefono(telefono)
                .build());
        long trasAlta = clienteRepository.count();

        // --- Simulación de la carrera ---
        handlerCarrera().simularCarrera();

        // El servicio debe recuperarse: sin excepción y con la ficha única
        Cliente resultado = clienteService.findOrCreateByTelefono(
                "Cliente Carrera", telefono, null);

        assertThat(resultado.getId()).isEqualTo(existente.getId());
        assertThat(clienteRepository.count()).isEqualTo(trasAlta); // sin fila nueva
        // Una sola ficha con esa identidad canónica en toda la tabla
        long filasConIdentidad = clienteRepository.findAll().stream()
                .filter(c -> telefono.equals(c.getTelefonoNormalizado()))
                .count();
        assertThat(filasConIdentidad).isEqualTo(1);
    }

    private HandlerCarrera handlerCarrera() {
        // El contenedor envuelve el doble en otro proxy JDK; se descatacha
        // hasta llegar a nuestro Proxy dinámico original.
        Object doble = org.springframework.test.util.AopTestUtils
                .getUltimateTargetObject(clienteRepository);
        assertThat(Proxy.isProxyClass(doble.getClass()))
                .as("el repositorio inyectado debe contener el doble dinámico de esta prueba")
                .isTrue();
        return (HandlerCarrera) Proxy.getInvocationHandler(doble);
    }

    /** Teléfono canónico de 9 dígitos empezando por 6 (no colisiona con otros tests). */
    private static String telefonoUnico() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder("6");
        for (int i = 0; i < 8; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * Doble de carrera (D5): envuelve el repositorio real y, cuando se arma,
     * la PRIMERA pre-comprobación no ve la fila ajena y el primer guardado
     * choca con el índice único; el resto de las llamadas delegan en el
     * repositorio real.
     */
    static class HandlerCarrera implements InvocationHandler {

        private final Object real;
        private boolean precheckNoVeLaFila = false;
        private boolean guardadoChoca = false;

        HandlerCarrera(Object real) {
            this.real = real;
        }

        void simularCarrera() {
            precheckNoVeLaFila = true;
            guardadoChoca = true;
        }

        @Override
        public Object invoke(Object proxy, Method metodo, Object[] args) throws Throwable {
            if (precheckNoVeLaFila && "findByTelefonoNormalizado".equals(metodo.getName())) {
                precheckNoVeLaFila = false; // solo la búsqueda PREVIA al guardado
                return Optional.empty();
            }
            if (guardadoChoca && "saveAndFlush".equals(metodo.getName())) {
                guardadoChoca = false;
                throw new DataIntegrityViolationException(
                        "Unique index or primary key violation: IDX_CLIENTE_TELEFONO_NORMALIZADO");
            }
            try {
                return metodo.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    @TestConfiguration
    static class ConfigCarrera {

        /** {@code @Primary} solo en ESTE contexto: el servicio recibe el doble. */
        @Bean
        @Primary
        ClienteRepository clienteRepositoryEnCarrera(
                @Qualifier("clienteRepository") ClienteRepository real) {
            return (ClienteRepository) Proxy.newProxyInstance(
                    ClienteRepository.class.getClassLoader(),
                    new Class<?>[]{ClienteRepository.class},
                    new HandlerCarrera(real));
        }
    }
}
