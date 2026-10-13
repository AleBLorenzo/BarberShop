/**
 * Button component with variants, sizes, loading and disabled states.
 * @param {{variant?: 'primary'|'secondary'|'danger'|'ghost', size?: 'sm'|'md'|'lg', loading?: boolean, disabled?: boolean, children: React.ReactNode, [key: string]: any}} props
 */
export default function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled = false,
  children,
  className = '',
  type = 'button',
  ...rest
}) {
  const classes = [
    'btn',
    `btn-${variant}`,
    `btn-${size}`,
    loading ? 'btn-loading' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button
      type={type}
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {children}
    </button>
  );
}
