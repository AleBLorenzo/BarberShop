import { useState } from 'react';
import { useNavigate, Navigate } from 'react-router-dom';
import { useForm, useWatch } from 'react-hook-form';
import { publicApi } from '../../services/api';
import { useBooking } from '../../hooks/useBooking';
import { formatDate, formatTime } from '../../utils/date';
import Button from '../../components/ui/Button';
import PriceWithOffer from '../../components/ui/PriceWithOffer';
import { Input, Label } from '../../components/ui/Input';

// Spanish phone: +34 prefix optional, 9 or 6/7 first digit, 9 digits total
const PHONE_PATTERN = /^(?:\+34\s?)?[679]\d{2}[\s-]?\d{3}[\s-]?\d{3}$/;
// Email OPCIONAL del cliente (spec booking-contact-email-optional): se valida
// SOLO cuando el campo trae contenido. NO reintroducir `required` ni un guard
// que aborte el envío por email vacío — sin email la reserva se crea igual y
// no se envía correo (el token de cancelación es la vía de recuperación).
const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

/**
 * Booking step 4: summary + client details form + submit.
 */
export default function BookingConfirm() {
  const { draft, updateDraft } = useBooking();
  const navigate = useNavigate();
  const [serverError, setServerError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm({
    defaultValues: {
      clienteNombre: '',
      clienteTelefono: '',
      clienteEmail: draft.clienteEmail || '',
    },
  });

  // useWatch en vez de watch(): watch() de react-hook-form impide que el
  // compilador de React analice el componente y desactiva otras reglas de hooks.
  // Email tecleado, para el copy condicional honesto (spec booking-contact-email-optional, D6)
  const clienteEmailValue = useWatch({ control, name: 'clienteEmail' });

  if (!draft.servicio || !draft.fecha || !draft.slot) {
    return <Navigate to="/reservar/servicio" replace />;
  }

  // Backend requires a concrete worker: the chosen one, or the owner of the slot
  const trabajadorId = draft.trabajador?.id || draft.slot.trabajadorId;

  const onSubmit = async (values) => {
    if (!trabajadorId) {
      setServerError('Selecciona un trabajador disponible para continuar.');
      return;
    }
    setServerError(null);
    setSubmitting(true);
    const clienteEmail = values.clienteEmail?.trim() || '';
    // Email OPCIONAL (spec booking-contact-email-optional, D1/D2): sin contenido
    // NO se bloquea el envío. Con contenido, el formato lo valida react-hook-form
    // (register.validate) y, como defensa, este guard condicional.
    if (clienteEmail && !EMAIL_PATTERN.test(clienteEmail)) {
      setServerError('Introduce un email válido para recibir la confirmación.');
      setSubmitting(false);
      return;
    }
    // Persist in draft so going back/forward keeps it
    updateDraft({ clienteEmail });
    try {
      const payload = {
        servicioId: draft.servicio.id,
        trabajadorId,
        fecha: draft.fecha,
        horaInicio: draft.slot.horaInicio,
        clienteNombre: values.clienteNombre,
        clienteTelefono: values.clienteTelefono.replace(/\s/g, ''),
        // Ausencia explícita: sin email se envía null (no cadena vacía)
        clienteEmail: clienteEmail || null,
      };
      const reserva = await publicApi.createReserva(payload);
      const successData = {
        ...reserva,
        fecha: draft.fecha,
        slot: draft.slot,
        servicio: draft.servicio,
        // null = el cliente no dejó email (BookingSuccess avisa de que no habrá
        // correo); string = sí dejó email. undefined (p. ej. al reprogramar) =
        // desconocido → sin aviso (spec booking-without-email-degradation).
        clienteEmail: clienteEmail || null,
      };
      // Navigate FIRST with replace: clearing the draft here would trigger this
      // page's guard (redirect to /reservar/servicio) before the success screen
      // mounts. BookingSuccess clears the draft once the receipt is on screen.
      navigate('/reservar/exito', { state: successData, replace: true });
    } catch (err) {
      const status = err?.response?.status;
      const msg = err?.response?.data?.message || err?.message || '';
      if (status === 409 || /disponible|no longer|SlotNotAvailable/i.test(msg)) {
        // Race condition: slot taken → return to step 3 with error
        navigate('/reservar/fecha-hora', {
          state: { error: 'Este hueco ya no está disponible, por favor elige otro' },
        });
        return;
      }
      setServerError(msg || 'No se pudo crear la reserva. Revisa los datos.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <div className="booking-summary">
        <div className="summary-row">
          <span className="label">Servicio</span>
          <span className="value">{draft.servicio.nombre}</span>
        </div>
        <div className="summary-row">
          <span className="label">Trabajador</span>
          <span className="value">{draft.trabajador?.nombre || draft.slot.trabajadorNombre || 'Cualquiera'}</span>
        </div>
        <div className="summary-row">
          <span className="label">Fecha</span>
          <span className="value">{formatDate(draft.fecha, 'EEEE dd/MM/yyyy')}</span>
        </div>
        <div className="summary-row">
          <span className="label">Hora</span>
          <span className="value">
            {formatTime(draft.slot.horaInicio)} - {formatTime(draft.slot.horaFin)}
          </span>
        </div>
        <div className="summary-row">
          <span className="label">Precio</span>
          <span className="value">
            <PriceWithOffer
              recalcular
              precio={draft.servicio.precio}
              precioOferta={draft.servicio.precioOferta}
              ofertaActiva={draft.servicio.ofertaActiva}
              descuentoPorcentaje={draft.servicio.descuentoPorcentaje}
              ofertaInicio={draft.servicio.ofertaInicio}
              ofertaFin={draft.servicio.ofertaFin}
            />
          </span>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className="form-group">
          <Label htmlFor="clienteNombre" required>Nombre</Label>
          <Input
            id="clienteNombre"
            placeholder="Tu nombre"
            error={errors.clienteNombre?.message}
            {...register('clienteNombre', {
              required: 'Nombre requerido',
              minLength: { value: 2, message: 'Mínimo 2 caracteres' },
            })}
          />
        </div>

        <div className="form-group">
          <Label htmlFor="clienteTelefono" required>Teléfono</Label>
          <Input
            id="clienteTelefono"
            type="tel"
            placeholder="612 345 678"
            error={errors.clienteTelefono?.message}
            {...register('clienteTelefono', {
              required: 'Teléfono requerido',
              pattern: {
                value: PHONE_PATTERN,
                message: 'Formato español inválido (ej. 612 345 678)',
              },
            })}
          />
        </div>

        <div className="form-group">
          <Label htmlFor="clienteEmail">Email (opcional)</Label>
          <Input
            id="clienteEmail"
            type="email"
            placeholder="tu@email.com"
            error={errors.clienteEmail?.message}
            {...register('clienteEmail', {
              // Email opcional: valida formato SOLO si el campo trae contenido
              validate: (value) =>
                !value?.trim() ||
                EMAIL_PATTERN.test(value.trim()) ||
                'Formato de email inválido',
            })}
          />
          <small style={{ color: 'var(--muted)' }}>
            {clienteEmailValue?.trim()
              ? 'Te enviaremos la confirmación y el recordatorio por correo.'
              : 'Sin email no recibirás la confirmación por correo. Guarda el token de cancelación para gestionar tu cita.'}
          </small>
        </div>

        {serverError && (
          <p className="form-error" role="alert" style={{ textAlign: 'center' }}>
            {serverError}
          </p>
        )}

        <div className="booking-actions">
          <Button variant="ghost" onClick={() => navigate('/reservar/fecha-hora')} type="button">
            Atrás
          </Button>
          <Button type="submit" variant="primary" loading={submitting}>
            {submitting ? 'Reservando...' : 'Confirmar reserva'}
          </Button>
        </div>
      </form>
    </div>
  );
}
