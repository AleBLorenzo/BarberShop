package com.barbershop.backend.service;

import com.barbershop.backend.dto.AvisoCancelacionDTO;
import com.barbershop.backend.dto.CierreCreateRequest;
import com.barbershop.backend.dto.CierreDTO;
import com.barbershop.backend.dto.CierrePreviewDTO;
import com.barbershop.backend.dto.CierreResultadoDTO;
import com.barbershop.backend.dto.ReservaConflictoDTO;
import com.barbershop.backend.exception.ConflictoException;
import com.barbershop.backend.exception.ResourceNotFoundException;
import com.barbershop.backend.model.AvisoCancelacion;
import com.barbershop.backend.model.CanalAviso;
import com.barbershop.backend.model.CierreDia;
import com.barbershop.backend.model.Reserva;
import com.barbershop.backend.model.ReservaEstado;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.CierreDiaRepository;
import com.barbershop.backend.repository.ReservaRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Cierres de día (D4): de toda la barbería (festivo) o del día de un
 * trabajador. Crear un cierre cancela, en la misma transacción, las citas
 * activas afectadas mediante {@link ReservaService#cancelarPorNegocio} (que
 * crea el aviso y publica el evento del email AFTER_COMMIT: un rollback no
 * envía nada). Eliminar un cierre solo restaura la disponibilidad: las citas
 * canceladas siguen CANCELADA.
 *
 * <p>Concurrencia (D3): el cierre de un trabajador adquiere el lock de su
 * fila; el general, el de TODOS los trabajadores (incluidos los inactivos,
 * ver {@link TrabajadorRepository#findAllIncluyendoInactivosForUpdateOrderById})
 * en orden ascendente de id. La comprobación de duplicados y la selección de
 * citas se hacen ya bajo el lock, de modo que una reserva concurrente o bien
 * entra antes (y se cancela) o bien espera y ve el cierre (400).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CierreService {

    private static final List<ReservaEstado> ACTIVAS = List.of(ReservaEstado.PENDIENTE, ReservaEstado.CONFIRMADA);
    private static final int MAX_MOTIVO = 255;
    /** Rango máximo del listado (un año). */
    static final int MAX_DIAS_LISTADO = 366;

    private final CierreDiaRepository cierreDiaRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final ReservaRepository reservaRepository;
    private final ReservaService reservaService;

    // ========== Crear ==========

    @Transactional
    public CierreResultadoDTO crear(CierreCreateRequest request, Long adminId) {
        if (request == null || request.getFecha() == null) {
            throw new IllegalArgumentException("La fecha es obligatoria");
        }
        LocalDate fecha = request.getFecha();
        validarFechaFutura(fecha, "No se pueden cerrar fechas pasadas");
        String motivo = limpiarMotivo(request.getMotivo());

        // Lock D3 ANTES de comprobar duplicados y leer citas
        Trabajador trabajador = null;
        if (request.getTrabajadorId() != null) {
            trabajador = trabajadorRepository.findByIdForUpdate(request.getTrabajadorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado"));
            if (cierreDiaRepository.findFirstByFechaAndTrabajador_Id(fecha, trabajador.getId()).isPresent()) {
                throw new ConflictoException("Ya existe un cierre de " + trabajador.getNombre() + " para esa fecha",
                        "CIERRE_DUPLICADO");
            }
        } else {
            trabajadorRepository.findAllIncluyendoInactivosForUpdateOrderById();
            if (cierreDiaRepository.findFirstByFechaAndTrabajadorIsNull(fecha).isPresent()) {
                throw new ConflictoException("La barbería ya está cerrada esa fecha", "CIERRE_DUPLICADO");
            }
        }

        Trabajador admin = adminId != null ? trabajadorRepository.findById(adminId).orElse(null) : null;
        CierreDia cierre = cierreDiaRepository.save(CierreDia.builder()
                .fecha(fecha)
                .trabajador(trabajador)
                .motivo(motivo)
                .creadoPor(admin)
                .build());

        // Misma transacción: si una cancelación falla, no queda ni el cierre
        // ni ninguna cita cancelada, y no se envía ningún email (AFTER_COMMIT).
        int porEmail = 0;
        int porTelefono = 0;
        List<AvisoCancelacionDTO> avisos = new ArrayList<>();
        List<Reserva> afectadas = afectadas(fecha, trabajador);
        for (Reserva reserva : afectadas) {
            AvisoCancelacion aviso = reservaService.cancelarPorNegocio(reserva, motivo, adminId);
            if (aviso.getCanal() == CanalAviso.EMAIL) {
                porEmail++;
            } else {
                porTelefono++;
            }
            avisos.add(AvisoCancelacionService.toDTO(aviso));
        }
        log.info("Cierre creado: ID={}, fecha={}, trabajador={}, canceladas={}",
                cierre.getId(), fecha, trabajador != null ? trabajador.getId() : "TODOS", afectadas.size());

        return CierreResultadoDTO.builder()
                .cierre(toDTO(cierre))
                .canceladas(afectadas.size())
                .porEmail(porEmail)
                .porTelefono(porTelefono)
                .avisos(avisos)
                .build();
    }

    // ========== Vista previa / listado / eliminar ==========

    /** Citas activas que cancelaría el cierre, sin modificar nada. */
    @Transactional(readOnly = true)
    public CierrePreviewDTO preview(LocalDate fecha, Long trabajadorId) {
        if (fecha == null) {
            throw new IllegalArgumentException("La fecha es obligatoria");
        }
        Trabajador trabajador = null;
        boolean yaExiste;
        if (trabajadorId != null) {
            trabajador = trabajadorRepository.findById(trabajadorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado"));
            yaExiste = cierreDiaRepository.findFirstByFechaAndTrabajador_Id(fecha, trabajadorId).isPresent();
        } else {
            yaExiste = cierreDiaRepository.findFirstByFechaAndTrabajadorIsNull(fecha).isPresent();
        }

        List<ReservaConflictoDTO> citas = afectadas(fecha, trabajador).stream()
                .map(CierreService::toCita)
                .toList();
        int porEmail = (int) citas.stream().filter(c -> Boolean.TRUE.equals(c.getClienteTieneEmail())).count();
        return CierrePreviewDTO.builder()
                .fecha(fecha)
                .general(trabajador == null)
                .trabajadorId(trabajador != null ? trabajador.getId() : null)
                .trabajadorNombre(trabajador != null ? trabajador.getNombre() : null)
                .yaExiste(yaExiste)
                .total(citas.size())
                .porEmail(porEmail)
                .porTelefono(citas.size() - porEmail)
                .citas(citas)
                .build();
    }

    @Transactional(readOnly = true)
    public List<CierreDTO> listar(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Indica las fechas desde y hasta");
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha hasta debe ser igual o posterior a la fecha desde");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAX_DIAS_LISTADO) {
            throw new IllegalArgumentException("El rango no puede superar " + MAX_DIAS_LISTADO + " días");
        }
        return cierreDiaRepository.findByFechaBetween(desde, hasta).stream()
                .map(CierreService::toDTO)
                .toList();
    }

    /**
     * Reabre el día: borra el cierre (solo hoy o futuro, 400 si es pasado).
     * NO reactiva las citas que canceló (el cliente ya fue avisado).
     */
    @Transactional
    public void eliminar(Long id) {
        CierreDia cierre = cierreDiaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cierre no encontrado"));
        validarFechaFutura(cierre.getFecha(), "No se pueden eliminar cierres de fechas pasadas");
        cierreDiaRepository.delete(cierre);
        log.info("Cierre eliminado: ID={}, fecha={}", id, cierre.getFecha());
    }

    // ========== Utilidades ==========

    /** Citas activas afectadas: todas las del día (general) o las del trabajador. */
    private List<Reserva> afectadas(LocalDate fecha, Trabajador trabajador) {
        if (trabajador == null) {
            return reservaRepository.findByFechaAndEstadoInOrderByHoraInicioAsc(fecha, ACTIVAS);
        }
        return reservaRepository.findByTrabajadorAndFechaAndEstadoIn(trabajador, fecha, ACTIVAS).stream()
                .sorted((a, b) -> a.getHoraInicio().compareTo(b.getHoraInicio()))
                .toList();
    }

    private static void validarFechaFutura(LocalDate fecha, String mensaje) {
        if (fecha.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(mensaje);
        }
    }

    private static String limpiarMotivo(String motivo) {
        String limpio = motivo == null || motivo.isBlank() ? null : motivo.trim();
        if (limpio != null && limpio.length() > MAX_MOTIVO) {
            throw new IllegalArgumentException("El motivo no puede exceder " + MAX_MOTIVO + " caracteres");
        }
        return limpio;
    }

    static CierreDTO toDTO(CierreDia c) {
        Trabajador t = c.getTrabajador();
        Trabajador creador = c.getCreadoPor();
        return CierreDTO.builder()
                .id(c.getId())
                .fecha(c.getFecha())
                .general(t == null)
                .trabajadorId(t != null ? t.getId() : null)
                .trabajadorNombre(t != null ? t.getNombre() : null)
                .motivo(c.getMotivo())
                .creadoPorNombre(creador != null ? creador.getNombre() : null)
                .createdAt(c.getCreatedAt())
                .build();
    }

    private static ReservaConflictoDTO toCita(Reserva r) {
        String email = r.getCliente().getEmail();
        return ReservaConflictoDTO.builder()
                .reservaId(r.getId())
                .fecha(r.getFecha())
                .horaInicio(r.getHoraInicio())
                .horaFin(r.getHoraFin())
                .estado(r.getEstado().name())
                .clienteNombre(r.getCliente().getNombre())
                .clienteTelefono(r.getCliente().getTelefono())
                .clienteTieneEmail(email != null && !email.isBlank())
                .trabajadorId(r.getTrabajador().getId())
                .trabajadorNombre(r.getTrabajador().getNombre())
                .servicioNombre(r.getServicio().getNombre())
                .tipo("CIERRE")
                .motivo("Se cancelará por el cierre")
                .build();
    }
}
