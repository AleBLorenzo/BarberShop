/**
 * Skeleton loading placeholder.
 * @param {{variant?: 'text'|'card', lines?: number, className?: string}} props
 */
export default function Skeleton({ variant = 'text', lines = 3, className = '' }) {
  if (variant === 'card') {
    return <div className={`skeleton skeleton-card ${className}`} aria-hidden="true" />;
  }
  return (
    <div className={className} aria-hidden="true">
      {Array.from({ length: lines }).map((_, i) => (
        <div
          key={i}
          className="skeleton skeleton-text"
          style={{ width: i === lines - 1 ? '60%' : '100%' }}
        />
      ))}
    </div>
  );
}

/**
 * Skeleton grid for cards (services/workers)
 * @param {{count?: number}} props
 */
export function SkeletonGrid({ count = 4 }) {
  return (
    <div className="grid-services">
      {Array.from({ length: count }).map((_, i) => (
        <Skeleton key={i} variant="card" />
      ))}
    </div>
  );
}
