import { Component, useEffect, useState, useSyncExternalStore } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * Capa decorativa de partículas, cargada de forma diferida para que nunca
 * bloquee el primer pintado (objetivo 3G: el chunk de tsparticles ~60 kB
 * compite por ancho de banda con el bundle principal).
 *
 * - El import dinámico se dispara en un useEffect (tras el primer pintado)
 *   con setState asíncrono, por lo que no incumple la regla
 *   react-hooks/set-state-in-effect.
 * - Si el chunk falla (red intermitente en 3G) se degrada a nada: las
 *   partículas son decorativas y no deben romper la app.
 * - Solo se muestran donde aportan (Landing y resto de páginas públicas
 *   informativas). En el flujo de reserva (`/reservar/*`, incluida la página
 *   de éxito) y en los paneles (`/admin`, `/trabajador`) distraen de la tarea y
 *   restan legibilidad a formularios y tablas: allí no se monta la capa y, si
 *   el usuario entra directamente por esas rutas, el chunk ni se descarga.
 * - Con `prefers-reduced-motion: reduce` tampoco se descarga ni se monta (antes
 *   se bajaba el chunk y ParticlesBackground decidía no pintar).
 * - El wrapper mantiene el contrato visual: la capa la pinta el propio
 *   ParticlesBackground (div .particles-bg con z-index -1 y
 *   pointer-events: none en App.css).
 */
class ParticlesBoundary extends Component {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error) {
    console.error('ParticlesBackground failed to load:', error?.message || String(error));
  }

  render() {
    return this.state.failed ? null : this.props.children;
  }
}

/** Prefijos de ruta sin partículas: flujo de reserva y paneles. */
const SIN_PARTICULAS = ['/reservar', '/admin', '/trabajador'];

function particlesAllowed(pathname) {
  return !SIN_PARTICULAS.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

const REDUCED_MOTION = '(prefers-reduced-motion: reduce)';

function subscribeMotion(callback) {
  const mq = window.matchMedia(REDUCED_MOTION);
  mq.addEventListener('change', callback);
  return () => mq.removeEventListener('change', callback);
}

const getMotion = () => window.matchMedia(REDUCED_MOTION).matches;
/** En servidor se asume movimiento reducido (variante estática). */
const getServerMotion = () => true;

export default function ParticlesLayer() {
  const { pathname } = useLocation();
  const reduced = useSyncExternalStore(subscribeMotion, getMotion, getServerMotion);
  const enabled = !reduced && particlesAllowed(pathname);
  const [ParticlesBackground, setParticlesBackground] = useState(null);

  useEffect(() => {
    // Import solo cuando hacen falta; una vez cargado, el módulo queda en caché.
    if (!enabled || ParticlesBackground) return undefined;
    let alive = true;
    import('./ParticlesBackground')
      .then((mod) => {
        if (alive) setParticlesBackground(() => mod.default);
      })
      .catch((error) => {
        console.error('ParticlesBackground chunk failed:', error?.message || String(error));
      });
    return () => {
      alive = false;
    };
  }, [enabled, ParticlesBackground]);

  if (!enabled || !ParticlesBackground) return null;

  const Loaded = ParticlesBackground;
  return (
    <ParticlesBoundary>
      <Loaded />
    </ParticlesBoundary>
  );
}
