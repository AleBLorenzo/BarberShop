import { useState, useCallback, useRef } from 'react';
import { createPortal } from 'react-dom';
import { ToastContext } from './toastContext';

let toastId = 0;

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const timers = useRef({});

  const dismiss = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
    clearTimeout(timers.current[id]);
    delete timers.current[id];
  }, []);

  const show = useCallback(
    (type, message, options = {}) => {
      const id = ++toastId;
      const duration = options.duration || (type === 'error' ? 6000 : 4000);
      setToasts((prev) => [...prev, { id, type, message, action: options.action }]);
      timers.current[id] = setTimeout(() => dismiss(id), duration);
    },
    [dismiss]
  );

  const toast = {
    success: (msg, opts) => show('success', msg, opts),
    error: (msg, opts) => show('error', msg, opts),
    info: (msg, opts) => show('info', msg, opts),
    dismiss,
  };

  return (
    <ToastContext.Provider value={toast}>
      {children}
      {createPortal(
        <div className="toaster" role="region" aria-label="Notificaciones">
          {toasts.map((t) => (
            <div key={t.id} className={`toast toast-${t.type}`} role="alert">
              <span>{t.message}</span>
              {t.action && (
                <button onClick={() => { t.action.onClick(); dismiss(t.id); }}>
                  {t.action.label}
                </button>
              )}
              <button onClick={() => dismiss(t.id)} aria-label="Cerrar">×</button>
            </div>
          ))}
        </div>,
        document.body
      )}
    </ToastContext.Provider>
  );
}
