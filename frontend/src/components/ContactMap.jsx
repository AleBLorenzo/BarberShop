import { useState } from 'react';
import { LuMapPin } from 'react-icons/lu';
import { GOOGLE_MAPS_EMBED_URL } from '../config/business';

/**
 * Mapa de Google embebido en «Visítanos» con **fachada «clic para cargar»**.
 *
 * D-CLICK: el `<iframe>` NO existe en el DOM hasta que el visitante pulsa
 * «Ver mapa»; antes de esa acción no hay ninguna petición a Google ni, por
 * tanto, cookies de Google. Es la forma de añadir el mapa sin contradecir lo
 * que promete la política publicada (capacidades `contact-map-embed` y
 * `cookie-policy`).
 *
 * D4: el marcador de posición es 100 % local (SVG en línea + CSS); no usa
 * *static maps* de Google, ni teselas de OpenStreetMap, ni ninguna `<img>`
 * externa que pudiera provocar una petición antes del clic.
 * D5: el estado «cargado» vive solo en memoria (`useState`); no se escribe en
 * `localStorage`, `sessionStorage` ni `document.cookie`.
 */
export default function ContactMap() {
  const [cargado, setCargado] = useState(false);

  // Degradación grácil: sin URL de embed no se pinta ni el control ni el mapa
  // (nunca un `src=""` ni un botón muerto). El resto de «Visítanos» sigue igual.
  if (!GOOGLE_MAPS_EMBED_URL || !GOOGLE_MAPS_EMBED_URL.trim()) {
    return null;
  }

  return (
    <div className="contact-map">
      {cargado ? (
        <iframe
          className="contact-map__frame"
          src={GOOGLE_MAPS_EMBED_URL}
          title="Mapa de ubicación de TheTankBarber"
          loading="lazy"
          referrerPolicy="no-referrer-when-downgrade"
          allowFullScreen
        />
      ) : (
        <div className="contact-map__placeholder">
          {/* Patrón decorativo *mapa-ish* 100 % local (SVG en línea). */}
          <svg
            className="contact-map__pattern"
            viewBox="0 0 320 180"
            preserveAspectRatio="xMidYMid slice"
            aria-hidden="true"
            focusable="false"
          >
            <g className="contact-map__roads">
              <path d="M-10 42 H330" />
              <path d="M-10 124 H330" />
              <path d="M64 -10 V190" />
              <path d="M214 -10 V190" />
              <path d="M-20 176 L340 14" />
            </g>
            <g className="contact-map__blocks">
              <rect x="78" y="56" width="52" height="26" rx="4" />
              <rect x="140" y="56" width="60" height="26" rx="4" />
              <rect x="78" y="92" width="70" height="22" rx="4" />
              <rect x="160" y="92" width="40" height="22" rx="4" />
              <rect x="228" y="56" width="66" height="58" rx="4" />
              <rect x="8" y="56" width="44" height="58" rx="4" />
            </g>
          </svg>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => setCargado(true)}
          >
            <LuMapPin aria-hidden="true" />
            Ver mapa
          </button>
        </div>
      )}
    </div>
  );
}
