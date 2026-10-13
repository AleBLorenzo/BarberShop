import { useState, useEffect, useCallback } from 'react';
import { AuthContext } from './authContext';
import { auth, workerApi, clearTokens, getAccessToken, getRefreshToken } from '../services/api';

/**
 * @typedef {Object} AuthUser
 * @property {number} id
 * @property {string} nombre
 * @property {string} email
 * @property {boolean} [activo]
 * @property {string} [foto] ruta pública de la foto (`/uploads/trabajadores/...`)
 * @property {'WORKER'|'ADMIN'} rol
 */

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // Restore session on mount: validate token via perfil endpoint
  useEffect(() => {
    let cancelled = false;
    const restoreSession = async () => {
      const access = getAccessToken();
      const refresh = getRefreshToken();
      if (!access && !refresh) {
        if (!cancelled) setLoading(false);
        return;
      }
      try {
        const perfil = await workerApi.getPerfil();
        if (cancelled) return;
        setUser({
          id: perfil.id,
          nombre: perfil.nombre,
          email: perfil.email,
          activo: perfil.activo,
          foto: perfil.foto,
          // rol real que devuelve el backend (TrabajadorDTO.rol)
          rol: perfil.rol || 'WORKER',
        });
      } catch {
        // Token invalid or expired and refresh failed (interceptor redirects)
        clearTokens();
        if (!cancelled) setUser(null);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    restoreSession();
    return () => {
      cancelled = true;
    };
  }, []);

  /**
   * @param {string} email
   * @param {string} password
   * @param {boolean} [rememberMe]
   * @returns {Promise<AuthUser>} throws on failure
   */
  const login = useCallback(async (email, password, rememberMe = false) => {
    const response = await auth.login({ email, password, rememberMe });
    const t = response.trabajador || {};
    const authUser = {
      id: t.id,
      nombre: t.nombre,
      email: t.email,
      activo: t.activo,
      foto: t.foto,
      rol: t.rol || 'WORKER',
    };
    setUser(authUser);
    return authUser;
  }, []);

  const logout = useCallback(() => {
    clearTokens();
    setUser(null);
  }, []);

  const isAuthenticated = user !== null;
  // Solo el rol ADMIN puede abrir el panel admin (el backend devuelve
  // 403 a /api/admin/** para WORKER; ver SecurityConfig).
  const isAdmin = isAuthenticated && user?.rol === 'ADMIN';

  const value = {
    user,
    loading,
    isAuthenticated,
    isAdmin,
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
