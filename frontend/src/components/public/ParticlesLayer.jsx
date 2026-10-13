import { Component, useEffect, useState } from 'react';

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

export default function ParticlesLayer() {
  const [ParticlesBackground, setParticlesBackground] = useState(null);

  useEffect(() => {
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
  }, []);

  if (!ParticlesBackground) return null;

  const Loaded = ParticlesBackground;
  return (
    <ParticlesBoundary>
      <Loaded />
    </ParticlesBoundary>
  );
}
