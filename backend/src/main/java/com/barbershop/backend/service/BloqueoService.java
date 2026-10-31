package com.barbershop.backend.service;

import com.barbershop.backend.dto.BloqueoConflictoDTO;
import com.barbershop.backend.dto.BloqueoCreateRequest;
import com.barbershop.backend.dto.BloqueoDTO;
import com.barbershop.backend.dto.ReservaConflictoDTO;
import com.barbershop.backend.exception.ConflictoException;
import com.barbershop.backend.exception.ResourceNotFoundException;
import com.barbershop.backend.model.BloqueoHorario;
import com.barbershop.backend.model.Reserva;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.BloqueoHorarioRepository;
import com.barbershop.backend.repository.ReservaRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Bloqueos de horas libres del propio trabajador (D5). A diferencia de un
 * cierre, un bloqueo NUNCA cancela citas: si pisa una cita activa u otro
 * bloqueo se rechaza con 409 y el detalle de los conflictos.
 *
 * <p>El trabajador lo decide siempre el llamador a partir del JWT
 * ({@code WorkerController}); este servicio no acepta un trabajador del
 * cuerpo. La creación adquiere el mismo lock pesimista que la reserva (D3),
 * así que una reserva y un bloqueo simultáneos sobre la misma franja nunca
 * tienen éxito los dos.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BloqueoService {

    private static final int MAX_MOTIVO = 255;
    /** Rango máximo de los listados (un año). */
    static final int MAX_DIAS_LISTADO = 366;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final BloqueoHorarioRepository bloqueoRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final ReservaRepository reservaRepository;
    private final AgendaService agendaService;

    // ========== Trabajador (propios) ==========

    @Transactional
    public BloqueoDTO crear(Long trabajadorId, BloqueoCreateRequest req) {
        if (req == null || req.getFecha() == null || req.getHoraInicio() == null || req.getHoraFin() == null) {
            throw new IllegalArgumentException("Indica la fecha, la hora de inicio y la hora de fin");
        }
        LocalDate fecha = req.getFecha();
        LocalTime inicio = req.getHoraInicio();
        LocalTime fin = req.getHoraFin();
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("La hora de fin debe ser posterior a la de inicio");
        }
        LocalDate hoy = LocalDate.now();
        if (fecha.isBefore(hoy)) {
            throw new IllegalArgumentException("No se pueden bloquear fechas pasadas");
        }
        if (fecha.equals(hoy) && inicio.isBefore(LocalTime.now())) {
            throw new IllegalArgumentException("La franja ya ha empezado: elige una hora futura");
        }
        String motivo = limpiarMotivo(req.getMotivo());

        // Lock D3 primero: serializa con reservas, cierres y otros bloqueos del trabajador
        Trabajador trabajador = trabajadorRepository.findByIdForUpdate(trabajadorId)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado"));

        AgendaService.DiaAgenda dia = agendaService.dia(trabajador, fecha);
        if (!dia.trabaja()) {
            throw new IllegalArgumentException(AgendaService.describirSinJornada(dia)
                    + ": no hace falta bloquear horas");
        }
        AgendaService.Jornada jornada = dia.jornada();
        if (!jornada.contiene(inicio, fin)) {
            throw new IllegalArgumentException("El bloqueo debe estar dentro de la jornada ("
                    + jornada.inicio().format(HORA) + "–" + jornada.fin().format(HORA) + ")");
        }

        List<Reserva> citas = reservaRepository.findActivasSolapadas(trabajador.getId(), fecha, inicio, fin);
        List<BloqueoHorario> bloqueos = bloqueoRepository.findSolapados(trabajador.getId(), fecha, inicio, fin);
        if (!citas.isEmpty() || !bloqueos.isEmpty()) {
            BloqueoConflictoDTO detalle = BloqueoConflictoDTO.builder()
                    .citas(citas.stream().map(BloqueoService::toCita).toList())
                    .bloqueos(bloqueos.stream().map(BloqueoService::toDTO).toList())
                    .build();
            String mensaje = !citas.isEmpty()
                    ? "No se puede bloquear: hay " + citas.size() + " cita(s) activa(s) en esa franja"
                    : "La franja se solapa con otro bloqueo";
            throw new ConflictoException(mensaje, "BLOQUEO_CONFLICTO", detalle);
        }

        BloqueoHorario bloqueo = bloqueoRepository.save(BloqueoHorario.builder()
                .trabajador(trabajador)
                .fecha(fecha)
                .horaInicio(inicio)
                .horaFin(fin)
                .motivo(motivo)
                .build());
        log.info("Bloqueo creado: ID={}, trabajador={}, fecha={}", bloqueo.getId(), trabajador.getId(), fecha);
        return toDTO(bloqueo);
    }

    @Transactional(readOnly = true)
    public List<BloqueoDTO> listarPropios(Long trabajadorId, LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        return bloqueoRepository
                .findByTrabajador_IdAndFechaBetweenOrderByFechaAscHoraInicioAsc(trabajadorId, desde, hasta)
                .stream()
                .map(BloqueoService::toDTO)
                .toList();
    }

    /** Un bloqueo ajeno responde igual que uno inexistente (404, no revela su existencia). */
    @Transactional
    public void eliminarPropio(Long trabajadorId, Long bloqueoId) {
        BloqueoHorario bloqueo = bloqueoRepository.findById(bloqueoId)
                .filter(b -> b.getTrabajador().getId().equals(trabajadorId))
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueo no encontrado"));
        bloqueoRepository.delete(bloqueo);
    }

    // ========== Admin (cualquier trabajador; no crea bloqueos ajenos) ==========

    @Transactional(readOnly = true)
    public List<BloqueoDTO> listarAdmin(Long trabajadorId, LocalDate desde, LocalDate hasta) {
        if (trabajadorId != null) {
            return listarPropios(trabajadorId, desde, hasta);
        }
        validarRango(desde, hasta);
        return bloqueoRepository.findByFechaBetween(desde, hasta).stream()
                .map(BloqueoService::toDTO)
                .toList();
    }

    @Transactional
    public void eliminarAdmin(Long bloqueoId) {
        BloqueoHorario bloqueo = bloqueoRepository.findById(bloqueoId)
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueo no encontrado"));
        bloqueoRepository.delete(bloqueo);
    }

    // ========== Utilidades ==========

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Indica las fechas desde y hasta");
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha hasta debe ser igual o posterior a la fecha desde");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAX_DIAS_LISTADO) {
            throw new IllegalArgumentException("El rango no puede superar " + MAX_DIAS_LISTADO + " días");
        }
    }

    private static String limpiarMotivo(String motivo) {
        String limpio = motivo == null || motivo.isBlank() ? null : motivo.trim();
        if (limpio != null && limpio.length() > MAX_MOTIVO) {
            throw new IllegalArgumentException("El motivo no puede exceder " + MAX_MOTIVO + " caracteres");
        }
        return limpio;
    }

    static BloqueoDTO toDTO(BloqueoHorario b) {
        return BloqueoDTO.builder()
                .id(b.getId())
                .trabajadorId(b.getTrabajador().getId())
                .trabajadorNombre(b.getTrabajador().getNombre())
                .fecha(b.getFecha())
                .horaInicio(b.getHoraInicio())
                .horaFin(b.getHoraFin())
                .motivo(b.getMotivo())
                .createdAt(b.getCreatedAt())
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
                .tipo("SOLAPA_BLOQUEO")
                .motivo("Cita activa dentro de la franja")
                .build();
    }
}
