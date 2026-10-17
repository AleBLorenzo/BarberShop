/** Política de cookies: refleja exactamente lo que la app guarda en el navegador. */
export default function Cookies() {
  return (
    <article style={{ maxWidth: 760, margin: '0 auto' }}>
      <h1>Política de cookies</h1>
      <p>
        <b>Esta web no usa cookies propias</b>. Cuando decimos «no usar cookies» nos
        referimos a que el servidor <b>nunca emite la cabecera{' '}
        <code>Set-Cookie</code></b>: el backend es <i>stateless</i> (no guarda la sesión en
        el servidor) y la autenticación viaja en el encabezado{' '}
        <code>Authorization: Bearer</code> con un token JWT. Tampoco usamos cookies
        analíticas ni publicitarias. Eso es distinto del{' '}
        <b>almacenamiento local del navegador</b>, que la app sí utiliza para funcionar (ver
        la tabla siguiente).
      </p>
      <p>
        Como no hay cookies propias no esenciales, ni analítica ni publicidad, y ningún
        recurso de terceros instala cookies por el simple hecho de visitar la web,{' '}
        <b>no se requiere un consentimiento previo</b> (LSSI-CE, art. 22.2) ni se muestra
        ningún banner de consentimiento: no hay nada que consentir antes de que lo pidas
        (el mapa de Google solo se carga si pulsas «Ver mapa»).
      </p>

      <h2>Almacenamiento que utilizamos</h2>
      <div className="table-wrapper">
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ textAlign: 'left', borderBottom: '1px solid var(--border)' }}>
            <th style={{ padding: '8px 4px' }}>Clave</th>
            <th style={{ padding: '8px 4px' }}>Tipo</th>
            <th style={{ padding: '8px 4px' }}>Finalidad</th>
            <th style={{ padding: '8px 4px' }}>Duración</th>
            <th style={{ padding: '8px 4px' }}>Esencial</th>
          </tr>
        </thead>
        <tbody>
          <tr style={{ borderBottom: '1px solid var(--border)' }}>
            <td style={{ padding: '8px 4px' }}><code>refreshToken</code></td>
            <td style={{ padding: '8px 4px' }}>localStorage</td>
            <td style={{ padding: '8px 4px' }}>Mantener la sesión iniciada en el panel (token
              JWT de refresco).</td>
            <td style={{ padding: '8px 4px' }}>Persistente hasta cerrar sesión o borrar los
              datos del navegador.</td>
            <td style={{ padding: '8px 4px' }}>Sí (solo si inicias sesión).</td>
          </tr>
          <tr style={{ borderBottom: '1px solid var(--border)' }}>
            <td style={{ padding: '8px 4px' }}><code>bookingDraft</code></td>
            <td style={{ padding: '8px 4px' }}>sessionStorage</td>
            <td style={{ padding: '8px 4px' }}>Conservar el borrador de la reserva mientras
              completas los pasos.</td>
            <td style={{ padding: '8px 4px' }}>Hasta cerrar la pestaña.</td>
            <td style={{ padding: '8px 4px' }}>Sí (para el flujo de reserva).</td>
          </tr>
          <tr>
            <td style={{ padding: '8px 4px' }}><code>lastReserva</code></td>
            <td style={{ padding: '8px 4px' }}>sessionStorage</td>
            <td style={{ padding: '8px 4px' }}>Mostrar el resumen de la última cita creada en
              la pantalla de éxito.</td>
            <td style={{ padding: '8px 4px' }}>Hasta cerrar la pestaña.</td>
            <td style={{ padding: '8px 4px' }}>No (funcional/UX).</td>
          </tr>
        </tbody>
        </table>
      </div>

      <h2>Cómo eliminarlos</h2>
      <p>
        Puedes borrarlos en cualquier momento desde la configuración de tu navegador
        («Borrar datos de navegación» o los sitios con datos guardados). Al hacerlo se
        cerrará tu sesión y se perderán los borradores de reserva; no se pierde ninguna
        cita, que sigue registrada en el sistema.
      </p>

      <h2>Recursos de terceros</h2>
      <p>
        <b>Google Fonts</b> (<code>fonts.googleapis.com</code>,{' '}
        <code>fonts.gstatic.com</code>): cargamos la tipografía Geist desde los servidores de
        Google. Al solicitarse, tu <b>dirección IP y algunas cabeceras del navegador</b>{' '}
        (como <code>User-Agent</code> o <code>Referer</code>) se transmiten a Google. Esta
        web <b>no</b> establece cookies a través de Google Fonts. Puedes consultar cómo trata
        Google esos datos en su{' '}
        <a
          href="https://policies.google.com/privacy"
          target="_blank"
          rel="noopener noreferrer"
        >
          política de privacidad
        </a>.
      </p>
      <p>
        Google Fonts se mantiene como CDN (no se autoaloja la tipografía), por lo que la{' '}
        <i>Content-Security-Policy</i> (CSP) del sitio no cambia por este motivo.
      </p>
      <p>
        <b>Google Maps</b> (carga <b>bajo demanda</b>): la sección «Visítanos» incluye un
        mapa embebido que <b>no se solicita a Google hasta que pulsas «Ver mapa»</b>.
        Mientras no lo hagas no hay ninguna petición a Google por el mapa —ni teselas, ni el
        mapa incrustado, ni cookies de Google—: solo se muestra un marcador de posición
        propio. Al pulsar «Ver mapa» el navegador carga el mapa desde{' '}
        <code>www.google.com</code>; desde ese momento Google <b>puede establecer sus
        propias cookies</b> y recibir datos técnicos de tu conexión (tu <b>dirección IP</b>{' '}
        y cabeceras como <code>User-Agent</code> o <code>Referer</code>). Para permitirlo, la{' '}
        <i>Content-Security-Policy</i> del sitio autoriza{' '}
        <code>frame-src https://www.google.com</code>, manteniendo intacto el resto de
        directivas. Puedes consultar cómo trata Google esos datos en su{' '}
        <a
          href="https://policies.google.com/privacy"
          target="_blank"
          rel="noopener noreferrer"
        >
          política de privacidad
        </a>.
      </p>
      <p>
        <b>Cloudflare Turnstile</b> (condicional): está <b>desactivado por defecto</b>. Solo
        se cargaría en la página de acceso si se definiera la variable{' '}
        <code>VITE_TURNSTILE_SITE_KEY</code>. Si algún día se activa, Cloudflare podría usar
        su propio almacenamiento para el reto anti-bot; en ese caso actualizaríamos esta
        política y la CSP (permitiendo <code>challenges.cloudflare.com</code> en{' '}
        <code>script-src</code> y <code>frame-src</code>) de forma simultánea.
      </p>

      <h2>Cambios en esta política</h2>
      <p>
        Esta política describe el almacenamiento y los recursos de terceros que la web usa
        hoy. Como no hay cookies no esenciales que consentir, no existe un consentimiento
        previo que revocar; si en el futuro se incorporara alguna herramienta de terceros
        (por ejemplo, al activar Turnstile o añadir analítica), actualizaríamos esta
        política —y la CSP cuando corresponda— para describirlo.
      </p>
    </article>
  );
}
