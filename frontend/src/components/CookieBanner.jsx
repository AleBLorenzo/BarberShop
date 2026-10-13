import { useState } from 'react';
import { Link } from 'react-router-dom';

const STORAGE_KEY = 'cookiesAccepted';

/**
 * Aviso de cookies (una sola vez): no usamos cookies ni scripts de terceros,
 * solo almacenamiento local propio (ver /legal/cookies).
 */
export default function CookieBanner() {
  const [aceptado, setAceptado] = useState(() => localStorage.getItem(STORAGE_KEY) === 'true');

  if (aceptado) return null;

  const aceptar = () => {
    localStorage.setItem(STORAGE_KEY, 'true');
    setAceptado(true);
  };

  return (
    <div
      role="region"
      aria-label="Aviso de cookies"
      style={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        zIndex: 200,
        background: 'var(--card-bg)',
        borderTop: '1px solid var(--border)',
        boxShadow: 'var(--shadow-md)',
        padding: '12px 24px',
        display: 'flex',
        flexWrap: 'wrap',
        gap: 12,
        alignItems: 'center',
        justifyContent: 'space-between',
      }}
    >
      <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', color: 'var(--muted)' }}>
        No usamos cookies ni scripts de terceros: solo almacenamiento local de tu
        navegador para la sesión y tus preferencias.{' '}
        <Link to="/legal/cookies" style={{ color: 'var(--accent)' }}>Más información</Link>
      </p>
      <button className="btn btn-sm" onClick={aceptar} type="button">
        Aceptar
      </button>
    </div>
  );
}
