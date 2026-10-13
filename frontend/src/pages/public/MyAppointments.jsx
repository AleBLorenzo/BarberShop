import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { publicApi } from '../../services/api';
import { formatDate, formatTime } from '../../utils/date';
import Badge from '../../components/ui/Badge';
import { estadoToVariant, estadoToLabel } from '../../utils/estado';
import Button from '../../components/ui/Button';
import Modal from '../../components/ui/Modal';
import { Input, Label } from '../../components/ui/Input';
import { EmptyState, ErrorState } from '../../components/ui/EmptyState';

/**
 * Client appointments lookup by phone + token.
 */
export default function MyAppointments() {
  const navigate = useNavigate();
  const [reservas, setReservas] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selected, setSelected] = useState(null);
  const [confirmCancel, setConfirmCancel] = useState(null);
  const [copied, setCopied] = useState(false);
  const [cancelError, setCancelError] = useState(null);
  const [cancelling, setCancelling] = useState(false);

  const { register, handleSubmit, formState: { errors } } = useForm();

  const onSubmit = async (values) => {
    setLoading(true);
    setError(null);
    setReservas(null);
    try {
      const data = await publicApi.getReservasByPhoneToken({
        telefono: values.telefono.replace(/\s/g, ''),
        token: values.token.trim(),
      });
      setReservas(data);
    } catch (err) {
      if (err?.response?.status === 404) {
        setError('No se encontraron reservas con esos datos.');
      } else {
        setError('Error al consultar. Reintenta más tarde.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = async () => {
    if (!confirmCancel) return;
    setCancelling(true);
    setCancelError(null);
    try {
      await publicApi.cancelReserva(confirmCancel.tokenCancelacion);
      setReservas((prev) =>
        (prev || []).map((r) =>
          r.id === confirmCancel.id ? { ...r, estado: 'CANCELADA' } : r
        )
      );
      setConfirmCancel(null);
      setSelected(null);
    } catch (err) {
      setCancelError(err?.response?.data?.message || 'No se pudo cancelar la reserva.');
    } finally {
      setCancelling(false);
    }
  };

  const copyToken = async (token) => {
    try {
      await navigator.clipboard.writeText(token);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch { /* noop */ }
  };

  const isCancellable = (estado) => estado === 'PENDIENTE' || estado === 'CONFIRMADA';

  return (
    <div style={{ maxWidth: 640, margin: '0 auto' }}>
      <h1 className="section-title">Mis citas</h1>
      <p style={{ textAlign: 'center', color: 'var(--muted)' }}>
        Introduce tu teléfono y el token de reserva que recibiste.
      </p>

      <form onSubmit={handleSubmit(onSubmit)} noValidate className="card" style={{ padding: 'var(--space-md)', marginBottom: 'var(--space-lg)' }}>
        <div className="form-group">
          <Label htmlFor="telefono" required>Teléfono</Label>
          <Input
            id="telefono"
            type="tel"
            placeholder="612 345 678"
            error={errors.telefono?.message}
            {...register('telefono', { required: 'Teléfono requerido' })}
          />
        </div>
        <div className="form-group">
          <Label htmlFor="token" required>Token de reserva</Label>
          <Input
            id="token"
            placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            error={errors.token?.message}
            {...register('token', { required: 'Token requerido' })}
          />
        </div>
        <Button type="submit" variant="primary" loading={loading} style={{ width: '100%' }}>
          Buscar mis citas
        </Button>
      </form>

      {error && <ErrorState message={error} />}
      {reservas && reservas.length === 0 && (
        <EmptyState title="Sin citas" message="No se encontraron reservas con esos datos." />
      )}

      {reservas && reservas.length > 0 && (
        <div className="reservation-list">
          {reservas.map((r) => (
            <div key={r.id} className="reservation-card" onClick={() => setSelected(r)} style={{ cursor: 'pointer' }}>
              <div className="res-time">
                {formatDate(r.fecha)}<br />
                <small>{formatTime(r.horaInicio)}</small>
              </div>
              <div className="res-client">
                <div className="name">{r.servicio?.nombre}</div>
                <div className="detail">
                  {r.trabajador?.nombre || 'Cualquiera'} · {r.cliente?.nombre}
                </div>
              </div>
              <Badge variant={estadoToVariant(r.estado)}>{estadoToLabel(r.estado)}</Badge>
            </div>
          ))}
        </div>
      )}

      {/* Detail modal */}
      <Modal
        open={!!selected}
        onClose={() => setSelected(null)}
        title="Detalle de la cita"
        footer={
          selected && (
            <>
              <Button variant="ghost" onClick={() => setSelected(null)}>Cerrar</Button>
              {isCancellable(selected.estado) && (
                <Button variant="danger" onClick={() => setConfirmCancel(selected)}>
                  Cancelar cita
                </Button>
              )}
              {isCancellable(selected.estado) && (
                <Button
                  variant="primary"
                  onClick={() => navigate(`/reprogramar/${selected.tokenCancelacion}`)}
                >
                  Reprogramar
                </Button>
              )}
            </>
          )
        }
      >
        {selected && (
          <div className="booking-summary">
            <div className="summary-row">
              <span className="label">Servicio</span>
              <span className="value">{selected.servicio?.nombre}</span>
            </div>
            <div className="summary-row">
              <span className="label">Trabajador</span>
              <span className="value">{selected.trabajador?.nombre || 'Cualquiera'}</span>
            </div>
            <div className="summary-row">
              <span className="label">Fecha</span>
              <span className="value">{formatDate(selected.fecha, 'EEEE dd/MM/yyyy')}</span>
            </div>
            <div className="summary-row">
              <span className="label">Hora</span>
              <span className="value">
                {formatTime(selected.horaInicio)} - {formatTime(selected.horaFin)}
              </span>
            </div>
            <div className="summary-row">
              <span className="label">Estado</span>
              <span className="value">
                <Badge variant={estadoToVariant(selected.estado)}>{estadoToLabel(selected.estado)}</Badge>
              </span>
            </div>
            <div className="summary-row">
              <span className="label">Token</span>
              <span className="value" style={{ fontSize: 'var(--font-size-xs)', wordBreak: 'break-all' }}>
                {selected.tokenCancelacion}{' '}
                <button
                  className="btn btn-ghost btn-sm"
                  onClick={() => copyToken(selected.tokenCancelacion)}
                >
                  {copied ? '¡Copiado!' : 'Copiar'}
                </button>
              </span>
            </div>
          </div>
        )}
      </Modal>

      {/* Cancel confirmation */}
      <Modal
        open={!!confirmCancel}
        onClose={() => { setConfirmCancel(null); setCancelError(null); }}
        title="¿Cancelar esta cita?"
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => { setConfirmCancel(null); setCancelError(null); }}>
              Volver
            </Button>
            <Button variant="danger" loading={cancelling} onClick={handleCancel}>
              Sí, cancelar
            </Button>
          </>
        }
      >
        <p>Se liberará el hueco para otros clientes. Esta acción no se puede deshacer.</p>
        {cancelError && <p className="form-error" role="alert">{cancelError}</p>}
      </Modal>
    </div>
  );
}
