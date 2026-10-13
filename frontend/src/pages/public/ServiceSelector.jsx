import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { publicApi, mediaUrl } from '../../services/api';
import { useBooking } from '../../hooks/useBooking';
import PriceWithOffer from '../../components/ui/PriceWithOffer';
import { SkeletonGrid } from '../../components/ui/Skeleton';
import { ErrorState } from '../../components/ui/EmptyState';
import Button from '../../components/ui/Button';

/**
 * Booking step 1: service selection.
 */
export default function ServiceSelector() {
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
              className={`booking-option ${selected?.id === serv.id ? 'selected' : ''}`}
              onClick={() => setSelected(serv)}
              aria-pressed={selected?.id === serv.id}
            >
              {serv.foto && (
                <img
                  src={mediaUrl(serv.foto)}
                  alt=""
                  loading="lazy"
                  style={{
                    width: 72,
                    height: 72,
                    display: 'block',
                    objectFit: 'cover',
                    borderRadius: 'var(--radius-md)',
                    margin: '0 auto 8px',
                  }}
                  onError={(e) => { e.currentTarget.style.display = 'none'; }}
                />
              )}
              <h4>{serv.nombre}</h4>
              {serv.descripcion && <p>{serv.descripcion}</p>}
              <p>
                <strong>
                  <PriceWithOffer
                    precio={serv.precio}
                    precioOferta={serv.precioOferta}
                    ofertaActiva={serv.ofertaActiva}
                    descuentoPorcentaje={serv.descuentoPorcentaje}
                    ofertaInicio={serv.ofertaInicio}
                    ofertaFin={serv.ofertaFin}
                  />
                </strong>
              </p>
              <p>{serv.duracionMinutos} min</p>
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
