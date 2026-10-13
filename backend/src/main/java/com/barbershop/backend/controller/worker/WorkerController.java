package com.barbershop.backend.controller.worker;

import com.barbershop.backend.dto.ApiResponse;
import com.barbershop.backend.dto.ReservaCreateRequest;
import com.barbershop.backend.dto.ReservaDTO;
import com.barbershop.backend.dto.ReservaResponse;
import com.barbershop.backend.dto.TrabajadorDTO;
import com.barbershop.backend.dto.WorkerDashboardDTO;
import com.barbershop.backend.dto.WorkerPerfilUpdateRequest;
import com.barbershop.backend.model.ReservaEstado;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.service.ReservaService;
import com.barbershop.backend.service.StatsService;
import com.barbershop.backend.service.TrabajadorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/worker")
@RequiredArgsConstructor
public class WorkerController {

    private final ReservaService reservaService;
    private final StatsService statsService;
    private final TrabajadorRepository trabajadorRepository;
    private final TrabajadorService trabajadorService;

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
     * Semana del trabajador: lunes a sábado (domingo cerrado, 6 días).
     * {@code fechaInicio} (yyyy-MM-dd) permite navegar a semanas futuras;
     * si no viene, se usa el lunes de la semana actual (compatibilidad).
     * {@code fechaFin} (yyyy-MM-dd, opcional, inclusive) permite pedir rangos
     * mayores que una semana, p. ej. el mes completo; por defecto sigue siendo
     * {@code fechaInicio + 5} (sábado).
     */
    @GetMapping("/reservas/semana")
    public ResponseEntity<ApiResponse<List<ReservaDTO>>> getReservasSemana(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) LocalDate fechaInicio,
            @RequestParam(required = false) LocalDate fechaFin) {
        Trabajador trabajador = getTrabajador(userDetails);
        LocalDate inicio = fechaInicio != null
                ? fechaInicio
                : LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate fin = fechaFin != null ? fechaFin : inicio.plusDays(5); // lunes → sábado (sin domingo)
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

    private Trabajador getTrabajador(UserDetails userDetails) {
        return trabajadorRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Trabajador no encontrado"));
    }
}