import { useContext } from 'react';
import { BookingContext } from '../context/bookingContext';

/**
 * Hook to access booking draft state.
 * @returns {{draft: Object, updateDraft: Function, clearDraft: Function}}
 */
export function useBooking() {
  const context = useContext(BookingContext);
  if (!context) throw new Error('useBooking must be used within a BookingProvider');
  return context;
}
