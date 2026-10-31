import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { addDays, format, parseISO, startOfMonth } from 'date-fns';
import { publicApi } from '../../services/api';
import { dedupeSlots } from '../../utils/slots';
import { useDiasCerrados } from '../../hooks/useDiasCerrados';
import CalendarPicker from '../../components/ui/CalendarPicker';
import TimeSlotPicker from '../../components/ui/TimeSlotPicker';
import Button from '../../components/ui/Button';
import Spinner from '../../components/ui/Spinner';
import { ErrorState } from '../../components/ui/EmptyState';
import { useSeo } from '../../components/Seo';

/**
 * Reschedule page reusing date/time picking for an existing token.
 * Verificado (spec booking-without-email-degradation): reprogramar usa el token
 * y no depende ni muestra el email del cliente.
 */
export default function Reschedule() {
  // Flujo con token de cancelación/reprogramación: noindex (filtra tokens; spec
  // `seo-meta` + regla `Disallow: /reprogramar/` de robots.txt).
  useSeo({
    title: 'Reprogramar cita — TheTankBarber',
    description: 'Cambia la fecha y la hora de tu cita en TheTankBarber.',
    noindex: true,
  });

  const { token } = useParams();
  const navigate = useNavigate();
  const [fecha, setFecha] = useState(format(new Date(), 'yyyy-MM-dd'));
  const [slots, setSlots] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [selectedSlot, setSelectedSlot] = useState(null);
  const [servicioId, setServicioId] = useState(null);

  // Días cerrados del mes visible (D12); sin trabajador = ninguno trabaja
  const minDate = format(new Date(), 'yyyy-MM-dd');
  const maxDate = format(addDays(new Date(), 60), 'yyyy-MM-dd');
  const [calMonth, setCalMonth] = useState(() => startOfMonth(parseISO(fecha)));
  const cerrados = useDiasCerrados(calMonth, { min: minDate, max: maxDate });
  const fechaCerrada = cerrados.has(fecha);

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
      // 400 de día cerrado (cierre creado mientras elegía): el mensaje del
      // backend lo explica ("La barbería está cerrada…"); se libera el hueco
      if (err?.response?.status === 400 || err?.response?.status === 409) setSelectedSlot(null);
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
        min={minDate}
        max={maxDate}
        closedDates={cerrados}
        onMonthChange={setCalMonth}
        required
      />

      <div className="form-group">
        <label className="form-label">Horarios disponibles</label>
        {fechaCerrada ? (
          <p className="closed-day-note" role="status">
            Cerrado: ese día no se puede reservar. Elige otra fecha.
          </p>
        ) : loading && <Spinner size="sm" />}
        {error && <ErrorState message={error} onRetry={handleRetry} />}
        {!fechaCerrada && !loading && (
          <TimeSlotPicker slots={slots} selected={selectedSlot?.horaInicio} onSelect={setSelectedSlot} />
        )}
      </div>

      <div className="booking-actions">
        <Button variant="ghost" onClick={() => navigate('/mis-citas')}>
          Cancelar
        </Button>
        <Button variant="primary" disabled={!selectedSlot || fechaCerrada} loading={submitting} onClick={handleSubmit}>
          Confirmar reprogramación
        </Button>
      </div>
    </div>
  );
}
