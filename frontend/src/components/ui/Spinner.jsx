/**
 * Spinner component
 * @param {{size?: 'sm'|'md'|'lg', label?: string}} props
 */
export default function Spinner({ size = 'md', label }) {
  return (
    <span
      className={`spinner spinner-${size}`}
      role="status"
      aria-label={label || 'Cargando'}
    />
  );
}
