/**
 * iCalendar (.ics) generation — lets clients add a reservation to their
 * phone/desktop calendar (Apple Calendar, Google Calendar, Outlook...).
 * The event includes a 30-minute-before alarm as a reminder.
 */

import { NOMBRE } from '../config/business';

/** Escape a text value per RFC 5545 (§3.3.11). */
function escapeIcs(text) {
  return String(text ?? '')
    .replace(/\\/g, '\\\\')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,')
    .replace(/\r?\n/g, '\\n');
}

/** Fold long content lines at ~75 octets per RFC 5545 (§3.1). */
function foldLine(line) {
  if (line.length <= 74) return line;
  const chunks = [line.slice(0, 74)];
  let rest = line.slice(74);
  while (rest.length > 73) {
    chunks.push(' ' + rest.slice(0, 73));
    rest = rest.slice(73);
  }
  if (rest) chunks.push(' ' + rest);
  return chunks.join('\r\n');
}

/** "18:00:00" → "180000", "18:00" → "180000" (ICS HHMMSS local time). */
function icsTime(time) {
  const compact = String(time || '').replace(/:/g, '');
  return compact.length === 4 ? `${compact}00` : compact;
}

/** Current UTC timestamp as ICS DTSTAMP value. */
function icsNow() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return (
    `${d.getUTCFullYear()}${p(d.getUTCMonth() + 1)}${p(d.getUTCDate())}` +
    `T${p(d.getUTCHours())}${p(d.getUTCMinutes())}${p(d.getUTCSeconds())}Z`
  );
}

/**
 * Build the iCalendar document for a reservation.
 * @param {object} r ReservaResponse-like data: id, fecha (YYYY-MM-DD),
 *   horaInicio/horaFin (HH:MM:SS), servicioNombre, trabajadorNombre,
 *   clienteNombre, clienteTelefono, precioTotal, tokenCancelacion
 * @returns {string} .ics content with CRLF line endings
 */
export function buildReservaIcs(r) {
  const dtStart = `${String(r.fecha).replace(/-/g, '')}T${icsTime(r.horaInicio)}`;
  const dtEnd = `${String(r.fecha).replace(/-/g, '')}T${icsTime(r.horaFin)}`;

  const description = [
    `Cliente: ${r.clienteNombre ?? ''}`,
    `Teléfono: ${r.clienteTelefono ?? ''}`,
    `Trabajador: ${r.trabajadorNombre ?? ''}`,
    `Servicio: ${r.servicioNombre ?? ''}`,
    r.precioTotal != null ? `Precio: ${r.precioTotal} €` : null,
    r.tokenCancelacion ? `Token de cancelación: ${r.tokenCancelacion}` : null,
  ]
    .filter(Boolean)
    .join('\n');

  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//' + NOMBRE + '//Reservas//ES',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    'BEGIN:VEVENT',
    `UID:barbershop-reserva-${r.id ?? 'x'}@barbershop`,
    `DTSTAMP:${icsNow()}`,
    `DTSTART:${dtStart}`,
    `DTEND:${dtEnd}`,
    `SUMMARY:${escapeIcs(`${r.servicioNombre ?? 'Cita'} · ${NOMBRE}`)}`,
    `DESCRIPTION:${escapeIcs(description)}`,
    `LOCATION:${escapeIcs(NOMBRE)}`,
    'BEGIN:VALARM',
    'TRIGGER:-PT30M',
    'ACTION:DISPLAY',
    `DESCRIPTION:${escapeIcs(`Cita en ${NOMBRE} en 30 minutos`)}`,
    'END:VALARM',
    'END:VEVENT',
    'END:VCALENDAR',
  ];

  return lines.map(foldLine).join('\r\n') + '\r\n';
}

/**
 * Download the reservation as an .ics file. On mobile it opens the system
 * calendar to save the event with its reminder.
 * @param {object} r ReservaResponse-like data
 */
export function downloadReservaIcs(r) {
  const blob = new Blob([buildReservaIcs(r)], {
    type: 'text/calendar;charset=utf-8',
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `barbershop-cita-${r.id ?? 'reserva'}.ics`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
}
