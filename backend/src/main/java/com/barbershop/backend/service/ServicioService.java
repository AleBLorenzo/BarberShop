package com.barbershop.backend.service;

import com.barbershop.backend.dto.ServicioDTO;
import com.barbershop.backend.exception.ResourceNotFoundException;
import com.barbershop.backend.mapper.ServicioMapper;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.repository.ReservaRepository;
import com.barbershop.backend.repository.ServicioRepository;
import com.barbershop.backend.security.LikeEscaper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ServicioService {

    private final ServicioRepository servicioRepository;
    private final ReservaRepository reservaRepository;
    private final ServicioMapper servicioMapper;

    @Transactional(readOnly = true)
    public List<ServicioDTO> findAll() {
        return servicioRepository.findAll().stream()
                .map(servicioMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServicioDTO> findAllActivos() {
        return servicioRepository.findByActivoTrue().stream()
                .map(servicioMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ServicioDTO> findById(Long id) {
        return servicioRepository.findById(id)
                .map(servicioMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public Optional<ServicioDTO> findByNombre(String nombre) {
        return servicioRepository.findByNombreIgnoreCase(nombre)
                .map(servicioMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public List<ServicioDTO> searchActivosByNombre(String nombre) {
        // Escaping de comodines %/_ (harden-sql-injection-anti-bot, D2).
        return servicioRepository.findActivosByNombreContainingIgnoreCase(LikeEscaper.contener(nombre)).stream()
                .map(servicioMapper::toDTO)
                .toList();
    }

    @Transactional
    public ServicioDTO create(ServicioDTO dto) {
        if (servicioRepository.existsByNombreIgnoreCase(dto.getNombre())) {
            throw new IllegalArgumentException("Ya existe un servicio con ese nombre");
        }
        validarOferta(dto);
        Servicio servicio = servicioMapper.toEntity(dto);
        return servicioMapper.toDTO(servicioRepository.save(servicio));
    }

    @Transactional
    public ServicioDTO update(Long id, ServicioDTO dto) {
        Servicio existing = servicioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + id));

        if (!existing.getNombre().equalsIgnoreCase(dto.getNombre()) &&
                servicioRepository.existsByNombreIgnoreCase(dto.getNombre())) {
            throw new IllegalArgumentException("Ya existe un servicio con ese nombre");
        }
        validarOferta(dto);

        existing.setNombre(dto.getNombre());
        existing.setPrecio(dto.getPrecio());
        existing.setDuracionMinutos(dto.getDuracionMinutos());
        existing.setActivo(dto.getActivo());
        // El panel envía foto siempre (vacío = sin foto): se reemplaza tal cual,
        // igual que el resto de campos de este update.
        existing.setFoto(dto.getFoto());
        // Oferta: null en los 3 campos = eliminar la oferta (la ventana completa es obligatoria).
        existing.setDescuentoPorcentaje(dto.getDescuentoPorcentaje());
        existing.setOfertaInicio(dto.getOfertaInicio());
        existing.setOfertaFin(dto.getOfertaFin());
        return servicioMapper.toDTO(servicioRepository.save(existing));
    }

    /**
     * Valida la coherencia de la oferta (400 vía {@code GlobalExceptionHandler}):
     * sin campos = sin oferta OK; con oferta, porcentaje 1–99 y ventana completa
     * (inicio y fin) con fin posterior a inicio. Un porcentaje sin ventana no podría
     * caducar nunca → se rechaza en lugar de crear una oferta "abierta" por accidente.
     */
    private void validarOferta(ServicioDTO dto) {
        Integer pct = dto.getDescuentoPorcentaje();
        LocalDateTime inicio = dto.getOfertaInicio();
        LocalDateTime fin = dto.getOfertaFin();

        if (pct == null && inicio == null && fin == null) {
            return; // sin oferta
        }
        if (pct == null) {
            throw new IllegalArgumentException("La oferta requiere un porcentaje de descuento");
        }
        if (pct < 1 || pct > 99) {
            throw new IllegalArgumentException("El porcentaje de oferta debe estar entre 1 y 99");
        }
        if (inicio == null || fin == null) {
            throw new IllegalArgumentException("La oferta requiere fecha de inicio y fin");
        }
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("La fecha de fin de la oferta debe ser posterior a la de inicio");
        }
    }

    /**
     * Borrado seguro: si el servicio tiene reservas no se puede borrar (FK);
     * en su lugar se desactiva (activo=false, ya oculta de reservas nuevas) y
     * se devuelve un mensaje claro. Sin reservas → borrado físico como antes.
     *
     * @return mensaje para el cliente ("Servicio eliminado" o el de desactivación)
     */
    @Transactional
    public String delete(Long id) {
        Servicio servicio = servicioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + id));

        if (reservaRepository.existsByServicio_Id(id)) {
            servicio.setActivo(false);
            servicioRepository.save(servicio);
            return "El servicio tiene reservas; se desactivó";
        }
        servicioRepository.deleteById(id);
        return "Servicio eliminado";
    }

    @Transactional(readOnly = true)
    public boolean existsByNombre(String nombre) {
        return servicioRepository.existsByNombreIgnoreCase(nombre);
    }
}