/**
 * Gestor de `<head>` propio y ligero — cambio `mobile-first-audit-and-web-essentials`
 * (decisión D2; specs `seo-meta` / `not-found-page`).
 *
 * Sin dependencias nuevas (nada de `react-helmet`): un hook que, por ruta,
 * fija `document.title`, `meta[name="description"]` y `meta[name="robots"]`,
 * **reutiliza** las etiquetas existentes (nunca crea duplicados) y **restaura**
 * los valores base de `index.html` al desmontar.
 *
 * CÓMO SE MANTIENE SIN DUPLICADOS
 * Las etiquetas `<title>`/`description`/`robots` son únicas por definición de
 * HTML, así que el gestor busca antes de crear (`querySelector` → `setAttribute`
 * si existe, `createElement` solo si falta) y usa una pila de descriptores: la
 * ruta activa es la cima; al desmontar se vuelve a la base.
 *
 * LÍMITE SPA, DECLARADO HONESTAMENTE (sin SSR ni prerender — spec `seo-meta`)
 * Esta es una SPA pura. Por tanto:
 *   - Los clientes que NO ejecutan JavaScript (`curl`, y los scrapers sociales
 *     de Facebook/X/WhatsApp/Slack/LinkedIn) solo ven el `<title>` y la
 *     descripción ESTÁTICOS de `index.html`, en TODAS las rutas. Es el límite
 *     aceptado de una SPA sin SSR; este gestor no lo puede cambiar.
 *   - Los crawlers que SÍ ejecutan JS (Googlebot/Bingbot) renderizan la página
 *     y respetan lo que este gestor fija en el cliente: es donde el cambio sí
 *     aporta.
 *   - Por eso las etiquetas Open Graph/Twitter (`og:*`, `twitter:*`)
 *     PERMANECEN estáticas en `index.html` y NO se gestionan aquí: los scrapers
 *     no ejecutan JS y jamás verían un valor inyectado en el cliente.
 *
 * Salir de este límite exigiría SSR/prerender (fuera de alcance por decisión de
 * diseño). No hay parche de cliente que lo evite.
 */
import { useEffect } from 'react';

/** Pila de descriptores `useSeo` activos (la cima es la ruta visible). */
const stack = [];

/** Valores base de `index.html`, capturados en el primer uso (aún sin tocar). */
let base = null;

function getMeta(name) {
  return document.head.querySelector(`meta[name="${name}"]`);
}

function ensureBase() {
  if (!base) {
    base = {
      title: document.title,
      description: getMeta('description')?.getAttribute('content') ?? '',
      // `null` = no existía en `index.html` ⇒ al restaurar se retira.
      robots: getMeta('robots')?.getAttribute('content') ?? null,
    };
  }
  return base;
}

/** Fija/crea/elimina una `<meta name>` reutilizando la etiqueta existente. */
function setMeta(name, content) {
  const existing = getMeta(name);
  if (content == null) {
    if (existing) existing.remove();
    return;
  }
  if (existing) {
    existing.setAttribute('content', content);
    return;
  }
  const el = document.createElement('meta');
  el.setAttribute('name', name);
  el.setAttribute('content', content);
  document.head.appendChild(el);
}

/** Aplica el descriptor de la cima de la pila (o la base si está vacía). */
function apply() {
  const fallback = ensureBase();
  const current = stack[stack.length - 1];
  document.title = current?.title || fallback.title;
  setMeta('description', current?.description || fallback.description);
  // Rutas públicas (sin noindex) retiran la directiva; las privadas la fijan.
  setMeta('robots', current ? (current.noindex ? 'noindex' : null) : fallback.robots);
}

/**
 * Declara el título, la descripción y (opcional) `noindex` de la ruta actual.
 *
 * @param {{title: string, description: string, noindex?: boolean}} seo
 */
export function useSeo({ title, description, noindex = false }) {
  useEffect(() => {
    const entry = { title, description, noindex };
    stack.push(entry);
    apply();
    return () => {
      const index = stack.indexOf(entry);
      if (index !== -1) stack.splice(index, 1);
      apply();
    };
  }, [title, description, noindex]);
}

export default useSeo;
