package com.barbershop.backend.service;

import com.barbershop.backend.dto.ClienteDTO;
import com.barbershop.backend.exception.ResourceNotFoundException;
import com.barbershop.backend.mapper.ClienteMapper;
import com.barbershop.backend.model.Cliente;
import com.barbershop.backend.repository.ClienteRepository;
import com.barbershop.backend.security.LikeEscaper;
import com.barbershop.backend.util.ClientNormalizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ClienteMapper clienteMapper;

    // Para descartar cambios no aplicados si un flush choca con el índice único
    // (D5) y aun así devolvemos una ficha existente.
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<ClienteDTO> findAll() {
        return clienteRepository.findAll().stream()
                .map(clienteMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ClienteDTO> findById(Long id) {
        return clienteRepository.findById(id)
                .map(clienteMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public Optional<ClienteDTO> findByTelefono(String telefono) {
        return clienteRepository.findByTelefono(telefono)
                .map(clienteMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public List<ClienteDTO> searchByNombre(String nombre) {
        // Escaping de comodines %/_ (harden-sql-injection-anti-bot, D2): el
        // patrón del usuario se interpreta literalmente; el @Query sigue
        // parametrizado y sin tocar.
        return clienteRepository.findByNombreContainingIgnoreCase(LikeEscaper.contener(nombre)).stream()
                .map(clienteMapper::toDTO)
                .toList();
    }

    @Transactional
    public ClienteDTO create(ClienteDTO dto) {
        // Normalización canónica ANTES de comprobar duplicados (D1/D3): la
        // comparación se hace siempre contra el teléfono normalizado, no contra
        // el texto escrito (una variante "+34…"/guiones ya no esquiva la regla).
        String telefono = ClientNormalizer.normalizarTelefono(dto.getTelefono());
        if (existeTelefonoNormalizado(telefono, null)) {
            throw new IllegalArgumentException("Ya existe un cliente con ese teléfono");
        }
        dto.setTelefono(telefono);
        dto.setEmail(ClientNormalizer.normalizarEmail(dto.getEmail()));
        Cliente cliente = clienteMapper.toEntity(dto);
        try {
            return clienteMapper.toDTO(clienteRepository.saveAndFlush(cliente));
        } catch (DataIntegrityViolationException e) {
            // D5: carrera contra otra inserción → reintentar la búsqueda UNA vez
            if (existeTelefonoNormalizado(telefono, null)) {
                throw new IllegalArgumentException("Ya existe un cliente con ese teléfono");
            }
            throw e;
        }
    }

    @Transactional
    public ClienteDTO update(Long id, ClienteDTO dto) {
        Cliente existing = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + id));

        String telefono = ClientNormalizer.normalizarTelefono(dto.getTelefono());
        String email = ClientNormalizer.normalizarEmail(dto.getEmail());

        // Check if new phone is taken by another client (siempre contra el valor normalizado)
        if (!estaVacio(telefono) && existeTelefonoNormalizado(telefono, id)) {
            throw new IllegalArgumentException("Ya existe un cliente con ese teléfono");
        }

        existing.setNombre(dto.getNombre());
        if (!estaVacio(telefono)) {
            existing.setTelefono(telefono);
        }
        // El email solo se actualiza si el DTO trae uno nuevo y no vacío: un
        // null/blank no borra el email existente (política de no sobrescribir).
        if (!estaVacio(dto.getEmail())) {
            existing.setEmail(email);
        }
        try {
            return clienteMapper.toDTO(clienteRepository.saveAndFlush(existing));
        } catch (DataIntegrityViolationException e) {
            // D5: si otra ficha se creó entre la comprobación y el guardado,
            // se resuelve como duplicado (400 con el mensaje de siempre).
            if (!estaVacio(telefono) && existeTelefonoNormalizado(telefono, id)) {
                throw new IllegalArgumentException("Ya existe un cliente con ese teléfono");
            }
            throw e;
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!clienteRepository.existsById(id)) {
            throw new ResourceNotFoundException("Cliente no encontrado con ID: " + id);
        }
        clienteRepository.deleteById(id);
    }

    // Internal method for booking flow - find or create by phone (email fallback).
    // Regla D1: teléfono es la clave natural; si no existe, se busca por email
    // para no duplicar clientes; si tampoco, se crea uno nuevo.
    //
    // Regla de consolidación (B-MED-10, spec client-record-consolidation):
    // "completar vacíos, nunca sobrescribir" — nombre/email solo se escriben
    // si el valor existente está vacío; los valores no vacíos se conservan
    // (se elimina la regla "último gana"). Aplica también a la rama de
    // reutilización por email. La corrección de datos queda para la edición
    // por ADMIN (panel existente).
    //
    // Orden determinista de resolución (design D2): normalizar → teléfono →
    // email → crear. Ver comentarios en cada paso.
    @Transactional
    public Cliente findOrCreateByTelefono(String nombre, String telefono, String email) {
        // 1. Normalizar la identidad de la petición (D2.1)
        String telefonoNorm = ClientNormalizer.normalizarTelefono(telefono);
        String emailNorm = ClientNormalizer.normalizarEmail(email);
        boolean hasEmail = emailNorm != null;
        boolean hasNombre = !estaVacio(nombre);

        // 2. Teléfono normalizado → reutilizar la ficha; completar únicamente
        //    los campos vacíos (B-MED-10: nunca sobrescribir valores existentes
        //    ni tocar el teléfono almacenado).
        Optional<Cliente> porTelefono = estaVacio(telefonoNorm) ? Optional.empty()
                : clienteRepository.findByTelefonoNormalizado(telefonoNorm);
        if (porTelefono.isPresent()) {
            Cliente cliente = porTelefono.get();
            boolean cambiado = false;
            if (hasEmail && estaVacio(cliente.getEmail())) {
                cliente.setEmail(emailNorm); // primer valor en rellenarlo gana
                cambiado = true;
            }
            if (hasNombre && estaVacio(cliente.getNombre())) {
                cliente.setNombre(nombre);
                cambiado = true;
            }
            return cambiado ? guardarConReintento(cliente, telefonoNorm, emailNorm) : cliente;
        }

        // 3. Email → reutilizar esa ficha TAL CUAL: se conserva su teléfono
        //    original como identificador (el nuevo no se guarda, para no pisar
        //    la clave de la ficha) y solo se completa el nombre si está vacío.
        //    Si el teléfono escrito difiere del almacenado se deja constancia
        //    en log: visibilidad sin romper la política de no sobrescribir.
        if (hasEmail) {
            Optional<Cliente> porEmail = clienteRepository.findByEmailIgnoreCase(emailNorm);
            if (porEmail.isPresent()) {
                Cliente cliente = porEmail.get();
                String telefonoAlmacenado = ClientNormalizer.normalizarTelefono(cliente.getTelefono());
                if (!estaVacio(telefonoNorm) && !telefonoNorm.equals(telefonoAlmacenado)) {
                    log.warn("Teléfono escrito '{}' (normalizado '{}') distinto del almacenado '{}' "
                                    + "en la ficha id={} con email '{}': se conserva el teléfono original",
                            telefono, telefonoNorm, cliente.getTelefono(), cliente.getId(), emailNorm);
                }
                if (hasNombre && estaVacio(cliente.getNombre())) {
                    cliente.setNombre(nombre);
                    return guardarConReintento(cliente, telefonoNorm, emailNorm);
                }
                return cliente;
            }
        }

        // 5. Conflicto cruzado (teléfono → ficha A, email → ficha B): gana el
        //    TELÉFONO — el paso 2 se resuelve primero y hace return, así que si
        //    el teléfono coincide con A nunca se llega a esta rama por email de
        //    B; A solo completa huecos y B queda intacta (sin fusión en tiempo
        //    de ejecución: la limpieza histórica la hace ClientDedupeBackfill).

        // 4. Crear con los valores YA normalizados (teléfono canónico en
        //    teléfono y telefono_normalizado, email en minúsculas).
        Cliente nuevo = Cliente.builder()
                .nombre(nombre)
                .telefono(telefonoNorm)
                .telefonoNormalizado(telefonoNorm)
                .email(emailNorm)
                .build();
        return guardarConReintento(nuevo, telefonoNorm, emailNorm);
    }

    // D5 — Condición de carrera: dos procesos creando la misma ficha a la vez
    // chocan con el índice único. En lugar de propagar el 500, se reintenta la
    // búsqueda UNA vez (la fila ajena ya existe) y se devuelve esa ficha; solo
    // se propaga el conflicto si el reintento vuelve a fallar.
    private Cliente guardarConReintento(Cliente cliente, String telefonoNorm, String emailNorm) {
        try {
            return clienteRepository.saveAndFlush(cliente);
        } catch (DataIntegrityViolationException e) {
            Optional<Cliente> existente = Optional.empty();
            try {
                if (!estaVacio(telefonoNorm)) {
                    existente = clienteRepository.findByTelefonoNormalizado(telefonoNorm);
                }
                if (existente.isEmpty() && emailNorm != null) {
                    existente = clienteRepository.findByEmailIgnoreCase(emailNorm);
                }
            } catch (Exception buscaExcepcion) {
                // En PostgreSQL la transacción queda abortada tras el choque y
                // la búsqueda de recuperación puede no poder ejecutarse: se
                // repropaga entonces el conflicto original (GlobalExceptionHandler
                // lo responde como 409 "ya existe", nunca 500).
                log.warn("Búsqueda de recuperación no disponible tras conflicto de unicidad: {}",
                        buscaExcepcion.getMessage());
            }
            if (existente.isPresent()) {
                // Descarta cambios pendientes del intento fallido si la ficha
                // seguía gestionada (p. ej. completado de huecos no aplicado).
                refrescarSiGestionado(cliente);
                return existente.get();
            }
            throw e;
        }
    }

    private void refrescarSiGestionado(Cliente cliente) {
        try {
            if (entityManager.contains(cliente)) {
                entityManager.refresh(cliente);
            }
        } catch (Exception ignorada) {
            // Mejor esfuerzo: si no se puede refrescar, la transacción ya
            // gestiona el conflicto por su cuenta.
        }
    }

    /** ¿Existe ya otra ficha (distinta de {@code propioId}) con ese teléfono normalizado? */
    private boolean existeTelefonoNormalizado(String telefono, Long propioId) {
        if (estaVacio(telefono)) {
            return false;
        }
        return clienteRepository.findByTelefonoNormalizado(telefono)
                .filter(cliente -> propioId == null || !propioId.equals(cliente.getId()))
                .isPresent();
    }

    private static boolean estaVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    // Overload de 2 argumentos (seeder y llamadores sin email): delega en el
    // método completo con email = null (flujo público: email opcional).
    @Transactional
    public Cliente findOrCreateByTelefono(String nombre, String telefono) {
        return findOrCreateByTelefono(nombre, telefono, null);
    }

    @Transactional(readOnly = true)
    public boolean existsByTelefono(String telefono) {
        return clienteRepository.existsByTelefono(telefono);
    }
}
