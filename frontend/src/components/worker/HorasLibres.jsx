import { useState, useEffect, useCallback } from 'react';
import { format } from 'date-fns';
import { workerApi } from '../../services/api';
import { formatDate, formatTime } from '../../utils/date';
import { toApiTime } from '../../utils/horario';
import Button from '../ui/Button';
import Modal from '../ui/Modal';
import Spinner from '../ui/Spinner';
import DatePicker from '../ui/DatePicker';
import { Input } from '../ui/Input';
import { EmptyState } from '../ui/EmptyState';
import { useToast } from '../../hooks/useToast';

const emptyForm = () => ({
  fecha: format(new Date(), 'yyyy-MM-dd'),
  horaInicio: '',
  horaFin: '',
  motivo: '',
});

/**
 * "Mis horas libres" (D5): bloqueos propios del trabajador (o del admin en
 * "Mi panel") de hoy a +60 días, modal "Bloquear horas" y eliminar. Un 409
 * `BLOQUEO_CONFLICTO` lista las citas/bloqueos que pisa la franja.
 * @param {{onChange?: Function}} props `onChange` tras crear/eliminar (recargar agenda)
 */
export default function HorasLibres({ onChange }) {
  const toast = useToast();
  const [bloqueos, setBloqueos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [conflicto, setConflicto] = useState(null);
  const [deleting, setDeleting] = useState(null);

  const load = useCallback(async () => {
    try {
      setBloqueos(await workerApi.getBloqueos());
    } catch {
      toast.error('No se pudieron cargar tus horas libres');
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    load();
  }, [load]);

  const setField = (key, value) => {
    setForm((f) => ({ ...f, [key]: value }));
    setError(null);
    setConflicto(null);
  };

  const close = () => {
    setOpen(false);
    setForm(emptyForm());
    setError(null);
    setConflicto(null);
  };

  const crear = async () => {
    if (!form.fecha || !form.horaInicio || !form.horaFin) {
      setError('Indica la fecha y las horas de inicio y fin');
      return;
    }
    if (form.horaFin <= form.horaInicio) {
      setError('La hora de fin debe ser posterior a la de inicio');
      return;
    }
    setSaving(true);
    setError(null);
    setConflicto(null);
    try {
      await workerApi.crearBloqueo({
        fecha: form.fecha,
        horaInicio: toApiTime(form.horaInicio),
        horaFin: toApiTime(form.horaFin),
        motivo: form.motivo.trim() || null,
      });
      toast.success('Horas bloqueadas');
      close();
      await load();
      onChange?.();
    } catch (err) {
      const body = err?.response?.data;
      if (err?.response?.status === 409) {
        setConflicto(body?.data || null);
      }
      setError(body?.message || 'No se pudieron bloquear las horas');
    } finally {
      setSaving(false);
    }
  };

  const eliminar = async (b) => {
    setDeleting(b.id);
    try {
      await workerApi.eliminarBloqueo(b.id);
      setBloqueos((prev) => prev.filter((x) => x.id !== b.id));
      toast.success('Horas liberadas');
      onChange?.();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'No se pudo eliminar');
    } finally {
      setDeleting(null);
    }
  };

  return (
    <section className="card admin-section" aria-labelledby="horas-libres-title">
      <div className="card-header admin-card-header">
        <h3 id="horas-libres-title">Mis horas libres</h3>
        <Button size="sm" variant="primary" onClick={() => setOpen(true)}>
          + Bloquear horas
        </Button>
      </div>
      <div className="card-body">
        {loading ? (
          <Spinner size="md" />
        ) : bloqueos.length === 0 ? (
          <EmptyState
            title="Sin horas bloqueadas"
            message="Bloquea una franja (médico, descanso…) para que nadie pueda reservarla."
          />
        ) : (
          <div className="reservation-list">
            {bloqueos.map((b) => (
              <div key={b.id} className="reservation-card">
                <div className="res-time">
                  {formatDate(b.fecha, 'EEE dd/MM')}
                  <br />
                  <small>
                    {formatTime(b.horaInicio)} – {formatTime(b.horaFin)}
                  </small>
                </div>
                <div className="res-client">
                  <div className="name">{b.motivo || 'Horas libres'}</div>
                </div>
                <div className="res-actions">
                  <Button
                    size="sm"
                    variant="danger"
                    loading={deleting === b.id}
                    onClick={() => eliminar(b)}
                  >
                    Eliminar
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <Modal
        open={open}
        onClose={close}
        title="Bloquear horas"
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={close}>Cancelar</Button>
            <Button variant="primary" loading={saving} onClick={crear}>
              Bloquear
            </Button>
          </>
        }
      >
        <DatePicker
          id="bloqueo-fecha"
          label="Fecha"
          value={form.fecha}
          min={format(new Date(), 'yyyy-MM-dd')}
          onChange={(v) => setField('fecha', v || '')}
          required
        />
        <div className="horario-horas horario-horas--form">
          <Input
            id="bloqueo-inicio"
            label="Desde"
            type="time"
            required
            value={form.horaInicio}
            onChange={(e) => setField('horaInicio', e.target.value)}
          />
          <Input
            id="bloqueo-fin"
            label="Hasta"
            type="time"
            required
            value={form.horaFin}
            onChange={(e) => setField('horaFin', e.target.value)}
          />
        </div>
        <Input
          id="bloqueo-motivo"
          label="Motivo (opcional)"
          placeholder="Ej.: médico"
          maxLength={255}
          value={form.motivo}
          onChange={(e) => setField('motivo', e.target.value)}
        />
        {error && <p className="form-error" role="alert">{error}</p>}
        {conflicto && (conflicto.citas?.length > 0 || conflicto.bloqueos?.length > 0) && (
          <ul className="cierre-citas">
            {(conflicto.citas || []).map((c) => (
              <li key={`c-${c.reservaId}`}>
                Cita {formatTime(c.horaInicio)}–{formatTime(c.horaFin)} · {c.clienteNombre}
              </li>
            ))}
            {(conflicto.bloqueos || []).map((b) => (
              <li key={`b-${b.id}`}>
                Ya bloqueado {formatTime(b.horaInicio)}–{formatTime(b.horaFin)}
                {b.motivo ? ` · ${b.motivo}` : ''}
              </li>
            ))}
          </ul>
        )}
      </Modal>
    </section>
  );
}
