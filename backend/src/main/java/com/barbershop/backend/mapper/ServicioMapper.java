package com.barbershop.backend.mapper;

import com.barbershop.backend.dto.ServicioDTO;
import com.barbershop.backend.dto.ServicioSimpleDTO;
import com.barbershop.backend.model.Servicio;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public interface ServicioMapper {
    ServicioMapper INSTANCE = Mappers.getMapper(ServicioMapper.class);

    /**
     * Punto de entrada real para todos los consumidores: delega en el método generado
     * {@link #toDTOInternal} y calcula los derivados de oferta. (Patrón método {@code default}
     * en vez de {@code @AfterMapping}: MapStruct 1.5.5 sobre javac 25 no aplicaba los métodos
     * de ciclo de vida en este proyecto.)
     */
    default ServicioDTO toDTO(Servicio servicio) {
        ServicioDTO dto = toDTOInternal(servicio);
        if (dto != null) {
            calcularOferta(dto);
        }
        return dto;
    }

    /**
     * Los campos derivados de oferta ({@code precioOferta}/{@code ofertaActiva}) no existen en
     * la entidad: se ignoran aquí y se calculan en {@link #calcularOferta} (único origen de
     * verdad del redondeo).
     */
    @Mapping(target = "precioOferta", ignore = true)
    @Mapping(target = "ofertaActiva", ignore = true)
    ServicioDTO toDTOInternal(Servicio servicio);

    ServicioSimpleDTO toSimpleDTO(Servicio servicio);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Servicio toEntity(ServicioDTO dto);

    /**
     * Rellena los derivados de oferta: {@code ofertaActiva} ⇔ hay porcentaje (1–99) y la
     * ventana [inicio, fin) contiene el instante actual del servidor (expira sola por reloj,
     * sin cron); {@code precioOferta} = precio con el % aplicado (HALF_UP, 2 decimales), o el
     * precio normal cuando no está activa — así la UI que solo pinta si {@code ofertaActiva}
     * nunca muestra un descuento vencido. Se lee del DTO porque los campos base ya se mapearon.
     */
    default void calcularOferta(ServicioDTO dto) {
        Integer pct = dto.getDescuentoPorcentaje();
        if (pct != null && (pct < 1 || pct > 99)) {
            pct = null; // datos corruptos → sin oferta
        }
        LocalDateTime ahora = LocalDateTime.now();
        boolean activa = pct != null
                && dto.getOfertaInicio() != null
                && dto.getOfertaFin() != null
                && !dto.getOfertaInicio().isAfter(ahora)
                && dto.getOfertaFin().isAfter(ahora);

        if (activa) {
            dto.setPrecioOferta(dto.getPrecio()
                    .multiply(BigDecimal.valueOf(100 - pct))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            dto.setOfertaActiva(true);
        } else {
            dto.setPrecioOferta(dto.getPrecio());
            dto.setOfertaActiva(false);
        }
    }
}
