import { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { adminApi, AVISOS_CAMBIADOS_EVENT } from '../services/api';

/**
 * Nº de avisos de cancelación pendientes de acción (D7) para el contador del
 * menú admin. Se recarga al navegar, cada 60 s y cuando la API emite
 * `AVISOS_CAMBIADOS_EVENT` (cierre creado, cita cancelada, aviso marcado).
 * @param {boolean} enabled solo ADMIN (el endpoint responde 403 al resto)
 * @returns {number}
 */
export function useAvisosPendientes(enabled) {
  const [count, setCount] = useState(0);
  const { pathname } = useLocation();

  useEffect(() => {
    if (!enabled) return undefined;
    let cancelled = false;
    const refresh = () => {
      adminApi
        .contarAvisos()
        .then((n) => { if (!cancelled) setCount(n); })
        .catch(() => { /* contador best-effort */ });
    };
    refresh();
    const interval = setInterval(refresh, 60000);
    window.addEventListener(AVISOS_CAMBIADOS_EVENT, refresh);
    return () => {
      cancelled = true;
      clearInterval(interval);
      window.removeEventListener(AVISOS_CAMBIADOS_EVENT, refresh);
    };
  }, [enabled, pathname]);

  return enabled ? count : 0;
}
