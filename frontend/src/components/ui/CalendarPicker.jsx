import { useState, useRef } from 'react';
import {
  format, parseISO, startOfMonth, endOfMonth, startOfWeek, endOfWeek,
  addDays, addMonths, isBefore, isAfter, isSameMonth, isSameDay,
} from 'date-fns';
import { es } from 'date-fns/locale/es';
import { LuChevronLeft, LuChevronRight } from 'react-icons/lu';

const WEEKDAYS = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];

/** Capitaliza la primera letra (date-fns en español devuelve minúsculas). */
const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);

/**
 * Calendario visual inline para elegir la fecha de una cita.
 * Sustituye al input nativo con un mes navegable, días deshabilitados
 * fuera de rango, marcador de hoy y navegación por teclado (flechas).
 * @param {{label?: string, value?: string|null, onChange: Function, min?: string, max?: string, error?: string}} props
 */
export default function CalendarPicker({ label, value, onChange, min, max, error, required }) {
  const parse = (v) => (v instanceof Date ? v : v ? parseISO(v) : null);
  const minD = min ? parse(min) : null;
  const maxD = max ? parse(max) : null;
  const selected = value ? parse(value) : null;

  const [month, setMonth] = useState(() => startOfMonth(selected || new Date()));
  const [focusIdx, setFocusIdx] = useState(-1);
  const cellRefs = useRef([]);

  // Rejilla de 6 filas × 7 días (semana starting en lunes)
  const gridStart = startOfWeek(startOfMonth(month), { weekStartsOn: 1 });
  const gridEnd = endOfWeek(endOfMonth(month), { weekStartsOn: 1 });
  const cells = [];
  for (let d = gridStart; d <= gridEnd; d = addDays(d, 1)) cells.push(d);

  const inRange = (d) =>
    (!minD || !isBefore(d, minD)) && (!maxD || !isAfter(d, maxD));

  const disabled = cells.map((d) => !isSameMonth(d, month) || !inRange(d));

  const canGoPrev = minD ? isAfter(month, startOfMonth(minD)) : true;
  const canGoNext = maxD ? isBefore(month, startOfMonth(maxD)) : true;

  // Roving tabindex: la foco cae en la seleccionada, hoy o la primera celda
  // habilitada del mes (nunca en una celda deshabilitada: saltaría el Tab)
  const selIdx = selected ? cells.findIndex((d) => isSameDay(d, selected)) : -1;
  const todayIdx = cells.findIndex((d, i) => isSameDay(d, new Date()) && !disabled[i]);
  const firstEnabled = disabled.indexOf(false);
  const defaultIdx = selIdx >= 0 && !disabled[selIdx]
    ? selIdx
    : (todayIdx >= 0 ? todayIdx : Math.max(0, firstEnabled));
  const rovingIdx = focusIdx >= 0 && focusIdx < cells.length && !disabled[focusIdx]
    ? focusIdx
    : defaultIdx;

  const goMonth = (delta) => {
    setMonth((m) => addMonths(m, delta));
    setFocusIdx(-1);
  };

  /** Busca la primera celda habilitada desde `from` en la dirección `dir`. */
  const seek = (from, dir) => {
    for (let i = from; i >= 0 && i < cells.length; i += dir) {
      if (!disabled[i]) return i;
    }
    return -1;
  };

  const onKeyDown = (e) => {
    const moves = {
      ArrowRight: [rovingIdx + 1, 1],
      ArrowLeft: [rovingIdx - 1, -1],
      ArrowDown: [rovingIdx + 7, 1],
      ArrowUp: [rovingIdx - 7, -1],
      Home: [0, 1],
      End: [cells.length - 1, -1],
    };
    const move = moves[e.key];
    if (!move) return;
    e.preventDefault();
    const [raw, dir] = move;
    const target = seek(Math.min(Math.max(raw, 0), cells.length - 1), dir);
    if (target >= 0) {
      setFocusIdx(target);
      cellRefs.current[target]?.focus();
    }
  };

  return (
    <div className="form-group calendar-group">
      {label && (
        <div className="form-label">
          {label}
          {required && <span style={{ color: 'var(--danger)' }}> *</span>}
        </div>
      )}
      <div className="calendar" role="group" aria-label={label || 'Calendario'}>
        <div className="calendar-header">
          <div className="calendar-title" aria-live="polite">
            {cap(format(month, 'MMMM yyyy', { locale: es }))}
          </div>
          <div className="calendar-nav-group">
            <button
              type="button"
              className="calendar-nav"
              onClick={() => goMonth(-1)}
              disabled={!canGoPrev}
              aria-label="Mes anterior"
            >
              <LuChevronLeft aria-hidden="true" />
            </button>
            <button
              type="button"
              className="calendar-nav"
              onClick={() => goMonth(1)}
              disabled={!canGoNext}
              aria-label="Mes siguiente"
            >
              <LuChevronRight aria-hidden="true" />
            </button>
          </div>
        </div>

        <div className="calendar-weekdays" aria-hidden="true">
          {WEEKDAYS.map((d) => <span key={d}>{d}</span>)}
        </div>

        <div className="calendar-grid" key={month.getTime()} onKeyDown={onKeyDown}>
          {cells.map((d, i) => {
            const outside = !isSameMonth(d, month);
            const isSel = Boolean(selected && isSameDay(d, selected));
            const isToday = isSameDay(d, new Date());
            return (
              <button
                key={i}
                ref={(el) => { cellRefs.current[i] = el; }}
                type="button"
                className={
                  `calendar-day${outside ? ' is-outside' : ''}` +
                  `${isSel ? ' is-selected' : ''}${isToday ? ' is-today' : ''}`
                }
                tabIndex={i === rovingIdx ? 0 : -1}
                disabled={disabled[i]}
                aria-pressed={isSel || undefined}
                aria-current={isToday ? 'date' : undefined}
                aria-label={`${cap(format(d, "EEEE d 'de' MMMM", { locale: es }))}${isSel ? ' (seleccionada)' : ''}`}
                onClick={() => {
                  setFocusIdx(i);
                  onChange(format(d, 'yyyy-MM-dd'));
                }}
              >
                {format(d, 'd')}
              </button>
            );
          })}
        </div>

        {error && <p className="form-error" role="alert">{error}</p>}
      </div>
    </div>
  );
}
