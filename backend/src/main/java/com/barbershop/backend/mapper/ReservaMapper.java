package com.barbershop.backend.mapper;

import com.barbershop.backend.dto.ClienteSimpleDTO;
import com.barbershop.backend.dto.ReservaDTO;
import com.barbershop.backend.dto.ReservaResponse;
import com.barbershop.backend.dto.ServicioSimpleDTO;
import com.barbershop.backend.dto.TrabajadorSimpleDTO;
import com.barbershop.backend.model.Cliente;
import com.barbershop.backend.model.Reserva;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.model.Trabajador;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring", uses = {ClienteMapper.class, TrabajadorMapper.class, ServicioMapper.class})
public interface ReservaMapper {
    ReservaMapper INSTANCE = Mappers.getMapper(ReservaMapper.class);

    @Mapping(target = "cliente", source = "cliente", qualifiedByName = "toClienteSimpleDTO")
    @Mapping(target = "trabajador", source = "trabajador", qualifiedByName = "toTrabajadorSimpleDTO")
    @Mapping(target = "servicio", source = "servicio", qualifiedByName = "toServicioSimpleDTO")
    @Mapping(target = "precioTotal", source = "servicio.precio")
    @Mapping(target = "duracionMinutos", source = "servicio.duracionMinutos")
    ReservaDTO toDTO(Reserva reserva);

    @Mapping(target = "clienteNombre", source = "cliente.nombre")
    @Mapping(target = "clienteTelefono", source = "cliente.telefono")
    @Mapping(target = "trabajadorNombre", source = "trabajador.nombre")
    @Mapping(target = "servicioNombre", source = "servicio.nombre")
    @Mapping(target = "precioTotal", source = "servicio.precio")
    @Mapping(target = "duracionMinutos", source = "servicio.duracionMinutos")
    @Mapping(target = "mensaje", constant = "Reserva realizada correctamente")
    ReservaResponse toResponse(Reserva reserva);

    @Named("toClienteSimpleDTO")
    default ClienteSimpleDTO toClienteSimpleDTO(Cliente cliente) {
        if (cliente == null) return null;
        return ClienteSimpleDTO.builder()
                .id(cliente.getId())
                .nombre(cliente.getNombre())
                .telefono(cliente.getTelefono())
                .build();
    }

    @Named("toTrabajadorSimpleDTO")
    default TrabajadorSimpleDTO toTrabajadorSimpleDTO(Trabajador trabajador) {
        if (trabajador == null) return null;
        return TrabajadorSimpleDTO.builder()
                .id(trabajador.getId())
                .nombre(trabajador.getNombre())
                .activo(trabajador.getActivo())
                .foto(trabajador.getFoto())
                .build();
    }

    @Named("toServicioSimpleDTO")
    default ServicioSimpleDTO toServicioSimpleDTO(Servicio servicio) {
        if (servicio == null) return null;
        return ServicioSimpleDTO.builder()
                .id(servicio.getId())
                .nombre(servicio.getNombre())
                .precio(servicio.getPrecio())
                .duracionMinutos(servicio.getDuracionMinutos())
                .foto(servicio.getFoto())
                .build();
    }

    @Named("toDayOfWeek")
    default DayOfWeek toDayOfWeek(LocalDate fecha) {
        return fecha == null ? null : fecha.getDayOfWeek();
    }
}