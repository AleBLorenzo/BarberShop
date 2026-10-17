import { useState, useEffect } from 'react';
import { addDays, format, parseISO, isToday, startOfWeek } from 'date-fns';
import { es } from 'date-fns/locale/es';
import { useWorkerReservas } from '../../hooks/useWorkerReservas';
import { publicApi, workerApi } from '../../services/api';
import ReservationCard from '../../components/worker/ReservationCard';
import PageHeader from '../../components/ui/PageHeader';
import Button from '../../components/ui/Button';
import Spinner from '../../components/ui/Spinner';
import Modal from '../../components/ui/Modal';
import DatePicker from '../../components/ui/DatePicker';
import TimeSlotPicker from '../../components/ui/TimeSlotPicker';
import { Input, Select } from '../../components/ui/Input';
import { EmptyState, ErrorState } from '../../components/ui/EmptyState';
import { formatCurrency } from '../../utils/date';
import { useToast } from '../../hooks/useToast';
import { LuChevronLeft, LuChevronRight, LuRefreshCw } from 'react-icons/lu';

// La barbería cierra los domingos: la semana se muestra de lunes a sábado
const DAY_LABELS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
const WEEK_DAYS = DAY_LABELS.length; // 6 (lun→sáb)

// Spanish phone: +34 prefix optional, 9 or 6/7 first digit, 9 digits total
const PHONE_PATTERN = /^(?:\+34\s?)?[679]\d{2}[\s-]?\d{3}[\s-]?\d{3}$/;
const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

const emptyForm = () => ({
  servicioId: '',
  fecha: format(new Date(), 'yyyy-MM-dd'),
  slot: null,
  clienteNombre: '',
  clienteTelefono: '',
  clienteEmail: '',
});

/**
 * Worker dashboard: weekly summary + day detail + reservation actions.
 */
