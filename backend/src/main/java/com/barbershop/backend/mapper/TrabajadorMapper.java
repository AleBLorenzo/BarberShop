package com.barbershop.backend.mapper;

import com.barbershop.backend.dto.TrabajadorCreateRequest;
import com.barbershop.backend.dto.TrabajadorDTO;
import com.barbershop.backend.dto.TrabajadorSimpleDTO;
import com.barbershop.backend.model.Trabajador;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface TrabajadorMapper {
    TrabajadorMapper INSTANCE = Mappers.getMapper(TrabajadorMapper.class);

    TrabajadorDTO toDTO(Trabajador trabajador);

    TrabajadorSimpleDTO toSimpleDTO(Trabajador trabajador);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "activo", source = "activo")
    Trabajador toEntity(TrabajadorCreateRequest request);
}