package com.barbershop.backend.service;

import com.barbershop.backend.dto.DisponibilidadRequest;
import com.barbershop.backend.dto.DisponibilidadSlotDTO;
import com.barbershop.backend.dto.ReservaCancelRequest;
import com.barbershop.backend.dto.ReservaCreateRequest;
import com.barbershop.backend.dto.ReservaDTO;
import com.barbershop.backend.dto.ReservaReprogramarRequest;
import com.barbershop.backend.dto.ReservaResponse;
import com.barbershop.backend.event.ReservaCanceladaPorNegocioEvent;
import com.barbershop.backend.event.ReservaCreadaEvent;
import com.barbershop.backend.event.ReservaCompletadaEvent;
import com.barbershop.backend.exception.ResourceNotFoundException;
import com.barbershop.backend.exception.SlotNotAvailableException;
import com.barbershop.backend.exception.TokenInvalidoException;
import com.barbershop.backend.mapper.ReservaMapper;
import com.barbershop.backend.model.AvisoCancelacion;
import com.barbershop.backend.model.CanceladaPor;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

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
    private final AgendaService agendaService;
    private final AvisoCancelacionService avisoCancelacionService;

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MAX_MOTIVO = 255;

    @Value("${app.booking.token-expiration-hours:24}")
    private int tokenExpirationHours;

    @Value("${app.booking.max-advance-days:60}")
    private int maxAdvanceDays;

    // El horario ya no se lee de app.business.* (solo siembra, D9): la jornada
    // efectiva sale de AgendaService (D2).

    // ========== PUBLIC BOOKING FLOW ==========

    @Transactional
    public ReservaResponse crearReservaPublica(ReservaCreateRequest request) {
        // Anti-spam (B-MED-6, D1/D7): rate limit por IP + techo diario global.
        // Se comprueba ANTES de crear la reserva ni publicar ReservaCreadaEvent
        // → sobre el límite: 429 con Retry-After y sin envío de email.
        rateLimiter.registrarReserva(rateLimiter.ipReal());
        return crearReservaInterna(request, request.getTrabajadorId(), true);
    }

    /**
     * Flujo del trabajador: el trabajador destino siempre viene del token JWT,
     * nunca del cuerpo de la petición (se ignora request.trabajadorId).
     */
    @Transactional
    public ReservaResponse crearReservaParaTrabajador(ReservaCreateRequest request, Long trabajadorId) {
        // D8: el panel (incluido el admin desde "Mi panel") crea citas propias
        // aunque el trabajador no sea reservable en la web.
        return crearReservaInterna(request, trabajadorId, false);
    }

    private ReservaResponse crearReservaInterna(ReservaCreateRequest request, Long trabajadorId,
                                                boolean exigirReservable) {
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
        if (exigirReservable && !Boolean.TRUE.equals(trabajador.getReservable())) {
            throw new IllegalArgumentException("El trabajador no admite reservas online");
        }

        // Validate date (pasado / antelación) y hora pasada
        validarFecha(request.getFecha());
        validarHoraNoPasada(request.getFecha(), request.getHoraInicio());

        // Validate time slot matches service duration - calculate horaFin from service duration
        LocalTime horaFin = request.getHoraInicio().plusMinutes(servicio.getDuracionMinutos());

        // Lock del trabajador + jornada, cierres, bloqueos y solapes (D3)
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

    /**
     * El email es opcional; si viene, debe tener formato válido (400 si no).
     * Cadena de degradación sin email (cambio optional-client-email-on-booking,
     * capacidad booking-without-email-degradation): {@code null}/blank →
     * {@link ClientNormalizer#normalizarEmail} lo vuelve {@code null} →
     * {@code ClienteService.findOrCreateByTelefono} crea/reutiliza la ficha sin
     * email → {@code EmailService} omite la confirmación y la invitación a
     * reseñar (guardas en las líneas 58-59 y 92-93). Aquí no hay nada que
     * cambiar: es la verificación de que el backend ya degrada.
     */
    private void validarEmail(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        if (!email.matches(EMAIL_PATTERN)) {
            throw new IllegalArgumentException("El email no tiene un formato válido");
        }
    }

    /**
     * Huecos por trabajador dentro de su jornada efectiva (D2): las citas
     * activas y los bloqueos se marcan como ocupados; días cerrados o sin
     * jornada no aportan huecos. Sin trabajadorId, solo activos y reservables
     * (D8). Con trabajadorId explícito basta con que esté activo (el panel del
     * trabajador lo usa para sus propias citas aunque no sea reservable).
     */
    @Transactional(readOnly = true)
    public List<DisponibilidadSlotDTO> consultarDisponibilidad(DisponibilidadRequest request) {
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
            trabajadores = trabajadorRepository.findByActivoTrueAndReservableTrue();
        }

        // Get all occupied slots for the date
        List<Reserva> reservasOcupadas = reservaRepository.findByFechaBetweenOrderByFechaAscHoraInicioAsc(
                request.getFecha(), request.getFecha()
        ).stream()
                .filter(r -> r.getEstado() == ReservaEstado.PENDIENTE || r.getEstado() == ReservaEstado.CONFIRMADA)
                .toList();

        // Slots que ya han pasado hoy no se muestran (evita reservar a horas pasadas)
        boolean esHoy = request.getFecha().equals(LocalDate.now());
        LocalTime ahora = LocalTime.now();

        List<DisponibilidadSlotDTO> resultado = new ArrayList<>();
        for (Trabajador t : trabajadores) {
            Optional<AgendaService.Jornada> jornada = agendaService.jornadaEfectiva(t, request.getFecha());
            if (jornada.isEmpty()) {
                continue; // día cerrado, día libre, cierre del trabajador…
            }
            List<SlotGenerator.ReservaSlot> ocupados = new ArrayList<>();
            reservasOcupadas.stream()
                    .filter(r -> r.getTrabajador().getId().equals(t.getId()))
                    .forEach(r -> ocupados.add(SlotGenerator.ReservaSlot.builder()
                            .inicio(r.getHoraInicio())
                            .fin(r.getHoraFin())
                            .build()));
            // Bloqueos (D5) = ocupados: el hueco aparece pero no disponible
            jornada.get().bloqueos().forEach(b -> ocupados.add(SlotGenerator.ReservaSlot.builder()
                    .inicio(b.inicio())
                    .fin(b.fin())
                    .build()));

            List<TimeSlot> allSlots = slotGenerator.generateSlotsForWorker(
                    jornada.get().inicio(), jornada.get().fin(), servicio.getDuracionMinutos(), ocupados);

            allSlots.stream()
                    .filter(slot -> !esHoy || slot.getInicio().isAfter(ahora))
                    .map(slot -> DisponibilidadSlotDTO.builder()
                            .horaInicio(slot.getInicio())
                            .horaFin(slot.getFin())
                            .disponible(slot.isDisponible())
                            .trabajadorId(t.getId())
                            .trabajadorNombre(t.getNombre())
                            .build())
                    .forEach(resultado::add);
        }
        return resultado;
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
        // D6: cancelación del propio cliente → sin motivo ni aviso
        reserva.setCanceladaPor(CanceladaPor.CLIENTE);
        reserva.setMotivoCancelacion(null);
        reserva = reservaRepository.save(reserva);
        log.info("Reserva cancelada por el cliente: ID={}", reserva.getId());

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

        // Validate new date (pasado / antelación) y hora pasada
        validarFecha(request.getNuevaFecha());
        validarHoraNoPasada(request.getNuevaFecha(), request.getNuevaHoraInicio());

        // Validate new time matches service duration
        LocalTime expectedEnd = request.getNuevaHoraInicio().plusMinutes(reserva.getServicio().getDuracionMinutos());

        // Lock del trabajador + jornada, cierres, bloqueos y solapes (D3)
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
        return updateReservaAdmin(id, dto, null);
    }

    /**
     * Edición del admin. Si el estado pasa de activo (PENDIENTE/CONFIRMADA) a
     * CANCELADA se delega en {@link #cancelarPorNegocio} (D6): no existe un
     * camino de cancelación del admin sin motivo ni aviso al cliente.
     */
    @Transactional
    public ReservaDTO updateReservaAdmin(Long id, ReservaDTO dto, Long adminId) {
        Reserva existing = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + id));

        ReservaEstado nuevoEstado = dto.getEstado() != null ? ReservaEstado.valueOf(dto.getEstado()) : null;
        boolean cancelaActiva = nuevoEstado == ReservaEstado.CANCELADA && esActiva(existing.getEstado());

        // Update fields
        if (nuevoEstado != null && !cancelaActiva) {
            existing.setEstado(nuevoEstado);
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

        if (cancelaActiva) {
            cancelarPorNegocio(existing, dto.getMotivoCancelacion(), adminId);
            return reservaMapper.toDTO(existing);
        }
        return reservaMapper.toDTO(reservaRepository.save(existing));
    }

    // ========== CANCELACIÓN POR EL NEGOCIO (D6, D7) ==========

    /** {@code POST /api/admin/reservas/{id}/cancelar}. */
    @Transactional
    public ReservaDTO cancelarPorNegocio(Long reservaId, String motivo, Long adminId) {
        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + reservaId));
        cancelarPorNegocio(reserva, motivo, adminId);
        return reservaMapper.toDTO(reserva);
    }

    /**
     * Cancelación hecha por el negocio (admin o cierre de día): solo citas
     * PENDIENTE/CONFIRMADA (400 si no). Deja la cita CANCELADA con motivo y
     * {@code canceladaPor = ADMIN}, crea su aviso (EMAIL/PENDIENTE_ENVIO o
     * TELEFONO/PENDIENTE, D7) y publica el evento cuyo listener enviará el
     * email tras el commit (un rollback no envía nada). Debe ejecutarse dentro
     * de la transacción del llamador (p. ej. la creación de un cierre).
     *
     * @return el aviso creado (el llamador puede contar email/teléfono)
     */
    @Transactional
    public AvisoCancelacion cancelarPorNegocio(Reserva reserva, String motivo, Long adminId) {
        if (!esActiva(reserva.getEstado())) {
            throw new IllegalArgumentException("Solo se pueden cancelar citas pendientes o confirmadas");
        }
        String motivoLimpio = motivo == null || motivo.isBlank() ? null : motivo.trim();
        if (motivoLimpio != null && motivoLimpio.length() > MAX_MOTIVO) {
            throw new IllegalArgumentException("El motivo no puede exceder " + MAX_MOTIVO + " caracteres");
        }

        reserva.setEstado(ReservaEstado.CANCELADA);
        reserva.setMotivoCancelacion(motivoLimpio);
        reserva.setCanceladaPor(CanceladaPor.ADMIN);
        reservaRepository.save(reserva);

        AvisoCancelacion aviso = avisoCancelacionService.registrar(reserva, motivoLimpio);
        log.info("Reserva cancelada por el negocio: ID={}, admin={}, canal={}",
                reserva.getId(), adminId, aviso.getCanal());

        ReservaResponse snapshot = reservaMapper.toResponse(reserva);
        snapshot.setMensaje("Reserva cancelada por la barbería");
        String email = reserva.getCliente() != null ? reserva.getCliente().getEmail() : null;
        applicationEventPublisher.publishEvent(
                new ReservaCanceladaPorNegocioEvent(snapshot, email, aviso.getId(), motivoLimpio));
        return aviso;
    }

    private static boolean esActiva(ReservaEstado estado) {
        return estado == ReservaEstado.PENDIENTE || estado == ReservaEstado.CONFIRMADA;
    }

    @Transactional
    public void deleteReserva(Long id) {
        if (!reservaRepository.existsById(id)) {
            throw new ResourceNotFoundException("Reserva no encontrada con ID: " + id);
        }
        reservaRepository.deleteById(id);
    }

    // ========== PRIVATE HELPERS ==========

    /** Pasado y antelación máxima; el "cerrado" lo decide la agenda (D2). */
    private void validarFecha(LocalDate fecha) {
        LocalDate hoy = LocalDate.now();
        if (fecha.isBefore(hoy)) {
            throw new IllegalArgumentException("No se pueden hacer reservas para fechas pasadas");
        }
        if (fecha.isAfter(hoy.plusDays(maxAdvanceDays))) {
            throw new IllegalArgumentException("No se pueden hacer reservas con más de " + maxAdvanceDays + " días de antelación");
        }
    }

    /** Rechaza horas ya pasadas si la fecha es hoy. */
    private void validarHoraNoPasada(LocalDate fecha, LocalTime horaInicio) {
        if (fecha.equals(LocalDate.now()) && !horaInicio.isAfter(LocalTime.now())) {
            throw new IllegalArgumentException("La hora seleccionada ya ha pasado. Elige una hora futura.");
        }
    }

    /**
     * Valida la cita contra la jornada efectiva del trabajador (D2): 400 si
     * ese día no hay jornada ("La barbería está cerrada…" / "El trabajador no
     * trabaja ese día") o si el servicio no cabe completo dentro de ella.
     *
     * @return la jornada del día (para comprobar bloqueos a continuación)
     */
    private AgendaService.Jornada validarHora(Trabajador trabajador, LocalDate fecha,
                                             LocalTime horaInicio, LocalTime horaFin) {
        AgendaService.DiaAgenda dia = agendaService.dia(trabajador, fecha);
        if (!dia.trabaja()) {
            throw new IllegalArgumentException(mensajeSinJornada(dia));
        }
        AgendaService.Jornada jornada = dia.jornada();
        if (horaInicio.isBefore(jornada.inicio())) {
            throw new IllegalArgumentException(String.format(
                    "La jornada empieza a las %s. Elige una hora igual o posterior.",
                    jornada.inicio().format(HORA)));
        }
        if (horaFin.isAfter(jornada.fin()) || horaFin.isBefore(horaInicio)) {
            throw new IllegalArgumentException(String.format(
                    "La última cita debe terminar a las %s como máximo (según la duración del servicio).",
                    jornada.fin().format(HORA)));
        }
        return jornada;
    }

    private static String mensajeSinJornada(AgendaService.DiaAgenda dia) {
        String motivo = dia.motivoCierre() != null && !dia.motivoCierre().isBlank()
                ? " (" + dia.motivoCierre() + ")" : "";
        return switch (dia.causa()) {
            case BARBERIA_CERRADA -> AgendaService.describirSinJornada(dia);
            case CIERRE_GENERAL -> "La barbería está cerrada el " + dia.fecha().format(FECHA) + motivo;
            case TRABAJADOR_INACTIVO -> "El trabajador no está disponible";
            case CIERRE_TRABAJADOR, DIA_LIBRE -> "El trabajador no trabaja ese día" + motivo;
        };
    }

    /**
     * Serializa la reserva con cierres, bloqueos y otras reservas del mismo
     * trabajador (D3): adquiere PRIMERO el lock pesimista de la fila del
     * trabajador y, ya bajo el lock, valida jornada y cierres (400) y solapes
     * con bloqueos o citas activas (409). El lock por filas de reservas y la
     * unique constraint se mantienen como defensa adicional.
     */
    private void checkAndLockAvailability(Trabajador trabajador, LocalDate fecha,
                                           LocalTime horaInicio, LocalTime horaFin) {
        Trabajador bloqueado = trabajadorRepository.findByIdForUpdate(trabajador.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado"));

        AgendaService.Jornada jornada = validarHora(bloqueado, fecha, horaInicio, horaFin);

        if (!jornada.bloqueosSolapados(horaInicio, horaFin).isEmpty()) {
            throw new SlotNotAvailableException("El horario seleccionado no está disponible: el trabajador lo tiene bloqueado");
        }

        if (reservaRepository.existsConflictingReserva(bloqueado, fecha, horaInicio, horaFin)) {
            throw new SlotNotAvailableException("El horario seleccionado ya no está disponible");
        }

        // Lock conflicting rows to prevent race condition
        List<Reserva> conflicting = reservaRepository.findConflictingReservasForUpdate(bloqueado, fecha);

        // Double-check after lock
        for (Reserva r : conflicting) {
            if (r.overlaps(Reserva.builder()
                    .trabajador(bloqueado)
                    .fecha(fecha)
                    .horaInicio(horaInicio)
                    .horaFin(horaFin)
                    .build())) {
                throw new SlotNotAvailableException("El horario seleccionado ya no está disponible");
            }
        }
    }
}
