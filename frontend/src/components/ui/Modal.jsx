import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';

/**
 * Modal with portal, ESC close, focus trap.
 * @param {{open: boolean, onClose: Function, title?: string, size?: 'sm'|'md'|'lg'|'xl', footer?: React.ReactNode, children: React.ReactNode}} props
 */
export default function Modal({ open, onClose, title, size = 'md', footer, children }) {
  const modalRef = useRef(null);
  const previousFocus = useRef(null);

  useEffect(() => {
    if (!open) return;
    previousFocus.current = document.activeElement;

    const handleKey = (e) => {
      if (e.key === 'Escape') {
        onClose();
      }
      if (e.key === 'Tab' && modalRef.current) {
        const focusable = modalRef.current.querySelectorAll(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        );
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };

    document.addEventListener('keydown', handleKey);
    document.body.style.overflow = 'hidden';
    // Focus first focusable element
    const timer = setTimeout(() => {
      const focusable = modalRef.current?.querySelector(
        'button, input, select, textarea, [href]'
      );
      focusable?.focus();
    }, 50);

    return () => {
      document.removeEventListener('keydown', handleKey);
      document.body.style.overflow = '';
      clearTimeout(timer);
      previousFocus.current?.focus?.();
    };
  }, [open, onClose]);

  if (!open) return null;

  return createPortal(
    <div
      className="modal-overlay"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      role="dialog"
      aria-modal="true"
      aria-label={title}
    >
      <div className={`modal modal-${size}`} ref={modalRef}>
        <div className="modal-header">
          {title && <h3>{title}</h3>}
          <button className="modal-close" onClick={onClose} aria-label="Cerrar">
            ×
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-footer">{footer}</div>}
      </div>
    </div>,
    document.body
  );
}
