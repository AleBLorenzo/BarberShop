import { useState, useEffect, useCallback } from 'react';
import { addMonths, endOfMonth, format, startOfMonth } from 'date-fns';
import { es } from 'date-fns/locale/es';
import { adminApi } from '../../services/api';
import { formatDate, formatTime } from '../../utils/date';
import PageHeader from '../../components/ui/PageHeader';
import Button from '../../components/ui/Button';
import Badge from '../../components/ui/Badge';
import Spinner from '../../components/ui/Spinner';
import Modal from '../../components/ui/Modal';
import DatePicker from '../../components/ui/DatePicker';
import { Input, Select } from '../../components/ui/Input';
import { EmptyState } from '../../components/ui/EmptyState';
import { useToast } from '../../hooks/useToast';
import { useSeo } from '../../components/Seo';
import { LuChevronLeft, LuChevronRight, LuPhone } from 'react-icons/lu';

const GENERAL = 'GENERAL';
const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);

/** Texto del estado de un aviso pendiente de acción (D7). */
const estadoAviso = (a) => {
  if (a.canal === 'EMAIL' && a.estado === 'FALLIDO') return 'El email falló';
  if (a.canal === 'EMAIL' && a.estado === 'PENDIENTE_ENVIO') return 'Email sin confirmar';
  return 'Sin email';
};

/**
 * Admin: cierres de día (toda la barbería o un trabajador) con vista previa
 * obligatoria antes de confirmar, y "Clientes por avisar" (avisos telefónicos
 * pendientes y emails fallidos) con botón "Avisado" (D4, D7, D12).
 */
