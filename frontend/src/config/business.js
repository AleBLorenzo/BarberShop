/**
 * Fuente única de datos de negocio (todo lo visible por el cliente).
 *
 * KEEP IN SYNC con:
 *  - backend/src/main/resources/application.properties  (app.business.*: el
 *    horario ya NO es fijo; esas propiedades solo siembran el horario general
 *    la primera vez — el real vive en BD y lo edita el admin, ver HORARIO_TEXTO)
 *  - backend/src/main/java/.../service/EmailService.java (asuntos/cuerpos de correo)
 *  - README.md
 *
 * Si el negocio se muda, actualizar JUNTAS: GOOGLE_MAPS_URL, GOOGLE_MAPS_LAT,
 * GOOGLE_MAPS_LNG y GOOGLE_MAPS_EMBED_URL (las coordenadas del embed no se
 * resuelven solas desde el enlace corto).
 */

/** Marca visible (títulos, headers, correos, .ics). */
export const NOMBRE = 'TheTankBarber';

/** Email de contacto público. */
export const EMAIL_CONTACTO = 'richelbarber2024@gmail.com';

/** Teléfono de contacto (móvil). */
export const TELEFONO = '653812324';

/** Descripción del negocio (landing «Sobre nosotros»). Dos párrafos. */
export const DESCRIPCION = [
  'The Tank Barber es una barbería moderna en Tenerife, creada para ofrecer una experiencia diferente, profesional y cercana. Nos especializamos en cortes de cabello, degradados, arreglos de barba y estilos personalizados, cuidando cada detalle para que cada cliente salga con el estilo que busca.',
  'Un espacio con personalidad, buen ambiente y atención cercana, donde la calidad y el trato al cliente son nuestra prioridad. En The Tank Barber trabajamos para que cada visita sea una experiencia y cada corte tenga su propio estilo.',
];

/** Dirección de la barbería. */
export const DIRECCION = 'Av. Antonio Dominguez, 20, 38650 Arona, Santa Cruz de Tenerife';

/**
 * Enlace público de Google Maps (usado en web y en los correos).
 * OJO: es sensible a mayúsculas — `...LRCx...` (R mayúscula) es la forma válida;
 * la variante con `r` minúscula devuelve 404. No «corregir» la constante.
 */
export const GOOGLE_MAPS_URL = 'https://maps.app.goo.gl/12BLsdYKtLRCxFRe8';

/**
 * Coordenadas reales del negocio (THE TANK BARBER), resueltas desde
 * GOOGLE_MAPS_URL. Se usan para fijar el pin del mapa embebido.
 */
export const GOOGLE_MAPS_LAT = 28.0571747;

/** Longitud real del negocio; ver GOOGLE_MAPS_LAT. */
export const GOOGLE_MAPS_LNG = -16.7231266;

/**
 * URL de embed de Google Maps SIN API key: `?q=<lat>,<lng>` da un pin
 * determinista en las coordenadas (z=17) sin pasar por el geocodificador.
 */
export const GOOGLE_MAPS_EMBED_URL =
  'https://www.google.com/maps?q=28.0571747,-16.7231266&z=17&output=embed';

/** Perfil público de Instagram del negocio (URL limpia, sin parámetros de seguimiento). */
export const INSTAGRAM_URL = 'https://www.instagram.com/the_tank_barber';

/**
 * Horario textual de RESPALDO (lun–sáb, domingos cerrado). La portada pinta el
 * horario real de `GET /api/public/horario` (editable por el admin en
 * /admin/horarios, cambio admin-agenda-y-cierres) y solo usa este texto si la
 * API falla. KEEP IN SYNC con la siembra del horario general
 * (`app.business.opening-hour/closing-hour/closed-days` en application.properties,
 * `HorarioDefaultsInitializer`) y con el texto de pages/legal/Terminos.jsx.
 */
export const HORARIO_TEXTO = 'Lunes a sábado · 9:00 – 20:00 · Domingos cerrado';

export default {
  NOMBRE,
  EMAIL_CONTACTO,
  TELEFONO,
  DESCRIPCION,
  DIRECCION,
  GOOGLE_MAPS_URL,
  GOOGLE_MAPS_LAT,
  GOOGLE_MAPS_LNG,
  GOOGLE_MAPS_EMBED_URL,
  INSTAGRAM_URL,
  HORARIO_TEXTO,
};
