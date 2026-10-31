package com.barbershop.backend.controller.worker;

import com.barbershop.backend.dto.ApiResponse;
import com.barbershop.backend.dto.BloqueoCreateRequest;
import com.barbershop.backend.dto.BloqueoDTO;
import com.barbershop.backend.dto.DiaAgendaDTO;
import com.barbershop.backend.dto.ReservaCreateRequest;
import com.barbershop.backend.dto.ReservaDTO;
import com.barbershop.backend.dto.ReservaResponse;
import com.barbershop.backend.dto.TrabajadorDTO;
import com.barbershop.backend.dto.WorkerDashboardDTO;
import com.barbershop.backend.dto.WorkerPerfilUpdateRequest;
import com.barbershop.backend.model.ReservaEstado;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.service.AgendaService;
import com.barbershop.backend.service.BloqueoService;
import com.barbershop.backend.service.ReservaService;
import com.barbershop.backend.service.StatsService;
import com.barbershop.backend.service.TrabajadorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Panel del trabajador (también lo usa el ADMIN en "Mi panel"). Regla D10:
 * NINGÚN endpoint de esta clase acepta un {@code trabajadorId} para leer o
 * escribir; el trabajador sale siempre del JWT ({@link #getTrabajador}) y
 * cualquier {@code ?trabajadorId=} o campo del cuerpo se ignora.
 */
@RestController
@RequestMapping("/api/worker")
@RequiredArgsConstructor
public class WorkerController {

    private final ReservaService reservaService;
    private final StatsService statsService;
    private final TrabajadorRepository trabajadorRepository;
    private final TrabajadorService trabajadorService;
    private final BloqueoService bloqueoService;
    private final AgendaService agendaService;

    @GetMapping("/perfil")
    public ResponseEntity<ApiResponse<TrabajadorDTO>> getPerfil(
            @AuthenticationPrincipal UserDetails userDetails) {
        Trabajador trabajador = getTrabajador(userDetails);
        TrabajadorDTO dto = TrabajadorDTO.builder()
                .id(trabajador.getId())
                .nombre(trabajador.getNombre())
                .email(trabajador.getEmail())
                .activo(trabajador.getActivo())
                .foto(trabajador.getFoto())
                .createdAt(trabajador.getCreatedAt())
                .build();
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    /**
     * Edita el perfil del trabajador autenticado (nombre, email y foto opcional).
     * La foto viene del path producido por {@code POST /api/admin/media/trabajadores}
     * (alcanzable por WORKER). Email duplicado → 400 "Ya existe un trabajador con
     * ese email" (mismo patrón que la edición admin). Si {@code foto} no viene en
     * el payload se conserva la actual.
     */
    @PutMapping("/perfil")
    public ResponseEntity<ApiResponse<TrabajadorDTO>> updatePerfil(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody WorkerPerfilUpdateRequest request) {
        Trabajador trabajador = getTrabajador(userDetails);
        TrabajadorDTO dto = trabajadorService.updatePerfil(trabajador.getId(), request);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Perfil actualizado"));
    }

    /**
     * Crea una cita para el trabajador autenticado. El trabajador SIEMPRE
     * sale del token JWT: se ignora request.trabajadorId si viene en el cuerpo.
     * (Sin @Valid: la validación de negocio la hace ReservaService, incluido
     * clienteEmail opcional con formato.)
     */
    @PostMapping("/reservas")
    public ResponseEntity<ApiResponse<ReservaResponse>> crearReserva(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ReservaCreateRequest request) {
        Trabajador trabajador = getTrabajador(userDetails);
        ReservaResponse response = reservaService.crearReservaParaTrabajador(request, trabajador.getId());
        return ResponseEntity.ok(ApiResponse.ok(response, "Reserva creada"));
    }

    @GetMapping("/reservas")
    public ResponseEntity<ApiResponse<List<ReservaDTO>>> getMisReservas(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) LocalDate fecha) {
        Trabajador trabajador = getTrabajador(userDetails);
        LocalDate targetFecha = fecha != null ? fecha : LocalDate.now();
        List<ReservaDTO> reservas = reservaService.findMisReservas(trabajador.getId(), targetFecha);
        return ResponseEntity.ok(ApiResponse.ok(reservas));
    }

    /**
     * Semana del trabajador: lunes a domingo (7 días; qué días trabaja de
     * verdad lo dice {@code GET /api/worker/agenda}).
     * {@code fechaInicio} (yyyy-MM-dd) permite navegar a semanas futuras;
     * si no viene, se usa el lunes de la semana actual.
     * {@code fechaFin} (yyyy-MM-dd, opcional, inclusive) permite pedir rangos
     * mayores que una semana, p. ej. el mes completo; por defecto
     * {@code fechaInicio + 6} (domingo).
     */
    @GetMapping("/reservas/semana")
    public ResponseEntity<ApiResponse<List<ReservaDTO>>> getReservasSemana(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) LocalDate fechaInicio,
            @RequestParam(required = false) LocalDate fechaFin) {
        Trabajador trabajador = getTrabajador(userDetails);
        LocalDate inicio = fechaInicio != null ? fechaInicio : lunesDeEstaSemana();
        LocalDate fin = fechaFin != null ? fechaFin : inicio.plusDays(6); // lunes → domingo
        List<ReservaDTO> reservas = reservaService.findMisReservasSemana(trabajador.getId(), inicio, fin);
        return ResponseEntity.ok(ApiResponse.ok(reservas));
    }

    /**
     * Cambia el estado. Para COMPLETADA es obligatorio el parámetro opcional
     * {@code metodoPago} (visa|efectivo, sin distinción de mayúsculas): si falta
     * o no es válido se responde 400. Los demás estados no lo requieren.
     */
    @PutMapping("/reservas/{id}/estado")
    public ResponseEntity<ApiResponse<ReservaDTO>> cambiarEstadoReserva(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestParam ReservaEstado estado,
            @RequestParam(required = false) String metodoPago) {
        Trabajador trabajador = getTrabajador(userDetails);
        ReservaDTO reserva = reservaService.cambiarEstadoReserva(trabajador.getId(), id, estado, metodoPago);
        return ResponseEntity.ok(ApiResponse.ok(reserva, "Estado actualizado"));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<WorkerDashboardDTO>> getDashboard(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) LocalDate fecha) {
        Trabajador trabajador = getTrabajador(userDetails);
        LocalDate targetFecha = fecha != null ? fecha : LocalDate.now();
        WorkerDashboardDTO dashboard = statsService.getWorkerDashboard(trabajador.getId(), targetFecha);
        return ResponseEntity.ok(ApiResponse.ok(dashboard));
    }

    // ========== AGENDA Y BLOQUEOS (D5, D11) ==========

    /**
     * Agenda efectiva del trabajador autenticado: por cada fecha de
     * [desde, hasta] si trabaja, su jornada, el cierre que le afecta (con
     * motivo) y sus bloqueos. Por defecto la semana actual lunes → domingo;
     * rango máximo 62 días.
     */
    @GetMapping("/agenda")
    public ResponseEntity<ApiResponse<List<DiaAgendaDTO>>> getAgenda(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Trabajador trabajador = getTrabajador(userDetails);
        LocalDate inicio = desde != null ? desde : lunesDeEstaSemana();
        LocalDate fin = hasta != null ? hasta : inicio.plusDays(6);
        return ResponseEntity.ok(ApiResponse.ok(agendaService.agendaTrabajador(trabajador, inicio, fin)));
    }

    /** Bloqueos propios en [desde, hasta] (por defecto hoy → +60 días). */
    @GetMapping("/bloqueos")
    public ResponseEntity<ApiResponse<List<BloqueoDTO>>> getBloqueos(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Trabajador trabajador = getTrabajador(userDetails);
        LocalDate inicio = desde != null ? desde : LocalDate.now();
        LocalDate fin = hasta != null ? hasta : inicio.plusDays(60);
        return ResponseEntity.ok(ApiResponse.ok(bloqueoService.listarPropios(trabajador.getId(), inicio, fin)));
    }

    /**
     * Bloquea una franja propia (201). El trabajador sale SIEMPRE del JWT:
     * un {@code trabajadorId} en el cuerpo se ignora. 400 si la franja no es
     * válida o el día no hay jornada; 409 (con las citas/bloqueos en
     * conflicto en {@code data}) si pisa citas activas u otro bloqueo.
     */
    @PostMapping("/bloqueos")
    public ResponseEntity<ApiResponse<BloqueoDTO>> crearBloqueo(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BloqueoCreateRequest request) {
        Trabajador trabajador = getTrabajador(userDetails);
        BloqueoDTO dto = bloqueoService.crear(trabajador.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto, "Horas bloqueadas"));
    }

    /** Elimina un bloqueo propio; uno ajeno o inexistente → 404. */
    @DeleteMapping("/bloqueos/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarBloqueo(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        Trabajador trabajador = getTrabajador(userDetails);
        bloqueoService.eliminarPropio(trabajador.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Bloqueo eliminado"));
    }

    private static LocalDate lunesDeEstaSemana() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private Trabajador getTrabajador(UserDetails userDetails) {
        return trabajadorRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Trabajador no encontrado"));
    }
}