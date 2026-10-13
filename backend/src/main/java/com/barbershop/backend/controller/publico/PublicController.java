package com.barbershop.backend.controller.publico;

import com.barbershop.backend.dto.ApiResponse;
import com.barbershop.backend.dto.DisponibilidadRequest;
import com.barbershop.backend.dto.DisponibilidadSlotDTO;
import com.barbershop.backend.dto.ReservaCancelRequest;
import com.barbershop.backend.dto.ReservaCreateRequest;
import com.barbershop.backend.dto.ReservaDTO;
import com.barbershop.backend.dto.ReservaReprogramarRequest;
import com.barbershop.backend.dto.ResenaDTO;
import com.barbershop.backend.dto.ResultadoDTO;
import com.barbershop.backend.dto.ReservaResponse;
import com.barbershop.backend.dto.ServicioDTO;
import com.barbershop.backend.dto.TrabajadorDTO;
import com.barbershop.backend.service.ResenaService;
import com.barbershop.backend.service.ReservaService;
import com.barbershop.backend.service.ResultadoService;
import com.barbershop.backend.service.ServicioService;
import com.barbershop.backend.service.TrabajadorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final ReservaService reservaService;
    private final ServicioService servicioService;
    private final TrabajadorService trabajadorService;
    private final ResenaService resenaService;
    private final ResultadoService resultadoService;

    @GetMapping("/servicios")
    public ResponseEntity<ApiResponse<List<ServicioDTO>>> getServicios() {
        return ResponseEntity.ok(ApiResponse.ok(servicioService.findAllActivos()));
    }

    @GetMapping("/trabajadores")
    public ResponseEntity<ApiResponse<List<TrabajadorDTO>>> getTrabajadores() {
        // DTO público: sin email ni rol de los empleados (auditoría B-BAJO-5)
        List<TrabajadorDTO> publicos = trabajadorService.findAllActivos().stream()
                .map(trab -> {
                    trab.setEmail(null);
                    trab.setRol(null);
                    return trab;
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(publicos));
    }

    /** Reseñas de la portada (gestionadas por el administrador). */
    @GetMapping("/resenas")
    public ResponseEntity<ApiResponse<List<ResenaDTO>>> getResenas() {
        return ResponseEntity.ok(ApiResponse.ok(resenaService.findAll()));
    }

    /** Galería «Resultados» de la portada (fotos de clientes). */
    @GetMapping("/resultados")
    public ResponseEntity<ApiResponse<List<ResultadoDTO>>> getResultados() {
        return ResponseEntity.ok(ApiResponse.ok(resultadoService.findAll()));
    }

    /**
     * Public lookup of a reservation: "Mis citas" by phone + cancellation token.
     */
    @GetMapping("/reservas")
    public ResponseEntity<ApiResponse<List<ReservaDTO>>> getReservasByTelefonoToken(
            @RequestParam String telefono,
            @RequestParam String token) {
        return ResponseEntity.ok(ApiResponse.ok(reservaService.buscarPorTelefonoYToken(telefono, token)));
    }

    @PostMapping("/disponibilidad")
    public ResponseEntity<ApiResponse<List<DisponibilidadSlotDTO>>> getDisponibilidad(
            @Valid @RequestBody DisponibilidadRequest request) {
        List<DisponibilidadSlotDTO> slots = reservaService.consultarDisponibilidad(request);
        return ResponseEntity.ok(ApiResponse.ok(slots));
    }

    @PostMapping("/reservas")
    public ResponseEntity<ApiResponse<ReservaResponse>> crearReserva(
            @Valid @RequestBody ReservaCreateRequest request) {
        ReservaResponse response = reservaService.crearReservaPublica(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Reserva realizada correctamente"));
    }

    @PostMapping("/reservas/cancelar")
    public ResponseEntity<ApiResponse<ReservaResponse>> cancelarReserva(
            @Valid @RequestBody ReservaCancelRequest request) {
        ReservaResponse response = reservaService.cancelarReserva(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Reserva cancelada correctamente"));
    }

    @PostMapping("/reservas/reprogramar")
    public ResponseEntity<ApiResponse<ReservaResponse>> reprogramarReserva(
            @Valid @RequestBody ReservaReprogramarRequest request) {
        ReservaResponse response = reservaService.reprogramarReserva(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Reserva reprogramada correctamente"));
    }
}