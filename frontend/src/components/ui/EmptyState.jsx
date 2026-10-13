import Button from './Button';

/**
 * Empty state component for lists with no data.
 * @param {{title?: string, message?: string, action?: React.ReactNode}} props
 */
export function EmptyState({ title = 'Sin resultados', message, action }) {
  return (
    <div className="empty-state">
      <h3>{title}</h3>
      {message && <p>{message}</p>}
      {action}
    </div>
  );
}

/**
 * Error state component with retry.
 * @param {{message?: string, onRetry?: Function}} props
 */
export function ErrorState({ message = 'Algo salió mal', onRetry }) {
  return (
    <div className="error-state" role="alert">
      <span>{message}</span>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          Reintentar
        </Button>
      )}
    </div>
  );
}

export default EmptyState;
