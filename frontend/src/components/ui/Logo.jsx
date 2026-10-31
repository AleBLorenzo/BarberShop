import { useState } from 'react';
import { LuScissors } from 'react-icons/lu';
import { NOMBRE } from '../../config/business';

/** Candidatos de logo en orden: PNG → SVG → texto. */
const FUENTES = ['/logo.png', '/logo.svg'];

/** Relación de aspecto natural del arte (logo.png: 1312×1199 → w/h). */
const ASPECTO = 1312 / 1199;

/**
 * Logotipo de la marca con fallback en cadena: intenta `/logo.png`, luego
 * `/logo.svg` y, si tampoco existe, muestra el nombre en texto.
 * @param {{height?: number, className?: string}} props
 */
export default function Logo({ height = 36, className = '' }) {
  const [etapa, setEtapa] = useState(0);
  const clases = `brand-logo ${className}`.trim();

  if (etapa >= FUENTES.length) {
    return (
      <span className={`${clases} brand-logo--text`}>
        <LuScissors aria-hidden="true" />
        {NOMBRE}
      </span>
    );
  }

  return (
    <span className={clases}>
      <img
        src={FUENTES[etapa]}
        alt={NOMBRE}
        width={Math.round(height * ASPECTO)}
        height={height}
        fetchPriority="high"
        // Alto dinámico (prop `height`): el ancho se deriva del aspecto
        style={{ height, width: 'auto', display: 'block' }}
        onError={() => setEtapa((prev) => prev + 1)}
      />
    </span>
  );
}
