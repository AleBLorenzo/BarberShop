import { Link } from 'react-router-dom';
import { NOMBRE, EMAIL_CONTACTO, TELEFONO, DIRECCION } from '../../config/business';

/** Política de privacidad ajustada al tratamiento real de datos de la app. */
export default function Privacidad() {
  return (
    <article style={{ maxWidth: 760, margin: '0 auto' }}>
      <h1>Política de privacidad</h1>
      <p>
        <b>Responsable del tratamiento:</b> {NOMBRE} — {DIRECCION}.
        Contacto: {EMAIL_CONTACTO} · {TELEFONO}.
      </p>

      <h2>Qué datos tratamos</h2>
      <p>Para gestionar tu cita tratamos únicamente los datos que nos facilitas al reservar:</p>
      <ul>
        <li><b>Nombre</b> (obligatorio).</li>
        <li><b>Teléfono</b> (obligatorio): identifica la reserva y permite cancelar o reprogramar.</li>
        <li><b>Email</b> (opcional): solo se usa para enviarte la confirmación de la cita y,
          si la cita se completa, una invitación a dejarnos una reseña. Si no lo indicas,
          la reserva se crea igualmente.</li>
      </ul>
      <p>
        No tratamos datos de tarjetas de pago, ni usamos perfiles de navegación,
        analítica ni publicidad.
      </p>
      <p>
        Para mostrar la tipografía, el sitio carga la fuente desde <b>Google Fonts</b>{' '}
        (<code>fonts.googleapis.com</code> / <code>fonts.gstatic.com</code>). Al solicitarse,
        se transmiten a Google datos técnicos de tu conexión (tu <b>dirección IP</b> y
        cabeceras del navegador como <code>User-Agent</code> o <code>Referer</code>). Esa
        carga <b>no</b> establece cookies ni se usa para analítica o publicidad; el detalle
        está en la <Link to="/legal/cookies">política de cookies</Link>.
      </p>

      <h2>Finalidad y base jurídica</h2>
      <p>
        Los datos se tratan para <b>prestar el servicio de reserva de citas</b> que nos
        solicitas (ejecución de contrato) y, en su caso, enviarte comunicaciones
        relacionadas con esa misma cita (interés legítimo). Nunca se ceden a terceros
        ni se usan con fines comerciales.
      </p>

      <h2>Conservación</h2>
      <p>
        Los datos se mantienen mientras la relación con la cita siga vigente y los
        plazos legales de responsabilidad aplicables. Puedes solicitar su supresión
        en cualquier momento.
      </p>

      <h2>Tus derechos (RGPD)</h2>
      <p>
        Puedes ejercer tus derechos de <b>acceso, rectificación, supresión,
        oposición, limitación y portabilidad</b> escribiendo a {EMAIL_CONTACTO} o
        llamando al {TELEFONO}, indicando «Protección de datos» en el asunto.
        Si consideras que el tratamiento no se ajusta a la normativa, puedes
        reclamar ante la Agencia Española de Protección de Datos (www.aepd.es).
      </p>

      <h2>Menores</h2>
      <p>El servicio de reservas no está dirigido a menores de 14 años.</p>
    </article>
  );
}
