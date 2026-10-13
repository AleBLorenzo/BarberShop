import { LuX } from 'react-icons/lu';

/**
 * TimeSlotPicker grid for booking step 3.
 * @param {{slots: Array<{horaInicio: string, horaFin: string, disponible: boolean, trabajadorNombre?: string}>, selected?: string, onSelect: Function, loading?: boolean}} props
 */
export default function TimeSlotPicker({ slots = [], selected, onSelect, loading }) {
  if (loading) {
    return (
      <div className="time-slot-grid">
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} className="skeleton" style={{ height: 40 }} />
        ))}
      </div>
    );
  }

  if (slots.length === 0) {
    return <p style={{ color: 'var(--muted)' }}>No hay horarios disponibles para esta fecha.</p>;
  }

  return (
    <div className="time-slot-grid" role="radiogroup" aria-label="Selecciona un horario">
      {slots.map((slot) => {
        const isSelected = selected === slot.horaInicio;
        return (
          <button
            key={slot.horaInicio}
            type="button"
            className={`time-slot ${isSelected ? 'selected' : ''}`}
            disabled={!slot.disponible}
            onClick={() => onSelect(slot)}
            role="radio"
            aria-checked={isSelected}
            title={!slot.disponible ? 'Ocupado' : undefined}
          >
            {slot.horaInicio.slice(0, 5)}
            {!slot.disponible && (
              <LuX aria-hidden="true" style={{ marginLeft: 'var(--space-xs)' }} />
            )}
          </button>
        );
      })}
    </div>
  );
}
