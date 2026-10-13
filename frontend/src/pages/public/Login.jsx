import { useState } from 'react';
import { useNavigate, useSearchParams, Navigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useAuth } from '../../hooks/useAuth';
import Button from '../../components/ui/Button';
import { Input, Label } from '../../components/ui/Input';

/**
 * Login page with form validation and redirect preservation.
 */
export default function Login() {
  const { login, isAuthenticated, loading } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [serverError, setServerError] = useState(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({
    defaultValues: { email: '', password: '', rememberMe: false },
  });

  // Solo rutas internas: evita open redirect (?redirect=//evil.com o https://…)
  const rawRedirect = searchParams.get('redirect') || '/trabajador';
  const redirectTo = rawRedirect.startsWith('/') && !rawRedirect.startsWith('//')
    ? rawRedirect
    : '/trabajador';

  if (!loading && isAuthenticated) {
    return <Navigate to={redirectTo} replace />;
  }

  const onSubmit = async (values) => {
    setServerError(null);
    try {
      const user = await login(values.email, values.password, values.rememberMe);
      const dest = user?.rol === 'ADMIN' && redirectTo === '/trabajador' ? '/admin' : redirectTo;
      navigate(dest, { replace: true });
    } catch {
      setServerError('Credenciales inválidas');
    }
  };

  return (
    <div className="login-page">
      <form
        className="login-card"
        onSubmit={handleSubmit(onSubmit)}
        noValidate
        aria-label="Formulario de inicio de sesión"
      >
        <h2>Iniciar sesión</h2>
        <p style={{ color: 'var(--muted)', marginTop: 0 }}>
          Accede al panel de trabajadores
        </p>

        <div className="form-group">
          <Label htmlFor="email" required>Correo electrónico</Label>
          <Input
            id="email"
            type="email"
            placeholder="tu@email.com"
            error={errors.email?.message}
            {...register('email', {
              required: 'Email requerido',
              pattern: {
                value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                message: 'Formato de email inválido',
              },
            })}
          />
        </div>

        <div className="form-group">
          <Label htmlFor="password" required>Contraseña</Label>
          <Input
            id="password"
            type="password"
            placeholder="••••••••"
            error={errors.password?.message}
            {...register('password', {
              required: 'Contraseña requerida',
              minLength: { value: 4, message: 'Mínimo 4 caracteres' },
            })}
          />
        </div>

        <div className="form-group" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <input type="checkbox" id="rememberMe" {...register('rememberMe')} />
          <label htmlFor="rememberMe" style={{ fontSize: 'var(--font-size-sm)', color: 'var(--muted)' }}>
            Recordarme
          </label>
        </div>

        {serverError && (
          <p className="form-error" role="alert" style={{ textAlign: 'center' }}>
            {serverError}
          </p>
        )}

        <Button type="submit" variant="primary" size="lg" loading={isSubmitting} style={{ width: '100%' }}>
          {isSubmitting ? 'Iniciando sesión...' : 'Iniciar sesión'}
        </Button>
      </form>
    </div>
  );
}
