import { useState, useEffect } from 'react';
import { useNavigate, Navigate } from 'react-router-dom';
import { publicApi, mediaUrl } from '../../services/api';
import { useBooking } from '../../hooks/useBooking';
import { SkeletonGrid } from '../../components/ui/Skeleton';
import { ErrorState } from '../../components/ui/EmptyState';
import Avatar from '../../components/ui/Avatar';
import Button from '../../components/ui/Button';
import { LuUsers } from 'react-icons/lu';

/**
 * Booking step 2: worker selection with "Cualquiera" option.
 */
export default function WorkerSelector() {
  const { draft, updateDraft } = useBooking();
  const navigate = useNavigate();
  const [trabajadores, setTrabajadores] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selected, setSelected] = useState(draft.trabajador);

  const load = async () => {
    try {
      const data = await publicApi.getTrabajadores();
      setTrabajadores(data.filter((t) => t.activo));
      setError(null);
      setLoading(false);
    } catch {
      setError('No se pudieron cargar los trabajadores.');
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    load();
  }, []);

  // Redirect if no service selected
  if (!draft.servicio) {
    return <Navigate to="/reservar/servicio" replace />;
  }

  const handleContinue = () => {
    if (selected === undefined) return;
    updateDraft({ trabajador: selected, slot: null, fecha: null });
    navigate('/reservar/fecha-hora');
  };

  return (
    <div>
      {error && <ErrorState message={error} onRetry={load} />}
      {loading ? (
        <SkeletonGrid count={3} />
      ) : (
        <div className="booking-grid">
          <button
            type="button"
            className={`booking-option ${selected === null ? 'selected' : ''}`}
            onClick={() => setSelected(null)}
            aria-pressed={selected === null}
          >
            <div style={{ fontSize: 'var(--font-size-2xl)' }}>
              <LuUsers aria-hidden="true" />
            </div>
            <h4>Cualquier disponible</h4>
            <p>Elige el mejor horario para ti</p>
          </button>

          {trabajadores.map((trab) => (
            <button
              key={trab.id}
              type="button"
              className={`booking-option ${selected?.id === trab.id ? 'selected' : ''}`}
              onClick={() => setSelected(trab)}
              aria-pressed={selected?.id === trab.id}
            >
              <Avatar src={mediaUrl(trab.foto)} name={trab.nombre} size="xl" />
              <h4>{trab.nombre}</h4>
              {trab.descripcion?.trim() ? (
                <p>{trab.descripcion}</p>
              ) : (
                <p>{trab.especialidad || 'Barbero profesional'}</p>
              )}
            </button>
          ))}
        </div>
      )}

      <div className="booking-actions">
        <Button variant="ghost" onClick={() => navigate('/reservar/servicio')}>
          Atrás
        </Button>
        <Button variant="primary" onClick={handleContinue}>
          Continuar
        </Button>
      </div>
    </div>
  );
}
