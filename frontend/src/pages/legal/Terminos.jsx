import { Link } from 'react-router-dom';
import { HORARIO_TEXTO, NOMBRE } from '../../config/business';

/** Términos de uso del servicio de reservas, coherentes con el comportamiento real. */
export default function Terminos() {
  return (
    <article style={{ maxWidth: 760, margin: '0 auto' }}>
      <h1>Términos y condiciones de uso</h1>
      <p>
        Estas condiciones regulan el uso del servicio de reservas en línea de {NOMBRE}.
        Al reservar una cita declaras haberlas leído y aceptado.
      </p>

      <h2>Reservas</h2>
      <ul>
        <li>Las citas se reservan sujetas a <b>disponibilidad en tiempo real</b>: si un
          hueco aparece ocupado, ya está reservado.</li>
        <li>Horario de atención: <b>{HORARIO_TEXTO}</b>. No se ofrecen horas pasadas ni
          citas que terminen después del cierre (la última empieza a la hora de cierre
          menos la duración del servicio).</li>
        <li>Solo se aceptan reservas con hasta <b>60 días de antelación</b> como máximo.</li>
        <li>Es obligatorio indicar <b>nombre y teléfono</b>; el email es opcional y se usa
          para enviarte la confirmación.</li>
      </ul>

      <h2>Confirmación y token</h2>
      <p>
        Al crear una reserva recibes un <b>token de cancelación</b> en pantalla (y por
        email, si lo facilitaste). Ese token es la única forma de cancelar o reprogramar
        la cita desde «Mis citas», así que guárdalo: quien lo posee, puede gestionar la
        reserva. Si lo pierdes, contáctanos por teléfono.
      </p>

      <h2>Cancelaciones, reprogramaciones y ausencias</h2>
      <ul>
        <li>Puedes cancelar o reprogramar tu cita en cualquier momento antes de la hora
          señalada, usando el token.</li>
        <li>Una reserva cancelada libera el hueco para otra persona.</li>
        <li>Si no acudes sin avisar, el establecimiento podrá marcar la cita como
          <b> no presentado</b>.</li>
      </ul>

      <h2>Servicios y precios</h2>
      <p>
        Los servicios, duraciones y precios mostrados en la web son los vigentes; pueden
        actualizarse sin previo aviso. El pago se realiza en el establecimiento tras el
        servicio.
      </p>

      <h2>Responsabilidad</h2>
      <p>
        {NOMBRE} no se responsabiliza de retrasos derivados de citas anteriores que se
        alarguen, ni del uso indebido del token por parte del cliente.
      </p>

      <h2>Datos personales</h2>
      <p>
        El tratamiento de tus datos se describe en nuestra{' '}
        <Link to="/legal/privacidad">política de privacidad</Link>; el uso del
        almacenamiento del navegador, en la <Link to="/legal/cookies">política de cookies</Link>.
      </p>
    </article>
  );
}
