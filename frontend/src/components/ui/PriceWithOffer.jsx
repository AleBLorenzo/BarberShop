import { formatCurrency } from '../../utils/date';
import Badge from './Badge';

/**
 * Precio de un servicio con soporte de oferta temporal.
 *
 * - Sin oferta activa → devuelve exactamente el precio normal (la interfaz no cambia).
 * - Con oferta activa → precio original tachado + precio rebajado + distintivo `-N%`.
 *
 * Los payloads sin campos de oferta (o con `ofertaActiva` false/null) caen en el modo
 * normal: nunca se pinta un descuento vencido.
 *
 * @param {number|string} precio Precio base del servicio.
 * @param {number|string|null} [precioOferta] Precio rebajado (calculado por el backend).
 * @param {boolean|null} [ofertaActiva] Flag de vigencia según el reloj del servidor.
 * @param {number|null} [descuentoPorcentaje] Porcentaje de la oferta (para el distintivo).
 * @param {string|null} [ofertaInicio] ISO del inicio de la ventana.
 * @param {string|null} [ofertaFin] ISO del fin de la ventana.
 * @param {boolean} [recalcular] Si es true, revalida la ventana con el reloj local: usar en
 *   la confirmación de reserva, donde el draft puede haber envejecido mientras el usuario
 *   rellenaba el formulario (design D5).
 */
export default function PriceWithOffer({
  precio,
  precioOferta,
  ofertaActiva,
  descuentoPorcentaje,
  ofertaInicio,
  ofertaFin,
  recalcular = false,
}) {
  let activa = ofertaActiva === true;

  if (activa && recalcular && ofertaInicio && ofertaFin) {
    // La vigencia de la oferta es una función del reloj actual por diseño (expiración por
    // tiempo, design D5): solo se evalúa al renderizar la confirmación de reserva.
    // eslint-disable-next-line react-hooks/purity -- lectura de reloj intencionada, ver arriba
    const ahora = Date.now();
    const inicio = Date.parse(ofertaInicio);
    const fin = Date.parse(ofertaFin);
    if (!Number.isNaN(inicio) && !Number.isNaN(fin)) {
      activa = ahora >= inicio && ahora < fin;
    }
  }

  if (!activa || precioOferta == null) {
    return <>{formatCurrency(precio)}</>;
  }

  return (
    <span
      style={{
        display: 'inline-flex',
        gap: 'var(--space-xs)',
        alignItems: 'baseline',
        flexWrap: 'wrap',
      }}
    >
      <s style={{ opacity: 0.55, fontWeight: 400 }}>{formatCurrency(precio)}</s>
      <strong>{formatCurrency(precioOferta)}</strong>
      {descuentoPorcentaje != null && (
        <Badge variant="pending">-{descuentoPorcentaje}%</Badge>
      )}
    </span>
  );
}
