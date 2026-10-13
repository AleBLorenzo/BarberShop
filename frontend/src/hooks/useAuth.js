import { useContext } from 'react';
import { AuthContext } from '../context/authContext';

/**
 * Hook to consume AuthContext.
 * @returns {{user: Object|null, loading: boolean, isAuthenticated: boolean, isAdmin: boolean, login: Function, logout: Function}}
 */
export function useAuth() {
  const context = useContext(AuthContext);
  if (context === null) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
