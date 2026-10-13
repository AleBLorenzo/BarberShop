import { useState, useEffect, useCallback } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { adminApi, mediaUrl } from '../../services/api';
import PageHeader from '../../components/ui/PageHeader';
import Table from '../../components/ui/Table';
import Modal from '../../components/ui/Modal';
import Button from '../../components/ui/Button';
import { Input, Label } from '../../components/ui/Input';
import { useToast } from '../../hooks/useToast';

const PAGE_SIZE = 8;
const CARPETA = 'resultados';
const URL_PATTERN = /^https?:\/\/.+|^\/uploads\/.+$/;
const URL_ERROR = 'Debe empezar por http://, https:// o /uploads/';

/**
 * Admin results-gallery page (CRUD by image URL). Photos appear on the
 * landing page. Backend returns the full list: pagination is client-side.
 */
export default function AdminResultados() {
  const toast = useToast();
  const [fotos, setFotos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [modal, setModal] = useState(null);
  const [toDelete, setToDelete] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [archivos, setArchivos] = useState([]); // imágenes ya subidas a la carpeta

  const { register, handleSubmit, reset, setValue, control, formState: { errors } } = useForm();
  // useWatch en vez de watch(): watch() de react-hook-form impide que el
  // compilador de React analice el componente y desactiva otras reglas de hooks
  const url = useWatch({ control, name: 'url' });

  const load = useCallback(async () => {
    try {
      const data = await adminApi.getResultados();
      setFotos(data);
      setLoading(false);
    } catch {
      toast.error('No se pudieron cargar las fotos');
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

  const totalPages = Math.max(1, Math.ceil(fotos.length / PAGE_SIZE));
  const pageRows = fotos.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  const openCreate = () => {
    reset({ url: '', titulo: '' });
    setModal({ mode: 'create' });
  };

  const openEdit = (foto) => {
    reset({ url: foto.url, titulo: foto.titulo || '' });
    setModal({ mode: 'edit', data: foto });
  };

  /** Sube la imagen y rellena el campo `url` con su ruta `/uploads/...`. */
  const handleUrlFile = async (event) => {
    const input = event.currentTarget;
    const file = input.files?.[0];
    if (!file) return;
    try {
      const subida = await adminApi.uploadMedia(CARPETA, file);
      if (!subida?.url) throw new Error('Respuesta de subida inválida');
      setValue('url', subida.url, { shouldValidate: true });
      setArchivos((prev) => [...prev.filter((a) => a.nombre !== subida.nombre), subida]);
      toast.success('Foto subida');
    } catch {
      toast.error('No se pudo subir la foto');
    } finally {
      input.value = '';
    }
  };

  /** Rellena el campo `url` con una imagen ya subida a la carpeta. */
  const handleUrlCarpeta = (event) => {
    if (event.target.value) setValue('url', event.target.value, { shouldValidate: true });
  };

  const onSubmit = async (values) => {
    setSubmitting(true);
    try {
      const payload = {
        url: values.url.trim(),
        titulo: values.titulo?.trim() ? values.titulo.trim() : null,
      };
      if (modal.mode === 'create') {
        await adminApi.createResultado(payload);
        toast.success('Foto añadida');
      } else {
        await adminApi.updateResultado(modal.data.id, payload);
        toast.success('Foto actualizada');
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
      await adminApi.deleteResultado(toDelete.id);
      setFotos((prev) => prev.filter((f) => f.id !== toDelete.id));
      toast.success('Foto eliminada');
      setToDelete(null);
    } catch (err) {
      toast.error(err?.response?.data?.message || 'No se pudo eliminar');
    }
  };

  const columns = [
    {
      key: 'miniatura',
      label: 'Foto',
      render: (row) => (
        <img
          src={mediaUrl(row.url)}
          alt={row.titulo || 'Trabajo'}
          loading="lazy"
          style={{ width: 56, height: 56, objectFit: 'cover', borderRadius: 6, background: 'var(--border)' }}
          onError={(e) => { e.currentTarget.style.visibility = 'hidden'; }}
        />
      ),
    },
    { key: 'titulo', label: 'Título', render: (row) => row.titulo || '—' },
    {
      key: 'createdAt',
      label: 'Fecha',
      render: (row) => (row.createdAt ? new Date(row.createdAt).toLocaleDateString('es-ES') : '—'),
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
        title="Resultados"
        actions={<Button onClick={openCreate}>+ Nueva foto</Button>}
      />

      <Table
        columns={columns}
        data={pageRows}
        loading={loading}
        emptyMessage="No hay fotos. Añade la URL de un trabajo para mostrarla en la portada."
        page={page}
        totalPages={totalPages}
        onPageChange={setPage}
      />

      <Modal
        open={!!modal}
        onClose={() => setModal(null)}
        title={modal?.mode === 'create' ? 'Nueva foto' : 'Editar foto'}
        footer={
          <>
            <Button variant="ghost" onClick={() => setModal(null)}>Cancelar</Button>
            <Button variant="primary" loading={submitting} onClick={handleSubmit(onSubmit)}>
              {modal?.mode === 'create' ? 'Añadir' : 'Guardar'}
            </Button>
          </>
        }
      >
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <div className="form-group">
            <Label htmlFor="url" required>URL de la imagen</Label>
            <Input
              id="url"
              placeholder="https://…/foto.jpg"
              error={errors.url?.message}
              {...register('url', {
                required: 'URL requerida',
                pattern: { value: URL_PATTERN, message: URL_ERROR },
                maxLength: { value: 500, message: 'Máximo 500 caracteres' },
              })}
            />
          </div>
          <div className="form-group">
            <Label htmlFor="url-file">…o subir una foto</Label>
            <input
              id="url-file"
              type="file"
              accept="image/*"
              className="form-input"
              onChange={handleUrlFile}
            />
            <select
              id="url-carpeta"
              className="form-select"
              value=""
              aria-label="…o elegir de la carpeta"
              style={{ marginTop: 'var(--space-xs)' }}
              onChange={handleUrlCarpeta}
            >
              <option value="">…o elegir de la carpeta</option>
              {archivos.map((archivo) => (
                <option key={archivo.nombre} value={archivo.url}>
                  {archivo.nombre}
                </option>
              ))}
            </select>
            {url && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 'var(--space-sm)',
                  marginTop: 'var(--space-sm)',
                }}
              >
                <img
                  src={mediaUrl(url)}
                  alt="Vista previa de la foto"
                  style={{ width: 64, height: 64, objectFit: 'cover', borderRadius: 6 }}
                  onError={(e) => { e.currentTarget.style.display = 'none'; }}
                />
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setValue('url', '', { shouldValidate: true })}
                >
                  Quitar
                </Button>
              </div>
            )}
          </div>
          <div className="form-group">
            <Label htmlFor="titulo">Título (opcional)</Label>
            <Input
              id="titulo"
              placeholder="Corte de pelo"
              error={errors.titulo?.message}
              {...register('titulo', {
                maxLength: { value: 100, message: 'Máximo 100 caracteres' },
              })}
            />
          </div>
          <p style={{ fontSize: 'var(--font-size-xs)', color: 'var(--muted)', margin: 0 }}>
            Se muestra en su color original en la portada. Si la imagen no carga,
            se reserva el hueco sin romper la cuadrícula.
          </p>
        </form>
      </Modal>

      <Modal
        open={!!toDelete}
        onClose={() => setToDelete(null)}
        title="Eliminar foto"
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setToDelete(null)}>Cancelar</Button>
            <Button variant="danger" onClick={handleDelete}>Eliminar</Button>
          </>
        }
      >
        <p>
          ¿Eliminar la foto <strong>{toDelete?.titulo || '(sin título)'}</strong>?
          Esta acción no se puede deshacer.
        </p>
      </Modal>
    </div>
  );
}
