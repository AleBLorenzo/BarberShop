package com.barbershop.backend.service;

import com.barbershop.backend.dto.TrabajadorCreateRequest;
import com.barbershop.backend.dto.TrabajadorDTO;
import com.barbershop.backend.dto.WorkerPerfilUpdateRequest;
import com.barbershop.backend.exception.ResourceNotFoundException;
import com.barbershop.backend.mapper.TrabajadorMapper;
import com.barbershop.backend.model.Rol;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.ReservaRepository;
import com.barbershop.backend.repository.SesionAuthRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.security.LikeEscaper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TrabajadorService {

    private final TrabajadorRepository trabajadorRepository;
    private final ReservaRepository reservaRepository;
    private final TrabajadorMapper trabajadorMapper;
    private final PasswordEncoder passwordEncoder;
    private final SesionAuthRepository sesionAuthRepository;

    @Transactional(readOnly = true)
    public List<TrabajadorDTO> findAll() {
        return trabajadorRepository.findAll().stream()
                .map(trabajadorMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TrabajadorDTO> findAllActivos() {
        return trabajadorRepository.findByActivoTrue().stream()
                .map(trabajadorMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TrabajadorDTO> findById(Long id) {
        return trabajadorRepository.findById(id)
                .map(trabajadorMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public Optional<TrabajadorDTO> findByEmail(String email) {
        return trabajadorRepository.findByEmail(email)
                .map(trabajadorMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public List<TrabajadorDTO> searchActivosByNombre(String nombre) {
        // Escaping de comodines %/_ (harden-sql-injection-anti-bot, D2).
        return trabajadorRepository.findActivosByNombreContainingIgnoreCase(LikeEscaper.contener(nombre)).stream()
                .map(trabajadorMapper::toDTO)
                .toList();
    }

    @Transactional
    public TrabajadorDTO create(TrabajadorCreateRequest request) {
        if (trabajadorRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Ya existe un trabajador con ese email");
        }
        Trabajador trabajador = trabajadorMapper.toEntity(request);
        trabajador.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        // Sin rol explícito en la petición → WORKER (nunca nacen admins por accidente)
        if (trabajador.getRol() == null) {
            trabajador.setRol(Rol.WORKER);
        }
        return trabajadorMapper.toDTO(trabajadorRepository.save(trabajador));
    }

    @Transactional
    public TrabajadorDTO update(Long id, TrabajadorDTO dto) {
        Trabajador existing = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado con ID: " + id));

        if (!existing.getEmail().equals(dto.getEmail()) &&
                trabajadorRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Ya existe un trabajador con ese email");
        }

        existing.setNombre(dto.getNombre());
        existing.setEmail(dto.getEmail());
        // Al desactivar una cuenta activa se revocan sus sesiones (D2): los
        // JWT emitidos dejan de autenticar al instante, sin esperar a caducar.
        boolean seDesactiva = Boolean.TRUE.equals(existing.getActivo())
                && Boolean.FALSE.equals(dto.getActivo());
        existing.setActivo(dto.getActivo());
        // El panel envía foto siempre (vacío = sin foto): se reemplaza tal cual,
        // igual que el resto de campos de este update.
        existing.setFoto(dto.getFoto());
        existing.setDescripcion(dto.getDescripcion());
        // rol: solo se cambia si viene explícito (el panel no lo envía → se conserva)
        if (dto.getRol() != null) {
            existing.setRol(dto.getRol());
        }
        Trabajador guardado = trabajadorRepository.save(existing);
        if (seDesactiva) {
            sesionAuthRepository.revocarActivas(guardado.getId(), LocalDateTime.now());
        }
        return trabajadorMapper.toDTO(guardado);
    }

    @Transactional
    public void changePassword(Long id, String currentPassword, String newPassword) {
        Trabajador trabajador = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado con ID: " + id));

        if (!passwordEncoder.matches(currentPassword, trabajador.getPasswordHash())) {
            throw new IllegalArgumentException("Contraseña actual incorrecta");
        }
        trabajador.setPasswordHash(passwordEncoder.encode(newPassword));
        trabajadorRepository.save(trabajador);
    }

    /**
     * El trabajador edita su propio perfil (nombre, email y foto opcional).
     * Reutiliza la comprobación de email duplicado de {@link #update}: si el
     * email pertenece a otro usuario se rechaza con "Ya existe un trabajador
     * con ese email". {@code foto} solo se guarda si viene en el payload.
     */
    @Transactional
    public TrabajadorDTO updatePerfil(Long trabajadorId, WorkerPerfilUpdateRequest request) {
        Trabajador existing = trabajadorRepository.findById(trabajadorId)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado con ID: " + trabajadorId));

        String nombre = request.getNombre().trim();
        String email = request.getEmail().trim();

        if (!existing.getEmail().equals(email) &&
                trabajadorRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Ya existe un trabajador con ese email");
        }

        existing.setNombre(nombre);
        existing.setEmail(email);
        if (request.getFoto() != null) {
            existing.setFoto(request.getFoto());
        }
        // descripcion: null = conserva la actual, vacío = la borra, texto = reemplaza
        if (request.getDescripcion() != null) {
            existing.setDescripcion(request.getDescripcion().trim());
        }
        return trabajadorMapper.toDTO(trabajadorRepository.save(existing));
    }

    /**
     * Borrado seguro: si el trabajador tiene reservas no se puede borrar (FK);
     * en su lugar se desactiva (activo=false, ya oculta de reservas nuevas) y
     * se devuelve un mensaje claro. Sin reservas → borrado físico como antes.
     *
     * @return mensaje para el cliente ("Trabajador eliminado" o el de desactivación)
     */
    @Transactional
    public String delete(Long id) {
        Trabajador trabajador = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado con ID: " + id));

        if (reservaRepository.existsByTrabajador_Id(id)) {
            trabajador.setActivo(false);
            trabajadorRepository.save(trabajador);
            // Misma regla que en update(): desactivar revoca las sesiones (D2)
            sesionAuthRepository.revocarActivas(id, LocalDateTime.now());
            return "El trabajador tiene reservas; se desactivó";
        }
        // Las sesiones tienen FK a trabajadores: se borran antes que la ficha
        sesionAuthRepository.deleteByTrabajador(trabajador);
        trabajadorRepository.deleteById(id);
        return "Trabajador eliminado";
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return trabajadorRepository.existsByEmail(email);
    }
}