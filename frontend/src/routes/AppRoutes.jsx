import { lazy } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import PublicLayout from '../components/layout/PublicLayout';
import DashLayout from '../components/layout/DashLayout';
import ErrorBoundary from '../components/ErrorBoundary';
import ProtectedRoute from './ProtectedRoute';
import { LuScissors } from 'react-icons/lu';

// Public pages — Landing y Login quedan EAGER (primer pintado: raíz + acceso).
// El resto de páginas públicas (flujo de reserva, citas, legales) se cargan
// bajo demanda para no inflar el bundle inicial en redes lentas.
import Landing from '../pages/public/Landing';
import Login from '../pages/public/Login';

const ServiceSelector = lazy(() => import('../pages/public/ServiceSelector'));
const WorkerSelector = lazy(() => import('../pages/public/WorkerSelector'));
const DateTimePicker = lazy(() => import('../pages/public/DateTimePicker'));
const BookingConfirm = lazy(() => import('../pages/public/BookingConfirm'));
const BookingSuccess = lazy(() => import('../pages/public/BookingSuccess'));
const MyAppointments = lazy(() => import('../pages/public/MyAppointments'));
const Reschedule = lazy(() => import('../pages/public/Reschedule'));

// Legal pages
const Privacidad = lazy(() => import('../pages/legal/Privacidad'));
const Terminos = lazy(() => import('../pages/legal/Terminos'));
const Cookies = lazy(() => import('../pages/legal/Cookies'));

// Worker pages
const WorkerDashboard = lazy(() => import('../pages/worker/Dashboard'));
const WorkerProfile = lazy(() => import('../pages/worker/Profile'));

// Admin pages
const AdminDashboard = lazy(() => import('../pages/admin/Dashboard'));
const AdminWorkers = lazy(() => import('../pages/admin/Workers'));
const AdminServices = lazy(() => import('../pages/admin/Services'));
const AdminReservations = lazy(() => import('../pages/admin/Reservations'));
const AdminResenas = lazy(() => import('../pages/admin/Resenas'));
const AdminResultados = lazy(() => import('../pages/admin/Resultados'));

const workerLinks = [
  { to: '/', label: 'Inicio' },
  { to: '/trabajador', label: 'Dashboard' },
  { to: '/trabajador/perfil', label: 'Perfil' },
  { to: '/admin', label: 'Administración' },
];

const adminLinks = [
  { to: '/', label: 'Inicio' },
  { to: '/admin', label: 'Dashboard' },
  { to: '/admin/trabajadores', label: 'Trabajadores' },
  { to: '/admin/servicios', label: 'Servicios' },
  { to: '/admin/resenas', label: 'Reseñas' },
  { to: '/admin/resultados', label: 'Resultados' },
  { to: '/admin/reservas', label: 'Reservas' },
  { to: '/trabajador', label: 'Mi panel' },
];

export default function AppRoutes() {
  return (
    <ErrorBoundary>
      <Routes>
        {/* Public */}
        <Route element={<PublicLayout />}>
          <Route path="/" element={<Landing />} />
          <Route path="/login" element={<Login />} />
          <Route path="/reservar/servicio" element={<ServiceSelector />} />
          <Route path="/reservar/trabajador" element={<WorkerSelector />} />
          <Route path="/reservar/fecha-hora" element={<DateTimePicker />} />
          <Route path="/reservar/confirmar" element={<BookingConfirm />} />
          <Route path="/reservar/exito" element={<BookingSuccess />} />
          <Route path="/mis-citas" element={<MyAppointments />} />
          <Route path="/reprogramar/:token" element={<Reschedule />} />
          <Route path="/legal/privacidad" element={<Privacidad />} />
          <Route path="/legal/terminos" element={<Terminos />} />
          <Route path="/legal/cookies" element={<Cookies />} />
        </Route>

        {/* Worker */}
        <Route
          path="/trabajador"
          element={
            <ProtectedRoute role="WORKER">
              <DashLayout links={workerLinks} roleLabel="Trabajador" />
            </ProtectedRoute>
          }
        >
          <Route index element={<WorkerDashboard />} />
          <Route path="perfil" element={<WorkerProfile />} />
        </Route>

        {/* Admin */}
        <Route
          path="/admin"
          element={
            <ProtectedRoute role="ADMIN">
              <DashLayout
                brand={
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 'var(--space-sm)' }}>
                    <LuScissors aria-hidden="true" />
                    Admin
                  </span>
                }
                links={adminLinks}
                roleLabel="Administrador"
              />
            </ProtectedRoute>
          }
        >
          <Route index element={<AdminDashboard />} />
          <Route path="trabajadores" element={<AdminWorkers />} />
          <Route path="servicios" element={<AdminServices />} />
          <Route path="resenas" element={<AdminResenas />} />
          <Route path="resultados" element={<AdminResultados />} />
          <Route path="reservas" element={<AdminReservations />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </ErrorBoundary>
  );
}