export default function AdminCierres() {
  useSeo({
    title: 'Cierres y avisos (admin) — TheTankBarber',
    description: 'Cierres de día de la barbería o de un trabajador y clientes por avisar. Área privada.',
    noindex: true,
  });

  const toast = useToast();
  const hoy = format(new Date(), 'yyyy-MM-dd');

  // --- Listado por mes ---
  const [mes, setMes] = useState(() => startOfMonth(new Date()));
  const [cierres, setCierres] = useState([]);
  const [loadingCierres, setLoadingCierres] = useState(true);
  const [toDelete, setToDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  // --- Formulario ---
  const [trabajadores, setTrabajadores] = useState([]);
  const [form, setForm] = useState({ alcance: GENERAL, fecha: '', motivo: '' });
  const [preview, setPreview] = useState(null);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [creating, setCreating] = useState(false);

  // --- Avisos ---
  const [avisos, setAvisos] = useState([]);
  const [loadingAvisos, setLoadingAvisos] = useState(true);
  const [marcando, setMarcando] = useState(null);

  const loadCierres = useCallback(async (m) => {
    setLoadingCierres(true);
    try {
      const data = await adminApi.getCierres(
        format(startOfMonth(m), 'yyyy-MM-dd'),
        format(endOfMonth(m), 'yyyy-MM-dd')
      );
      setCierres(data);
    } catch {
      toast.error('No se pudieron cargar los cierres');
    } finally {
      setLoadingCierres(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const loadAvisos = useCallback(async () => {
    try {
      setAvisos(await adminApi.getAvisosPendientes());
    } catch {
      toast.error('No se pudieron cargar los clientes por avisar');
    } finally {
      setLoadingAvisos(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    loadCierres(mes);
  }, [mes, loadCierres]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    loadAvisos();
    adminApi
      .getTrabajadores()
      .then((ts) => setTrabajadores(ts.filter((t) => t.activo)))
      .catch(() => {});
  }, [loadAvisos]);

  // Cualquier cambio en el formulario invalida la vista previa (es obligatoria)
  const setField = (key, value) => {
    setForm((f) => ({ ...f, [key]: value }));
    setPreview(null);
  };

  const trabajadorIdForm = form.alcance === GENERAL ? null : Number(form.alcance);

  const verPreview = async () => {
    if (!form.fecha) {
      toast.error('Elige la fecha del cierre');
      return;
    }
    setLoadingPreview(true);
    try {
      setPreview(await adminApi.previewCierre(form.fecha, trabajadorIdForm));
    } catch (err) {
      toast.error(err?.response?.data?.message || 'No se pudo calcular la vista previa');
    } finally {
      setLoadingPreview(false);
    }
  };

  const confirmarCierre = async () => {
    setCreating(true);
    try {
      const res = await adminApi.crearCierre({
        fecha: form.fecha,
        trabajadorId: trabajadorIdForm,
        motivo: form.motivo.trim() || null,
      });
      const n = res?.canceladas || 0;
      toast.success(
        n > 0
          ? `Día cerrado · ${n} cita(s) cancelada(s): ${res.porEmail || 0} por email, ${res.porTelefono || 0} por teléfono`
          : 'Día cerrado'
      );
      setPreview(null);
      setForm({ alcance: GENERAL, fecha: '', motivo: '' });
      await Promise.all([loadCierres(mes), loadAvisos()]);
    } catch (err) {
      const code = err?.response?.data?.errorCode;
      toast.error(
        code === 'CIERRE_DUPLICADO'
          ? err.response.data.message || 'Ese cierre ya existe'
          : err?.response?.data?.message || 'No se pudo crear el cierre'
      );
    } finally {
      setCreating(false);
    }
  };

  const eliminarCierre = async () => {
    if (!toDelete) return;
    setDeleting(true);
    try {
      await adminApi.eliminarCierre(toDelete.id);
      setCierres((prev) => prev.filter((c) => c.id !== toDelete.id));
      toast.success('Día reabierto');
      setToDelete(null);
    } catch (err) {
      toast.error(err?.response?.data?.message || 'No se pudo eliminar el cierre');
    } finally {
      setDeleting(false);
    }
  };

  const marcarAvisado = async (aviso) => {
    setMarcando(aviso.id);
    try {
      await adminApi.marcarAvisado(aviso.id);
      setAvisos((prev) => prev.filter((a) => a.id !== aviso.id));
      toast.success(`${aviso.clienteNombre} marcado como avisado`);
    } catch (err) {
      toast.error(err?.response?.data?.message || 'No se pudo marcar como avisado');
    } finally {
      setMarcando(null);
    }
  };

  return (
    <div>
      <PageHeader title="Cierres y avisos" />

      {/* Clientes por avisar: lo primero en móvil, es lo urgente */}
      <section className="card admin-section" aria-labelledby="avisos-title">
        <div className="card-header">
          <h3 id="avisos-title">
            Clientes por avisar{avisos.length > 0 ? ` (${avisos.length})` : ''}
          </h3>
        </div>
        <div className="card-body">
          {loadingAvisos ? (
            <Spinner size="md" />
          ) : avisos.length === 0 ? (
            <EmptyState title="Todo al día" message="No hay clientes pendientes de avisar." />
          ) : (
            <div className="reservation-list">
              {avisos.map((a) => (
                <div key={a.id} className="reservation-card">
                  <div className="res-time">
                    {formatDate(a.fecha, 'EEE dd/MM')}
                    <br />
                    <small>{formatTime(a.horaInicio)}</small>
                  </div>
                  <div className="res-client">
                    <div className="name">{a.clienteNombre}</div>
                    <div className="detail">
                      {a.servicioNombre} · {a.trabajadorNombre}
                    </div>
                    {a.motivo && <div className="detail">Motivo: {a.motivo}</div>}
                  </div>
                  <Badge variant="pending">{estadoAviso(a)}</Badge>
                  <div className="res-actions">
                    {a.clienteTelefono && (
                      <a className="btn btn-secondary btn-sm" href={`tel:${a.clienteTelefono}`}>
                        <LuPhone aria-hidden="true" />
                        {a.clienteTelefono}
                      </a>
                    )}
                    <Button
                      size="sm"
                      variant="primary"
                      loading={marcando === a.id}
                      onClick={() => marcarAvisado(a)}
                    >
                      Avisado
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* Nuevo cierre */}
      <section className="card admin-section" aria-labelledby="nuevo-cierre-title">
        <div className="card-header">
          <h3 id="nuevo-cierre-title">Cerrar un día</h3>
        </div>
        <div className="card-body">
          <Select
            id="cierre-alcance"
            label="¿Quién cierra?"
            value={form.alcance}
            onChange={(e) => setField('alcance', e.target.value)}
          >
            <option value={GENERAL}>Toda la barbería</option>
            {trabajadores.map((t) => (
              <option key={t.id} value={t.id}>
                Solo {t.nombre}
              </option>
            ))}
          </Select>
          <DatePicker
            id="cierre-fecha"
            label="Fecha"
            value={form.fecha}
            min={hoy}
            onChange={(v) => setField('fecha', v || '')}
            required
          />
          <Input
            id="cierre-motivo"
            label="Motivo (se le comunica a los clientes)"
            placeholder="Ej.: festivo local"
            maxLength={255}
            value={form.motivo}
            onChange={(e) => setField('motivo', e.target.value)}
          />

          {!preview && (
            <div className="admin-section-actions">
              <Button variant="secondary" loading={loadingPreview} onClick={verPreview}>
                Ver citas afectadas
              </Button>
            </div>
          )}

          {preview && (
            <div className="cierre-preview" aria-live="polite">
              {preview.yaExiste ? (
                <div className="error-state" role="alert">
                  <span>Ya existe ese cierre para el {formatDate(preview.fecha)}.</span>
                </div>
              ) : (
                <>
                  <p>
                    <strong>
                      {preview.total === 0
                        ? 'No hay citas activas ese día.'
                        : `Se cancelarán ${preview.total} cita(s)`}
                    </strong>
                    {preview.total > 0 && (
                      <>
                        {' '}· {preview.porEmail} se avisarán por email, {preview.porTelefono} tendrás
                        que llamarlas.
                      </>
                    )}
                  </p>
                  {preview.citas?.length > 0 && (
                    <ul className="cierre-citas">
                      {preview.citas.map((c) => (
                        <li key={c.reservaId}>
                          <strong>{formatTime(c.horaInicio)}</strong> {c.clienteNombre} · {c.servicioNombre}
                          {preview.general ? ` · ${c.trabajadorNombre}` : ''}
                          {c.clienteTieneEmail ? '' : ' · sin email'}
                        </li>
                      ))}
                    </ul>
                  )}
                </>
              )}
              <div className="admin-section-actions">
                <Button variant="ghost" onClick={() => setPreview(null)}>
                  Volver
                </Button>
                {!preview.yaExiste && (
                  <Button variant="danger" loading={creating} onClick={confirmarCierre}>
                    {preview.total > 0 ? `Cerrar y cancelar ${preview.total} cita(s)` : 'Confirmar cierre'}
                  </Button>
                )}
              </div>
            </div>
          )}
        </div>
      </section>

      {/* Listado del mes */}
      <section className="card admin-section" aria-labelledby="cierres-mes-title">
        <div className="card-header admin-card-header">
          <h3 id="cierres-mes-title">{cap(format(mes, 'MMMM yyyy', { locale: es }))}</h3>
          <span className="btn-pair">
            <Button size="sm" variant="secondary" aria-label="Mes anterior" onClick={() => setMes((m) => addMonths(m, -1))}>
              <LuChevronLeft aria-hidden="true" />
            </Button>
            <Button size="sm" variant="secondary" aria-label="Mes siguiente" onClick={() => setMes((m) => addMonths(m, 1))}>
              <LuChevronRight aria-hidden="true" />
            </Button>
          </span>
        </div>
        <div className="card-body">
          {loadingCierres ? (
            <Spinner size="md" />
          ) : cierres.length === 0 ? (
            <EmptyState title="Sin cierres" message="No hay días cerrados este mes." />
          ) : (
            <div className="reservation-list">
              {cierres.map((c) => (
                <div key={c.id} className="reservation-card">
                  <div className="res-time">{formatDate(c.fecha, 'EEE dd/MM')}</div>
                  <div className="res-client">
                    <div className="name">{c.general ? 'Toda la barbería' : c.trabajadorNombre}</div>
                    {c.motivo && <div className="detail">{c.motivo}</div>}
                  </div>
                  {c.fecha >= hoy && (
                    <div className="res-actions">
                      <Button size="sm" variant="ghost" onClick={() => setToDelete(c)}>
                        Reabrir
                      </Button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      <Modal
        open={!!toDelete}
        onClose={() => setToDelete(null)}
        title="¿Reabrir este día?"
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setToDelete(null)}>Volver</Button>
            <Button variant="primary" loading={deleting} onClick={eliminarCierre}>
              Reabrir
            </Button>
          </>
        }
      >
        <p>
          Se podrá volver a reservar el {toDelete ? formatDate(toDelete.fecha) : ''}. Las citas que se
          cancelaron con el cierre <strong>no</strong> se reactivan.
        </p>
      </Modal>
    </div>
  );
}
