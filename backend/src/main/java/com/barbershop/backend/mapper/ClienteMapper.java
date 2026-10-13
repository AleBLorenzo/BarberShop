package com.barbershop.backend.mapper;

import com.barbershop.backend.dto.ClienteDTO;
import com.barbershop.backend.dto.ClienteSimpleDTO;
import com.barbershop.backend.model.Cliente;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface ClienteMapper {
    ClienteMapper INSTANCE = Mappers.getMapper(ClienteMapper.class);

    @Mapping(target = "createdAt", source = "createdAt")
    ClienteDTO toDTO(Cliente cliente);

    ClienteSimpleDTO toSimpleDTO(Cliente cliente);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Cliente toEntity(ClienteDTO dto);
}