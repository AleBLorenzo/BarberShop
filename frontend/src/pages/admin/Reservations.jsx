import { useState, useEffect, useMemo } from 'react';
import { adminApi } from '../../services/api';
import { formatDate, formatTime } from '../../utils/date';
import PageHeader from '../../components/ui/PageHeader';
import Table from '../../components/ui/Table';
import Modal from '../../components/ui/Modal';
import Button from '../../components/ui/Button';
import { estadoToLabel } from '../../utils/estado';
import { Input, Select, Label } from '../../components/ui/Input';
import DatePicker from '../../components/ui/DatePicker';
import { useToast } from '../../hooks/useToast';

const PAGE_SIZE = 20;
const ESTADOS = ['PENDIENTE', 'CONFIRMADA', 'COMPLETADA', 'CANCELADA', 'NO_ASISTIO'];

/**
 * Admin global reservations page with filters, status override and edit.
 */
export default function AdminReservations() {
  const toast = useToast();
  const [reservas, setReservas] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);

  // Filters
  const [filters, setFilters] = useState({ fecha: '', estado: '', cliente: '' });

  // Edit modal
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({ trabajadorId: '', fecha: '', horaInicio: '', estado: '' });
  const [saving, setSaving] = useState(false);

  const load = async () => {
    try {
      const data = await adminApi.getAllReservas();
      setReservas(Array.isArray(data) ? data : []);
      setLoading(false);
    } catch {
      toast.error('No se pudieron cargar las reservas');
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Backend ignores filter params (TODO upstream): filter + paginate client-side
  const filtered = useMemo(() => {
    return reservas.filter((r) => {
      if (filters.fecha && r.fecha !== filters.fecha) return false;
      if (filters.estado && r.estado !== filters.estado) return false;
      if (filters.cliente) {
        const q = filters.cliente.toLowerCase();
        const nombre = r.cliente?.nombre?.toLowerCase() || '';
        const tel = r.cliente?.telefono || '';
        if (!nombre.includes(q) && !tel.includes(q)) return false;
      }
      return true;
    });
  }, [reservas, filters]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const pageRows = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  const openEdit = (r) => {
    setEditing(r);
    setForm({
      trabajadorId: r.trabajador?.id || '',
      fecha: r.fecha,
      horaInicio: r.horaInicio?.slice(0, 5),
      estado: r.estado,
    });
  };

  const quickStatus = async (r, estado) => {
    try {
      await adminApi.updateReserva(r.id, { estado });
      setReservas((prev) => prev.map((x) => (x.id === r.id ? { ...x, estado } : x)));
      toast.success(`Estado actualizado: ${estadoToLabel(estado)}`);
    } catch (err) {
      toast.error(err?.response?.data?.message || 'No se pudo actualizar');
    }
  };

  const saveEdit = async () => {
    setSaving(true);
    try {
      const payload = { estado: form.estado };
      if (form.fecha) payload.fecha = form.fecha;
      if (form.horaInicio) {
        payload.horaInicio = form.horaInicio;
        // Keep horaFin in sync with the service duration (backend does not recompute it)
        const [h, m] = form.horaInicio.split(':').map(Number);
        const dur = editing?.servicio?.duracionMinutos || 30;
        const total = h * 60 + m + dur;
        const pad = (n) => String(n).padStart(2, '0');
        payload.horaFin = `${pad(Math.floor(total / 60) % 24)}:${pad(total % 60)}:00`;
      }
      await adminApi.updateReserva(editing.id, payload);
      toast.success('Reserva actualizada');
      setEditing(null);
      load();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'No se pudo actualizar la reserva');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    { key: 'fecha', label: 'Fecha', render: (row) => formatDate(row.fecha) },
    {
      key: 'hora',
      label: 'Hora',
      render: (row) => `${formatTime(row.horaInicio)} - ${formatTime(row.horaFin)}`,
    },
    { key: 'cliente', label: 'Cliente', render: (row) => row.cliente?.nombre },
    { key: 'telefono', label: 'Teléfono', render: (row) => row.cliente?.telefono },
    { key: 'trabajador', label: 'Trabajador', render: (row) => row.trabajador?.nombre || '—' },
    { key: 'servicio', label: 'Servicio', render: (row) => row.servicio?.nombre },
    {
      key: 'estado',
      label: 'Estado',
      render: (row) => (
        <select
          className="form-select"
          style={{ padding: '4px 8px', fontSize: 'var(--font-size-xs)' }}
          value={row.estado}
          onChange={(e) => quickStatus(row, e.target.value)}
          aria-label={`Cambiar estado de reserva ${row.id}`}
        >
          {ESTADOS.map((e) => (
            <option key={e} value={e}>
              {estadoToLabel(e)}
            </option>
          ))}
        </select>
      ),
    },
    {
      key: 'actions',
      label: 'Acciones',
      render: (row) => (
        <Button size="sm" variant="secondary" onClick={() => openEdit(row)}>
          Editar
        </Button>
      ),
    },
  ];

  return (
    <div>
      <PageHeader title="Reservas" />

      <div className="filters-bar">
        <DatePicker
          label=""
          value={filters.fecha}
          onChange={(v) => { setFilters((f) => ({ ...f, fecha: v || '' })); setPage(1); }}
          placeholder="Fecha"
        />
        <Select
          value={filters.estado}
          onChange={(e) => { setFilters((f) => ({ ...f, estado: e.target.value })); setPage(1); }}
          options={[
            { value: '', label: 'Todos los estados' },
            ...ESTADOS.map((e) => ({ value: e, label: estadoToLabel(e) })),
          ]}
          aria-label="Filtrar por estado"
        />
        <Input
          placeholder="Buscar cliente..."
          value={filters.cliente}
          onChange={(e) => { setFilters((f) => ({ ...f, cliente: e.target.value })); setPage(1); }}
          aria-label="Buscar por cliente"
        />
      </div>

      <Table
        columns={columns}
        data={pageRows}
        loading={loading}
        emptyMessage="No hay reservas con estos filtros."
        page={page}
        totalPages={totalPages}
        onPageChange={setPage}
      />

      {/* Edit modal */}
      <Modal
        open={!!editing}
        onClose={() => setEditing(null)}
        title={`Editar reserva #${editing?.id ?? ''}`}
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditing(null)}>Cancelar</Button>
            <Button variant="primary" loading={saving} onClick={saveEdit}>
              Guardar
            </Button>
          </>
        }
      >
        <div className="booking-summary" style={{ marginBottom: 16 }}>
          <div className="summary-row">
            <span className="label">Cliente</span>
            <span className="value">
              {editing?.cliente?.nombre} · {editing?.cliente?.telefono}
            </span>
          </div>
          <div className="summary-row">
            <span className="label">Servicio</span>
            <span className="value">{editing?.servicio?.nombre}</span>
          </div>
        </div>

        <div className="form-group">
          <Label htmlFor="editEstado">Estado</Label>
          <Select
            id="editEstado"
            value={form.estado}
            onChange={(e) => setForm((f) => ({ ...f, estado: e.target.value }))}
            options={ESTADOS.map((e) => ({ value: e, label: estadoToLabel(e) }))}
          />
        </div>
        <div className="form-group">
          <Label htmlFor="editFecha">Fecha</Label>
          <DatePicker id="editFecha" value={form.fecha} onChange={(v) => setForm((f) => ({ ...f, fecha: v }))} />
        </div>
        <div className="form-group">
          <Label htmlFor="editHora">Hora de inicio</Label>
          <Input
            id="editHora"
            type="time"
            value={form.horaInicio}
            onChange={(e) => setForm((f) => ({ ...f, horaInicio: e.target.value }))}
          />
        </div>
      </Modal>
    </div>
  );
}
