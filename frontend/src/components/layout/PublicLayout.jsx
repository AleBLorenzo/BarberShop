import { useState, useEffect, useRef, Suspense } from 'react';
import { Outlet, Link, NavLink } from 'react-router-dom';
import CookieBanner from '../CookieBanner';
import Logo from '../ui/Logo';
import RouteFallback from '../RouteFallback';
import { NOMBRE, EMAIL_CONTACTO, DIRECCION, GOOGLE_MAPS_URL, TELEFONO } from '../../config/business';

/**
 * Barra de progreso de lectura (3px, fija arriba).
 * El scaleX avanza con interpolación suave (efecto muelle del sitio de
 * referencia); con prefers-reduced-motion salta al destino sin interpolar.
 */
function ScrollProgress() {
  const barRef = useRef(null);

  useEffect(() => {
    let raf = 0;
    let current = 0;
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    const tick = () => {
      const max = document.documentElement.scrollHeight - window.innerHeight;
      const target = max > 0 ? Math.min(window.scrollY / max, 1) : 0;
      current = reduce ? target : current + (target - current) * 0.12;
      if (barRef.current) barRef.current.style.transform = `scaleX(${current.toFixed(4)})`;
      if (Math.abs(target - current) > 0.0005) {
        raf = requestAnimationFrame(tick);
      } else {
        current = target;
      }
    };

    const onScroll = () => {
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(tick);
    };

    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onScroll);
    };
  }, []);

  return <div className="scroll-progress" ref={barRef} aria-hidden="true" />;
}

/**
 * Layout for public pages (landing, booking, login).
 */
export default function PublicLayout() {
  const [scrolled, setScrolled] = useState(false);

  // Header sticky: glass (blur + sombra) al pasar los 24px de scroll
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <div className="public-layout">
      <ScrollProgress />
      {/* Los estilos fijos viven en .public-header/.public-nav (App.css) para
          poder ajustarlos con media query en móvil; aquí solo el estado scrolled */}
      <header
        className="public-header"
        style={
          scrolled
            ? {
                backdropFilter: 'blur(12px)',
                WebkitBackdropFilter: 'blur(12px)',
                background: 'color-mix(in srgb, var(--card-bg) 85%, transparent)',
                boxShadow: 'var(--shadow)',
              }
            : undefined
        }
      >
        <Link to="/" style={{ fontWeight: 600, fontSize: 'var(--font-size-lg)', color: 'var(--accent)' }}>
          <Logo />
        </Link>
        <nav className="public-nav" aria-label="Navegación principal">
          <NavLink to="/" style={({ isActive }) => ({ color: isActive ? 'var(--accent)' : 'var(--text)' })}>
            Inicio
          </NavLink>
          <NavLink to="/reservar/servicio" style={({ isActive }) => ({ color: isActive ? 'var(--accent)' : 'var(--text)' })}>
            Reservar
          </NavLink>
          <NavLink to="/mis-citas" style={({ isActive }) => ({ color: isActive ? 'var(--accent)' : 'var(--text)' })}>
            Mis citas
          </NavLink>
          <NavLink
            to="/login"
            className="btn btn-secondary btn-sm"
            style={({ isActive }) => (isActive ? { borderColor: 'var(--accent)', color: 'var(--accent)' } : undefined)}
          >
            Área personal
          </NavLink>
        </nav>
      </header>

      <main className="public-content">
        {/* Páginas públicas lazy: el header/footer permanecen montados y solo
            el contenido muestra el spinner (sin salto de layout). */}
        <Suspense fallback={<RouteFallback />}>
          <Outlet />
        </Suspense>
      </main>

      <footer className="public-footer">
        <p>{DIRECCION}</p>
        <p style={{ display: 'flex', gap: 12, justifyContent: 'center', flexWrap: 'wrap' }}>
          <a href={`mailto:${EMAIL_CONTACTO}`}>{EMAIL_CONTACTO}</a>
          <a href={`tel:${TELEFONO}`}>{TELEFONO}</a>
          <a href={GOOGLE_MAPS_URL} target="_blank" rel="noopener noreferrer">Google Maps</a>
        </p>
        <p>
          © {new Date().getFullYear()} {NOMBRE} — Tu barbería de confianza
        </p>
        <p style={{ display: 'flex', gap: 12, justifyContent: 'center', flexWrap: 'wrap', fontSize: 'var(--font-size-xs)' }}>
          <Link to="/legal/privacidad">Política de privacidad</Link>
          <Link to="/legal/terminos">Términos y condiciones</Link>
          <Link to="/legal/cookies">Política de cookies</Link>
        </p>
      </footer>

      <CookieBanner />
    </div>
  );
}