export default function WorkerDashboard() {
  const {
    semana,
    reservasMes,
    loading,
    error,
    refresh,
    fetchSemana,
    reservasDia,
    loadingDia,
    fechaSeleccionada,
    fetchDia,
    cambiarEstado,
  } = useWorkerReservas();
  const toast = useToast();
  const [actionLoading, setActionLoading] = useState(null);
  const [refreshing, setRefreshing] = useState(false);

  // --- Modal "Nueva cita" ---
  const [showNew, setShowNew] = useState(false);
  const [servicios, setServicios] = useState([]);
  const [perfil, setPerfil] = useState(null);
  const [slots, setSlots] = useState([]);
  const [slotsLoading, setSlotsLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [fieldErrors, setFieldErrors] = useState({});
  const [form, setForm] = useState(emptyForm);

  const clearError = (key) => {
    setFieldErrors((prev) => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  };

  const setField = (key, value) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    clearError(key);
  };

  const fetchSlots = async (servicioId, fecha, trabajadorId) => {
    setSlotsLoading(true);
    try {
      const data = await publicApi.getDisponibilidad({ servicioId, fecha, trabajadorId });
      setSlots(data);
    } catch {
      setFieldErrors((prev) => ({ ...prev, slots: 'No se pudo consultar la disponibilidad' }));
    } finally {
      setSlotsLoading(false);
    }
  };

  // Load services + own profile the first time the modal opens
  useEffect(() => {
    if (!showNew || (servicios.length > 0 && perfil)) return;
    (async () => {
      try {
        const [servs, perf] = await Promise.all([publicApi.getServicios(), workerApi.getPerfil()]);
        setServicios(servs.filter((s) => s.activo));
        setPerfil(perf);
      } catch {
        toast.error('No se pudieron cargar los datos para la nueva cita');
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [showNew]);

  // Own slots via /public/disponibilidad (trabajadorId = JWT identity)
  useEffect(() => {
    if (!showNew || !form.servicioId || !form.fecha || !perfil?.id) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    fetchSlots(form.servicioId, form.fecha, perfil.id);
  }, [showNew, form.servicioId, form.fecha, perfil?.id]);

  const handleServicioChange = (e) => {
    const value = e.target.value;
    setForm((prev) => ({ ...prev, servicioId: value ? Number(value) : '', slot: null }));
    clearError('servicioId');
    clearError('slot');
  };

  const handleFechaChange = (fechaStr) => {
    setForm((prev) => ({ ...prev, fecha: fechaStr, slot: null }));
    clearError('fecha');
    clearError('slot');
  };

  const handleSelectSlot = (slot) => {
    setForm((prev) => ({ ...prev, slot }));
    clearError('slot');
  };

  const closeModal = () => {
    setShowNew(false);
    setForm(emptyForm());
    setSlots([]);
    setFieldErrors({});
  };

  const handleCreateReserva = async () => {
    const errs = {};
    if (!form.servicioId) errs.servicioId = 'Selecciona un servicio';
    if (!form.fecha) errs.fecha = 'Selecciona una fecha';
    if (!form.slot) errs.slot = 'Selecciona un horario';
    if (!form.clienteNombre.trim()) errs.clienteNombre = 'Nombre requerido';
    const telefono = form.clienteTelefono.replace(/\s/g, '');
    if (!PHONE_PATTERN.test(telefono)) {
      errs.clienteTelefono = 'Formato español inválido (ej. 612 345 678)';
    }
    // Email OPCIONAL del cliente (mismo criterio que el formulario público,
    // spec booking-contact-email-optional/D2): solo se valida el formato si
    // el campo trae contenido. NO reintroducir 'Email requerido'.
    const email = form.clienteEmail.trim();
    if (email && !EMAIL_PATTERN.test(email)) {
      errs.clienteEmail = 'Formato de email inválido';
    }
    setFieldErrors(errs);
    if (Object.keys(errs).length > 0) return;

    setCreating(true);
    try {
      const creada = await workerApi.createReserva({
        servicioId: form.servicioId,
        fecha: form.fecha,
        horaInicio: form.slot.horaInicio,
        clienteNombre: form.clienteNombre.trim(),
        clienteTelefono: telefono,
        // Ausencia explícita: sin email se envía null (no cadena vacía)
        clienteEmail: email || null,
      });
      toast.success(`Cita creada · Token de cancelación: ${creada.tokenCancelacion}`);
      const fechaCreada = form.fecha;
      closeModal();
      // Refresh weekly summary + select the day so the new cita is visible
      await Promise.allSettled([refresh(), fetchDia(fechaCreada)]);
    } catch (err) {
      const status = err?.response?.status;
      const msg = err?.response?.data?.message || err?.message || 'No se pudo crear la cita';
      if (status === 409) {
        setFieldErrors({ slot: 'Ese hueco acaba de ocuparse, elige otro' });
        if (perfil?.id) fetchSlots(form.servicioId, form.fecha, perfil.id);
      } else {
        setFieldErrors({ submit: msg });
      }
    } finally {
      setCreating(false);
    }
  };

  // Count reservations per ISO date
  const countsByDate = {};
  semana.forEach((r) => {
    countsByDate[r.fecha] = (countsByDate[r.fecha] || 0) + 1;
  });

  // Ingresos: total (completadas + confirmadas) y desglose por
  // método de pago (solo COMPLETADA lleva metodoPago en el backend).
  // Reutilizado por las tarjetas de semana, día y mes.
  const resumenIngresos = (reservas) => {
    const esIngresable = (r) => r.estado === 'COMPLETADA' || r.estado === 'CONFIRMADA';
    const sumaPorMetodo = (metodo) =>
      reservas
        .filter((r) => r.estado === 'COMPLETADA' && r.metodoPago === metodo)
        .reduce((sum, r) => sum + (r.precioTotal || 0), 0);
    return {
      total: reservas
        .filter(esIngresable)
        .reduce((sum, r) => sum + (r.precioTotal || 0), 0),
      visa: sumaPorMetodo('VISA'),
      efectivo: sumaPorMetodo('EFECTIVO'),
    };
  };
  const ingresosSemana = resumenIngresos(semana);
  // Mes en curso: diario = reservas de hoy (00:00→ahora), mensual = todo el mes
  const hoyIso = format(new Date(), 'yyyy-MM-dd');
  const ingresosDia = resumenIngresos(reservasMes.filter((r) => r.fecha === hoyIso));
  const ingresosMes = resumenIngresos(reservasMes);

  // Week being viewed: Monday of the selected week (defaults to current week)
  const [weekStart, setWeekStart] = useState(() =>
    startOfWeek(new Date(), { weekStartsOn: 1 })
  );
  // Week dates Mon-Sat (Sundays the shop is closed)
  const weekDates = Array.from({ length: WEEK_DAYS }, (_, i) => addDays(weekStart, i));
  const weekRangeLabel = `${format(weekDates[0], 'dd/MM')} – ${format(
    weekDates[weekDates.length - 1],
    'dd/MM/yyyy'
  )}`;

  const goToWeek = (monday) => {
    setWeekStart(monday);
    fetchSemana(format(monday, 'yyyy-MM-dd'));
  };
  const handlePrevWeek = () => goToWeek(addDays(weekStart, -7));
  const handleNextWeek = () => goToWeek(addDays(weekStart, 7));
  const handleCurrentWeek = () => {
    setWeekStart(startOfWeek(new Date(), { weekStartsOn: 1 }));
    // null → el backend devuelve la semana en curso
    fetchSemana(null);
  };

  const handleAction = async (reservaId, estado, metodoPago) => {
    setActionLoading(reservaId);
    try {
      await cambiarEstado(reservaId, estado, metodoPago);
      toast.success(
        metodoPago
          ? `Cita completada · pago: ${metodoPago.toLowerCase()}`
          : `Estado actualizado: ${estado.toLowerCase().replace(/_/g, ' ')}`
      );
    } catch (err) {
      toast.error(
        err?.response?.data?.message || 'No se pudo actualizar el estado'
      );
    } finally {
      setActionLoading(null);
    }
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    await refresh();
    setRefreshing(false);
    toast.info('Actualizado');
  };

  if (loading) {
    return (
      <div className="loading-page">
        <Spinner size="lg" />
        <p>Cargando tu semana...</p>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Mi semana"
        actions={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <span
              style={{
                fontSize: 'var(--font-size-sm)',
                color: 'var(--muted)',
                fontWeight: 600,
                whiteSpace: 'nowrap',
              }}
              aria-live="polite"
            >
              {weekRangeLabel}
            </span>
            <Button
              variant="secondary"
              size="sm"
              onClick={handlePrevWeek}
              aria-label="Semana anterior"
            >
              <LuChevronLeft aria-hidden="true" />
            </Button>
            <Button variant="secondary" size="sm" onClick={handleCurrentWeek}>
              Semana actual
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={handleNextWeek}
              aria-label="Semana siguiente"
            >
              <LuChevronRight aria-hidden="true" />
            </Button>
            <Button variant="primary" size="sm" onClick={() => setShowNew(true)}>
              + Nueva cita
            </Button>
            <Button variant="secondary" size="sm" onClick={handleRefresh} loading={refreshing}>
              <LuRefreshCw aria-hidden="true" />
              Actualizar
            </Button>
          </div>
        }
      />

      {error && <ErrorState message={error} onRetry={() => refresh()} />}

      {/* Weekly summary */}
      <div className="week-summary" role="list" aria-label="Reservas de la semana">
        {weekDates.map((d, i) => {
          const iso = format(d, 'yyyy-MM-dd');
          const count = countsByDate[iso] || 0;
          const selected = fechaSeleccionada === iso;
          return (
            <button
              key={iso}
              type="button"
              role="listitem"
              className={`day-card ${selected ? 'selected' : ''} ${isToday(d) ? 'today' : ''}`}
              onClick={() => fetchDia(iso)}
              aria-pressed={selected}
            >
              <div className="day-name">{DAY_LABELS[i]}</div>
              <div className="day-count">{count}</div>
              <div className="day-date">{format(d, 'dd/MM')}</div>
            </button>
          );
        })}
      </div>

      {/* Day detail */}
      {fechaSeleccionada ? (
        <section aria-label="Reservas del día seleccionado">
          <h2 style={{ fontSize: 'var(--font-size-lg)' }}>
            {format(parseISO(fechaSeleccionada), "EEEE dd 'de' MMMM", { locale: es })}
          </h2>
          {loadingDia ? (
            <Spinner size="md" />
          ) : reservasDia.length === 0 ? (
            <EmptyState title="Sin citas" message="No tienes citas este día." />
          ) : (
            <div className="reservation-list">
              {reservasDia.map((r) => (
                <ReservationCard key={r.id} reserva={r} onAction={handleAction} actionLoading={actionLoading} />
              ))}
            </div>
          )}
        </section>
      ) : (
        <EmptyState
          title="Selecciona un día"
          message="Haz clic en un día de la semana para ver tus citas."
        />
      )}

      {/* Weekly totals */}
      <section style={{ marginTop: 'var(--space-xl)' }}>
        <div className="stats-grid">
          <div className="stat-card">
            <div className="stat-label">Total semanal</div>
            <div className="stat-value accent">{semana.length}</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">Confirmadas</div>
            <div className="stat-value success">
              {semana.filter((r) => r.estado === 'CONFIRMADA').length}
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-label">Pendientes</div>
            <div className="stat-value warning">
              {semana.filter((r) => r.estado === 'PENDIENTE').length}
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-label">Ingresos semana</div>
            <div className="stat-value">{formatCurrency(ingresosSemana.total)}</div>
            <div className="stat-breakdown">
              <span>VISA · {formatCurrency(ingresosSemana.visa)}</span>
              <span>Efectivo · {formatCurrency(ingresosSemana.efectivo)}</span>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-label">Ingresos diario</div>
            <div className="stat-value">{formatCurrency(ingresosDia.total)}</div>
            <div className="stat-breakdown">
              <span>VISA · {formatCurrency(ingresosDia.visa)}</span>
              <span>Efectivo · {formatCurrency(ingresosDia.efectivo)}</span>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-label">Ingresos mensual</div>
            <div className="stat-value">{formatCurrency(ingresosMes.total)}</div>
            <div className="stat-breakdown">
              <span>VISA · {formatCurrency(ingresosMes.visa)}</span>
              <span>Efectivo · {formatCurrency(ingresosMes.efectivo)}</span>
            </div>
          </div>
        </div>
      </section>

      {/* Modal: nueva cita (identidad del trabajador sale del JWT) */}
      <Modal
        open={showNew}
        onClose={closeModal}
        title="Nueva cita"
        size="md"
        footer={
          <>
            <Button variant="ghost" onClick={closeModal} type="button">
              Cancelar
            </Button>
            <Button variant="primary" onClick={handleCreateReserva} loading={creating} type="button">
              {creating ? 'Creando...' : 'Crear cita'}
            </Button>
          </>
        }
      >
        {fieldErrors.submit && (
          <p className="form-error" role="alert" style={{ textAlign: 'center' }}>
            {fieldErrors.submit}
          </p>
        )}

        <Select
          id="nueva-servicio"
          label="Servicio"
          required
          value={form.servicioId}
          onChange={handleServicioChange}
          error={fieldErrors.servicioId}
        >
          <option value="">Selecciona un servicio</option>
          {servicios.map((s) => (
            <option key={s.id} value={s.id}>
              {s.nombre} · {s.duracionMinutos} min · {formatCurrency(s.precio)}
            </option>
          ))}
        </Select>

        <DatePicker
          label="Fecha de la cita"
          value={form.fecha}
          onChange={handleFechaChange}
          min={format(new Date(), 'yyyy-MM-dd')}
          max={format(addDays(new Date(), 60), 'yyyy-MM-dd')}
          error={fieldErrors.fecha}
          required
        />

        <div className="form-group">
          <label className="form-label" htmlFor="nueva-hora">
            Horario<span style={{ color: 'var(--danger)' }}> *</span>
          </label>
          {!form.servicioId ? (
            <p style={{ color: 'var(--muted)' }}>Elige un servicio para ver los huecos disponibles.</p>
          ) : slotsLoading ? (
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <Spinner size="sm" /> Consultando disponibilidad...
            </div>
          ) : (
            <>
              <TimeSlotPicker
                slots={slots}
                selected={form.slot?.horaInicio}
                onSelect={handleSelectSlot}
              />
              {(fieldErrors.slot || fieldErrors.slots) && (
                <p className="form-error" role="alert">
                  {fieldErrors.slot || fieldErrors.slots}
                </p>
              )}
            </>
          )}
        </div>

        <Input
          id="nueva-nombre"
          label="Nombre del cliente"
          required
          placeholder="Nombre y apellidos"
          value={form.clienteNombre}
          onChange={(e) => setField('clienteNombre', e.target.value)}
          error={fieldErrors.clienteNombre}
        />

        <Input
          id="nueva-telefono"
          label="Teléfono"
          required
          type="tel"
          placeholder="612 345 678"
          value={form.clienteTelefono}
          onChange={(e) => setField('clienteTelefono', e.target.value)}
          error={fieldErrors.clienteTelefono}
        />

        <Input
          id="nueva-email"
          label="Email (opcional)"
          type="email"
          placeholder="tu@email.com"
          value={form.clienteEmail}
          onChange={(e) => setField('clienteEmail', e.target.value)}
          error={fieldErrors.clienteEmail}
        />
        <small style={{ color: 'var(--muted)' }}>
          {form.clienteEmail.trim()
            ? 'Se enviará la confirmación de la cita al cliente por correo.'
            : 'Sin email no se enviará la confirmación al cliente; el token queda en pantalla.'}
        </small>
      </Modal>
    </div>
  );
}
