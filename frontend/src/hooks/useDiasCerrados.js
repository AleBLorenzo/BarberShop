import { useState, useEffect } from 'react';
import { format, startOfMonth, endOfMonth, isAfter, isBefore, parseISO } from 'date-fns';
import { publicApi } from '../services/api';

/**
 * Días cerrados (sin jornada) del mes visible del calendario público (D12):
 * `GET /api/public/dias-cerrados` acotado a [min, max] (máx. 62 días; un mes
 * siempre cabe). Best-effort: si falla, no se marca ningún día.
 * @param {Date} month primer día del mes visible
 * @param {{min: string, max: string, trabajadorId?: number}} opts
 * @returns {Set<string>} fechas ISO cerradas
 */
export function useDiasCerrados(month, { min, max, trabajadorId }) {
  const [cerrados, setCerrados] = useState(() => new Set());
  const monthKey = format(month, 'yyyy-MM');

  useEffect(() => {
    let cancelled = false;
    const minD = parseISO(min);
    const maxD = parseISO(max);
    let desde = startOfMonth(month);
    let hasta = endOfMonth(month);
    if (isBefore(desde, minD)) desde = minD;
    if (isAfter(hasta, maxD)) hasta = maxD;
    if (isAfter(desde, hasta)) return undefined;

    publicApi
      .getDiasCerrados(format(desde, 'yyyy-MM-dd'), format(hasta, 'yyyy-MM-dd'), trabajadorId)
      .then((fechas) => {
        if (!cancelled) setCerrados(new Set(fechas));
      })
      .catch(() => {
        if (!cancelled) setCerrados(new Set());
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- monthKey identifica el mes
  }, [monthKey, min, max, trabajadorId]);

  return cerrados;
}
