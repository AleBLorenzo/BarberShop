import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import Spinner from '../components/ui/Spinner';

/**
 * Wrapper for protected routes with role checking.
 * @param {{children: React.ReactNode, role?: 'WORKER'|'ADMIN'}} props
 */
export default function ProtectedRoute({ children, role }) {
  const { isAuthenticated, isAdmin, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="loading-page">
        <Spinner size="lg" />
        <p>Verificando sesión...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    const redirect = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }

  if (role === 'ADMIN' && !isAdmin) {
    return <Navigate to="/trabajador" replace />;
  }

  return children;
}
