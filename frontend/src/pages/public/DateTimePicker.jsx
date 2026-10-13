import { useState, useEffect } from 'react';
import { useNavigate, useLocation, Navigate } from 'react-router-dom';
import { addDays, format, parseISO } from 'date-fns';
import { es } from 'date-fns/locale/es';
import { publicApi } from '../../services/api';
import { dedupeSlots } from '../../utils/slots';
import { useBooking } from '../../hooks/useBooking';
import CalendarPicker from '../../components/ui/CalendarPicker';
import TimeSlotPicker from '../../components/ui/TimeSlotPicker';
import { ErrorState } from '../../components/ui/EmptyState';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';

const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);

/**
 * Booking step 3: date + time slot selection.
 */
export default function DateTimePicker() {
  const { draft, updateDraft } = useBooking();
  const navigate = useNavigate();
  const location = useLocation();
  const navError = location.state?.error;
  const [fecha, setFecha] = useState(draft.fecha || format(new Date(), 'yyyy-MM-dd'));
  const [slots, setSlots] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedSlot, setSelectedSlot] = useState(draft.slot);

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

  const minDate = format(new Date(), 'yyyy-MM-dd');
  const maxDate = format(addDays(new Date(), 60), 'yyyy-MM-dd');

  return (
    <div>
      {navError && (
        <div className="error-state" role="alert" style={{ marginBottom: 16 }}>
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
        required
      />

      <div className="form-group">
        <label className="form-label">Horarios disponibles</label>
        {loading && (
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <Spinner size="sm" /> Consultando disponibilidad...
          </div>
        )}
        {error && <ErrorState message={error} onRetry={handleRetry} />}
        {!loading && !error && (
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
        <Button variant="primary" disabled={!selectedSlot} onClick={handleContinue}>
          Continuar
        </Button>
      </div>
    </div>
  );
}
