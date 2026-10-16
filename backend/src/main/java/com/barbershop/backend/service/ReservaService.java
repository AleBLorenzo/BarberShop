package com.barbershop.backend.service;

import com.barbershop.backend.dto.DisponibilidadRequest;
import com.barbershop.backend.dto.DisponibilidadSlotDTO;
import com.barbershop.backend.dto.ReservaCancelRequest;
import com.barbershop.backend.dto.ReservaCreateRequest;
import com.barbershop.backend.dto.ReservaDTO;
import com.barbershop.backend.dto.ReservaReprogramarRequest;
import com.barbershop.backend.dto.ReservaResponse;
import com.barbershop.backend.event.ReservaCreadaEvent;
import com.barbershop.backend.event.ReservaCompletadaEvent;
import com.barbershop.backend.exception.ResourceNotFoundException;
import com.barbershop.backend.exception.SlotNotAvailableException;
import com.barbershop.backend.exception.TokenInvalidoException;
import com.barbershop.backend.mapper.ReservaMapper;
import com.barbershop.backend.model.Cliente;
import com.barbershop.backend.model.MetodoPago;
import com.barbershop.backend.model.Reserva;
import com.barbershop.backend.model.ReservaEstado;
import com.barbershop.backend.model.Servicio;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.ClienteRepository;
import com.barbershop.backend.repository.ReservaRepository;
import com.barbershop.backend.repository.ServicioRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.security.RateLimiterService;
import com.barbershop.backend.util.ClientNormalizer;
import com.barbershop.backend.util.SlotGenerator;
import com.barbershop.backend.util.TimeSlot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservaService {

    private final ReservaRepository reservaRepository;
    private final ClienteRepository clienteRepository;
    private final ServicioRepository servicioRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final ReservaMapper reservaMapper;
    private final SlotGenerator slotGenerator;
    private final ClienteService clienteService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final RateLimiterService rateLimiter;

    @Value("${app.booking.token-expiration-hours:24}")
    private int tokenExpirationHours;

    @Value("${app.booking.max-advance-days:60}")
    private int maxAdvanceDays;

    @Value("${app.business.opening-hour:9}")
    private int openingHour;

    @Value("${app.business.closing-hour:20}")
    private int closingHour;

    @Value("${app.business.closed-days:SUNDAY}")
    private String closedDaysProp;

    // ========== PUBLIC BOOKING FLOW ==========

    @Transactional
    public ReservaResponse crearReservaPublica(ReservaCreateRequest request) {
        // Anti-spam (B-MED-6, D1/D7): rate limit por IP + techo diario global.
        // Se comprueba ANTES de crear la reserva ni publicar ReservaCreadaEvent
        // → sobre el límite: 429 con Retry-After y sin envío de email.
        rateLimiter.registrarReserva(rateLimiter.ipReal());
        return crearReservaInterna(request, request.getTrabajadorId());
    }

    /**
     * Flujo del trabajador: el trabajador destino siempre viene del token JWT,
     * nunca del cuerpo de la petición (se ignora request.trabajadorId).
     */
    @Transactional
    public ReservaResponse crearReservaParaTrabajador(ReservaCreateRequest request, Long trabajadorId) {
        return crearReservaInterna(request, trabajadorId);
    }

    private ReservaResponse crearReservaInterna(ReservaCreateRequest request, Long trabajadorId) {
        // Validate client data (server-side, also when the endpoint skips @Valid)
        validarDatosCliente(request);

        // Validate service
        Servicio servicio = servicioRepository.findById(request.getServicioId())
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado"));

        if (!servicio.getActivo()) {
            throw new IllegalArgumentException("El servicio no está disponible");
        }

        // Validate worker (id del token en el flujo del trabajador)
        Trabajador trabajador = trabajadorRepository.findById(trabajadorId)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado"));

        if (!trabajador.getActivo()) {
            throw new IllegalArgumentException("El trabajador no está disponible");
        }

        // Validate date
        validarFecha(request.getFecha());
        validarHora(request.getFecha(), request.getHoraInicio(), servicio.getDuracionMinutos());

        // Validate time slot matches service duration - calculate horaFin from service duration
        LocalTime horaFin = request.getHoraInicio().plusMinutes(servicio.getDuracionMinutos());

        // Check availability WITH locking to prevent double-booking
        checkAndLockAvailability(trabajador, request.getFecha(), request.getHoraInicio(), horaFin);

        // Find or create client (teléfono → email → crear; sin duplicar por email).
        // D3: se pasan los valores YA normalizados (la validación de formato de
        // `validarDatosCliente` ya se ejecutó antes, sobre el texto escrito).
        Cliente cliente = clienteService.findOrCreateByTelefono(
                request.getClienteNombre(),
                ClientNormalizer.normalizarTelefono(request.getClienteTelefono()),
                ClientNormalizer.normalizarEmail(request.getClienteEmail()));

        // Create reservation
        Reserva reserva = Reserva.builder()
                .cliente(cliente)
                .trabajador(trabajador)
                .servicio(servicio)
                .fecha(request.getFecha())
                .horaInicio(request.getHoraInicio())
                .horaFin(horaFin)
                .estado(ReservaEstado.CONFIRMADA)
                .build();

        reserva = reservaRepository.save(reserva);
        // Sin el token en el log: quien lea los logs podría cancelar la cita (B-MED-7)
        log.info("Reserva creada: ID={}", reserva.getId());

        ReservaResponse response = reservaMapper.toResponse(reserva);

        // El correo se enviará tras el commit (listener AFTER_COMMIT, asíncrono)
        applicationEventPublisher.publishEvent(new ReservaCreadaEvent(response, request.getClienteEmail()));

        return response;
    }

    // ========== CLIENT DATA VALIDATION (compartido: público y trabajador) ==========

    private static final String EMAIL_PATTERN = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    private static final String PHONE_PATTERN = "^[+]?[0-9\\s-]{9,15}$";

    private void validarDatosCliente(ReservaCreateRequest request) {
        if (request.getClienteNombre() == null || request.getClienteNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del cliente es obligatorio");
        }
        String telefono = request.getClienteTelefono();
        if (telefono == null || telefono.isBlank()) {
            throw new IllegalArgumentException("El teléfono del cliente es obligatorio");
        }
        if (!telefono.matches(PHONE_PATTERN)) {
            throw new IllegalArgumentException("Formato de teléfono inválido");
        }
        validarEmail(request.getClienteEmail());
    }

    /** El email es opcional; si viene, debe tener formato válido (400 si no). */
    private void validarEmail(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        if (!email.matches(EMAIL_PATTERN)) {
            throw new IllegalArgumentException("El email no tiene un formato válido");
        }
    }

    @Transactional(readOnly = true)
    public List<DisponibilidadSlotDTO> consultarDisponibilidad(DisponibilidadRequest request) {
        // Días cerrados (domingos): no hay ningún hueco
        if (diasCerrados().contains(request.getFecha().getDayOfWeek())) {
            return List.of();
        }

        Servicio servicio = servicioRepository.findById(request.getServicioId())
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado"));

        List<Trabajador> trabajadores;
        if (request.getTrabajadorId() != null) {
            Trabajador t = trabajadorRepository.findById(request.getTrabajadorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado"));
            if (!t.getActivo()) {
                throw new IllegalArgumentException("El trabajador no está disponible");
            }
            trabajadores = List.of(t);
        } else {
            trabajadores = trabajadorRepository.findByActivoTrue();
        }

        // Get all occupied slots for the date
        List<Reserva> reservasOcupadas = reservaRepository.findByFechaBetweenOrderByFechaAscHoraInicioAsc(
                request.getFecha(), request.getFecha()
        ).stream()
                .filter(r -> r.getEstado() == ReservaEstado.PENDIENTE || r.getEstado() == ReservaEstado.CONFIRMADA)
                .toList();

        // Generate slots for each worker
        // Slots que ya han pasado hoy no se muestran (evita reservar a horas pasadas)
        boolean esHoy = request.getFecha().equals(LocalDate.now());
        LocalTime ahora = LocalTime.now();

        return trabajadores.stream()
                .flatMap(t -> {
                    List<SlotGenerator.ReservaSlot> slotsOcupados = reservasOcupadas.stream()
                            .filter(r -> r.getTrabajador().getId().equals(t.getId()))
                            .map(r -> SlotGenerator.ReservaSlot.builder()
                                    .inicio(r.getHoraInicio())
                                    .fin(r.getHoraFin())
                                    .build())
                            .toList();

                    List<TimeSlot> allSlots = slotGenerator.generateSlotsForWorker(
                            servicio.getDuracionMinutos(), slotsOcupados);

                    return allSlots.stream()
                            .filter(slot -> !esHoy || slot.getInicio().isAfter(ahora))
                            .map(slot -> DisponibilidadSlotDTO.builder()
                                    .horaInicio(slot.getInicio())
                                    .horaFin(slot.getFin())
                                    .disponible(slot.isDisponible())
                                    .trabajadorId(t.getId())
                                    .trabajadorNombre(t.getNombre())
                                    .build());
                })
                .collect(Collectors.toList());
    }

    // ========== CANCEL / REPROGRAMAR WITH TOKEN ==========

    @Transactional
    public ReservaResponse cancelarReserva(ReservaCancelRequest request) {
        Reserva reserva = reservaRepository.findByTokenCancelacion(request.getToken() != null ? request.getToken().toString() : null)
                .orElseThrow(() -> new TokenInvalidoException("Token de cancelación inválido"));

        if (reserva.getEstado() == ReservaEstado.CANCELADA) {
            throw new TokenInvalidoException("La reserva ya ha sido cancelada");
        }
        if (reserva.getEstado() == ReservaEstado.COMPLETADA) {
            throw new TokenInvalidoException("No se puede cancelar una reserva completada");
        }

        reserva.setEstado(ReservaEstado.CANCELADA);
        reserva = reservaRepository.save(reserva);
        log.info("Reserva cancelada: ID={}", reserva.getId());

        return reservaMapper.toResponse(reserva);
    }

    @Transactional
    public ReservaResponse reprogramarReserva(ReservaReprogramarRequest request) {
        Reserva reserva = reservaRepository.findByTokenCancelacion(request.getToken() != null ? request.getToken().toString() : null)
                .orElseThrow(() -> new TokenInvalidoException("Token de reprogramación inválido"));

        if (reserva.getEstado() == ReservaEstado.CANCELADA) {
            throw new TokenInvalidoException("No se puede reprogramar una reserva cancelada");
        }
        if (reserva.getEstado() == ReservaEstado.COMPLETADA) {
            throw new TokenInvalidoException("No se puede reprogramar una reserva completada");
        }

        // Validate new date
        validarFecha(request.getNuevaFecha());
        validarHora(request.getNuevaFecha(), request.getNuevaHoraInicio(),
                reserva.getServicio().getDuracionMinutos());

        // Validate new time matches service duration
        LocalTime expectedEnd = request.getNuevaHoraInicio().plusMinutes(reserva.getServicio().getDuracionMinutos());

        // Check new availability (with lock)
        checkAndLockAvailability(reserva.getTrabajador(), request.getNuevaFecha(),
                request.getNuevaHoraInicio(), expectedEnd);

        // Update reservation
        reserva.setFecha(request.getNuevaFecha());
        reserva.setHoraInicio(request.getNuevaHoraInicio());
        reserva.setHoraFin(expectedEnd);
        reserva.setEstado(ReservaEstado.CONFIRMADA);

        reserva = reservaRepository.save(reserva);
        log.info("Reserva reprogramada: ID={}, Nueva fecha={}, Nueva hora={}",
                reserva.getId(), reserva.getFecha(), reserva.getHoraInicio());

        return reservaMapper.toResponse(reserva);
    }

    // ========== WORKER ENDPOINTS ==========

    @Transactional(readOnly = true)
    public List<ReservaDTO> findMisReservas(Long trabajadorId, LocalDate fecha) {
        Trabajador trabajador = trabajadorRepository.findById(trabajadorId)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado"));

        return reservaRepository.findByTrabajadorAndFechaOrderByHoraInicioAsc(trabajador, fecha).stream()
                .map(reservaMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservaDTO> findMisReservasSemana(Long trabajadorId, LocalDate fechaInicio, LocalDate fechaFin) {
        Trabajador trabajador = trabajadorRepository.findById(trabajadorId)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado"));

        return reservaRepository.findByTrabajadorAndFechaBetweenOrderByHoraInicioAsc(trabajador, fechaInicio, fechaFin).stream()
                .map(reservaMapper::toDTO)
                .toList();
    }

    /**
     * Cambia el estado de una reserva. Al pasar a COMPLETADA es obligatorio
     * indicar el método de pago (visa/efectivo): sin él se rechaza con 400.
     * Demás estados NO_ASISTIO/CANCELADA/... no requieren método de pago.
     */
    @Transactional
    public ReservaDTO cambiarEstadoReserva(Long trabajadorId, Long reservaId, ReservaEstado nuevoEstado,
                                           String metodoPago) {
        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));

        if (!reserva.getTrabajador().getId().equals(trabajadorId)) {
            throw new SecurityException("No tiene permisos para modificar esta reserva");
        }

        MetodoPago pago = null;
        if (nuevoEstado == ReservaEstado.COMPLETADA) {
            if (metodoPago == null || metodoPago.isBlank()) {
                throw new IllegalArgumentException(
                        "Debe indicar si el cliente asistió y el método de pago (visa o efectivo)");
            }
            try {
                pago = MetodoPago.valueOf(metodoPago.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Método de pago no válido: use visa o efectivo");
            }
        }

        ReservaEstado estadoAnterior = reserva.getEstado();
        reserva.setEstado(nuevoEstado);
        if (pago != null) {
            reserva.setMetodoPago(pago);
        }
        reserva = reservaRepository.save(reserva);

        // Invitación a reseñar: solo en la transición → COMPLETADA (una vez)
        if (nuevoEstado == ReservaEstado.COMPLETADA && estadoAnterior != ReservaEstado.COMPLETADA) {
            ReservaResponse snapshot = reservaMapper.toResponse(reserva);
            String email = reserva.getCliente() != null ? reserva.getCliente().getEmail() : null;
            applicationEventPublisher.publishEvent(new ReservaCompletadaEvent(snapshot, email));
        }

        return reservaMapper.toDTO(reserva);
    }

    // ========== ADMIN ENDPOINTS ==========

    @Transactional(readOnly = true)
    public List<ReservaDTO> findAll() {
        return reservaRepository.findAll().stream()
                .map(reservaMapper::toDTO)
                .toList();
    }

    /**
     * Lookup a reservation by cancellation token, verifying the client's phone number.
     * Used by the public "Mis citas" view (phone + token).
     */
    @Transactional(readOnly = true)
    public List<ReservaDTO> buscarPorTelefonoYToken(String telefono, String token) {
        Reserva reserva = reservaRepository.findByTokenCancelacion(token)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontraron reservas con esos datos"));

        // Identidad canónica (D1): se compara contra telefono_normalizado — con
        // un cliente guardado como "+34612345678" basta consultar "612345678".
        // Si la columna aún no está rellena (fila previa al backfill) se
        // normaliza el teléfono original con la misma regla.
        Cliente cliente = reserva.getCliente();
        String telefonoReserva = estaVacio(cliente.getTelefonoNormalizado())
                ? ClientNormalizer.normalizarTelefono(cliente.getTelefono())
                : cliente.getTelefonoNormalizado();
        String telefonoConsulta = ClientNormalizer.normalizarTelefono(telefono);
        if (!Objects.equals(telefonoReserva, telefonoConsulta)) {
            throw new ResourceNotFoundException("No se encontraron reservas con esos datos");
        }
        return List.of(reservaMapper.toDTO(reserva));
    }

    private static boolean estaVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    @Transactional(readOnly = true)
    public Optional<ReservaDTO> findById(Long id) {
        return reservaRepository.findById(id)
                .map(reservaMapper::toDTO);
    }

    @Transactional
    public ReservaDTO updateReservaAdmin(Long id, ReservaDTO dto) {
        Reserva existing = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + id));

        // Update fields
        if (dto.getEstado() != null) {
            existing.setEstado(ReservaEstado.valueOf(dto.getEstado()));
        }
        if (dto.getFecha() != null) {
            existing.setFecha(dto.getFecha());
        }
        if (dto.getHoraInicio() != null) {
            existing.setHoraInicio(dto.getHoraInicio());
        }
        if (dto.getHoraFin() != null) {
            existing.setHoraFin(dto.getHoraFin());
        }

        return reservaMapper.toDTO(reservaRepository.save(existing));
    }

    @Transactional
    public void deleteReserva(Long id) {
        if (!reservaRepository.existsById(id)) {
            throw new ResourceNotFoundException("Reserva no encontrada con ID: " + id);
        }
        reservaRepository.deleteById(id);
    }

    // ========== PRIVATE HELPERS ==========

    private void validarFecha(LocalDate fecha) {
        LocalDate hoy = LocalDate.now();
        if (fecha.isBefore(hoy)) {
            throw new IllegalArgumentException("No se pueden hacer reservas para fechas pasadas");
        }
        if (diasCerrados().contains(fecha.getDayOfWeek())) {
            String dia = fecha.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"));
            throw new IllegalArgumentException("La barbería está cerrada el " + dia);
        }
        if (fecha.isAfter(hoy.plusDays(maxAdvanceDays))) {
            throw new IllegalArgumentException("No se pueden hacer reservas con más de " + maxAdvanceDays + " días de antelación");
        }
    }

    /**
     * Rechaza horas pasadas (hoy) y las que se salgan de la jornada:
     * empezar antes de la apertura o terminar después del cierre
     * (la última cita es cierre − duración del servicio).
     */
    private void validarHora(LocalDate fecha, LocalTime horaInicio, int duracionMinutos) {
        if (fecha.equals(LocalDate.now()) && !horaInicio.isAfter(LocalTime.now())) {
            throw new IllegalArgumentException("La hora seleccionada ya ha pasado. Elige una hora futura.");
        }
        if (horaInicio.isBefore(LocalTime.of(openingHour, 0))) {
            throw new IllegalArgumentException(String.format(
                    "La barbería abre a las %02d:00. Elige una hora igual o posterior.", openingHour));
        }
        if (horaInicio.plusMinutes(duracionMinutos).isAfter(LocalTime.of(closingHour, 0))) {
            throw new IllegalArgumentException(String.format(
                    "La última cita debe terminar a las %02d:00 como máximo (según la duración del servicio).",
                    closingHour));
        }
    }

    /** Días cerrados configurados (p. ej. SUNDAY) a partir de app.business.closed-days. */
    private Set<DayOfWeek> diasCerrados() {
        Set<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        if (closedDaysProp == null || closedDaysProp.isBlank()) {
            return dias;
        }
        for (String parte : closedDaysProp.split(",")) {
            String limpio = parte.trim();
            if (!limpio.isEmpty()) {
                dias.add(DayOfWeek.valueOf(limpio.toUpperCase(Locale.ROOT)));
            }
        }
        return dias;
    }

    /**
     * Checks availability and locks the conflicting rows to prevent double-booking.
     * Uses SELECT ... FOR UPDATE to lock the rows.
     */
    private void checkAndLockAvailability(Trabajador trabajador, LocalDate fecha,
                                           LocalTime horaInicio, LocalTime horaFin) {
        // First quick check without lock
        if (reservaRepository.existsConflictingReserva(trabajador, fecha, horaInicio, horaFin)) {
            throw new SlotNotAvailableException("El horario seleccionado ya no está disponible");
        }

        // Lock conflicting rows to prevent race condition
        List<Reserva> conflicting = reservaRepository.findConflictingReservasForUpdate(trabajador, fecha);

        // Double-check after lock
        for (Reserva r : conflicting) {
            if (r.overlaps(Reserva.builder()
                    .trabajador(trabajador)
                    .fecha(fecha)
                    .horaInicio(horaInicio)
                    .horaFin(horaFin)
                    .build())) {
                throw new SlotNotAvailableException("El horario seleccionado ya no está disponible");
            }
        }
    }
}