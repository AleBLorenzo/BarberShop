package com.barbershop.backend.service;

import com.barbershop.backend.dto.ClienteDTO;
import com.barbershop.backend.exception.ResourceNotFoundException;
import com.barbershop.backend.mapper.ClienteMapper;
import com.barbershop.backend.model.Cliente;
import com.barbershop.backend.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ClienteMapper clienteMapper;

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
        return clienteRepository.findByNombreContainingIgnoreCase(nombre).stream()
                .map(clienteMapper::toDTO)
                .toList();
    }

    @Transactional
    public ClienteDTO create(ClienteDTO dto) {
        // Check if phone already exists
        if (clienteRepository.existsByTelefono(dto.getTelefono())) {
            throw new IllegalArgumentException("Ya existe un cliente con ese teléfono");
        }
        Cliente cliente = clienteMapper.toEntity(dto);
        return clienteMapper.toDTO(clienteRepository.save(cliente));
    }

    @Transactional
    public ClienteDTO update(Long id, ClienteDTO dto) {
        Cliente existing = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + id));

        // Check if new phone is taken by another client
        if (!existing.getTelefono().equals(dto.getTelefono()) &&
                clienteRepository.existsByTelefono(dto.getTelefono())) {
            throw new IllegalArgumentException("Ya existe un cliente con ese teléfono");
        }

        existing.setNombre(dto.getNombre());
        existing.setTelefono(dto.getTelefono());
        return clienteMapper.toDTO(clienteRepository.save(existing));
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
    @Transactional
    public Cliente findOrCreateByTelefono(String nombre, String telefono, String email) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasNombre = nombre != null && !nombre.isBlank();

        Optional<Cliente> porTelefono = clienteRepository.findByTelefono(telefono);
        if (porTelefono.isPresent()) {
            Cliente cliente = porTelefono.get();
            boolean cambiado = false;
            if (hasEmail && estaVacio(cliente.getEmail())) {
                cliente.setEmail(email); // primer valor en rellenarlo gana
                cambiado = true;
            }
            if (hasNombre && estaVacio(cliente.getNombre())) {
                cliente.setNombre(nombre);
                cambiado = true;
            }
            return cambiado ? clienteRepository.save(cliente) : cliente;
        }

        if (hasEmail) {
            Optional<Cliente> porEmail = clienteRepository.findByEmail(email);
            if (porEmail.isPresent()) {
                // El email ya existe en otra ficha: reutilizar (nunca duplicar
                // por email) y completar solo el nombre si está vacío — nunca
                // sobrescribir el existente.
                Cliente cliente = porEmail.get();
                if (hasNombre && estaVacio(cliente.getNombre())) {
                    cliente.setNombre(nombre);
                    return clienteRepository.save(cliente);
                }
                return cliente;
            }
        }

        Cliente nuevo = Cliente.builder()
                .nombre(nombre)
                .telefono(telefono)
                .email(hasEmail ? email : null)
                .build();
        return clienteRepository.save(nuevo);
    }

    private static boolean estaVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    @Transactional
    public Cliente findOrCreateByTelefono(String nombre, String telefono) {
        return findOrCreateByTelefono(nombre, telefono, null);
    }

    @Transactional(readOnly = true)
    public boolean existsByTelefono(String telefono) {
        return clienteRepository.existsByTelefono(telefono);
    }
}