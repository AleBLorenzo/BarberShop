package com.barbershop.backend.service;

import com.barbershop.backend.dto.ResultadoDTO;
import com.barbershop.backend.exception.ResourceNotFoundException;
import com.barbershop.backend.model.Resultado;
import com.barbershop.backend.repository.ResultadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Galería «Resultados»: lectura pública (portada) y CRUD administrativo.
 * Patrón idéntico a {@link ResenaService}.
 */
@Service
@RequiredArgsConstructor
public class ResultadoService {

    private final ResultadoRepository resultadoRepository;

    /** Portada: las más recientes primero. */
    @Transactional(readOnly = true)
    public List<ResultadoDTO> findAll() {
        return resultadoRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public ResultadoDTO create(ResultadoDTO dto) {
        Resultado resultado = Resultado.builder()
                .url(dto.getUrl().trim())
                .titulo(dto.getTitulo() != null && !dto.getTitulo().isBlank()
                        ? dto.getTitulo().trim() : null)
                .build();
        return toDTO(resultadoRepository.save(resultado));
    }

    @Transactional
    public ResultadoDTO update(Long id, ResultadoDTO dto) {
        Resultado resultado = resultadoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resultado no encontrado"));
        resultado.setUrl(dto.getUrl().trim());
        resultado.setTitulo(dto.getTitulo() != null && !dto.getTitulo().isBlank()
                ? dto.getTitulo().trim() : null);
        return toDTO(resultadoRepository.save(resultado));
    }

    @Transactional
    public void delete(Long id) {
        if (!resultadoRepository.existsById(id)) {
            throw new ResourceNotFoundException("Resultado no encontrado");
        }
        resultadoRepository.deleteById(id);
    }

    private ResultadoDTO toDTO(Resultado resultado) {
        return ResultadoDTO.builder()
                .id(resultado.getId())
                .url(resultado.getUrl())
                .titulo(resultado.getTitulo())
                .createdAt(resultado.getCreatedAt())
                .build();
    }
}
