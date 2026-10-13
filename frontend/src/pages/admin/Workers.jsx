import { useState, useEffect, useCallback, useMemo } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { adminApi, mediaUrl } from '../../services/api';
import PageHeader from '../../components/ui/PageHeader';
import Table from '../../components/ui/Table';
import Modal from '../../components/ui/Modal';
import Button from '../../components/ui/Button';
import Badge from '../../components/ui/Badge';
import { Input, Label, Textarea } from '../../components/ui/Input';
import { useToast } from '../../hooks/useToast';

const PAGE_SIZE = 10;
const CARPETA = 'trabajadores';

/**
 * Admin workers page: create / edit / toggle / delete.
 * The backend returns the full list, so search and pagination are client-side.
 */
export default function AdminWorkers() {
  const toast = useToast();
  const [trabajadores, setTrabajadores] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const [modal, setModal] = useState(null); // {mode: 'create'|'edit', data}
  const [toDelete, setToDelete] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [archivos, setArchivos] = useState([]); // imágenes ya subidas a la carpeta

  const { register, handleSubmit, reset, setValue, control, formState: { errors } } = useForm();
  // useWatch en vez de watch(): watch() de react-hook-form impide que el
  // compilador de React analice el componente y desactiva otras reglas de hooks
  const foto = useWatch({ control, name: 'foto' });

  const load = useCallback(async () => {
    try {
      const data = await adminApi.getTrabajadores();
      setTrabajadores(data);
      setLoading(false);
    } catch {
      toast.error('No se pudieron cargar los trabajadores');
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

  // Client-side filter + pagination (backend has no search params)
  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return trabajadores;
    return trabajadores.filter(
      (t) =>
        t.nombre?.toLowerCase().includes(q) ||
        t.email?.toLowerCase().includes(q)
    );
  }, [trabajadores, search]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const pageRows = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  const openCreate = () => {
    reset({ nombre: '', email: '', password: '', activo: true, foto: '', descripcion: '' });
    setModal({ mode: 'create' });
  };

  const openEdit = (trab) => {
    reset({
      nombre: trab.nombre,
      email: trab.email,
      password: '',
      activo: trab.activo,
      foto: trab.foto || '',
      descripcion: trab.descripcion || '',
    });
    setModal({ mode: 'edit', data: trab });
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
      if (modal.mode === 'create') {
        await adminApi.createTrabajador({
          nombre: values.nombre,
          email: values.email,
          password: values.password,
          activo: !!values.activo,
          foto: values.foto || '',
          // vacío → null (no se almacena string vacío)
          descripcion: values.descripcion?.trim() || null,
        });
        toast.success('Trabajador creado');
      } else {
        await adminApi.updateTrabajador(modal.data.id, {
          id: modal.data.id,
          nombre: values.nombre,
          email: values.email,
          activo: !!values.activo,
          foto: values.foto || '',
          descripcion: values.descripcion?.trim() || null,
        });
        toast.success('Trabajador actualizado');
      }
      setModal(null);
      load();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Error al guardar');
    } finally {
      setSubmitting(false);
    }
  };

  const toggleActivo = async (trab) => {
    try {
      await adminApi.toggleTrabajadorActivo(trab);
      setTrabajadores((prev) =>
        prev.map((t) => (t.id === trab.id ? { ...t, activo: !t.activo } : t))
      );
      toast.success(trab.activo ? 'Desactivado' : 'Activado');
    } catch {
      toast.error('No se pudo cambiar el estado');
    }
  };

  const handleDelete = async () => {
    if (!toDelete) return;
    try {
      const res = await adminApi.deleteTrabajador(toDelete.id);
      setTrabajadores((prev) => prev.filter((t) => t.id !== toDelete.id));
      // El backend explica el resultado (p. ej. «El trabajador tiene reservas; se desactivó»)
      toast.success(res?.message || 'Trabajador eliminado');
      setToDelete(null);
    } catch (err) {
      toast.error(err?.response?.data?.message || 'No se pudo eliminar');
    }
  };

  const columns = [
    { key: 'nombre', label: 'Nombre' },
    { key: 'email', label: 'Email' },
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
              width: 40,
              height: 40,
              objectFit: 'cover',
              borderRadius: 6,
              background: 'var(--border)',
            }}
            onError={(e) => { e.currentTarget.style.display = 'none'; }}
          />
        ) : '—'
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
          <Button size="sm" variant="ghost" onClick={() => toggleActivo(row)}>
            {row.activo ? 'Desactivar' : 'Activar'}
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
        title="Trabajadores"
        actions={<Button onClick={openCreate}>+ Nuevo trabajador</Button>}
      />

      <div className="filters-bar">
        <input
          className="form-input"
          placeholder="Buscar por nombre o email..."
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(1); }}
          aria-label="Buscar trabajadores"
        />
      </div>

      <Table
        columns={columns}
        data={pageRows}
        loading={loading}
        emptyMessage="No hay trabajadores registrados."
        page={page}
        totalPages={totalPages}
        onPageChange={setPage}
      />

      {/* Create/Edit modal */}
      <Modal
        open={!!modal}
        onClose={() => setModal(null)}
        title={modal?.mode === 'create' ? 'Nuevo trabajador' : 'Editar trabajador'}
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
            <Label htmlFor="email" required>Email</Label>
            <Input
              id="email"
              type="email"
              error={errors.email?.message}
              {...register('email', {
                required: 'Email requerido',
                pattern: { value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: 'Email inválido' },
              })}
            />
          </div>
          <div className="form-group">
            <Label htmlFor="password" required={modal?.mode === 'create'}>
              Contraseña {modal?.mode === 'edit' && '(no se modifica aquí)'}
            </Label>
            <Input
              id="password"
              type="password"
              disabled={modal?.mode === 'edit'}
              error={errors.password?.message}
              {...register('password', {
                required: modal?.mode === 'create' ? 'Contraseña requerida' : false,
                minLength: { value: 8, message: 'Mínimo 8 caracteres' },
              })}
            />
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
                  style={{ width: 64, height: 64, objectFit: 'cover', borderRadius: 6 }}
                  onError={(e) => { e.currentTarget.style.display = 'none'; }}
                />
                <Button size="sm" variant="ghost" onClick={() => setValue('foto', '')}>
                  Quitar
                </Button>
              </div>
            )}
          </div>
          <Textarea
            id="descripcion"
            label="Descripción (opcional)"
            rows={3}
            maxLength={500}
            placeholder="Especialidades, experiencia… (máx. 500 caracteres)"
            error={errors.descripcion?.message}
            {...register('descripcion', {
              maxLength: { value: 500, message: 'Máximo 500 caracteres' },
            })}
          />
          <div className="form-group" style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <input type="checkbox" id="activo" {...register('activo')} />
            <label htmlFor="activo">Activo</label>
          </div>
        </form>
      </Modal>

      {/* Delete confirm */}
      <Modal
        open={!!toDelete}
        onClose={() => setToDelete(null)}
        title="Eliminar trabajador"
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setToDelete(null)}>Cancelar</Button>
            <Button variant="danger" onClick={handleDelete}>Eliminar</Button>
          </>
        }
      >
        <p>
          ¿Eliminar a <strong>{toDelete?.nombre}</strong>? Esta acción no se puede deshacer.
        </p>
      </Modal>
    </div>
  );
}
