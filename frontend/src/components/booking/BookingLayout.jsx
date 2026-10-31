import { Suspense } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import StepIndicator from './StepIndicator';
import RouteFallback from '../RouteFallback';

const stepMap = {
  '/reservar/servicio': 1,
  '/reservar/trabajador': 2,
  '/reservar/fecha-hora': 3,
  '/reservar/confirmar': 4,
};

/**
 * Layout del asistente de reserva: título + indicador de pasos comunes a los
 * 4 pasos. El estado del borrador lo da el <BookingProvider> de App.jsx (no se
 * anida otro aquí: perdería el borrador al llegar a /reservar/exito).
 * El <Suspense> propio evita que el título y los pasos parpadeen mientras
 * carga el chunk lazy de cada paso.
 */
export default function BookingLayout() {
  const { pathname } = useLocation();
  const step = stepMap[pathname] || 1;

  return (
    <div className="booking-wizard">
      <h1 className="booking-title">Reservar cita</h1>
      <StepIndicator current={step} />
      <Suspense fallback={<RouteFallback />}>
        <Outlet />
      </Suspense>
    </div>
  );
}
