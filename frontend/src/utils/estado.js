/**
 * Map backend estado to badge variant.
 * @param {string} estado
 * @returns {'pending'|'confirmed'|'completed'|'cancelled'|'no-show'|'default'}
 */
export function estadoToVariant(estado) {
  const map = {
    PENDIENTE: 'pending',
    CONFIRMADA: 'confirmed',
    COMPLETADA: 'completed',
    CANCELADA: 'cancelled',
    NO_ASISTIO: 'no-show',
  };
  return map[estado] || 'default';
}

/**
 * Map backend estado to Spanish label.
 * @param {string} estado
 * @returns {string}
 */
export function estadoToLabel(estado) {
  const map = {
    PENDIENTE: 'Pendiente',
    CONFIRMADA: 'Confirmada',
    COMPLETADA: 'Completada',
    CANCELADA: 'Cancelada',
    NO_ASISTIO: 'No asistió',
  };
  return map[estado] || estado;
}
