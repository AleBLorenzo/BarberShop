import { useState } from 'react';
import Badge from '../ui/Badge';
import { estadoToVariant, estadoToLabel } from '../../utils/estado';
import Button from '../ui/Button';
import Modal from '../ui/Modal';
import { LuPhone } from 'react-icons/lu';

const PAGO_METHODS = ['VISA', 'EFECTIVO'];
const PAGO_LABELS = { VISA: 'VISA / tarjeta', EFECTIVO: 'Efectivo' };

/**
 * Reservation card with status actions for workers.
 * Completing a reservation opens a confirmation modal («¿Vino el cliente?»)
 * where the worker must pick the payment method before COMPLETADA is sent.
 * @param {{reserva: Object, onAction: Function, actionLoading?: number}} props
 */
export default function ReservationCard({ reserva, onAction, actionLoading }) {
  const estado = reserva.estado;
  const isLoading = actionLoading === reserva.id;

  const time = reserva.horaInicio?.slice(0, 5);
  const timeEnd = reserva.horaFin?.slice(0, 5);

  // --- Modal de confirmación de asistencia + pago ---
  const [showConfirm, setShowConfirm] = useState(false);
  const [metodoPago, setMetodoPago] = useState(null);
  const [pagoError, setPagoError] = useState(null);

  const openConfirm = () => {
    setMetodoPago(null);
    setPagoError(null);
    setShowConfirm(true);
  };

  const closeConfirm = () => {
    setShowConfirm(false);
    setMetodoPago(null);
    setPagoError(null);
  };

  const handleSelectPago = (metodo) => {
    setMetodoPago(metodo);
    setPagoError(null);
  };

  const handleConfirmar = () => {
    if (!metodoPago) {
      setPagoError('Selecciona el método de pago para completar la cita');
      return;
    }
    // El toast de error (p. ej. el 400 del backend si falta el método) lo
    // muestra la página que maneja onAction; aquí se cierra al enviar.
    closeConfirm();
    onAction(reserva.id, 'COMPLETADA', metodoPago);
  };

  return (
    <div className="reservation-card">
      <div className="res-time">
        {time} - {timeEnd}
      </div>
      <div className="res-client">
        <div className="name">{reserva.cliente?.nombre}</div>
        <div className="detail">
          <LuPhone aria-hidden="true" style={{ marginRight: 6 }} />
          {reserva.cliente?.telefono} · {reserva.servicio?.nombre}
        </div>
      </div>
      <Badge variant={estadoToVariant(estado)}>{estadoToLabel(estado)}</Badge>
      <div className="res-actions">
        {estado === 'PENDIENTE' && (
          <Button
            size="sm"
            variant="primary"
            disabled={isLoading}
            onClick={() => onAction(reserva.id, 'CONFIRMADA')}
          >
            Confirmar
          </Button>
        )}
        {estado === 'CONFIRMADA' && (
          <Button size="sm" variant="secondary" disabled={isLoading} onClick={openConfirm}>
            Completar
          </Button>
        )}
        {(estado === 'PENDIENTE' || estado === 'CONFIRMADA') && (
          <Button
            size="sm"
            variant="ghost"
            disabled={isLoading}
            onClick={() => onAction(reserva.id, 'NO_ASISTIO')}
          >
            No asistió
          </Button>
        )}
        {(estado === 'PENDIENTE' || estado === 'CONFIRMADA') && (
          <Button
            size="sm"
            variant="danger"
            disabled={isLoading}
            onClick={() => {
              if (window.confirm('El cliente será notificado. ¿Cancelar esta cita?')) {
                onAction(reserva.id, 'CANCELADA');
              }
            }}
          >
            Cancelar
          </Button>
        )}
      </div>

      {/* Confirmar asistencia + método de pago antes de marcar COMPLETADA */}
      <Modal
        open={showConfirm}
        onClose={closeConfirm}
        title="¿Vino el cliente?"
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={closeConfirm}>
              Cancelar
            </Button>
            <Button variant="primary" loading={isLoading} onClick={handleConfirmar}>
              Completar cita
            </Button>
          </>
        }
      >
        <p>
          Confirma la asistencia de <strong>{reserva.cliente?.nombre}</strong> y cómo pagó:
        </p>
        <div className="form-group">
          <span className="form-label" id="pago-label">
            Método de pago<span style={{ color: 'var(--danger)' }}> *</span>
          </span>
          <div
            role="group"
            aria-labelledby="pago-label"
            style={{ display: 'flex', gap: 'var(--space-sm)', flexWrap: 'wrap' }}
          >
            {PAGO_METHODS.map((metodo) => (
              <Button
                key={metodo}
                size="sm"
                variant={metodoPago === metodo ? 'primary' : 'secondary'}
                aria-pressed={metodoPago === metodo}
                onClick={() => handleSelectPago(metodo)}
              >
                {PAGO_LABELS[metodo]}
              </Button>
            ))}
          </div>
          {pagoError && (
            <p className="form-error" role="alert">
              {pagoError}
            </p>
          )}
        </div>
      </Modal>
    </div>
  );
}
