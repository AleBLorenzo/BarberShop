package com.barbershop.backend.service;

import com.barbershop.backend.dto.AvisoCancelacionDTO;
import com.barbershop.backend.exception.ResourceNotFoundException;
import com.barbershop.backend.model.AvisoCancelacion;
import com.barbershop.backend.model.CanalAviso;
import com.barbershop.backend.model.Cliente;
import com.barbershop.backend.model.EstadoAviso;
import com.barbershop.backend.model.Reserva;
import com.barbershop.backend.model.Trabajador;
import com.barbershop.backend.repository.AvisoCancelacionRepository;
import com.barbershop.backend.repository.TrabajadorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cola de avisos de cancelación por el negocio (D7): email (PENDIENTE_ENVIO →
 * ENVIADO/FALLIDO) o teléfono (PENDIENTE), y marcado manual como AVISADO.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AvisoCancelacionService {

    /** Un email que lleva más de esto en PENDIENTE_ENVIO se trata como pendiente de acción. */
    static final long MINUTOS_GRACIA_ENVIO = 15;

    private static final int MAX_ERROR = 255;

    private final AvisoCancelacionRepository avisoRepository;
    private final TrabajadorRepository trabajadorRepository;

    /**
     * Crea (o reinicia, si la reserva ya tuvo uno) el aviso de una reserva
     * cancelada por el negocio. Debe llamarse dentro de la transacción de la
     * cancelación. Con email → EMAIL/PENDIENTE_ENVIO; sin él → TELEFONO/PENDIENTE.
     */
    @Transactional
    public AvisoCancelacion registrar(Reserva reserva, String motivo) {
        Cliente cliente = reserva.getCliente();
        boolean conEmail = cliente != null && cliente.getEmail() != null && !cliente.getEmail().isBlank();
        // Una reserva reactivada por el admin y vuelta a cancelar reutiliza su
        // aviso (reserva_id es único): se reinicia (created_at no es actualizable
        // y conserva la fecha original).
        AvisoCancelacion aviso = avisoRepository.findByReserva_Id(reserva.getId())
                .orElseGet(() -> AvisoCancelacion.builder().reserva(reserva).build());
        aviso.setCanal(conEmail ? CanalAviso.EMAIL : CanalAviso.TELEFONO);
        aviso.setEstado(conEmail ? EstadoAviso.PENDIENTE_ENVIO : EstadoAviso.PENDIENTE);
        aviso.setMotivo(motivo);
        aviso.setError(null);
        aviso.setAvisadoAt(null);
        aviso.setAvisadoPor(null);
        return avisoRepository.save(aviso);
    }

    /**
     * Resultado del envío asíncrono del email (D7), en transacción propia: el
     * listener corre tras el commit, fuera de la transacción de cancelación.
     * Solo transiciona avisos EMAIL aún en PENDIENTE_ENVIO (no pisa un AVISADO).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarResultadoEnvio(Long avisoId, boolean enviado, String error) {
        avisoRepository.findById(avisoId).ifPresentOrElse(aviso -> {
            if (aviso.getCanal() != CanalAviso.EMAIL || aviso.getEstado() != EstadoAviso.PENDIENTE_ENVIO) {
                return;
            }
            aviso.setEstado(enviado ? EstadoAviso.ENVIADO : EstadoAviso.FALLIDO);
            aviso.setError(enviado ? null : recortar(error));
            avisoRepository.save(aviso);
        }, () -> log.warn("Aviso de cancelación {} no encontrado al registrar el envío", avisoId));
    }

    /** Avisos que requieren acción manual, ordenados por fecha de la cita. */
    @Transactional(readOnly = true)
    public List<AvisoCancelacionDTO> pendientes() {
        return avisoRepository.findPendientesDeAccion(CanalAviso.TELEFONO, CanalAviso.EMAIL,
                        EstadoAviso.PENDIENTE, EstadoAviso.FALLIDO, EstadoAviso.PENDIENTE_ENVIO, limiteGracia())
                .stream()
                .map(AvisoCancelacionService::toDTO)
                .toList();
    }

    /** Todos los avisos, los más recientes primero (histórico). */
    @Transactional(readOnly = true)
    public List<AvisoCancelacionDTO> todos() {
        return avisoRepository.findAllConDetalle().stream()
                .map(AvisoCancelacionService::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public long contarPendientes() {
        return avisoRepository.countPendientesDeAccion(CanalAviso.TELEFONO, CanalAviso.EMAIL,
                EstadoAviso.PENDIENTE, EstadoAviso.FALLIDO, EstadoAviso.PENDIENTE_ENVIO, limiteGracia());
    }

    /** El admin avisó al cliente a mano: AVISADO con quién y cuándo. Idempotente. */
    @Transactional
    public AvisoCancelacionDTO marcarAvisado(Long avisoId, Long adminId) {
        AvisoCancelacion aviso = avisoRepository.findById(avisoId)
                .orElseThrow(() -> new ResourceNotFoundException("Aviso no encontrado"));
        if (aviso.getEstado() != EstadoAviso.AVISADO) {
            Trabajador admin = adminId != null ? trabajadorRepository.findById(adminId).orElse(null) : null;
            aviso.setEstado(EstadoAviso.AVISADO);
            aviso.setAvisadoAt(LocalDateTime.now());
            aviso.setAvisadoPor(admin);
            aviso = avisoRepository.save(aviso);
        }
        return toDTO(aviso);
    }

    private static LocalDateTime limiteGracia() {
        return LocalDateTime.now().minusMinutes(MINUTOS_GRACIA_ENVIO);
    }

    private static String recortar(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > MAX_ERROR ? s.substring(0, MAX_ERROR) : s;
    }

    static AvisoCancelacionDTO toDTO(AvisoCancelacion a) {
        Reserva r = a.getReserva();
        return AvisoCancelacionDTO.builder()
                .id(a.getId())
                .reservaId(r.getId())
                .clienteNombre(r.getCliente().getNombre())
                .clienteTelefono(r.getCliente().getTelefono())
                .clienteEmail(r.getCliente().getEmail())
                .fecha(r.getFecha())
                .horaInicio(r.getHoraInicio())
                .horaFin(r.getHoraFin())
                .servicioNombre(r.getServicio().getNombre())
                .trabajadorNombre(r.getTrabajador().getNombre())
                .motivo(a.getMotivo())
                .canal(a.getCanal().name())
                .estado(a.getEstado().name())
                .error(a.getError())
                .createdAt(a.getCreatedAt())
                .avisadoAt(a.getAvisadoAt())
                .avisadoPorNombre(a.getAvisadoPor() != null ? a.getAvisadoPor().getNombre() : null)
                .build();
    }
}
