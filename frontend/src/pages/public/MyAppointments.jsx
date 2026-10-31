import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { publicApi } from '../../services/api';
import { formatDate, formatTime } from '../../utils/date';
import Badge from '../../components/ui/Badge';
import { estadoToVariant, estadoToLabel } from '../../utils/estado';
import Button from '../../components/ui/Button';
import Modal from '../../components/ui/Modal';
import { Input, Label } from '../../components/ui/Input';
import { EmptyState, ErrorState } from '../../components/ui/EmptyState';
import { useSeo } from '../../components/Seo';

/**
 * Client appointments lookup by phone + token.
 * Verificado (spec booking-without-email-degradation): la recuperación y la
 * cancelación usan teléfono + token y no dependen ni muestran el email.
 */
export default function MyAppointments() {
  // Zona de sesión con datos privados: noindex (spec `seo-meta`; D2).
  useSeo({
    title: 'Mis citas — TheTankBarber',
    description: 'Consulta, cancela o reprograma tus citas en TheTankBarber con tu teléfono y token.',
    noindex: true,
  });

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
  // Cancelada por el negocio (cierre de día o cancelación del admin, D6)
  const porBarberia = (r) => r.estado === 'CANCELADA' && r.canceladaPor === 'ADMIN';
  const etiquetaEstado = (r) => (porBarberia(r) ? 'Cancelada por la barbería' : estadoToLabel(r.estado));

  return (
    <div className="narrow-page">
      <h1 className="section-title">Mis citas</h1>
      <p className="page-lead">
        Introduce tu teléfono y el token de reserva que recibiste.
      </p>

      <form onSubmit={handleSubmit(onSubmit)} noValidate className="card form-card">
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
        <Button type="submit" variant="primary" loading={loading} className="btn-block">
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
            /* Tarjeta clicable accesible por teclado (antes un <div> con onClick:
               no se podía abrir el detalle sin ratón) */
            <div
              key={r.id}
              className="reservation-card reservation-card--clickable"
              role="button"
              tabIndex={0}
              aria-label={`Ver detalle: ${r.servicio?.nombre ?? 'cita'}, ${formatDate(r.fecha)} ${formatTime(r.horaInicio)}`}
              onClick={() => setSelected(r)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  setSelected(r);
                }
              }}
            >
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
              <Badge variant={estadoToVariant(r.estado)}>{etiquetaEstado(r)}</Badge>
              {porBarberia(r) && r.motivoCancelacion && (
                <div className="res-cancel-note">Motivo: {r.motivoCancelacion}</div>
              )}
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
              {porBarberia(selected) && (
                <Link to="/reservar/servicio" className="btn btn-primary btn-md">
                  Reservar otra cita
                </Link>
              )}
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
                <Badge variant={estadoToVariant(selected.estado)}>{etiquetaEstado(selected)}</Badge>
              </span>
            </div>
            {porBarberia(selected) && (
              <div className="summary-row">
                <span className="label">Motivo</span>
                <span className="value">
                  {selected.motivoCancelacion || 'La barbería ha tenido que cancelar la cita.'}
                </span>
              </div>
            )}
            <div className="summary-row">
              <span className="label">Token</span>
              <span className="value text-xs break-all">
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
