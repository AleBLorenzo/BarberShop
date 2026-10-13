package com.barbershop.backend.service;

import com.barbershop.backend.dto.ResenaDTO;
import com.barbershop.backend.exception.ResourceNotFoundException;
import com.barbershop.backend.model.Resena;
import com.barbershop.backend.repository.ResenaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reseñas de la web: lectura pública (portada) y CRUD de administración.
 */
@Service
@RequiredArgsConstructor
public class ResenaService {

    private final ResenaRepository resenaRepository;

    /** Portada: más recientes primero. */
    @Transactional(readOnly = true)
    public List<ResenaDTO> findAll() {
        return resenaRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public ResenaDTO create(ResenaDTO dto) {
        Resena resena = Resena.builder()
                .autor(dto.getAutor().trim())
                .puntuacion(dto.getPuntuacion())
                .texto(dto.getTexto().trim())
                .build();
        return toDTO(resenaRepository.save(resena));
    }

    @Transactional
    public ResenaDTO update(Long id, ResenaDTO dto) {
        Resena resena = resenaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reseña no encontrada"));
        resena.setAutor(dto.getAutor().trim());
        resena.setPuntuacion(dto.getPuntuacion());
        resena.setTexto(dto.getTexto().trim());
        return toDTO(resenaRepository.save(resena));
    }

    @Transactional
    public void delete(Long id) {
        if (!resenaRepository.existsById(id)) {
            throw new ResourceNotFoundException("Reseña no encontrada");
        }
        resenaRepository.deleteById(id);
    }

    private ResenaDTO toDTO(Resena resena) {
        return ResenaDTO.builder()
                .id(resena.getId())
                .autor(resena.getAutor())
                .puntuacion(resena.getPuntuacion())
                .texto(resena.getTexto())
                .createdAt(resena.getCreatedAt())
                .build();
    }
}
