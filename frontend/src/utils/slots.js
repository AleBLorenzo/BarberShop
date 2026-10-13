/**
 * When no specific worker is chosen the backend returns the slots of every
 * active worker, so the same time appears several times (once per worker).
 * Keep one entry per horaInicio, preferring an available slot, preserving order.
 * @param {Array<{horaInicio: string, disponible: boolean, trabajadorId?: number, trabajadorNombre?: string}>} slots
 * @returns {Array} deduplicated, sorted by horaInicio
 */
export function dedupeSlots(slots) {
  const map = new Map();
  for (const slot of slots) {
    const existing = map.get(slot.horaInicio);
    if (!existing || (!existing.disponible && slot.disponible)) {
      map.set(slot.horaInicio, slot);
    }
  }
  return [...map.values()].sort((a, b) =>
    String(a.horaInicio).localeCompare(String(b.horaInicio))
  );
}
