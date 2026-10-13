import { forwardRef } from 'react';
import { format } from 'date-fns';

/**
 * DatePicker wrapping native input[type=date].
 * @param {{label?: string, value?: Date|string|null, onChange: Function, min?: Date|string, max?: Date|string, error?: string}} props
 */
export const DatePicker = forwardRef(function DatePicker(
  { label, value, onChange, min, max, error, required, ...rest },
  ref
) {
  const toInputValue = (v) => {
    if (!v) return '';
    if (v instanceof Date) return format(v, 'yyyy-MM-dd');
    return v;
  };

  const handleChange = (e) => {
    const str = e.target.value;
    onChange(str ? str : null);
  };

  const inputId = rest.id || rest.name || 'date-picker';

  return (
    <div className="form-group">
      {label && (
        <label className="form-label" htmlFor={inputId}>
          {label}
          {required && <span style={{ color: 'var(--danger)' }}> *</span>}
        </label>
      )}
      <input
        id={inputId}
        ref={ref}
        type="date"
        className={`form-input ${error ? 'error' : ''}`}
        value={toInputValue(value)}
        onChange={handleChange}
        min={min ? toInputValue(min) : undefined}
        max={max ? toInputValue(max) : undefined}
        aria-invalid={error ? 'true' : undefined}
        {...rest}
      />
      {error && <p className="form-error" role="alert">{error}</p>}
    </div>
  );
});

export default DatePicker;
