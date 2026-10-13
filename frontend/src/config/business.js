/**
 * Fuente única de datos de negocio (todo lo visible por el cliente).
 *
 * KEEP IN SYNC con:
 *  - backend/src/main/resources/application.properties  (app.business.*)
 *  - backend/src/main/java/.../service/EmailService.java (asuntos/cuerpos de correo)
 *  - README.md
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

/** Enlace público de Google Maps (usado en web y en los correos). */
export const GOOGLE_MAPS_URL = 'https://maps.app.goo.gl/12BLsdYKtLRCxFRe8';

/** Horario textual (lun–sáb, domingos cerrado). */
export const HORARIO_TEXTO = 'Lunes a sábado · 9:00 – 20:00 · Domingos cerrado';

export default {
  NOMBRE,
  EMAIL_CONTACTO,
  TELEFONO,
  DESCRIPCION,
  DIRECCION,
  GOOGLE_MAPS_URL,
  HORARIO_TEXTO,
};
