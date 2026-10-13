import { format, parseISO, startOfWeek, endOfWeek, addDays, isToday, isSameDay } from 'date-fns';
import { es } from 'date-fns/locale/es';

/**
 * Format a date string (ISO) to Spanish display.
 * @param {string|Date} date
 * @param {string} [pattern] date-fns pattern
 */
export function formatDate(date, pattern = 'dd/MM/yyyy') {
  if (!date) return '';
  const d = typeof date === 'string' ? parseISO(date) : date;
  return format(d, pattern, { locale: es });
}

/**
 * Format time HH:MM:SS → HH:MM
 * @param {string} time
 */
export function formatTime(time) {
  if (!time) return '';
  return time.length > 5 ? time.slice(0, 5) : time;
}

/**
 * Format currency EUR
 * @param {number} amount
 */
export function formatCurrency(amount) {
  if (amount == null) return '—';
  return new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(amount);
}

/**
 * Get current week (Mon-Sun) as array of dates.
 * @returns {Date[]}
 */
export function getWeekDates(from = new Date()) {
  const monday = startOfWeek(from, { weekStartsOn: 1 });
  return Array.from({ length: 7 }, (_, i) => addDays(monday, i));
}

export { isToday, isSameDay, parseISO, format, addDays, startOfWeek, endOfWeek };
