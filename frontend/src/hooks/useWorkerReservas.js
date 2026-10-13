import { useState, useEffect, useCallback, useRef } from 'react';
import { endOfMonth, format, startOfMonth } from 'date-fns';
import { workerApi } from '../services/api';

/**
 * Worker reservations hook with 60s auto-refresh.
 * The selected week is remembered inside the hook, so the auto-refresh keeps
 * fetching the week the worker is currently viewing. Also keeps the current
 * calendar month loaded (`reservasMes`) for the daily/monthly income cards.
 * @returns {{semana: Array, reservasMes: Array, loading: boolean, error: string|null, refresh: Function, reservasDia: Array, loadingDia: boolean, fechaSeleccionada: string|null, fetchDia: Function, cambiarEstado: Function}}
 */
export function useWorkerReservas() {
  const [semana, setSemana] = useState([]);
  const [reservasMes, setReservasMes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [reservasDia, setReservasDia] = useState([]);
  const [loadingDia, setLoadingDia] = useState(false);
  const [fechaSeleccionada, setFechaSeleccionada] = useState(null);
  const intervalRef = useRef(null);
  // 'YYYY-MM-DD' del lunes seleccionado, o null = semana en curso
  const weekStartRef = useRef(null);

  /**
   * @param {string|null} [fechaInicio] lunes de la semana a cargar; si se pasa
   *   (string o null) se fija como semana seleccionada; si se omite se recarga
   *   la semana seleccionada actual (útil para el auto-refresh silencioso)
   * @param {boolean} [silent] no altera el estado de carga inicial
   */
  const fetchSemana = useCallback(async (fechaInicio, silent = false) => {
    // Solo string ('YYYY-MM-DD') o null cambian la semana seleccionada;
    // cualquier otro argumento (p. ej. un evento) se ignora
    if (typeof fechaInicio === 'string' || fechaInicio === null) {
      weekStartRef.current = fechaInicio;
    }
    try {
      const data = await workerApi.getReservasSemana(weekStartRef.current || undefined);
      setSemana(data);
      setError(null);
      if (!silent) setLoading(false);
    } catch {
      if (!silent) {
        setError('No se pudieron cargar las reservas.');
        setLoading(false);
      }
    }
  }, []);

  // Reservas del mes natural en curso (1 → último día): alimentan las tarjetas
  // de "Ingresos diario" y "Ingresos mensual". Se recalculan los límites en
  // cada llamada, así el auto-refresh cruza el mes sin recargar la página.
  const fetchMes = useCallback(async () => {
    const hoy = new Date();
    const fechaInicio = format(startOfMonth(hoy), 'yyyy-MM-dd');
    const fechaFin = format(endOfMonth(hoy), 'yyyy-MM-dd');
    try {
      const data = await workerApi.getReservasRango(fechaInicio, fechaFin);
      setReservasMes(data);
    } catch {
      // Silencioso: la semana ya gestiona su propio error; el mes es best-effort
    }
  }, []);

  const fetchDia = useCallback(async (fecha) => {
    setFechaSeleccionada(fecha);
    try {
      const data = await workerApi.getReservasDia(fecha);
      setReservasDia(data);
      setLoadingDia(false);
    } catch {
      setError('No se pudo cargar el día seleccionado.');
      setLoadingDia(false);
    }
  }, []);

  // 60s auto-refresh (de la semana seleccionada actualmente y del mes en curso)
  useEffect(() => {
    fetchSemana();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    fetchMes();
    intervalRef.current = setInterval(() => {
      fetchSemana(undefined, true);
      fetchMes();
    }, 60000);
    return () => clearInterval(intervalRef.current);
  }, [fetchSemana, fetchMes]);

  const cambiarEstado = useCallback(
    async (reservaId, estado, metodoPago) => {
      await workerApi.cambiarEstado(reservaId, estado, metodoPago);
      await Promise.all([fetchSemana(undefined, true), fetchMes()]);
      if (fechaSeleccionada) {
        const data = await workerApi.getReservasDia(fechaSeleccionada);
        setReservasDia(data);
      }
    },
    [fetchSemana, fetchMes, fechaSeleccionada]
  );

  return {
    semana,
    reservasMes,
    loading,
    error,
    // Refresca semana y mes a la vez (botón "Actualizar" y reintentos)
    refresh: () => {
      fetchSemana();
      fetchMes();
    },
    fetchSemana,
    reservasDia,
    loadingDia,
    fechaSeleccionada,
    fetchDia,
    setReservasDia,
    cambiarEstado,
  };
}
