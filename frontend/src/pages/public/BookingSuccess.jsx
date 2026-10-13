import { useLocation, Navigate, Link } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { formatDate, formatTime, formatCurrency } from '../../utils/date';
import { estadoToLabel, estadoToVariant } from '../../utils/estado';
import { downloadReservaIcs } from '../../utils/ics';
import { useBooking } from '../../hooks/useBooking';
import Button from '../../components/ui/Button';
import Badge from '../../components/ui/Badge';
import { LuCircleCheck, LuCalendar } from 'react-icons/lu';

const STORAGE_KEY = 'lastReserva';

function loadStored() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (raw) return JSON.parse(raw);
  } catch {
    /* ignore corrupted receipt */
  }
  return null;
}

/**
 * Booking success page: full receipt with every reservation field + the
 * cancellation token (copy button) + .ics download so the appointment can be
 * added to the phone calendar with a reminder. Survives a page refresh via
 * sessionStorage.
 */
export default function BookingSuccess() {
  const { state } = useLocation();
  const { clearDraft } = useBooking();
  const [copied, setCopied] = useState(false);
  const [reserva] = useState(() => state || loadStored());

  useEffect(() => {
    if (state) {
      try {
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
      } catch {
        /* storage unavailable */
      }
      // The receipt is on screen: the wizard draft is no longer needed.
      // Clearing it HERE (not in BookingConfirm) avoids the guard race that
      // used to bounce users back to /reservar/servicio.
      clearDraft();
    }
  }, [state, clearDraft]);

  if (!reserva) {
    return <Navigate to="/" replace />;
  }

  const token = reserva.tokenCancelacion || reserva.token;
  const fecha = reserva.fecha || reserva.slot?.fecha;
  const ini = reserva.horaInicio || reserva.slot?.horaInicio;
  const fin = reserva.horaFin || reserva.slot?.horaFin;
  const horario = [formatTime(ini), formatTime(fin)].filter(Boolean).join(' – ');
  const servicioNombre = reserva.servicioNombre || reserva.servicio?.nombre;
  const trabajadorNombre =
    reserva.trabajadorNombre || reserva.slot?.trabajadorNombre || 'Cualquiera';
  const precio = reserva.precioTotal ?? reserva.servicio?.precio;
  const duracion = reserva.duracionMinutos ?? reserva.servicio?.duracionMinutos;

  const row = (label, value) =>
    value != null && value !== '' ? (
      <div className="summary-row">
        <span className="label">{label}</span>
        <span className="value">{value}</span>
      </div>
    ) : null;

  const copyToken = async () => {
    if (!token) return;
    try {
      await navigator.clipboard.writeText(String(token));
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      /* clipboard unavailable */
    }
  };

  return (
    <div className="booking-wizard" style={{ textAlign: 'center' }}>
      <div style={{ fontSize: 'var(--font-size-3xl)', marginBottom: 8 }}>
        <LuCircleCheck aria-hidden="true" />
      </div>
      <h1 style={{ fontSize: 'var(--font-size-2xl)' }}>¡Reserva confirmada!</h1>
      <p style={{ color: 'var(--muted)' }}>
        Te esperamos el {fecha ? formatDate(fecha, 'EEEE dd/MM/yyyy') : ''} a las{' '}
        {formatTime(ini)}
      </p>

      {/* Recibo con todos los datos de la reserva */}
      <div
        className="booking-summary"
        style={{ textAlign: 'left', maxWidth: 480, margin: '24px auto' }}
      >
        {row('Reserva nº', reserva.id)}
        {row('Cliente', reserva.clienteNombre)}
        {row('Teléfono', reserva.clienteTelefono)}
        {row('Servicio', servicioNombre)}
        {row('Duración', duracion ? `${duracion} min` : null)}
        {row('Trabajador', trabajadorNombre)}
        {row('Fecha', fecha ? formatDate(fecha, 'EEEE dd/MM/yyyy') : null)}
        {row('Horario', horario)}
        {row('Precio', formatCurrency(precio))}
        {reserva.estado &&
          row('Estado', <Badge variant={estadoToVariant(reserva.estado)}>{estadoToLabel(reserva.estado)}</Badge>)}
        {token && (
          <div className="summary-row">
            <span className="label">Token de cancelación</span>
            <span
              className="value"
              style={{ fontSize: 'var(--font-size-xs)', wordBreak: 'break-all', fontFamily: 'monospace' }}
            >
              {token}
            </span>
          </div>
        )}
      </div>

      <div
        style={{
          display: 'flex',
          gap: 12,
          justifyContent: 'center',
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <Button variant="primary" onClick={() => downloadReservaIcs(reserva)}>
          <LuCalendar aria-hidden="true" />
          Añadir a mi calendario
        </Button>
        {token && (
          <Button variant="secondary" onClick={copyToken}>
            {copied ? '¡Copiado!' : 'Copiar token'}
          </Button>
        )}
      </div>

      <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--muted)', maxWidth: 480, margin: '0 auto' }}>
        «Añadir a mi calendario» descarga un recordatorio con la cita y una
        alarma 30 minutos antes. Guarda el token para cancelar o reprogramar
        tu cita.
      </p>

      <div style={{ display: 'flex', gap: 12, justifyContent: 'center', flexWrap: 'wrap', marginTop: 24 }}>
        <Link to="/" className="btn btn-primary btn-md">Volver al inicio</Link>
        <Link to="/mis-citas" className="btn btn-secondary btn-md">Ver mis citas</Link>
      </div>
    </div>
  );
}
