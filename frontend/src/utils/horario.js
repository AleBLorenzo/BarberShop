/**
 * Utilidades del horario laboral (cambio admin-agenda-y-cierres, D1/D12).
 * El backend usa `java.time.DayOfWeek` (MONDAY…SUNDAY) y horas "HH:mm:ss".
 */

/** Días de la semana en el orden del backend (lunes primero). */
export const DIAS_SEMANA = [
  { key: 'MONDAY', label: 'Lunes', corto: 'Lun' },
  { key: 'TUESDAY', label: 'Martes', corto: 'Mar' },
  { key: 'WEDNESDAY', label: 'Miércoles', corto: 'Mié' },
  { key: 'THURSDAY', label: 'Jueves', corto: 'Jue' },
  { key: 'FRIDAY', label: 'Viernes', corto: 'Vie' },
  { key: 'SATURDAY', label: 'Sábado', corto: 'Sáb' },
  { key: 'SUNDAY', label: 'Domingo', corto: 'Dom' },
];

/** "09:00:00" → "09:00" (cadena vacía si no hay hora). */
export function hhmm(time) {
  if (!time) return '';
  return time.length > 5 ? time.slice(0, 5) : time;
}

/** "9:00" / "09:00" → "09:00:00" (o null si está vacío). */
export function toApiTime(value) {
  if (!value) return null;
  const [h, m = '00'] = value.split(':');
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:00`;
}

/**
 * Resume el horario general en líneas legibles agrupando días consecutivos
 * con el mismo horario, p. ej. "Lunes a sábado · 9:00 – 20:00", "Domingo · Cerrado".
 * @param {Array<{diaSemana: string, abierto: boolean, horaInicio?: string, horaFin?: string}>} dias
 * @returns {string[]}
 */
export function resumirHorario(dias) {
  const porDia = new Map((dias || []).map((d) => [d.diaSemana, d]));
  const sinCero = (t) => hhmm(t).replace(/^0(\d)/, '$1');
  const clave = (d) => (d && d.abierto && d.horaInicio && d.horaFin
    ? `${sinCero(d.horaInicio)} – ${sinCero(d.horaFin)}`
    : 'Cerrado');

  const grupos = [];
  DIAS_SEMANA.forEach((dia) => {
    const texto = clave(porDia.get(dia.key));
    const ultimo = grupos[grupos.length - 1];
    if (ultimo && ultimo.texto === texto) {
      ultimo.hasta = dia;
    } else {
      grupos.push({ desde: dia, hasta: dia, texto });
    }
  });

  return grupos.map(({ desde, hasta, texto }) => {
    const rango = desde.key === hasta.key
      ? desde.label
      : `${desde.label} a ${hasta.label.toLowerCase()}`;
    return `${rango} · ${texto}`;
  });
}
