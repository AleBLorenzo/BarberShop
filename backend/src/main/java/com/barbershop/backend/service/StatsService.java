package com.barbershop.backend.service;

import com.barbershop.backend.dto.DashboardStatsDTO;
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
import java.util.List;
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
        BigDecimal[] porMetodo = ingresosPorMetodoPago(fecha, fecha);

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
     * Desglose de ingresos COMPLETADA por método de pago en el rango:
     * [ingresosVisa, ingresosEfectivo]. Las reservas completadas sin método
     * registrado (metodoPago null) cuentan en el total pero no en ninguna franja.
     */
    private BigDecimal[] ingresosPorMetodoPago(LocalDate start, LocalDate end) {
        BigDecimal visa = BigDecimal.ZERO;
        BigDecimal efectivo = BigDecimal.ZERO;
        for (Object[] fila : reservaRepository.sumIngresosCompletadasPorMetodoPagoBetween(start, end)) {
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