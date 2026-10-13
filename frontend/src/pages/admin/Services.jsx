import { useState, useEffect, useCallback } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { adminApi, mediaUrl } from '../../services/api';
import { formatCurrency } from '../../utils/date';
import PageHeader from '../../components/ui/PageHeader';
import Table from '../../components/ui/Table';
import Modal from '../../components/ui/Modal';
import Button from '../../components/ui/Button';
import Badge from '../../components/ui/Badge';
import { Input, Label } from '../../components/ui/Input';
import { useToast } from '../../hooks/useToast';

const PAGE_SIZE = 10;
const CARPETA = 'servicios';

/**
 * Admin services CRUD page. Backend returns the full list: pagination is client-side.
 */
export default function AdminServices() {
  const toast = useToast();
  const [servicios, setServicios] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [modal, setModal] = useState(null);
  const [toDelete, setToDelete] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [archivos, setArchivos] = useState([]); // imágenes ya subidas a la carpeta

  const { register, handleSubmit, reset, setValue, getValues, control, formState: { errors } } = useForm();
  // useWatch en vez de watch(): watch() de react-hook-form impide que el
  // compilador de React analice el componente y desactiva otras reglas de hooks
  const foto = useWatch({ control, name: 'foto' });
  const descuento = useWatch({ control, name: 'descuentoPorcentaje' });
  const tieneOferta = descuento !== '' && descuento != null;

  const load = useCallback(async () => {
    try {
      const data = await adminApi.getServicios();
      setServicios(data);
      setLoading(false);
    } catch {
      toast.error('No se pudieron cargar los servicios');
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- toast se recrea cada render
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    load();
  }, [load]);

  // Las fotos ya subidas se cargan al abrir el modal (para poder elegir una de la carpeta)
  const loadArchivos = useCallback(async () => {
    try {
      const lista = await adminApi.listMedia(CARPETA);
      setArchivos(lista);
    } catch {
      setArchivos([]);
    }
  }, []);

  useEffect(() => {
    if (!modal) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    loadArchivos();
  }, [modal, loadArchivos]);

  const totalPages = Math.max(1, Math.ceil(servicios.length / PAGE_SIZE));
  const pageRows = servicios.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  const openCreate = () => {
    reset({
      nombre: '', duracionMinutos: 30, precio: '', activo: true, foto: '',
      descuentoPorcentaje: '', ofertaInicio: '', ofertaFin: '',
    });
    setModal({ mode: 'create' });
  };

  const openEdit = (serv) => {
    reset({
      nombre: serv.nombre,
      duracionMinutos: serv.duracionMinutos,
      precio: serv.precio,
      activo: serv.activo,
      foto: serv.foto || '',
      descuentoPorcentaje: serv.descuentoPorcentaje ?? '',
      ofertaInicio: serv.ofertaInicio ?? '',
      ofertaFin: serv.ofertaFin ?? '',
    });
    setModal({ mode: 'edit', data: serv });
  };

  /** Sube la imagen elegida y guarda su ruta en el campo `foto`. */
  const handleFotoFile = async (event) => {
    const input = event.currentTarget;
    const file = input.files?.[0];
    if (!file) return;
    try {
      const subida = await adminApi.uploadMedia(CARPETA, file);
      if (!subida?.url) throw new Error('Respuesta de subida inválida');
      setValue('foto', subida.url);
      setArchivos((prev) => [...prev.filter((a) => a.nombre !== subida.nombre), subida]);
      toast.success('Foto subida');
    } catch {
      toast.error('No se pudo subir la foto');
    } finally {
      input.value = '';
    }
  };

  /** Rellena el campo `foto` con una imagen ya subida a la carpeta. */
  const handleFotoCarpeta = (event) => {
    if (event.target.value) setValue('foto', event.target.value);
  };

  const onSubmit = async (values) => {
    setSubmitting(true);
    try {
      const pct = values.descuentoPorcentaje === '' || values.descuentoPorcentaje == null
        ? null
        : Number(values.descuentoPorcentaje);
      const payload = {
        nombre: values.nombre,
        duracionMinutos: Number(values.duracionMinutos),
        precio: Number(values.precio),
        activo: !!values.activo,
        foto: values.foto || '',
        // Oferta: null en los 3 campos = sin oferta (el backend exige ventana completa)
        descuentoPorcentaje: pct,
        ofertaInicio: pct != null ? (values.ofertaInicio || null) : null,
        ofertaFin: pct != null ? (values.ofertaFin || null) : null,
      };
      if (modal.mode === 'create') {
        await adminApi.createServicio(payload);
        toast.success('Servicio creado');
      } else {
        await adminApi.updateServicio(modal.data.id, { id: modal.data.id, ...payload });
        toast.success('Servicio actualizado');
      }
      setModal(null);
      load();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Error al guardar');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!toDelete) return;
    try {
      const res = await adminApi.deleteServicio(toDelete.id);
      setServicios((prev) => prev.filter((s) => s.id !== toDelete.id));
      // El backend explica el resultado (p. ej. «El servicio tiene reservas; se desactivó»)
      toast.success(res?.message || 'Servicio eliminado');
      setToDelete(null);
    } catch (err) {
      toast.error(err?.response?.data?.message || 'No se pudo eliminar');
    }
  };

  const columns = [
    { key: 'nombre', label: 'Nombre' },
    {
      key: 'foto',
      label: 'Foto',
      render: (row) => (
        row.foto ? (
          <img
            src={mediaUrl(row.foto)}
            alt={row.nombre}
            loading="lazy"
            style={{
              width: 56,
              height: 56,
              objectFit: 'cover',
              borderRadius: 'var(--radius-sm)',
              background: 'var(--border)',
            }}
            onError={(e) => { e.currentTarget.style.display = 'none'; }}
          />
        ) : '—'
      ),
    },
    { key: 'duracionMinutos', label: 'Duración', render: (row) => `${row.duracionMinutos} min` },
    {
      key: 'precio',
      label: 'Precio',
      render: (row) => (
        row.descuentoPorcentaje != null ? (
          <span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
            {row.ofertaActiva && (
              <s style={{ opacity: 0.6 }}>{formatCurrency(row.precio)}</s>
            )}
            <strong>{row.ofertaActiva ? formatCurrency(row.precioOferta) : formatCurrency(row.precio)}</strong>
            <Badge variant={row.ofertaActiva ? 'pending' : 'default'}>
              -{row.descuentoPorcentaje}%
            </Badge>
          </span>
        ) : formatCurrency(row.precio)
      ),
    },
    {
      key: 'activo',
      label: 'Estado',
      render: (row) => (
        <Badge variant={row.activo ? 'confirmed' : 'cancelled'}>
          {row.activo ? 'Activo' : 'Inactivo'}
        </Badge>
      ),
    },
    {
      key: 'actions',
      label: 'Acciones',
      render: (row) => (
        <span style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          <Button size="sm" variant="secondary" onClick={() => openEdit(row)}>
            Editar
          </Button>
          <Button size="sm" variant="danger" onClick={() => setToDelete(row)}>
            Eliminar
          </Button>
        </span>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Servicios"
        actions={<Button onClick={openCreate}>+ Nuevo servicio</Button>}
      />

      <Table
        columns={columns}
        data={pageRows}
        loading={loading}
        emptyMessage="No hay servicios registrados."
        page={page}
        totalPages={totalPages}
        onPageChange={setPage}
      />

      <Modal
        open={!!modal}
        onClose={() => setModal(null)}
        title={modal?.mode === 'create' ? 'Nuevo servicio' : 'Editar servicio'}
        footer={
          <>
            <Button variant="ghost" onClick={() => setModal(null)}>Cancelar</Button>
            <Button variant="primary" loading={submitting} onClick={handleSubmit(onSubmit)}>
              {modal?.mode === 'create' ? 'Crear' : 'Guardar'}
            </Button>
          </>
        }
      >
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <div className="form-group">
            <Label htmlFor="nombre" required>Nombre</Label>
            <Input
              id="nombre"
              error={errors.nombre?.message}
              {...register('nombre', { required: 'Nombre requerido' })}
            />
          </div>
          <div className="form-group">
            <Label htmlFor="duracionMinutos" required>Duración (minutos)</Label>
            <Input
              id="duracionMinutos"
              type="number"
              min="5"
              step="5"
              error={errors.duracionMinutos?.message}
              {...register('duracionMinutos', {
                required: 'Duración requerida',
                min: { value: 5, message: 'Mínimo 5 minutos' },
              })}
            />
          </div>
          <div className="form-group">
            <Label htmlFor="precio" required>Precio (€)</Label>
            <Input
              id="precio"
              type="number"
              min="0"
              step="0.5"
              error={errors.precio?.message}
              {...register('precio', {
                required: 'Precio requerido',
                min: { value: 0, message: 'Precio inválido' },
              })}
            />
          </div>
          <div className="form-group">
            <Label htmlFor="descuentoPorcentaje">Oferta (% de descuento)</Label>
            <Input
              id="descuentoPorcentaje"
              type="number"
              min="1"
              max="99"
              placeholder="Sin oferta"
              error={errors.descuentoPorcentaje?.message}
              {...register('descuentoPorcentaje', {
                validate: (value) => {
                  if (value === '' || value == null) return true;
                  const n = Number(value);
                  return (Number.isInteger(n) && n >= 1 && n <= 99)
                    || 'Porcentaje de oferta entre 1 y 99';
                },
              })}
            />
          </div>
          <div
            className="form-group"
            style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-sm)' }}
          >
            <div>
              <Label htmlFor="ofertaInicio">Inicio de la oferta</Label>
              <Input
                id="ofertaInicio"
                type="datetime-local"
                disabled={!tieneOferta}
                error={errors.ofertaInicio?.message}
                {...register('ofertaInicio')}
              />
            </div>
            <div>
              <Label htmlFor="ofertaFin">Fin de la oferta</Label>
              <Input
                id="ofertaFin"
                type="datetime-local"
                disabled={!tieneOferta}
                error={errors.ofertaFin?.message}
                {...register('ofertaFin', {
                  validate: (fin) => {
                    const pct = getValues('descuentoPorcentaje');
                    const inicio = getValues('ofertaInicio');
                    const tienePct = pct !== '' && pct != null;
                    if (tienePct && !inicio) return 'Selecciona el inicio de la oferta';
                    if (tienePct && !fin) return 'Selecciona el fin de la oferta';
                    if (inicio && fin && fin <= inicio) return 'El fin debe ser posterior al inicio';
                    return true;
                  },
                })}
              />
            </div>
          </div>
          <div className="form-group">
            <Label htmlFor="foto-file">Foto</Label>
            {/* Registra `foto` en el formulario (visible solo como estado interno) */}
            <input type="hidden" {...register('foto')} />
            <input
              id="foto-file"
              type="file"
              accept="image/*"
              className="form-input"
              onChange={handleFotoFile}
            />
            <select
              id="foto-carpeta"
              className="form-select"
              value=""
              aria-label="…o elegir de la carpeta"
              style={{ marginTop: 'var(--space-xs)' }}
              onChange={handleFotoCarpeta}
            >
              <option value="">…o elegir de la carpeta</option>
              {archivos.map((archivo) => (
                <option key={archivo.nombre} value={archivo.url}>
                  {archivo.nombre}
                </option>
              ))}
            </select>
            {foto && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 'var(--space-sm)',
                  marginTop: 'var(--space-sm)',
                }}
              >
                <img
                  src={mediaUrl(foto)}
                  alt="Vista previa de la foto"
                  style={{
                    height: 140,
                    maxWidth: 240,
                    objectFit: 'contain',   /* foto completa, sin recortar */
                    background: 'var(--social-bg)',
                    borderRadius: 'var(--radius-md)',
                    padding: 'var(--space-xs)',
                  }}
                  onError={(e) => { e.currentTarget.style.display = 'none'; }}
                />
                <Button size="sm" variant="ghost" onClick={() => setValue('foto', '')}>
                  Quitar
                </Button>
              </div>
            )}
          </div>
          <div className="form-group" style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <input type="checkbox" id="activo" {...register('activo')} />
            <label htmlFor="activo">Activo</label>
          </div>
        </form>
      </Modal>

      <Modal
        open={!!toDelete}
        onClose={() => setToDelete(null)}
        title="Eliminar servicio"
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setToDelete(null)}>Cancelar</Button>
            <Button variant="danger" onClick={handleDelete}>Eliminar</Button>
          </>
        }
      >
        <p>
          ¿Eliminar <strong>{toDelete?.nombre}</strong>? Esta acción no se puede deshacer.
        </p>
      </Modal>
    </div>
  );
}
