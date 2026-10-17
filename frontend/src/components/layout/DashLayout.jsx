import { useEffect, useState, Suspense } from 'react';
import { Outlet, NavLink, Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { mediaUrl } from '../../services/api';
import Avatar from '../ui/Avatar';
import Logo from '../ui/Logo';
import RouteFallback from '../RouteFallback';
import { LuMenu } from 'react-icons/lu';

/**
 * Shared dashboard layout with sidebar for worker and admin.
 * @param {{brand?: string, links: Array<{to: string, label: string}>, roleLabel?: string}} props
 */
export default function DashLayout({ brand, links, roleLabel }) {
  const { user, logout } = useAuth();
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
          <span style={{ background: '#fff', padding: 6, borderRadius: 'var(--radius-sm)' }}>
            <Logo height={40} />
          </span>
          {brand}
        </div>
        <nav className="sidebar-nav" aria-label="Navegación del panel">
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.to === '/' || link.to.split('/').length === 2 && !link.to.includes('/:')}
              className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
              onClick={() => setOpen(false)}
            >
              {link.label}
            </NavLink>
          ))}
          <button className="sidebar-link logout" onClick={handleLogout} style={{
            background: 'none', border: 'none', textAlign: 'left', width: '100%', cursor: 'pointer',
            fontFamily: 'inherit', fontSize: 'inherit'
          }}>
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
          <div className="dash-user" style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span className="dash-role" style={{ fontSize: 'var(--font-size-sm)', color: 'var(--muted)' }}>{roleLabel}</span>
            <Avatar src={mediaUrl(user?.foto)} name={user?.nombre || ''} size="sm" />
            <div className="dash-user-info">
              <div style={{ fontSize: 'var(--font-size-sm)', fontWeight: 600 }}>{user?.nombre}</div>
              <div className="dash-user-email" style={{ fontSize: 'var(--font-size-xs)', color: 'var(--muted)' }}>{user?.email}</div>
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
        <footer
          style={{
            borderTop: '1px solid var(--border)',
            padding: '12px 24px',
            fontSize: 'var(--font-size-xs)',
            color: 'var(--muted)',
            display: 'flex',
            gap: 16,
            flexWrap: 'wrap',
          }}
        >
          <Link to="/legal/privacidad">Privacidad</Link>
          <Link to="/legal/terminos">Términos</Link>
          <Link to="/legal/cookies">Cookies</Link>
        </footer>
      </div>
    </div>
  );
}
