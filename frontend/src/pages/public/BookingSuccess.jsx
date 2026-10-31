import { useLocation, Navigate, Link } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { formatDate, formatTime, formatCurrency } from '../../utils/date';
import { estadoToLabel, estadoToVariant } from '../../utils/estado';
import { downloadReservaIcs } from '../../utils/ics';
import { useBooking } from '../../hooks/useBooking';
import Button from '../../components/ui/Button';
import Badge from '../../components/ui/Badge';
import { LuCircleCheck, LuCalendar } from 'react-icons/lu';
import { useSeo } from '../../components/Seo';

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
  // Recibo de la reserva (transitorio, D2). Solo tiene título: no lleva
  // noindex porque el diseño reserva esa directiva a las rutas de la spec.
  useSeo({
    title: 'Cita confirmada — TheTankBarber',
    description: 'Tu cita en TheTankBarber se ha registrado correctamente.',
  });

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

  // Regla de representación segura (spec booking-without-email-degradation):
  // el recibo NUNCA imprime `null`/`undefined` — el helper `row()` omite los
  // valores `null`/`''` y `ReservaResponse` no expone el email del cliente. Si
  // algún día se expone, la ausencia se renderiza como "—" o se omite la fila.
  //
  // Aviso honesto sin email: `null` = el cliente no dejó email (no se envió
  // confirmación); `undefined` = desconocido (p. ej. al reprogramar) → sin aviso.
  const sinEmail = reserva.clienteEmail === null || reserva.clienteEmail === '';

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
    <div className="booking-wizard booking-success">
      <div className="booking-success__icon">
        <LuCircleCheck aria-hidden="true" />
      </div>
      <h1 className="booking-success__title">¡Reserva confirmada!</h1>
      <p className="muted-text">
        Te esperamos el {fecha ? formatDate(fecha, 'EEEE dd/MM/yyyy') : ''} a las{' '}
        {formatTime(ini)}
      </p>

      {/* Recibo con todos los datos de la reserva */}
      <div className="booking-summary">
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
            <span className="value token-text">{token}</span>
          </div>
        )}
      </div>

      {sinEmail && (
        <p role="status" className="booking-success__notice">
          Sin email registrado: no se ha enviado confirmación por correo. Guarda
          o copia el token para gestionar tu cita.
        </p>
      )}

      <div className="booking-success__actions">
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

      <p className="booking-success__note">
        «Añadir a mi calendario» descarga un recordatorio con la cita y una
        alarma 30 minutos antes. Guarda el token para cancelar o reprogramar
        tu cita.
      </p>

      <div className="booking-success__actions booking-success__actions--end">
        <Link to="/" className="btn btn-primary btn-md">Volver al inicio</Link>
        <Link to="/mis-citas" className="btn btn-secondary btn-md">Ver mis citas</Link>
      </div>
    </div>
  );
}
