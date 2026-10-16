import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams, Navigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useAuth } from '../../hooks/useAuth';
import Button from '../../components/ui/Button';
import { Input, Label } from '../../components/ui/Input';

// CAPTCHA Turnstile opcional (3.8): el widget SOLO se carga si el propietario
// define la clave pública por env var. Deshabilitado (default) → cero widget
// y cero llamadas a terceros. Requiere además que el backend active
// APP_SECURITY_TURNSTILE_ENABLED=true con su secret-key, y que la CSP de
// nginx permita challenges.cloudflare.com (ver deploy/nginx-barbershop.conf).
const TURNSTILE_SITE_KEY = import.meta.env.VITE_TURNSTILE_SITE_KEY || '';

/**
 * Login page with form validation and redirect preservation.
 */
export default function Login() {
  const { login, isAuthenticated, loading } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [serverError, setServerError] = useState(null);
  const [turnstileToken, setTurnstileToken] = useState(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({
    defaultValues: { email: '', password: '', rememberMe: false, website: '' },
  });

  // Turnstile (solo con clave pública definida): inyecta el script oficial
  // una vez; el widget renderizado llama a setTurnstileToken al completarse.
  useEffect(() => {
    if (!TURNSTILE_SITE_KEY || document.getElementById('cf-turnstile-script')) return undefined;
    const script = document.createElement('script');
    script.id = 'cf-turnstile-script';
    script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js';
    script.async = true;
    document.head.appendChild(script);
    return undefined;
  }, []);

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
      // `website` es el honeypot: un usuario real lo envía vacío (no lo ve
      // ni puede enfocarlo). Si está relleno, el backend responde 401
      // genérico sin verificar credenciales.
      const user = await login(values.email, values.password, values.rememberMe, {
        website: values.website,
        turnstileToken,
      });
      const dest = user?.rol === 'ADMIN' && redirectTo === '/trabajador' ? '/admin' : redirectTo;
      navigate(dest, { replace: true });
    } catch (err) {
      const status = err?.response?.status;
      const headers = err?.response?.headers;
      const retryAfter = Number(headers?.get?.('Retry-After') ?? headers?.['retry-after']);
      if (status === 429) {
        // Cuenta o rate limit bloqueado (429 + Retry-After): cuánto queda
        setServerError(
          Number.isFinite(retryAfter) && retryAfter > 0
            ? `Demasiados intentos. Inténtalo en ${retryAfter} s`
            : 'Demasiados intentos. Inténtalo más tarde'
        );
      } else if (status === 401) {
        setServerError('Credenciales inválidas');
      } else {
        // Resto de 4xx no-401 (y errores de red/servidor): mensaje genérico
        // sin lanzar excepción, para no romper el formulario ni la navegación
        setServerError('No se pudo iniciar sesión. Inténtalo de nuevo');
      }
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

        {/*
          Campo honeypot anti-bot (3.1/3.3): existe en el DOM pero NO es
          visible ni enfocable — off-screen con CSS (posición absoluta a la
          izquierda; NO display:none porque algunos bots lo detectan),
          aria-hidden, tabIndex=-1 y autocomplete=off. Un usuario real lo
          envía vacío; si un bot lo rellena el backend responde 401
          INVALID_CREDENTIALS genérico sin verificar credenciales.
        */}
        <div
          aria-hidden="true"
          style={{ position: 'absolute', left: '-5000px', top: 'auto', width: '1px', height: '1px', overflow: 'hidden' }}
        >
          <label htmlFor="website">Website</label>
          <input
            id="website"
            type="text"
            name="website"
            tabIndex={-1}
            autoComplete="off"
            {...register('website')}
          />
        </div>

        {/*
          Turnstile (3.8): SOLO se renderiza si hay clave pública definida.
          Sin clave → cero widget y cero llamadas a terceros.
        */}
        {TURNSTILE_SITE_KEY && (
          <div
            className="cf-turnstile"
            data-sitekey={TURNSTILE_SITE_KEY}
            data-callback={(token) => setTurnstileToken(token)}
            data-expired-callback={() => setTurnstileToken(null)}
          />
        )}

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
