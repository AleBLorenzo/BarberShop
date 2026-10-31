import { useState, useEffect } from 'react';
import { useNavigate, Navigate } from 'react-router-dom';
import { publicApi, mediaUrl } from '../../services/api';
import { useBooking } from '../../hooks/useBooking';
import { SkeletonGrid } from '../../components/ui/Skeleton';
import { ErrorState } from '../../components/ui/EmptyState';
import Avatar from '../../components/ui/Avatar';
import Button from '../../components/ui/Button';
import { LuUsers } from 'react-icons/lu';
import { useSeo } from '../../components/Seo';

/**
 * Booking step 2: worker selection with "Cualquiera" option.
 */
export default function WorkerSelector() {
  // Paso 2 del flujo de reserva (estado en memoria, no indexable; D2).
  useSeo({
    title: 'Elegir barbero — TheTankBarber',
    description: 'Selecciona a tu barbero preferido o deja que te asignemos el primero disponible en TheTankBarber.',
  });

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
            className={`booking-option booking-option--stack ${selected === null ? 'selected' : ''}`}
            onClick={() => setSelected(null)}
            aria-pressed={selected === null}
          >
            <span className="booking-option-icon" aria-hidden="true">
              <LuUsers />
            </span>
            <span className="booking-option-body">
              <span className="booking-option-title">Cualquier disponible</span>
              <span className="booking-option-desc">Elige el mejor horario para ti</span>
            </span>
          </button>

          {trabajadores.map((trab) => (
            <button
              key={trab.id}
              type="button"
              className={`booking-option booking-option--stack ${selected?.id === trab.id ? 'selected' : ''}`}
              onClick={() => setSelected(trab)}
              aria-pressed={selected?.id === trab.id}
            >
              <Avatar src={mediaUrl(trab.foto)} name={trab.nombre} size="xl" />
              <span className="booking-option-body">
                <span className="booking-option-title">{trab.nombre}</span>
                <span className="booking-option-desc">
                  {trab.descripcion?.trim() ? trab.descripcion : trab.especialidad || 'Barbero profesional'}
                </span>
              </span>
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
