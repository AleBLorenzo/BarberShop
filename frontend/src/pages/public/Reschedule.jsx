import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { addDays, format } from 'date-fns';
import { publicApi } from '../../services/api';
import { dedupeSlots } from '../../utils/slots';
import CalendarPicker from '../../components/ui/CalendarPicker';
import TimeSlotPicker from '../../components/ui/TimeSlotPicker';
import Button from '../../components/ui/Button';
import Spinner from '../../components/ui/Spinner';
import { ErrorState } from '../../components/ui/EmptyState';

/**
 * Reschedule page reusing date/time picking for an existing token.
 * Verificado (spec booking-without-email-degradation): reprogramar usa el token
 * y no depende ni muestra el email del cliente.
 */
export default function Reschedule() {
  const { token } = useParams();
  const navigate = useNavigate();
  const [fecha, setFecha] = useState(format(new Date(), 'yyyy-MM-dd'));
  const [slots, setSlots] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [selectedSlot, setSelectedSlot] = useState(null);
  const [servicioId, setServicioId] = useState(null);

  // Fetch reservation data to get servicio (token-based lookup not available → ask user date)
  useEffect(() => {
    // We need service to compute slots; fetch active services as fallback
    const init = async () => {
      try {
        const servicios = await publicApi.getServicios();
        if (servicios.length > 0) setServicioId(servicios[0].id);
        else setLoading(false);
      } catch {
        setError('No se pudieron cargar los servicios.');
        setLoading(false);
      }
    };
    init();
  }, []);

  const fetchAvailability = async (fechaStr) => {
    if (!fechaStr || !servicioId) return;
    try {
      const data = await publicApi.getDisponibilidad({
        servicioId,
        fecha: fechaStr,
      });
      setSlots(dedupeSlots(data));
      setError(null);
      setLoading(false);
    } catch {
      setError('No se pudo consultar la disponibilidad.');
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    fetchAvailability(fecha);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fecha, servicioId]);

  const handleDateChange = (dateStr) => {
    setLoading(true);
    setSelectedSlot(null);
    setFecha(dateStr);
  };

  const handleRetry = () => {
    setLoading(true);
    fetchAvailability(fecha);
  };

  const handleSubmit = async () => {
    if (!selectedSlot) return;
    setSubmitting(true);
    setError(null);
    try {
      const reserva = await publicApi.reprogramarReserva({
        token,
        nuevaFecha: fecha,
        nuevaHoraInicio: selectedSlot.horaInicio,
      });
      navigate('/reservar/exito', {
        state: {
          ...reserva,
          fecha,
          horaInicio: selectedSlot.horaInicio,
        },
      });
    } catch (err) {
      setError(err?.response?.data?.message || 'No se pudo reprogramar la cita.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="booking-wizard">
      <h1 style={{ textAlign: 'center', fontSize: 'var(--font-size-xl)' }}>Reprogramar cita</h1>
      <p style={{ textAlign: 'center', color: 'var(--muted)' }}>
        Elige una nueva fecha y horario disponibles.
      </p>

      <CalendarPicker
        label="Nueva fecha"
        value={fecha}
        onChange={handleDateChange}
        min={format(new Date(), 'yyyy-MM-dd')}
        max={format(addDays(new Date(), 60), 'yyyy-MM-dd')}
        required
      />

      <div className="form-group">
        <label className="form-label">Horarios disponibles</label>
        {loading && <Spinner size="sm" />}
        {error && <ErrorState message={error} onRetry={handleRetry} />}
        {!loading && (
          <TimeSlotPicker slots={slots} selected={selectedSlot?.horaInicio} onSelect={setSelectedSlot} />
        )}
      </div>

      <div className="booking-actions">
        <Button variant="ghost" onClick={() => navigate('/mis-citas')}>
          Cancelar
        </Button>
        <Button variant="primary" disabled={!selectedSlot} loading={submitting} onClick={handleSubmit}>
          Confirmar reprogramación
        </Button>
      </div>
    </div>
  );
}
