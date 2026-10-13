import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { BookingProvider } from './context/BookingContext';
import AppRoutes from './routes/AppRoutes';
// Deferido a post-paint: tsparticles es decorativo y no debe competir
// por ancho de banda con el bundle principal en redes lentas (3G).
import ParticlesLayer from './components/public/ParticlesLayer';
import './App.css';

export default function App() {
  return (
    <BrowserRouter>
      <ParticlesLayer />
      <AuthProvider>
        <ToastProvider>
          <BookingProvider>
            <AppRoutes />
          </BookingProvider>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
