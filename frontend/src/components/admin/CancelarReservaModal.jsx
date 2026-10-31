import { useState } from 'react';
import Modal from '../ui/Modal';
import Button from '../ui/Button';
import { Textarea } from '../ui/Input';
import { adminApi } from '../../services/api';
import { formatDate, formatTime } from '../../utils/date';

/**
 * Modal "Cancelar y avisar" (D6/D7): cancela una cita activa por el negocio
 * con motivo opcional. El backend avisa al cliente por email o, si no dejó
 * email, crea un aviso telefónico pendiente en "Cierres → Clientes por avisar".
 *
 * Se monta con `key` distinta por cita para reiniciar el motivo.
 *
 * @param {{
 *   cita: null | {id: number, fecha: string, horaInicio: string, clienteNombre?: string, clienteTelefono?: string, servicioNombre?: string, trabajadorNombre?: string, clienteTieneEmail?: boolean},
 *   onClose: Function,
 *   onCancelada: (reserva: object) => void,
 * }} props
 */
export default function CancelarReservaModal({ cita, onClose, onCancelada }) {
  const [motivo, setMotivo] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  const confirmar = async () => {
    if (!cita) return;
    setSaving(true);
    setError(null);
    try {
      const reserva = await adminApi.cancelarReserva(cita.id, motivo);
      onCancelada?.(reserva);
    } catch (err) {
      setError(err?.response?.data?.message || 'No se pudo cancelar la cita');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={!!cita}
      onClose={onClose}
      title="Cancelar y avisar al cliente"
      size="sm"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Volver</Button>
          <Button variant="danger" loading={saving} onClick={confirmar}>
            Cancelar cita
          </Button>
        </>
      }
    >
      {cita && (
        <div className="booking-summary booking-summary--compact">
          <div className="summary-row">
            <span className="label">Cliente</span>
            <span className="value">
              {cita.clienteNombre}
              {cita.clienteTelefono ? ` · ${cita.clienteTelefono}` : ''}
            </span>
          </div>
          <div className="summary-row">
            <span className="label">Cita</span>
            <span className="value">
              {formatDate(cita.fecha)} · {formatTime(cita.horaInicio)}
              {cita.servicioNombre ? ` · ${cita.servicioNombre}` : ''}
            </span>
          </div>
          {cita.trabajadorNombre && (
            <div className="summary-row">
              <span className="label">Trabajador</span>
              <span className="value">{cita.trabajadorNombre}</span>
            </div>
          )}
        </div>
      )}
      <Textarea
        id="motivo-cancelacion"
        label="Motivo (se le comunica al cliente)"
        placeholder="Ej.: el barbero está enfermo"
        maxLength={255}
        rows={3}
        value={motivo}
        onChange={(e) => setMotivo(e.target.value)}
      />
      <p className="hint-text">
        {cita?.clienteTieneEmail === false
          ? 'El cliente no dejó email: quedará en "Clientes por avisar" para que le llames.'
          : 'Si el cliente dejó email se le enviará un correo; si no, quedará en "Clientes por avisar".'}
      </p>
      {error && <p className="form-error" role="alert">{error}</p>}
    </Modal>
  );
}
