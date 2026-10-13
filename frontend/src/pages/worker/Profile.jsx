import { useState, useEffect } from 'react';
import { workerApi, adminApi, mediaUrl } from '../../services/api';
import Avatar from '../../components/ui/Avatar';
import PageHeader from '../../components/ui/PageHeader';
import Button from '../../components/ui/Button';
import Spinner from '../../components/ui/Spinner';
import { Input, Textarea } from '../../components/ui/Input';
import { ErrorState } from '../../components/ui/EmptyState';
import { useToast } from '../../hooks/useToast';
import { LuCheck, LuPause } from 'react-icons/lu';

// Email obligatorio: la confirmación de la cita y las notificaciones llegan aquí
const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

/**
 * Worker profile page with photo and details, editable (name, email, photo).
 */
export default function WorkerProfile() {
  const toast = useToast();
  const [perfil, setPerfil] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // --- Edición del perfil ---
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({ nombre: '', email: '', foto: '', descripcion: '' });
  const [fieldErrors, setFieldErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const load = async () => {
    try {
      const data = await workerApi.getPerfil();
      setPerfil(data);
      setError(null);
      setLoading(false);
    } catch {
      setError('No se pudo cargar tu perfil.');
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- setState ocurre tras await (fetch), no sincrónicamente en el effect
    load();
  }, []);

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

  const startEditing = () => {
    setForm({
      nombre: perfil?.nombre || '',
      email: perfil?.email || '',
      foto: perfil?.foto || '',
      descripcion: perfil?.descripcion || '',
    });
    setFieldErrors({});
    setEditing(true);
  };

  const cancelEditing = () => {
    setEditing(false);
    setFieldErrors({});
  };

  /** Sube la foto al momento; su ruta se guarda junto al resto al guardar. */
  const handleFotoFile = async (event) => {
    const input = event.currentTarget;
    const file = input.files?.[0];
    if (!file) return;
    try {
      const subida = await adminApi.uploadMedia('trabajadores', file);
      if (!subida?.url) throw new Error('Respuesta de subida inválida');
      setField('foto', subida.url);
      toast.success('Foto subida');
    } catch (err) {
      toast.error(err?.response?.data?.message || 'No se pudo subir la foto');
    } finally {
      input.value = '';
    }
  };

  const handleSave = async (e) => {
    e.preventDefault();
    const nombre = form.nombre.trim();
    const email = form.email.trim();
    // Siempre se envía string (vacío = borra la descripción; el backend solo
    // conserva el valor cuando el campo viene `null`, que aquí nunca ocurre)
    const descripcion = form.descripcion.trim();
    const errs = {};
    if (!nombre) errs.nombre = 'Nombre requerido';
    if (!email) {
      errs.email = 'Email requerido';
    } else if (!EMAIL_PATTERN.test(email)) {
      errs.email = 'Formato de email inválido';
    }
    if (descripcion.length > 500) errs.descripcion = 'Máximo 500 caracteres';
    setFieldErrors(errs);
    if (Object.keys(errs).length > 0) return;

    setSaving(true);
    try {
      const actualizado = await workerApi.updatePerfil({
        nombre,
        email,
        foto: form.foto,
        descripcion,
      });
      // El backend devuelve el perfil actualizado; se refresca la vista
      setPerfil((prev) => ({
        ...prev,
        ...(actualizado || {}),
        nombre,
        email,
        foto: form.foto,
        descripcion,
      }));
      setEditing(false);
      setFieldErrors({});
      toast.success('Perfil actualizado');
    } catch (err) {
      // p. ej. email duplicado: el backend responde con `message` en español
      const msg = err?.response?.data?.message || 'No se pudo guardar el perfil';
      setFieldErrors({ submit: msg });
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="loading-page">
        <Spinner size="lg" />
        <p>Cargando perfil...</p>
      </div>
    );
  }

  if (error) {
    return <ErrorState message={error} onRetry={load} />;
  }

  return (
    <div>
      <PageHeader title="Mi perfil" />

      <div className="card" style={{ padding: 'var(--space-lg)' }}>
        <div className="profile-grid">
          <div className="profile-photo">
            <Avatar
              src={mediaUrl(editing ? form.foto : perfil?.foto)}
              name={(editing ? form.nombre : perfil?.nombre) || ''}
              size="xl"
            />
            <p style={{ color: 'var(--muted)', fontSize: 'var(--font-size-sm)' }}>
              {(editing ? form.nombre : perfil?.nombre) || ''}
            </p>
          </div>
          <div className="profile-details">
            {editing ? (
              <form onSubmit={handleSave} noValidate>
                {fieldErrors.submit && (
                  <p className="form-error" role="alert" style={{ textAlign: 'center' }}>
                    {fieldErrors.submit}
                  </p>
                )}

                <Input
                  id="perfil-nombre"
                  label="Nombre"
                  required
                  value={form.nombre}
                  onChange={(e) => setField('nombre', e.target.value)}
                  error={fieldErrors.nombre}
                />

                <Input
                  id="perfil-email"
                  label="Email"
                  type="email"
                  required
                  value={form.email}
                  onChange={(e) => setField('email', e.target.value)}
                  error={fieldErrors.email}
                />

                <Textarea
                  id="perfil-descripcion"
                  label="Descripción (opcional)"
                  rows={3}
                  maxLength={500}
                  placeholder="Especialidades, experiencia… (máx. 500 caracteres)"
                  value={form.descripcion}
                  onChange={(e) => setField('descripcion', e.target.value)}
                  error={fieldErrors.descripcion}
                />

                <div className="form-group">
                  <label className="form-label" htmlFor="perfil-foto">
                    Foto
                  </label>
                  <input
                    id="perfil-foto"
                    type="file"
                    accept="image/*"
                    className="form-input"
                    onChange={handleFotoFile}
                  />
                  {form.foto && (
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 'var(--space-sm)',
                        marginTop: 'var(--space-sm)',
                      }}
                    >
                      <img
                        src={mediaUrl(form.foto)}
                        alt="Vista previa de la foto"
                        style={{ width: 64, height: 64, objectFit: 'cover', borderRadius: 6 }}
                        onError={(e) => {
                          e.currentTarget.style.display = 'none';
                        }}
                      />
                      <Button size="sm" variant="ghost" onClick={() => setField('foto', '')}>
                        Quitar
                      </Button>
                    </div>
                  )}
                </div>

                <div style={{ display: 'flex', gap: 8, marginTop: 'var(--space-md)' }}>
                  <Button type="submit" variant="primary" loading={saving}>
                    {saving ? 'Guardando...' : 'Guardar'}
                  </Button>
                  <Button type="button" variant="ghost" onClick={cancelEditing} disabled={saving}>
                    Cancelar
                  </Button>
                </div>
              </form>
            ) : (
              <>
                <div className="profile-row">
                  <span className="label">Nombre</span>
                  <span>{perfil?.nombre}</span>
                </div>
                <div className="profile-row">
                  <span className="label">Email</span>
                  <span>{perfil?.email}</span>
                </div>
                <div className="profile-row">
                  <span className="label">Estado</span>
                  <span>
                    {perfil?.activo ? (
                      <>
                        <LuCheck aria-hidden="true" style={{ marginRight: 6 }} />
                        Activo
                      </>
                    ) : (
                      <>
                        <LuPause aria-hidden="true" style={{ marginRight: 6 }} />
                        Inactivo
                      </>
                    )}
                  </span>
                </div>
                {perfil?.descripcion && (
                  <div className="profile-row">
                    <span className="label">Descripción</span>
                    <span>{perfil.descripcion}</span>
                  </div>
                )}
                <div style={{ marginTop: 'var(--space-md)' }}>
                  <Button variant="secondary" onClick={startEditing}>
                    Editar perfil
                  </Button>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
