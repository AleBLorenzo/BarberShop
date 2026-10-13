/** Política de cookies: refleja exactamente lo que la app guarda en el navegador. */
export default function Cookies() {
  return (
    <article style={{ maxWidth: 760, margin: '0 auto' }}>
      <h1>Política de cookies</h1>
      <p>
        <b>Esta web no usa cookies</b> —ni propias ni de terceros— ni scripts de
        analítica o publicidad. Solo guardamos algunos datos en el almacenamiento local
        de tu navegador, imprescindibles para que la app funcione:
      </p>

      <h2>Almacenamiento que utilizamos</h2>
      <div className="table-wrapper">
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ textAlign: 'left', borderBottom: '1px solid var(--border)' }}>
            <th style={{ padding: '8px 4px' }}>Clave</th>
            <th style={{ padding: '8px 4px' }}>Tipo</th>
            <th style={{ padding: '8px 4px' }}>Para qué sirve</th>
          </tr>
        </thead>
        <tbody>
          <tr style={{ borderBottom: '1px solid var(--border)' }}>
            <td style={{ padding: '8px 4px' }}><code>refreshToken</code></td>
            <td style={{ padding: '8px 4px' }}>localStorage</td>
            <td style={{ padding: '8px 4px' }}>Mantener tu sesión iniciada en el panel.</td>
          </tr>
          <tr style={{ borderBottom: '1px solid var(--border)' }}>
            <td style={{ padding: '8px 4px' }}><code>cookiesAccepted</code></td>
            <td style={{ padding: '8px 4px' }}>localStorage</td>
            <td style={{ padding: '8px 4px' }}>Recordar que has aceptado este aviso para no
              volver a mostrártelo.</td>
          </tr>
          <tr style={{ borderBottom: '1px solid var(--border)' }}>
            <td style={{ padding: '8px 4px' }}><code>bookingDraft</code></td>
            <td style={{ padding: '8px 4px' }}>sessionStorage</td>
            <td style={{ padding: '8px 4px' }}>Conservar el borrador de tu reserva mientras
              completas los pasos (se borra al cerrar la pestaña).</td>
          </tr>
          <tr>
            <td style={{ padding: '8px 4px' }}><code>lastReserva</code></td>
            <td style={{ padding: '8px 4px' }}>sessionStorage</td>
            <td style={{ padding: '8px 4px' }}>Mostrar el resumen de la última cita creada en
              la pantalla de éxito.</td>
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

      <h2>Cambios en esta política</h2>
      <p>Si en el futuro incorporamos cualquier herramienta de terceros, este aviso se
        actualizará y se solicitará tu consentimiento previo.</p>
    </article>
  );
}
