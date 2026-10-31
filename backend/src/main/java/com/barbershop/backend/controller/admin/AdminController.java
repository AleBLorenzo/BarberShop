package com.barbershop.backend.controller.admin;

import com.barbershop.backend.dto.ApiResponse;
import com.barbershop.backend.dto.AvisoCancelacionDTO;
import com.barbershop.backend.dto.BloqueoDTO;
import com.barbershop.backend.dto.CierreCreateRequest;
import com.barbershop.backend.dto.CierreDTO;
import com.barbershop.backend.dto.CierrePreviewDTO;
import com.barbershop.backend.dto.CierreResultadoDTO;
import com.barbershop.backend.dto.IngresosResumenDTO;
import com.barbershop.backend.dto.CancelarReservaRequest;
import com.barbershop.backend.dto.HorarioDiaDTO;
import com.barbershop.backend.dto.HorarioGuardadoDTO;
import com.barbershop.backend.dto.ReservaConflictoDTO;
import com.barbershop.backend.dto.ClienteDTO;
import com.barbershop.backend.dto.DashboardStatsDTO;
import com.barbershop.backend.dto.MediaFileDTO;
import com.barbershop.backend.dto.ResenaDTO;
import com.barbershop.backend.dto.ResultadoDTO;
import com.barbershop.backend.dto.ReservaDTO;
import com.barbershop.backend.dto.ServicioDTO;
import com.barbershop.backend.dto.TrabajadorCreateRequest;
import com.barbershop.backend.dto.TrabajadorDTO;
import com.barbershop.backend.dto.TopServicioDTO;
import com.barbershop.backend.model.ReservaEstado;
import com.barbershop.backend.repository.TrabajadorRepository;
import com.barbershop.backend.service.AgendaService;
import com.barbershop.backend.service.AvisoCancelacionService;
import com.barbershop.backend.service.BloqueoService;
import com.barbershop.backend.service.CierreService;
import com.barbershop.backend.service.ClienteService;
import com.barbershop.backend.service.MediaService;
import com.barbershop.backend.service.ResenaService;
import com.barbershop.backend.service.ReservaService;
import com.barbershop.backend.service.ResultadoService;
import com.barbershop.backend.service.ServicioService;
import com.barbershop.backend.service.StatsService;
import com.barbershop.backend.service.TrabajadorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * API de administración. Doble barrera (D10, defensa en profundidad): la
 * regla de URL {@code /api/admin/** hasRole("ADMIN")} de {@code SecurityConfig}
 * y este {@code @PreAuthorize} de clase. La única excepción es la subida de
 * la foto propia ({@code POST /api/admin/media/trabajadores}), abierta
 * también a WORKER en ambas capas (ver {@link #subirMedia}).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ClienteService clienteService;
    private final TrabajadorService trabajadorService;
    private final ServicioService servicioService;
    private final ReservaService reservaService;
    private final StatsService statsService;
    private final ResenaService resenaService;
    private final ResultadoService resultadoService;
    private final MediaService mediaService;
    private final AgendaService agendaService;
    private final AvisoCancelacionService avisoCancelacionService;
    private final CierreService cierreService;
    private final BloqueoService bloqueoService;
    private final TrabajadorRepository trabajadorRepository;

    // ========== CLIENTES ==========

    @GetMapping("/clientes")
    public ResponseEntity<ApiResponse<List<ClienteDTO>>> getAllClientes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(clienteService.searchByNombre(search)));
        }
        return ResponseEntity.ok(ApiResponse.ok(clienteService.findAll()));
    }

    @GetMapping("/clientes/{id}")
    public ResponseEntity<ApiResponse<ClienteDTO>> getCliente(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(clienteService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"))));
    }

    @PostMapping("/clientes")
    public ResponseEntity<ApiResponse<ClienteDTO>> createCliente(@Valid @RequestBody ClienteDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(clienteService.create(dto), "Cliente creado"));
    }

    @PutMapping("/clientes/{id}")
    public ResponseEntity<ApiResponse<ClienteDTO>> updateCliente(@PathVariable Long id, @Valid @RequestBody ClienteDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(clienteService.update(id, dto), "Cliente actualizado"));
    }

    @DeleteMapping("/clientes/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCliente(@PathVariable Long id) {
        clienteService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Cliente eliminado"));
    }

    // ========== TRABAJADORES ==========

    @GetMapping("/trabajadores")
    public ResponseEntity<ApiResponse<List<TrabajadorDTO>>> getAllTrabajadores() {
        return ResponseEntity.ok(ApiResponse.ok(trabajadorService.findAll()));
    }

    @GetMapping("/trabajadores/{id}")
    public ResponseEntity<ApiResponse<TrabajadorDTO>> getTrabajador(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(trabajadorService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Trabajador no encontrado"))));
    }

    @PostMapping("/trabajadores")
    public ResponseEntity<ApiResponse<TrabajadorDTO>> createTrabajador(@Valid @RequestBody TrabajadorCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(trabajadorService.create(request), "Trabajador creado"));
    }

    @PutMapping("/trabajadores/{id}")
    public ResponseEntity<ApiResponse<TrabajadorDTO>> updateTrabajador(@PathVariable Long id, @Valid @RequestBody TrabajadorDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(trabajadorService.update(id, dto), "Trabajador actualizado"));
    }

    @DeleteMapping("/trabajadores/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTrabajador(@PathVariable Long id) {
        String mensaje = trabajadorService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, mensaje));
    }

    // ========== SERVICIOS ==========

    @GetMapping("/servicios")
    public ResponseEntity<ApiResponse<List<ServicioDTO>>> getAllServicios() {
        return ResponseEntity.ok(ApiResponse.ok(servicioService.findAll()));
    }

    @GetMapping("/servicios/{id}")
    public ResponseEntity<ApiResponse<ServicioDTO>> getServicio(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(servicioService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"))));
    }

    @PostMapping("/servicios")
    public ResponseEntity<ApiResponse<ServicioDTO>> createServicio(@Valid @RequestBody ServicioDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(servicioService.create(dto), "Servicio creado"));
    }

    @PutMapping("/servicios/{id}")
    public ResponseEntity<ApiResponse<ServicioDTO>> updateServicio(@PathVariable Long id, @Valid @RequestBody ServicioDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(servicioService.update(id, dto), "Servicio actualizado"));
    }

    @DeleteMapping("/servicios/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteServicio(@PathVariable Long id) {
        String mensaje = servicioService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, mensaje));
    }

    // ========== RESEÑAS ==========

    @GetMapping("/resenas")
    public ResponseEntity<ApiResponse<List<ResenaDTO>>> getAllResenas() {
        return ResponseEntity.ok(ApiResponse.ok(resenaService.findAll()));
    }

    @PostMapping("/resenas")
    public ResponseEntity<ApiResponse<ResenaDTO>> createResena(@Valid @RequestBody ResenaDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(resenaService.create(dto), "Reseña creada"));
    }

    @PutMapping("/resenas/{id}")
    public ResponseEntity<ApiResponse<ResenaDTO>> updateResena(@PathVariable Long id, @Valid @RequestBody ResenaDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(resenaService.update(id, dto), "Reseña actualizada"));
    }

    @DeleteMapping("/resenas/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteResena(@PathVariable Long id) {
        resenaService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Reseña eliminada"));
    }

    // ========== RESULTADOS (galería de fotos) ==========

    @GetMapping("/resultados")
    public ResponseEntity<ApiResponse<List<ResultadoDTO>>> getAllResultados() {
        return ResponseEntity.ok(ApiResponse.ok(resultadoService.findAll()));
    }

    @PostMapping("/resultados")
    public ResponseEntity<ApiResponse<ResultadoDTO>> createResultado(@Valid @RequestBody ResultadoDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(resultadoService.create(dto), "Foto añadida"));
    }

    @PutMapping("/resultados/{id}")
    public ResponseEntity<ApiResponse<ResultadoDTO>> updateResultado(@PathVariable Long id, @Valid @RequestBody ResultadoDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(resultadoService.update(id, dto), "Foto actualizada"));
    }

    @DeleteMapping("/resultados/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteResultado(@PathVariable Long id) {
        resultadoService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Foto eliminada"));
    }

    // ========== MEDIOS (fotos locales) ==========

    /** Lista las imágenes de una carpeta ({@code trabajadores}, {@code servicios} o {@code resultados}). */
    @GetMapping("/media/{carpeta}")
    public ResponseEntity<ApiResponse<List<MediaFileDTO>>> listarMedia(@PathVariable String carpeta) {
        return ResponseEntity.ok(ApiResponse.ok(mediaService.list(carpeta)));
    }

    /**
     * Sube una imagen (multipart, campo {@code archivo}) a la carpeta indicada.
     * WORKER solo puede subir a {@code trabajadores} (su foto de perfil, B-BAJO-2):
     * el {@code @PreAuthorize} de método sustituye al de clase y replica la
     * excepción de la regla de URL.
     */
    @PreAuthorize("hasRole('ADMIN') or (hasRole('WORKER') and #carpeta == 'trabajadores')")
    @PostMapping("/media/{carpeta}")
    public ResponseEntity<ApiResponse<MediaFileDTO>> subirMedia(
            @PathVariable String carpeta,
            @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(ApiResponse.ok(mediaService.upload(carpeta, archivo), "Archivo subido"));
    }

    /** Elimina una imagen de la carpeta indicada. */
    @DeleteMapping("/media/{carpeta}/{nombre}")
    public ResponseEntity<ApiResponse<Void>> eliminarMedia(
            @PathVariable String carpeta,
            @PathVariable String nombre) {
        mediaService.delete(carpeta, nombre);
        return ResponseEntity.ok(ApiResponse.ok(null, "Archivo eliminado"));
    }

    // ========== RESERVAS ==========

    @GetMapping("/reservas")
    public ResponseEntity<ApiResponse<List<ReservaDTO>>> getAllReservas(
            @RequestParam(required = false) LocalDate fechaInicio,
            @RequestParam(required = false) LocalDate fechaFin,
            @RequestParam(required = false) ReservaEstado estado) {
        List<ReservaDTO> reservas = reservaService.findAll();
        // TODO: Add filtering by date range and estado
        return ResponseEntity.ok(ApiResponse.ok(reservas));
    }

    @GetMapping("/reservas/{id}")
    public ResponseEntity<ApiResponse<ReservaDTO>> getReserva(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(reservaService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"))));
    }

    /** Si el estado pasa de activo a CANCELADA aplica el flujo de cancelación con aviso (D6). */
    @PutMapping("/reservas/{id}")
    public ResponseEntity<ApiResponse<ReservaDTO>> updateReserva(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ReservaDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(
                reservaService.updateReservaAdmin(id, dto, adminId(userDetails)), "Reserva actualizada"));
    }

    /** Cancelación de una cita por el negocio con motivo opcional y aviso al cliente (D6, D7). */
    @PostMapping("/reservas/{id}/cancelar")
    public ResponseEntity<ApiResponse<ReservaDTO>> cancelarReserva(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CancelarReservaRequest request) {
        String motivo = request != null ? request.getMotivo() : null;
        ReservaDTO dto = reservaService.cancelarPorNegocio(id, motivo, adminId(userDetails));
        return ResponseEntity.ok(ApiResponse.ok(dto, "Cita cancelada y cliente avisado"));
    }

    @DeleteMapping("/reservas/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReserva(@PathVariable Long id) {
        reservaService.deleteReserva(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Reserva eliminada"));
    }

    // ========== HORARIOS (D1, D11) ==========

    @GetMapping("/horario")
    public ResponseEntity<ApiResponse<List<HorarioDiaDTO>>> getHorario() {
        return ResponseEntity.ok(ApiResponse.ok(agendaService.getHorarioBarberia()));
    }

    /** Reemplaza los 7 días; las citas que queden fuera NO se cancelan: vuelven en {@code conflictos}. */
    @PutMapping("/horario")
    public ResponseEntity<ApiResponse<HorarioGuardadoDTO>> guardarHorario(
            @Valid @RequestBody List<HorarioDiaDTO> dias) {
        return ResponseEntity.ok(ApiResponse.ok(agendaService.guardarHorarioBarberia(dias), "Horario guardado"));
    }

    /** Días con horario propio del trabajador; los ausentes heredan el general. */
    @GetMapping("/trabajadores/{id}/horario")
    public ResponseEntity<ApiResponse<List<HorarioDiaDTO>>> getHorarioTrabajador(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(agendaService.getHorarioTrabajador(id)));
    }

    /** PUT reemplaza: día ausente = hereda el general; con horas debe caber en el general (400). */
    @PutMapping("/trabajadores/{id}/horario")
    public ResponseEntity<ApiResponse<HorarioGuardadoDTO>> guardarHorarioTrabajador(
            @PathVariable Long id,
            @Valid @RequestBody List<HorarioDiaDTO> dias) {
        return ResponseEntity.ok(ApiResponse.ok(agendaService.guardarHorarioTrabajador(id, dias),
                "Horario del trabajador guardado"));
    }

    /** Citas activas futuras fuera de jornada, en día cerrado o sobre bloqueo (por defecto hoy → +60 días). */
    @GetMapping("/agenda/conflictos")
    public ResponseEntity<ApiResponse<List<ReservaConflictoDTO>>> getConflictos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        LocalDate start = desde != null ? desde : LocalDate.now();
        LocalDate end = hasta != null ? hasta : start.plusDays(60);
        return ResponseEntity.ok(ApiResponse.ok(agendaService.conflictos(start, end)));
    }

    // ========== AVISOS DE CANCELACIÓN (D7) ==========

    /** {@code pendientes=true} (por defecto): solo los que requieren aviso manual. */
    @GetMapping("/avisos")
    public ResponseEntity<ApiResponse<List<AvisoCancelacionDTO>>> getAvisos(
            @RequestParam(defaultValue = "true") boolean pendientes) {
        return ResponseEntity.ok(ApiResponse.ok(pendientes
                ? avisoCancelacionService.pendientes()
                : avisoCancelacionService.todos()));
    }

    @GetMapping("/avisos/pendientes/count")
    public ResponseEntity<ApiResponse<Long>> contarAvisosPendientes() {
        return ResponseEntity.ok(ApiResponse.ok(avisoCancelacionService.contarPendientes()));
    }

    @PutMapping("/avisos/{id}/avisado")
    public ResponseEntity<ApiResponse<AvisoCancelacionDTO>> marcarAvisado(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                avisoCancelacionService.marcarAvisado(id, adminId(userDetails)), "Cliente marcado como avisado"));
    }

    // ========== CIERRES DE DÍA (D4) ==========

    /** Cierres (generales y de trabajador) en [desde, hasta]; por defecto hoy → +60 días. */
    @GetMapping("/cierres")
    public ResponseEntity<ApiResponse<List<CierreDTO>>> getCierres(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        LocalDate start = desde != null ? desde : LocalDate.now();
        LocalDate end = hasta != null ? hasta : start.plusDays(60);
        return ResponseEntity.ok(ApiResponse.ok(cierreService.listar(start, end)));
    }

    /** Citas activas que cancelaría el cierre (sin {@code trabajadorId} = toda la barbería). No modifica nada. */
    @GetMapping("/cierres/preview")
    public ResponseEntity<ApiResponse<CierrePreviewDTO>> previewCierre(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Long trabajadorId) {
        return ResponseEntity.ok(ApiResponse.ok(cierreService.preview(fecha, trabajadorId)));
    }

    /**
     * Crea el cierre (201) y cancela con aviso las citas activas afectadas.
     * 400 fecha pasada; 409 si ya existe ese cierre; 404 trabajador inexistente.
     */
    @PostMapping("/cierres")
    public ResponseEntity<ApiResponse<CierreResultadoDTO>> crearCierre(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CierreCreateRequest request) {
        CierreResultadoDTO resultado = cierreService.crear(request, adminId(userDetails));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(resultado, "Día cerrado: " + resultado.getCanceladas() + " cita(s) cancelada(s)"));
    }

    /** Reabre el día (solo hoy o futuro). Las citas canceladas por el cierre NO se reactivan. */
    @DeleteMapping("/cierres/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarCierre(@PathVariable Long id) {
        cierreService.eliminar(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Cierre eliminado"));
    }

    // ========== BLOQUEOS (D5): el admin lista y borra; no crea bloqueos ajenos ==========

    @GetMapping("/bloqueos")
    public ResponseEntity<ApiResponse<List<BloqueoDTO>>> getBloqueos(
            @RequestParam(required = false) Long trabajadorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        LocalDate start = desde != null ? desde : LocalDate.now();
        LocalDate end = hasta != null ? hasta : start.plusDays(60);
        return ResponseEntity.ok(ApiResponse.ok(bloqueoService.listarAdmin(trabajadorId, start, end)));
    }

    @DeleteMapping("/bloqueos/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarBloqueo(@PathVariable Long id) {
        bloqueoService.eliminarAdmin(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Bloqueo eliminado"));
    }

    // ========== INGRESOS POR TRABAJADOR (D10) ==========

    /** Ingresos por trabajador y totales (mismo rango por defecto y criterio que /dashboard). */
    @GetMapping("/ingresos")
    public ResponseEntity<ApiResponse<IngresosResumenDTO>> getIngresos(
            @RequestParam(required = false) LocalDate inicio,
            @RequestParam(required = false) LocalDate fin) {
        LocalDate start = inicio != null ? inicio : LocalDate.now().minusDays(30);
        LocalDate end = fin != null ? fin : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(statsService.getIngresosPorTrabajador(start, end)));
    }

    // ========== DASHBOARD / STATS ==========

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStatsDTO>> getDashboard(
            @RequestParam(required = false) LocalDate inicio,
            @RequestParam(required = false) LocalDate fin) {
        LocalDate start = inicio != null ? inicio : LocalDate.now().minusDays(30);
        LocalDate end = fin != null ? fin : LocalDate.now();
        DashboardStatsDTO stats = statsService.getDashboardStats(start, end);
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    @GetMapping("/dashboard/top-servicios")
    public ResponseEntity<ApiResponse<List<TopServicioDTO>>> getTopServicios(
            @RequestParam(required = false) LocalDate inicio,
            @RequestParam(required = false) LocalDate fin,
            @RequestParam(defaultValue = "5") int limit) {
        LocalDate start = inicio != null ? inicio : LocalDate.now().minusDays(30);
        LocalDate end = fin != null ? fin : LocalDate.now();
        List<TopServicioDTO> top = statsService.getTopServicios(start, end, limit);
        return ResponseEntity.ok(ApiResponse.ok(top));
    }

    /** Id del admin autenticado (auditoría de cancelaciones y avisos); null si no se resuelve. */
    private Long adminId(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        return trabajadorRepository.findByEmail(userDetails.getUsername())
                .map(t -> t.getId())
                .orElse(null);
    }
}
