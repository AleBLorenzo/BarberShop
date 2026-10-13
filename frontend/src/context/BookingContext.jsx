import { useState, useEffect } from 'react';
import { BookingContext } from './bookingContext';

const STORAGE_KEY = 'bookingDraft';

const defaultDraft = {
  servicio: null,
  trabajador: null,
  fecha: null,
  slot: null,
  clienteEmail: '',
};

function loadDraft() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (raw) return { ...defaultDraft, ...JSON.parse(raw) };
  } catch {
    /* ignore corrupted draft */
  }
  return defaultDraft;
}

/**
 * Booking flow state with sessionStorage persistence.
 */
export function BookingProvider({ children }) {
  const [draft, setDraft] = useState(loadDraft);

  useEffect(() => {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(draft));
    } catch {
      /* storage full or unavailable */
    }
  }, [draft]);

  const updateDraft = (patch) => {
    setDraft((prev) => ({ ...prev, ...patch }));
  };

  const clearDraft = () => {
    setDraft(defaultDraft);
    sessionStorage.removeItem(STORAGE_KEY);
  };

  return (
    <BookingContext.Provider value={{ draft, updateDraft, clearDraft }}>
      {children}
    </BookingContext.Provider>
  );
}
