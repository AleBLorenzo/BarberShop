package com.barbershop.backend.controller.admin;

import com.barbershop.backend.dto.ApiResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ClienteService clienteService;
    private final TrabajadorService trabajadorService;
    private final ServicioService servicioService;
    private final ReservaService reservaService;
    private final StatsService statsService;
    private final ResenaService resenaService;
    private final ResultadoService resultadoService;
    private final MediaService mediaService;

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

    /** Sube una imagen (multipart, campo {@code archivo}) a la carpeta indicada. */
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

    @PutMapping("/reservas/{id}")
    public ResponseEntity<ApiResponse<ReservaDTO>> updateReserva(@PathVariable Long id, @Valid @RequestBody ReservaDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(reservaService.updateReservaAdmin(id, dto), "Reserva actualizada"));
    }

    @DeleteMapping("/reservas/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReserva(@PathVariable Long id) {
        reservaService.deleteReserva(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Reserva eliminada"));
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
}