package com.barbershop.backend.service;

import com.barbershop.backend.dto.DashboardStatsDTO;
import com.barbershop.backend.dto.IngresoTrabajadorDTO;
import com.barbershop.backend.dto.IngresosResumenDTO;
import com.barbershop.backend.dto.TopServicioDTO;
import com.barbershop.backend.dto.WorkerDashboardDTO;
import com.barbershop.backend.mapper.ReservaMapper;
import com.barbershop.backend.model.MetodoPago;
import com.barbershop.backend.model.Reserva;
import com.barbershop.backend.model.ReservaEstado;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.ReservaRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final ReservaRepository reservaRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final ReservaMapper reservaMapper;

    @Transactional(readOnly = true)
    public DashboardStatsDTO getDashboardStats(LocalDate start, LocalDate end) {
        long totalReservas = reservaRepository.countCompletedReservasBetween(start, end)
                + reservaRepository.countNoShowsBetween(start, end)
                + reservaRepository.countByFechaBetweenAndEstadoIn(start, end, List.of(ReservaEstado.CANCELADA));

        long confirmadas = reservaRepository.countCompletedReservasBetween(start, end);
        long completadas = reservaRepository.countCompletedReservasBetween(start, end);
        long canceladas = reservaRepository.countByFechaBetweenAndEstadoIn(start, end, List.of(ReservaEstado.CANCELADA));
        long noShows = reservaRepository.countNoShowsBetween(start, end);

        BigDecimal ingresosTotales = reservaRepository.sumIngresosBetween(start, end);
        if (ingresosTotales == null) ingresosTotales = BigDecimal.ZERO;

        BigDecimal[] porMetodo = ingresosPorMetodoPago(start, end);

        BigDecimal ingresoPromedio = confirmadas > 0
                ? ingresosTotales.divide(BigDecimal.valueOf(confirmadas), 2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;

        // Calculate occupancy rate (simplified)
        double tasaOcupacion = calcularTasaOcupacion(start, end);
        double tasaNoShow = (confirmadas + noShows) > 0
                ? (double) noShows / (confirmadas + noShows) * 100
                : 0.0;

        return DashboardStatsDTO.builder()
                .totalReservas(totalReservas)
                .reservasConfirmadas(confirmadas)
                .reservasCompletadas(completadas)
                .reservasCanceladas(canceladas)
                .noShows(noShows)
                .ingresosTotales(ingresosTotales)
                .ingresoPromedioPorReserva(ingresoPromedio)
                .ingresosVisa(porMetodo[0])
                .ingresosEfectivo(porMetodo[1])
                .tasaOcupacion(Math.round(tasaOcupacion * 100.0) / 100.0)
                .tasaNoShow(Math.round(tasaNoShow * 100.0) / 100.0)
                .build();
    }

    @Transactional(readOnly = true)
    public List<TopServicioDTO> getTopServicios(LocalDate start, LocalDate end, int limit) {
        List<Object[]> results = reservaRepository.countReservasByServicioBetween(start, end);
        return results.stream()
                .limit(limit)
                .map(row -> {
                    com.barbershop.backend.model.Servicio servicio = (com.barbershop.backend.model.Servicio) row[0];
                    Long count = (Long) row[1];
                    BigDecimal ingresos = servicio.getPrecio().multiply(BigDecimal.valueOf(count));
                    return TopServicioDTO.builder()
                            .servicioId(servicio.getId())
                            .servicioNombre(servicio.getNombre())
                            .precio(servicio.getPrecio())
                            .totalReservas(count)
                            .ingresosGenerados(ingresos)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WorkerDashboardDTO getWorkerDashboard(Long trabajadorId, LocalDate fecha) {
        Trabajador trabajador = trabajadorRepository.findById(trabajadorId)
                .orElseThrow(() -> new IllegalArgumentException("Trabajador no encontrado"));

        List<Reserva> reservas = reservaRepository.findByTrabajadorAndFechaOrderByHoraInicioAsc(trabajador, fecha);
        List<Reserva> reservasActivas = reservas.stream()
                .filter(r -> r.getEstado() == ReservaEstado.CONFIRMADA || r.getEstado() == ReservaEstado.PENDIENTE)
                .toList();

        BigDecimal ingresos = reservas.stream()
                .filter(r -> r.getEstado() == ReservaEstado.COMPLETADA || r.getEstado() == ReservaEstado.CONFIRMADA)
                .map(r -> r.getServicio().getPrecio())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Desglose visa/efectivo: solo reservas COMPLETADA con método registrado
        // y SOLO del trabajador (D10: antes sumaba toda la barbería, fuga de
        // ingresos de compañeros y del admin en /api/worker/dashboard).
        BigDecimal[] porMetodo = sumarPorMetodo(
                reservaRepository.sumIngresosCompletadasPorMetodoPagoTrabajadorBetween(trabajador, fecha, fecha));

        int horasOcupadas = reservasActivas.stream()
                .mapToInt(r -> r.getHoraFin().getHour() * 60 + r.getHoraFin().getMinute()
                        - (r.getHoraInicio().getHour() * 60 + r.getHoraInicio().getMinute()))
                .sum() / 60;

        return WorkerDashboardDTO.builder()
                .trabajadorId(trabajador.getId())
                .trabajadorNombre(trabajador.getNombre())
                .fecha(fecha)
                .reservasDelDia(reservas.stream()
                        .map(reservaMapper::toDTO)
                        .peek(dto -> dto.setDiaSemana(fecha.getDayOfWeek()))
                        .collect(Collectors.toList()))
                .totalReservas(reservasActivas.size())
                .horasOcupadas(horasOcupadas)
                .ingresosDelDia(ingresos)
                .ingresosVisa(porMetodo[0])
                .ingresosEfectivo(porMetodo[1])
                .build();
    }

    /**
     * Desglose GLOBAL (toda la barbería) de ingresos COMPLETADA por método de
     * pago en el rango: [ingresosVisa, ingresosEfectivo]. Solo para
     * {@link #getDashboardStats} (ADMIN); nunca para endpoints de trabajador (D10).
     * Las reservas completadas sin método registrado (metodoPago null) cuentan
     * en el total pero no en ninguna franja.
     */
    private BigDecimal[] ingresosPorMetodoPago(LocalDate start, LocalDate end) {
        return sumarPorMetodo(reservaRepository.sumIngresosCompletadasPorMetodoPagoBetween(start, end));
    }

    /** Filas [metodoPago, suma] → [visa, efectivo] (el grupo null se omite). */
    private static BigDecimal[] sumarPorMetodo(List<Object[]> filas) {
        BigDecimal visa = BigDecimal.ZERO;
        BigDecimal efectivo = BigDecimal.ZERO;
        for (Object[] fila : filas) {
            MetodoPago metodo = (MetodoPago) fila[0];
            BigDecimal suma = fila[1] != null ? (BigDecimal) fila[1] : BigDecimal.ZERO;
            if (metodo == MetodoPago.VISA) {
                visa = visa.add(suma);
            } else if (metodo == MetodoPago.EFECTIVO) {
                efectivo = efectivo.add(suma);
            }
        }
        return new BigDecimal[]{visa, efectivo};
    }

    /**
     * Ingresos por trabajador en [start, end] (D10, solo ADMIN). Mismo
     * criterio que {@link #getDashboardStats}: total = COMPLETADA +
     * CONFIRMADA; desglose por método = solo COMPLETADA. Incluye a todos los
     * trabajadores activos (aunque no tengan citas, con ceros) y a los
     * inactivos que tengan citas en el rango; la suma de filas coincide con
     * {@code ingresosTotales} del dashboard.
     */
    @Transactional(readOnly = true)
    public IngresosResumenDTO getIngresosPorTrabajador(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            throw new IllegalArgumentException("La fecha fin debe ser igual o posterior a la fecha inicio");
        }
        Map<Long, IngresoTrabajadorDTO> filas = new LinkedHashMap<>();
        for (Trabajador t : trabajadorRepository.findByActivoTrue()) {
            filas.put(t.getId(), filaVacia(t));
        }
        for (Object[] fila : reservaRepository.sumIngresosPorTrabajadorEstadoMetodoBetween(start, end)) {
            Long trabajadorId = (Long) fila[0];
            ReservaEstado estado = (ReservaEstado) fila[1];
            MetodoPago metodo = (MetodoPago) fila[2];
            BigDecimal suma = fila[3] != null ? (BigDecimal) fila[3] : BigDecimal.ZERO;
            long citas = fila[4] != null ? (Long) fila[4] : 0L;

            IngresoTrabajadorDTO dto = filas.computeIfAbsent(trabajadorId, id -> trabajadorRepository.findById(id)
                    .map(StatsService::filaVacia)
                    .orElseGet(() -> filaVacia(null)));
            acumular(dto, estado, metodo, suma, citas);
        }

        IngresoTrabajadorDTO totales = filaVacia(null);
        for (IngresoTrabajadorDTO f : filas.values()) {
            totales.setTotal(totales.getTotal().add(f.getTotal()));
            totales.setVisa(totales.getVisa().add(f.getVisa()));
            totales.setEfectivo(totales.getEfectivo().add(f.getEfectivo()));
            totales.setSinMetodo(totales.getSinMetodo().add(f.getSinMetodo()));
            totales.setCompletadas(totales.getCompletadas() + f.getCompletadas());
            totales.setConfirmadas(totales.getConfirmadas() + f.getConfirmadas());
        }

        List<IngresoTrabajadorDTO> ordenadas = new ArrayList<>(filas.values());
        ordenadas.sort(Comparator.comparing(IngresoTrabajadorDTO::getTotal).reversed()
                .thenComparing(f -> f.getNombre() != null ? f.getNombre() : ""));
        return IngresosResumenDTO.builder()
                .inicio(start)
                .fin(end)
                .filas(ordenadas)
                .totales(totales)
                .build();
    }

    private static void acumular(IngresoTrabajadorDTO dto, ReservaEstado estado, MetodoPago metodo,
                                 BigDecimal suma, long citas) {
        dto.setTotal(dto.getTotal().add(suma));
        if (estado == ReservaEstado.COMPLETADA) {
            dto.setCompletadas(dto.getCompletadas() + citas);
            if (metodo == MetodoPago.VISA) {
                dto.setVisa(dto.getVisa().add(suma));
            } else if (metodo == MetodoPago.EFECTIVO) {
                dto.setEfectivo(dto.getEfectivo().add(suma));
            } else {
                dto.setSinMetodo(dto.getSinMetodo().add(suma));
            }
        } else {
            dto.setConfirmadas(dto.getConfirmadas() + citas);
        }
    }

    private static IngresoTrabajadorDTO filaVacia(Trabajador t) {
        return IngresoTrabajadorDTO.builder()
                .trabajadorId(t != null ? t.getId() : null)
                .nombre(t != null ? t.getNombre() : null)
                .rol(t != null && t.getRol() != null ? t.getRol().name() : null)
                .activo(t != null ? t.getActivo() : null)
                .total(BigDecimal.ZERO)
                .visa(BigDecimal.ZERO)
                .efectivo(BigDecimal.ZERO)
                .sinMetodo(BigDecimal.ZERO)
                .completadas(0L)
                .confirmadas(0L)
                .build();
    }

    private double calcularTasaOcupacion(LocalDate start, LocalDate end) {
        // Simplified: count working days * working hours * workers / actual booked hours
        long dias = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        long horasLaborablesPorDia = 12; // 9am to 9pm
        long trabajadoresActivos = trabajadorRepository.countByActivoTrue();
        long slotsTotales = dias * horasLaborablesPorDia * 2 * trabajadoresActivos; // 30 min slots

        long slotsOcupados = reservaRepository.countCompletedReservasBetween(start, end);

        return slotsTotales > 0 ? (double) slotsOcupados / slotsTotales * 100 : 0.0;
    }
}