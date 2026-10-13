import { useMemo, useSyncExternalStore } from 'react';
import { Particles, ParticlesProvider } from '@tsparticles/react';
import { loadSlim } from '@tsparticles/slim';

/**
 * Fondo de partículas interactivo (tsparticles) detrás de todo el contenido.
 *
 * - Capa fija con `z-index: -1` y `pointer-events: none` (ver App.css, sección
 *   PARTICLES): los eventos de ratón siguen llegando al contenido y la
 *   interactividad se detecta a nivel de `window` (`detectsOn: "window"`).
 * - El engine se inicializa UNA sola vez: `initParticles` es una referencia a
 *   nivel de módulo (requisito de ParticlesProvider) y el propio provider
 *   guarda un flag de módulo, así que StrictMode no duplica la inicialización.
 * - `prefers-reduced-motion: reduce` => no se monta el motor (cero movimiento).
 * - SSR-safe: `matchMedia` se lee vía getSnapshot del cliente y
 *   `getServerSnapshot` asume la variante estática; `getComputedStyle` está
 *   protegido en `readThemeTokens`.
 */

/** Callback estable para ParticlesProvider (no debe cambiar entre renders). */
const initParticles = async (engine) => {
  await loadSlim(engine);
};

/** Fallbacks sin hex literales (guardrails del restyle) por si faltan tokens. */
const FALLBACK_LIGHT = { muted: 'rgb(115, 115, 115)', accent: 'rgb(10, 10, 10)' };
const FALLBACK_DARK = { muted: 'rgb(161, 161, 161)', accent: 'rgb(245, 245, 245)' };

/**
 * Lee los tokens DESIGN.md en tiempo de ejecución (fuente de verdad de la
 * paleta). `isDark` solo decide el fallback en servidor; en cliente el valor
 * computado ya refleja el tema activo.
 */
function readThemeTokens(isDark) {
  if (typeof window === 'undefined') {
    return isDark ? FALLBACK_DARK : FALLBACK_LIGHT;
  }
  const styles = getComputedStyle(document.documentElement);
  const muted = styles.getPropertyValue('--muted').trim();
  const accent = styles.getPropertyValue('--accent').trim();
  return {
    muted: muted || (isDark ? FALLBACK_DARK.muted : FALLBACK_LIGHT.muted),
    accent: accent || (isDark ? FALLBACK_DARK.accent : FALLBACK_LIGHT.accent),
  };
}

function subscribeScheme(callback) {
  const mq = window.matchMedia('(prefers-color-scheme: dark)');
  mq.addEventListener('change', callback);
  return () => mq.removeEventListener('change', callback);
}

const getScheme = () => window.matchMedia('(prefers-color-scheme: dark)').matches;
const getServerScheme = () => false;

function subscribeMotion(callback) {
  const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
  mq.addEventListener('change', callback);
  return () => mq.removeEventListener('change', callback);
}

const getMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;
/** En servidor se asume movimiento reducido: se sirve la variante estática. */
const getServerMotion = () => true;

/**
 * Opciones tsparticles: campo sutil proporcional al área (densidad), deriva
 * lenta con envoltura (wrap) para que el recuento sea estable, enlaces finos
 * semitransparentes, `grab` en hover y `push` con vida limitada en clic.
 */
function buildOptions({ muted, accent }) {
  return {
    fullScreen: { enable: false },
    fpsLimit: 60,
    pauseOnBlur: true,
    detectRetina: true,
    particles: {
      number: {
        value: 55,
        // 55 partículas por 1600x900 px de área CSS: móvil ~13, 720p ~35,
        // 1080p ~78. El techo (mode wait) evita que el clic crezca sin límite.
        density: { enable: true, width: 1600, height: 900 },
        limit: { value: 88, mode: 'wait' },
      },
      color: { value: muted },
      opacity: { value: { min: 0.25, max: 0.5 } },
      size: { value: { min: 1, max: 2.5 } },
      links: {
        enable: true,
        distance: 110,
        opacity: 0.2,
        width: 1,
      },
      move: {
        enable: true,
        speed: { min: 0.25, max: 0.55 },
        direction: 'none',
        // "wrap" en lugar de "out": al salir por un borde entra por el
        // contrario y el recuento base no decae con el tiempo.
        outModes: { default: 'wrap' },
        drift: { min: -0.3, max: 0.3 },
        straight: false,
      },
    },
    interactivity: {
      detectsOn: 'window',
      events: {
        onHover: { enable: true, mode: 'grab' },
        onClick: { enable: true, mode: 'push' },
      },
      modes: {
        grab: {
          distance: 130,
          links: { opacity: 0.4, color: accent },
        },
        push: {
          quantity: 8,
          particles: {
            // "burst" no existe en v4/slim: el push + vida limitada añade
            // partículas en el punto del clic que mueren pasados 3.5-6.5 s.
            life: {
              count: 1,
              duration: { value: { min: 3500, max: 6500 }, sync: true },
            },
          },
        },
      },
    },
  };
}

export default function ParticlesBackground() {
  const reduced = useSyncExternalStore(subscribeMotion, getMotion, getServerMotion);
  const dark = useSyncExternalStore(subscribeScheme, getScheme, getServerScheme);
  const tokens = useMemo(() => readThemeTokens(dark), [dark]);
  const options = useMemo(() => buildOptions(tokens), [tokens]);

  return (
    <div className="particles-bg" aria-hidden="true">
      {!reduced && (
        <ParticlesProvider init={initParticles}>
          <Particles className="particles-bg__layer" options={options} />
        </ParticlesProvider>
      )}
    </div>
  );
}
