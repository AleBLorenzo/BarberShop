package com.barbershop.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Un día de la agenda efectiva del trabajador autenticado
 * ({@code GET /api/worker/agenda}, D2/D11). Si {@code trabaja = false},
 * {@code inicio}/{@code fin} son null y {@code causa} dice por qué.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiaAgendaDTO {
    private LocalDate fecha;
    private Boolean trabaja;
    private LocalTime inicio;
    private LocalTime fin;
    /**
     * null si trabaja; si no: TRABAJADOR_INACTIVO | CIERRE_GENERAL |
     * BARBERIA_CERRADA | CIERRE_TRABAJADOR | DIA_LIBRE.
     */
    private String causa;
    /** ¿Cierra la barbería entera (CIERRE_GENERAL / BARBERIA_CERRADA)? */
    private Boolean cerradoBarberia;
    /** Motivo del cierre puntual (general o propio), si lo tiene. */
    private String motivoCierre;
    /** Texto legible ("La barbería está cerrada ese día (Festivo)"). */
    private String descripcion;
    /** Bloqueos propios del día (vacío si no trabaja). */
    private List<BloqueoDTO> bloqueos;
}
