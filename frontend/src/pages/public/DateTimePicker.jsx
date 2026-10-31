import { useState, useEffect } from 'react';
import { useNavigate, useLocation, Navigate } from 'react-router-dom';
import { addDays, format, parseISO, startOfMonth } from 'date-fns';
import { es } from 'date-fns/locale/es';
import { publicApi } from '../../services/api';
import { dedupeSlots } from '../../utils/slots';
import { useBooking } from '../../hooks/useBooking';
import { useDiasCerrados } from '../../hooks/useDiasCerrados';
import CalendarPicker from '../../components/ui/CalendarPicker';
import TimeSlotPicker from '../../components/ui/TimeSlotPicker';
import { ErrorState } from '../../components/ui/EmptyState';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';
import { useSeo } from '../../components/Seo';

const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);

/**
 * Booking step 3: date + time slot selection.
 */
export default function DateTimePicker() {
  // Paso 3 del flujo de reserva (D2).
  useSeo({
    title: 'Elegir fecha y hora — TheTankBarber',
    description: 'Selecciona el día y la hora de tu cita en TheTankBarber.',
  });

  const { draft, updateDraft } = useBooking();
  const navigate = useNavigate();
  const location = useLocation();
  const navError = location.state?.error;
  const [fecha, setFecha] = useState(draft.fecha || format(new Date(), 'yyyy-MM-dd'));
  const [slots, setSlots] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedSlot, setSelectedSlot] = useState(draft.slot);

  // Días cerrados del mes visible (horario, festivos, días libres; D12)
  const minDate = format(new Date(), 'yyyy-MM-dd');
  const maxDate = format(addDays(new Date(), 60), 'yyyy-MM-dd');
  const [calMonth, setCalMonth] = useState(() => startOfMonth(parseISO(fecha)));
  const cerrados = useDiasCerrados(calMonth, {
    min: minDate,
    max: maxDate,
    trabajadorId: draft.trabajador?.id,
  });
  const fechaCerrada = cerrados.has(fecha);

  const fetchAvailability = async (fechaStr) => {
    if (!fechaStr) return;
    try {
      const data = await publicApi.getDisponibilidad({
        servicioId: draft.servicio.id,
        fecha: fechaStr,
        ...(draft.trabajador ? { trabajadorId: draft.trabajador.id } : {}),
      });
      // Without a chosen worker the API returns slots of every worker: dedupe
      setSlots(draft.trabajador ? data : dedupeSlots(data));
      setError(null);
      setLoading(false);
    } catch {
      setError('No se pudo consultar la disponibilidad. Reintenta.');
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    fetchAvailability(fecha);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fecha]);

  if (!draft.servicio) {
    return <Navigate to="/reservar/servicio" replace />;
  }

  const handleDateChange = (dateStr) => {
    // Re-pulsar el día ya elegido no cambia `fecha`: el effect no se relanza y
    // el spinner quedaba colgado («Consultando disponibilidad…» para siempre).
    if (dateStr === fecha) return;
    setLoading(true);
    setSelectedSlot(null);
    setFecha(dateStr);
  };

  const handleRetry = () => {
    setLoading(true);
    fetchAvailability(fecha);
  };

  const handleSelectSlot = (slot) => {
    setSelectedSlot(slot);
  };

  const handleContinue = () => {
    if (!selectedSlot) return;
    updateDraft({ fecha, slot: selectedSlot });
    navigate('/reservar/confirmar');
  };

  return (
    <div>
      {navError && (
        <div className="error-state mb-md" role="alert">
          <span>{navError}</span>
        </div>
      )}

      <div className="booking-summary">
        <div className="summary-row">
          <span className="label">Servicio</span>
          <span className="value">{draft.servicio.nombre}</span>
        </div>
        <div className="summary-row">
          <span className="label">Trabajador</span>
          <span className="value">{draft.trabajador?.nombre || 'Cualquiera'}</span>
        </div>
        <div className="summary-row">
          <span className="label">Fecha</span>
          <span className="value">{cap(format(parseISO(fecha), "EEEE d 'de' MMMM", { locale: es }))}</span>
        </div>
      </div>

      <CalendarPicker
        label="Fecha de la cita"
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
        ) : loading && (
          <div className="inline-row">
            <Spinner size="sm" /> Consultando disponibilidad...
          </div>
        )}
        {!fechaCerrada && error && <ErrorState message={error} onRetry={handleRetry} />}
        {!fechaCerrada && !loading && !error && (
          <TimeSlotPicker
            slots={slots}
            selected={selectedSlot?.horaInicio}
            onSelect={handleSelectSlot}
          />
        )}
      </div>

      <div className="booking-actions">
        <Button variant="ghost" onClick={() => navigate('/reservar/trabajador')}>
          Atrás
        </Button>
        <Button variant="primary" disabled={!selectedSlot || fechaCerrada} onClick={handleContinue}>
          Continuar
        </Button>
      </div>
    </div>
  );
}
