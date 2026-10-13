/**
 * Fallback mínimo para <Suspense> en rutas lazy: un spinner centrado que
 * reutiliza las clases existentes (.loading-page / .spinner) para no
 * introducir saltos de layout ni CSS nuevo. Sin dependencias.
 */
export default function RouteFallback() {
  return (
    <div className="loading-page" role="status" aria-live="polite" aria-label="Cargando">
      <span className="spinner spinner-lg" aria-hidden="true" />
    </div>
  );
}
