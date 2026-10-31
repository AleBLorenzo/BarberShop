import { useState } from 'react';
import Button from '../ui/Button';
import Badge from '../ui/Badge';
import { EmptyState } from '../ui/EmptyState';
import CancelarReservaModal from './CancelarReservaModal';
import { formatDate, formatTime } from '../../utils/date';

const TIPO_LABEL = {
  DIA_CERRADO: 'Día cerrado',
  FUERA_DE_JORNADA: 'Fuera de horario',
  SOBRE_BLOQUEO: 'Sobre horas libres',
  CIERRE: 'Cierre',
  SOLAPA_BLOQUEO: 'Solapa',
};

/**
 * Lista de citas en conflicto con la agenda (horario reducido, día cerrado,
 * bloqueo…). Las citas NO se cancelan solas: cada una tiene "Cancelar y avisar".
 * Tarjetas apiladas (mobile-first) en vez de tabla.
 *
 * @param {{conflictos: Array<object>, onCancelada?: (reservaId: number) => void, emptyMessage?: string}} props
 */
export default function ConflictosList({ conflictos, onCancelada, emptyMessage }) {
  const [cita, setCita] = useState(null);

  if (!conflictos || conflictos.length === 0) {
    return (
      <EmptyState
        title="Sin conflictos"
        message={emptyMessage || 'Ninguna cita activa queda fuera del horario.'}
      />
    );
  }

  return (
    <>
      <div className="reservation-list">
        {conflictos.map((c) => (
          <div key={c.reservaId} className="reservation-card">
            <div className="res-time">
              {formatDate(c.fecha, 'EEE dd/MM')}
              <br />
              <small>
                {formatTime(c.horaInicio)} – {formatTime(c.horaFin)}
              </small>
            </div>
            <div className="res-client">
              <div className="name">{c.clienteNombre}</div>
              <div className="detail">
                {c.clienteTelefono && (
                  <a href={`tel:${c.clienteTelefono}`}>{c.clienteTelefono}</a>
                )}
                {' · '}
                {c.servicioNombre} · {c.trabajadorNombre}
              </div>
              {c.motivo && <div className="detail">{c.motivo}</div>}
            </div>
            {c.tipo && <Badge variant="pending">{TIPO_LABEL[c.tipo] || c.tipo}</Badge>}
            <div className="res-actions">
              <Button
                size="sm"
                variant="danger"
                onClick={() =>
                  setCita({
                    id: c.reservaId,
                    fecha: c.fecha,
                    horaInicio: c.horaInicio,
                    clienteNombre: c.clienteNombre,
                    clienteTelefono: c.clienteTelefono,
                    servicioNombre: c.servicioNombre,
                    trabajadorNombre: c.trabajadorNombre,
                    clienteTieneEmail: c.clienteTieneEmail,
                  })
                }
              >
                Cancelar y avisar
              </Button>
            </div>
          </div>
        ))}
      </div>

      <CancelarReservaModal
        key={cita?.id ?? 'none'}
        cita={cita}
        onClose={() => setCita(null)}
        onCancelada={() => {
          const id = cita.id;
          setCita(null);
          onCancelada?.(id);
        }}
      />
    </>
  );
}
