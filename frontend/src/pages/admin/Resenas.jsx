import { useState, useEffect, useCallback } from 'react';
import { useForm } from 'react-hook-form';
import { adminApi } from '../../services/api';
import PageHeader from '../../components/ui/PageHeader';
import Table from '../../components/ui/Table';
import Modal from '../../components/ui/Modal';
import Button from '../../components/ui/Button';
import { Input, Label, Select } from '../../components/ui/Input';
import { useToast } from '../../hooks/useToast';
import { LuStar } from 'react-icons/lu';

const PAGE_SIZE = 10;

/**
 * Estrellas compactas para la columna de puntuación (1–5, monocromas).
 */
function Estrellas({ puntuacion }) {
  const llenas = Math.round(puntuacion);
  return (
    <span
      aria-label={`${llenas} de 5 estrellas`}
      style={{ color: 'var(--text)', display: 'inline-flex', gap: 2 }}
    >
      {Array.from({ length: 5 }, (_, i) => (
        <LuStar key={i} aria-hidden="true" fill={i < llenas ? 'currentColor' : 'none'} />
      ))}
    </span>
  );
}

/**
 * Admin reviews page (CRUD). Reviews are shown on the landing page.
 * Backend returns the full list: pagination is client-side.
 */
export default function AdminResenas() {
  const toast = useToast();
  const [resenas, setResenas] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [modal, setModal] = useState(null);
  const [toDelete, setToDelete] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const { register, handleSubmit, reset, formState: { errors } } = useForm();

  const load = useCallback(async () => {
    try {
      const data = await adminApi.getResenas();
      setResenas(data);
      setLoading(false);
    } catch {
      toast.error('No se pudieron cargar las reseñas');
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- toast se recrea cada render
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    load();
  }, [load]);

  const totalPages = Math.max(1, Math.ceil(resenas.length / PAGE_SIZE));
  const pageRows = resenas.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  const openCreate = () => {
    reset({ autor: '', puntuacion: 5, texto: '' });
    setModal({ mode: 'create' });
  };

  const openEdit = (rev) => {
    reset({ autor: rev.autor, puntuacion: rev.puntuacion, texto: rev.texto });
    setModal({ mode: 'edit', data: rev });
  };

  const onSubmit = async (values) => {
    setSubmitting(true);
    try {
      const payload = {
        autor: values.autor,
        puntuacion: Number(values.puntuacion),
        texto: values.texto,
      };
      if (modal.mode === 'create') {
        await adminApi.createResena(payload);
        toast.success('Reseña creada');
      } else {
        await adminApi.updateResena(modal.data.id, payload);
        toast.success('Reseña actualizada');
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
      await adminApi.deleteResena(toDelete.id);
      setResenas((prev) => prev.filter((r) => r.id !== toDelete.id));
      toast.success('Reseña eliminada');
      setToDelete(null);
    } catch (err) {
      toast.error(err?.response?.data?.message || 'No se pudo eliminar');
    }
  };

  const columns = [
    { key: 'autor', label: 'Autor' },
    { key: 'puntuacion', label: 'Puntuación', render: (row) => <Estrellas puntuacion={row.puntuacion} /> },
    {
      key: 'texto',
      label: 'Reseña',
      render: (row) => (
        <span style={{ display: 'block', maxWidth: 360 }}>
          {row.texto.length > 110 ? `${row.texto.slice(0, 110)}…` : row.texto}
        </span>
      ),
    },
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
        title="Reseñas"
        actions={<Button onClick={openCreate}>+ Nueva reseña</Button>}
      />

      <Table
        columns={columns}
        data={pageRows}
        loading={loading}
        emptyMessage="No hay reseñas todavía. Aparecerán en la portada al crearlas."
        page={page}
        totalPages={totalPages}
        onPageChange={setPage}
      />

      <Modal
        open={!!modal}
        onClose={() => setModal(null)}
        title={modal?.mode === 'create' ? 'Nueva reseña' : 'Editar reseña'}
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
            <Label htmlFor="autor" required>Autor</Label>
            <Input
              id="autor"
              error={errors.autor?.message}
              {...register('autor', {
                required: 'Autor requerido',
                maxLength: { value: 100, message: 'Máximo 100 caracteres' },
              })}
            />
          </div>
          <Select
            label="Puntuación"
            id="puntuacion"
            required
            error={errors.puntuacion?.message}
            {...register('puntuacion', { required: 'Puntuación requerida' })}
          >
            {[5, 4, 3, 2, 1].map((n) => (
              // <option> solo admite texto (navegadores descartan <svg> dentro),
              // así que aquí se mantiene el glifo ★ como label del select.
              <option key={n} value={n}>{n} {'★'.repeat(n)}</option>
            ))}
          </Select>
          <div className="form-group">
            <Label htmlFor="texto" required>Reseña</Label>
            <textarea
              id="texto"
              rows={4}
              className={`form-input ${errors.texto ? 'error' : ''}`}
              aria-invalid={errors.texto ? 'true' : undefined}
              {...register('texto', {
                required: 'Texto requerido',
                maxLength: { value: 1000, message: 'Máximo 1000 caracteres' },
              })}
            />
            {errors.texto && <p className="form-error" role="alert">{errors.texto.message}</p>}
          </div>
        </form>
      </Modal>

      <Modal
        open={!!toDelete}
        onClose={() => setToDelete(null)}
        title="Eliminar reseña"
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setToDelete(null)}>Cancelar</Button>
            <Button variant="danger" onClick={handleDelete}>Eliminar</Button>
          </>
        }
      >
        <p>
          ¿Eliminar la reseña de <strong>{toDelete?.autor}</strong>? Esta acción no se puede deshacer.
        </p>
      </Modal>
    </div>
  );
}
