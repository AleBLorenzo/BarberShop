import { forwardRef } from 'react';

/**
 * Label component
 */
export function Label({ htmlFor, children, required }) {
  return (
    <label className="form-label" htmlFor={htmlFor}>
      {children}
      {required && <span style={{ color: 'var(--danger)' }}> *</span>}
    </label>
  );
}

/**
 * Input component compatible with react-hook-form (forwards ref and spread).
 * @param {{label?: string, error?: string, id?: string, [key: string]: any}} props
 */
export const Input = forwardRef(function Input(
  { label, error, id, required, className = '', ...rest },
  ref
) {
  const inputId = id || rest.name;
  return (
    <div className="form-group">
      {label && <Label htmlFor={inputId} required={required}>{label}</Label>}
      <input
        id={inputId}
        ref={ref}
        className={`form-input ${error ? 'error' : ''} ${className}`}
        aria-invalid={error ? 'true' : undefined}
        {...rest}
      />
      {error && <p className="form-error" role="alert">{error}</p>}
    </div>
  );
});

/**
 * Select component compatible with react-hook-form.
 * @param {{label?: string, error?: string, options?: Array<{value: any, label: string}>, id?: string, [key: string]: any}} props
 */
export const Select = forwardRef(function Select(
  { label, error, options = [], id, required, className = '', children, ...rest },
  ref
) {
  const inputId = id || rest.name;
  return (
    <div className="form-group">
      {label && <Label htmlFor={inputId} required={required}>{label}</Label>}
      <select
        id={inputId}
        ref={ref}
        className={`form-select ${error ? 'error' : ''} ${className}`}
        aria-invalid={error ? 'true' : undefined}
        {...rest}
      >
        {children}
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
      {error && <p className="form-error" role="alert">{error}</p>}
    </div>
  );
});

/**
 * Textarea component compatible with react-hook-form.
 * @param {{label?: string, error?: string, id?: string, [key: string]: any}} props
 */
export const Textarea = forwardRef(function Textarea(
  { label, error, id, required, className = '', ...rest },
  ref
) {
  const inputId = id || rest.name;
  return (
    <div className="form-group">
      {label && <Label htmlFor={inputId} required={required}>{label}</Label>}
      <textarea
        id={inputId}
        ref={ref}
        className={`form-textarea ${error ? 'error' : ''} ${className}`}
        aria-invalid={error ? 'true' : undefined}
        {...rest}
      />
      {error && <p className="form-error" role="alert">{error}</p>}
    </div>
  );
});

export default Input;
