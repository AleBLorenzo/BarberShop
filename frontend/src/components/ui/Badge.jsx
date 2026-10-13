/**
 * Badge component with semantic variants for reservation states.
 * @param {{variant?: 'pending'|'confirmed'|'completed'|'cancelled'|'no-show'|'default', children: React.ReactNode}} props
 */
export default function Badge({ variant = 'default', children }) {
  return <span className={`badge badge-${variant}`}>{children}</span>;
}
