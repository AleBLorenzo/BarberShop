import { Outlet, useLocation } from 'react-router-dom';
import { BookingProvider } from '../../context/BookingContext';
import StepIndicator from './StepIndicator';

const stepMap = {
  '/reservar/servicio': 1,
  '/reservar/trabajador': 2,
  '/reservar/fecha-hora': 3,
  '/reservar/confirmar': 4,
};

/**
 * Layout for the booking wizard: provides booking context + step indicator.
 */
export default function BookingLayout() {
  const { pathname } = useLocation();
  const step = stepMap[pathname] || 1;

  return (
    <BookingProvider>
      <div className="booking-wizard">
        <h1 style={{ textAlign: 'center', fontSize: 'var(--font-size-xl)' }}>Reservar cita</h1>
        <StepIndicator current={step} />
        <Outlet />
      </div>
    </BookingProvider>
  );
}
