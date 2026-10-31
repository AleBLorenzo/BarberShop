import { Link } from 'react-router-dom';
import { useSeo } from '../../components/Seo';
import { NOMBRE } from '../../config/business';
import { LuArrowLeft, LuCalendar } from 'react-icons/lu';

/**
 * Página 404 de la SPA (cambio `mobile-first-audit-and-web-essentials`, D1).
 * Sustituye el antiguo `<Navigate to="/" replace/>` del catch-all del router:
 * una ruta desconocida ya **no** redirige en silencio a `/` (que era un
 * soft-404 para el SEO) sino que muestra este aviso con vías de recuperación.
 *
 * Se declara `noindex` (spec `not-found-page` + `seo-meta`). La pieza sin JS
 * equivalente es `frontend/public/404.html`, que es la que sirve nginx con un
 * HTTP 404 real en una carga en frío de una URL desconocida.
 */
export default function NotFound() {
  useSeo({
    title: `Página no encontrada — ${NOMBRE}`,
    description: 'La página que buscas no existe o se ha movido. Vuelve a la portada o reserva tu cita en TheTankBarber.',
    noindex: true,
  });

  return (
    <div className="empty-state not-found">
      <p aria-hidden="true" className="not-found__code">
        404
      </p>
      <h1 className="not-found__title">
        Página no encontrada
      </h1>
      <p className="mt-0">
        La página que buscas no existe o se ha movido. Comprueba la dirección o vuelve a empezar.
      </p>
      <div className="not-found__actions">
        <Link to="/" className="btn btn-primary">
          <LuArrowLeft aria-hidden="true" />
          Volver a la portada
        </Link>
        <Link to="/reservar/servicio" className="btn btn-secondary">
          <LuCalendar aria-hidden="true" />
          Reservar cita
        </Link>
      </div>
    </div>
  );
}
