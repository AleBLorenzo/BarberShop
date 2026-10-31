import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { publicApi, mediaUrl } from '../../services/api';
import { useBooking } from '../../hooks/useBooking';
import PriceWithOffer from '../../components/ui/PriceWithOffer';
import { SkeletonGrid } from '../../components/ui/Skeleton';
import { ErrorState } from '../../components/ui/EmptyState';
import Button from '../../components/ui/Button';
import { useSeo } from '../../components/Seo';

/**
 * Booking step 1: service selection.
 */
export default function ServiceSelector() {
  // Paso 1 del flujo de reserva; entrada pública indexable (spec `seo-meta`).
  useSeo({
    title: 'Reservar cita — TheTankBarber',
    description: 'Elige el servicio que quieres: cortes, degradados, barba y estilos personalizados. Reserva online en TheTankBarber.',
  });

  const { draft, updateDraft } = useBooking();
  const navigate = useNavigate();
  const [servicios, setServicios] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selected, setSelected] = useState(draft.servicio);

  const load = async () => {
    try {
      const data = await publicApi.getServicios();
      setServicios(data.filter((s) => s.activo));
      setError(null);
      setLoading(false);
    } catch {
      setError('No se pudieron cargar los servicios.');
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    load();
  }, []);

  const handleContinue = () => {
    if (!selected) return;
    updateDraft({ servicio: selected, slot: null, fecha: null });
    navigate('/reservar/trabajador');
  };

  return (
    <div>
      {error && <ErrorState message={error} onRetry={load} />}
      {loading ? (
        <SkeletonGrid count={4} />
      ) : (
        <div className="booking-grid">
          {servicios.map((serv) => (
            <button
              key={serv.id}
              type="button"
              className={`booking-option booking-option--row ${selected?.id === serv.id ? 'selected' : ''}`}
              onClick={() => setSelected(serv)}
              aria-pressed={selected?.id === serv.id}
            >
              {serv.foto && (
                <img
                  className="booking-option-media"
                  src={mediaUrl(serv.foto)}
                  alt=""
                  loading="lazy"
                  onError={(e) => { e.currentTarget.style.display = 'none'; }}
                />
              )}
              <span className="booking-option-body">
                <span className="booking-option-title">{serv.nombre}</span>
                {serv.descripcion && <span className="booking-option-desc">{serv.descripcion}</span>}
                <span className="booking-option-meta">{serv.duracionMinutos} min</span>
              </span>
              <span className="booking-option-price">
                <PriceWithOffer
                  precio={serv.precio}
                  precioOferta={serv.precioOferta}
                  ofertaActiva={serv.ofertaActiva}
                  descuentoPorcentaje={serv.descuentoPorcentaje}
                  ofertaInicio={serv.ofertaInicio}
                  ofertaFin={serv.ofertaFin}
                />
              </span>
            </button>
          ))}
        </div>
      )}

      <div className="booking-actions">
        <Button variant="ghost" onClick={() => navigate('/')}>
          Atrás
        </Button>
        <Button variant="primary" disabled={!selected} onClick={handleContinue}>
          Continuar
        </Button>
      </div>
    </div>
  );
}
