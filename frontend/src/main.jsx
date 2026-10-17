import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

// Carga no bloqueante de Geist (capacidad `ui-style-guide`): el `<link>` llega con
// media="print" y hay que pasarlo a "all" para que la hoja se aplique. No puede hacerse
// desde un `onload` inline en el HTML porque la CSP de producción (`script-src 'self'`,
// sin 'unsafe-inline' ni hashes) bloquea los manejadores en línea (script-src-attr cae
// en script-src) y la fuente nunca se aplicaría. Lo hacemos desde el bundle, que es un
// módulo del mismo origen y la CSP sí permite ejecutar. Idempotente (no-op si ya es
// "all") y tolerante a que el elemento no exista (`getElementById` devolvería null).
const geistLink = document.getElementById('geist-font')
if (geistLink && geistLink.media !== 'all') {
  geistLink.media = 'all'
}

// PWA: registrar el service worker solo en builds de producción
// (en dev interferiría con el HMR de Vite).
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {})
  })
}
