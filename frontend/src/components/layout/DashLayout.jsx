import { useEffect, useState, Suspense } from 'react';
import { Outlet, NavLink, Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { useAvisosPendientes } from '../../hooks/useAvisosPendientes';
import { mediaUrl } from '../../services/api';
import Avatar from '../ui/Avatar';
import Logo from '../ui/Logo';
import RouteFallback from '../RouteFallback';
import { LuMenu } from 'react-icons/lu';

/**
 * Shared dashboard layout with sidebar for worker and admin.
 * - `adminOnly`: el enlace solo se muestra a ADMIN (p. ej. "Administración"
 *   en el menú del trabajador; cosmético, la API ya responde 403, D10).
 * - `badge: 'avisos'`: muestra el nº de clientes pendientes de avisar (D7).
 * @param {{brand?: string, links: Array<{to: string, label: string, adminOnly?: boolean, badge?: 'avisos'}>, roleLabel?: string}} props
 */
export default function DashLayout({ brand, links, roleLabel }) {
  const { user, logout, isAdmin } = useAuth();
  const visibleLinks = links.filter((link) => !link.adminOnly || isAdmin);
  const avisosPendientes = useAvisosPendientes(
    isAdmin && visibleLinks.some((link) => link.badge === 'avisos')
  );
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  // Menú móvil abierto: bloquea el scroll del fondo y permite cerrar con Escape
  useEffect(() => {
    if (!open) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open]);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="dash-layout">
      <aside id="dash-sidebar" className={`dash-sidebar ${open ? 'open' : ''}`}>
        <div className="sidebar-brand">
          {/* El sidebar es oscuro en modo oscuro: fondo blanco para que el logo se vea */}
          <span className="sidebar-logo">
            <Logo height={40} />
          </span>
          {brand}
        </div>
        <nav className="sidebar-nav" aria-label="Navegación del panel">
          {visibleLinks.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.to === '/' || link.to.split('/').length === 2 && !link.to.includes('/:')}
              className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
              onClick={() => setOpen(false)}
            >
              {link.label}
              {link.badge === 'avisos' && avisosPendientes > 0 && (
                <span
                  className="sidebar-badge"
                  aria-label={`${avisosPendientes} clientes por avisar`}
                >
                  {avisosPendientes}
                </span>
              )}
            </NavLink>
          ))}
          <button type="button" className="sidebar-link logout" onClick={handleLogout}>
            Cerrar sesión
          </button>
        </nav>
      </aside>

      {/* Fondo semitransparente del drawer móvil: al tocarlo se cierra el menú */}
      <div
        className={`dash-backdrop ${open ? 'open' : ''}`}
        onClick={() => setOpen(false)}
        aria-hidden="true"
      />

      <div className="dash-main">
        <header className="dash-topbar">
          <button
            className="sidebar-toggle"
            onClick={() => setOpen(!open)}
            aria-label={open ? 'Cerrar menú' : 'Abrir menú'}
            aria-expanded={open}
            aria-controls="dash-sidebar"
          >
            <LuMenu aria-hidden="true" />
          </button>
          <div className="dash-user">
            <span className="dash-role">{roleLabel}</span>
            <Avatar src={mediaUrl(user?.foto)} name={user?.nombre || ''} size="sm" />
            <div className="dash-user-info">
              <div className="dash-user-name">{user?.nombre}</div>
              <div className="dash-user-email">{user?.email}</div>
            </div>
          </div>
        </header>
        <main className="dash-content">
          {/* Páginas del dashboard (worker/admin) lazy: el sidebar permanece
              montado y solo el contenido muestra el spinner. */}
          <Suspense fallback={<RouteFallback />}>
            <Outlet />
          </Suspense>
        </main>
        <footer className="dash-footer">
          <Link to="/legal/privacidad">Privacidad</Link>
          <Link to="/legal/terminos">Términos</Link>
          <Link to="/legal/cookies">Cookies</Link>
        </footer>
      </div>
    </div>
  );
}
