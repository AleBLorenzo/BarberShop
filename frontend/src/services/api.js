import axios from 'axios';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

/**
 * Origen del backend (API_BASE sin el prefijo `/api`): las rutas de medios
 * que devuelve la API (`/uploads/...`) son relativas al origen del backend,
 * que puede no coincidir con el origen de la web (p. ej. en el túnel de dev).
 */
export const MEDIA_ORIGIN = API_BASE.replace(/\/api\/?$/, '');

// --- Anti-bot: reto X-Login-Challenge (opcional, OFF por defecto) ---
// La cabecera solo se envía cuando el propietario activa la feature por env
// var (VITE_BOT_CHALLENGE_ENABLED=true); con el reto OFF el flujo del login
// no cambia en nada (compatibilidad con clientes API legítimos). El backend
// verifica hex(SHA-256("v1|<email>|<minuto>")) aceptando ±1 minuto de desfase
// de reloj (caducidad). Requiere contexto seguro (https o localhost):
// crypto.subtle no existe en http:// plano — por eso el OFF por defecto no
// depende de esto.
const BOT_CHALLENGE_ENABLED = import.meta.env.VITE_BOT_CHALLENGE_ENABLED === 'true';

/**
 * Calcula el valor del reto anti-bot de login: hex(SHA-256("v1|<email>|<minuto>")),
 * donde minuto = epoch_ms / 60000. Usa Web Crypto (sin dependencias).
 * @param {string} email email que se va a enviar en el login
 * @returns {Promise<string>} valor para la cabecera X-Login-Challenge
 */
export async function calcularRetoLogin(email) {
  const minuto = Math.floor(Date.now() / 60000);
  const datos = new TextEncoder().encode(`v1|${email}|${minuto}`);
  const digest = await crypto.subtle.digest('SHA-256', datos);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

/**
 * Convierte una ruta de medio devuelta por la API en una URL utilizable en `<img>`.
 *
 * - vacía o `null` → `null` (sin imagen)
 * - `http...` → se devuelve tal cual (URL externa absoluta)
 * - `/uploads/...` → se antepone el origen del backend (encodeURI preserva `ñ`/espacios)
 * - cualquier otro valor → se devuelve tal cual
 *
 * @param {string|null|undefined} path ruta relativa, URL absoluta o valor vacío
 * @returns {string|null} URL lista para usar, o `null` si no hay imagen
 */
export function mediaUrl(path) {
  if (!path) return null;
  // Bloquea esquemas peligrosos (javascript:, data:, vbscript:) — el resto
  // (http(s)://, /uploads/…, rutas relativas) se comporta como siempre
  if (/^(javascript|data|vbscript):/i.test(path)) return null;
  if (path.startsWith('http')) return path;
  if (path.startsWith('/uploads/')) return MEDIA_ORIGIN + encodeURI(path);
  return path;
}

/**
 * @typedef {Object} LoginRequest
 * @property {string} email
 * @property {string} password
 * @property {boolean} [rememberMe]
 * @property {string} [website] campo honeypot anti-bot (el frontend lo envía vacío)
 * @property {string} [turnstileToken] token opcional de Turnstile (solo si está activo)
 *
 * @typedef {Object} LoginResponse
 * @property {string} accessToken
 * @property {string} refreshToken
 * @property {number} expiresIn
 * @property {WorkerProfile} trabajador
 *
 * @typedef {Object} WorkerProfile
 * @property {number} id
 * @property {string} nombre
 * @property {string} email
 * @property {boolean} activo
 *
 * @typedef {Object} Servicio
 * @property {number} id
 * @property {string} nombre
 * @property {number} precio
 * @property {number} duracionMinutos
 * @property {boolean} activo
 * @property {string} [foto] ruta pública de la foto (`/uploads/servicios/...`)
 *
 * @typedef {Object} Trabajador
 * @property {number} id
 * @property {string} nombre
 * @property {string} [email]
 * @property {boolean} activo
 * @property {string} [foto] ruta pública de la foto (`/uploads/trabajadores/...`)
 *
 * @typedef {Object} DisponibilidadSlot
 * @property {string} horaInicio
 * @property {string} horaFin
 * @property {boolean} disponible
 * @property {number} trabajadorId
 * @property {string} trabajadorNombre
 *
 * @typedef {Object} Reserva
 * @property {number} id
 * @property {ClienteSimple} cliente
 * @property {TrabajadorSimple} trabajador
 * @property {ServicioSimple} servicio
 * @property {string} fecha
 * @property {string} [diaSemana]
 * @property {string} horaInicio
 * @property {string} horaFin
 * @property {string} estado
 * @property {string|null} [metodoPago] VISA|EFECTIVO|null (solo COMPLETADA)
 * @property {string} tokenCancelacion
 * @property {string} createdAt
 * @property {number} precioTotal
 * @property {number} duracionMinutos
 *
 * @typedef {Object} ClienteSimple
 * @property {number} id
 * @property {string} nombre
 * @property {string} telefono
 *
 * @typedef {Object} TrabajadorSimple
 * @property {number} id
 * @property {string} nombre
 * @property {boolean} activo
 *
 * @typedef {Object} ServicioSimple
 * @property {number} id
 * @property {string} nombre
 * @property {number} precio
 * @property {number} duracionMinutos
 *
 * @typedef {Object} ApiResponse
 * @property {any} data
 * @property {string} [message]
 * @property {string} [error]
 */

// --- Token storage ---
let accessToken = null;
let refreshToken = localStorage.getItem('refreshToken');

/**
 * @typedef {Object} Resena
 * @property {number} id
 * @property {string} autor
 * @property {number} puntuacion 1–5 estrellas
 * @property {string} texto
 * @property {string} [createdAt]
 */

/**
 * @typedef {Object} Resultado
 * @property {number} id
 * @property {string} url URL http(s) de la imagen o ruta `/uploads/...`
 * @property {string} [titulo] etiqueta opcional del trabajo
 * @property {string} [createdAt]
 */

/**
 * @typedef {Object} MediaFile
 * @property {string} nombre nombre del archivo dentro de la carpeta
 * @property {string} url ruta pública de la imagen (p. ej. `/uploads/servicios/x.jpg`)
 */

export function setTokens(access, refresh) {
  accessToken = access;
  if (refresh) {
    refreshToken = refresh;
    localStorage.setItem('refreshToken', refresh);
  }
}

export function clearTokens() {
  accessToken = null;
  refreshToken = null;
  localStorage.removeItem('refreshToken');
}

export function getAccessToken() {
  return accessToken;
}

export function getRefreshToken() {
  return refreshToken;
}

// --- Axios instance ---
const api = axios.create({
  baseURL: API_BASE,
  headers: { 'Content-Type': 'application/json' },
});

// --- Request interceptor: inject access token ---
api.interceptors.request.use(
  (config) => {
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// --- Response interceptor: 401 → refresh → retry queue ---
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // skipAuthRefresh: el login fallido (401 INVALID_CREDENTIALS) no debe
    // disparar el refresh ni reescribir el error — Login.jsx necesita el
    // status real para diferenciar 429 (Retry-After) de 401 (tarea 2.8)
    if (error.response?.status === 401 && !originalRequest._retry && !originalRequest.skipAuthRefresh) {
      if (isRefreshing) {
        // Queue the request
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return api(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        if (!refreshToken) {
          throw new Error('No refresh token');
        }
        const res = await axios.post(`${API_BASE}/auth/refresh`, null, {
          headers: { Authorization: `Bearer ${refreshToken}` },
        });
        const { accessToken: newAccess, refreshToken: newRefresh } = res.data?.data || res.data;
        setTokens(newAccess, newRefresh);
        processQueue(null, newAccess);
        originalRequest.headers.Authorization = `Bearer ${newAccess}`;
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        clearTokens();
        // Redirect to login preserving intended destination
        if (window.location.pathname !== '/login') {
          const redirect = encodeURIComponent(window.location.pathname + window.location.search);
          window.location.href = `/login?redirect=${redirect}`;
        }
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

// --- Runtime response validation for critical endpoints ---
function validate(data, requiredFields = []) {
  if (data == null || typeof data !== 'object') {
    throw new Error('Respuesta inválida del servidor');
  }
  for (const field of requiredFields) {
    if (data[field] === undefined || data[field] === null) {
      throw new Error(`Respuesta del servidor incompleta: falta "${field}"`);
    }
  }
  return data;
}

function unwrap(response) {
  // Backend wraps in { data, message, error } via ApiResponse
  const body = response.data;
  if (body && typeof body === 'object' && 'data' in body) {
    return body.data;
  }
  return body;
}

// ============================================================
// API METHODS
// ============================================================

export const auth = {
  /**
   * @param {LoginRequest} credentials
   * @returns {Promise<LoginResponse>}
   */
  async login(credentials) {
    // Reto anti-bot (3.5): solo cuando la feature está activa por env var.
    // Si el navegador no tiene crypto.subtle (contexto inseguro) se envía sin
    // la cabecera a propósito: el backend responderá 403 BOT_CHALLENGE y el
    // error es visible para el operador (mejor que fallar en silencio aquí).
    const config = { skipAuthRefresh: true };
    if (BOT_CHALLENGE_ENABLED && credentials?.email) {
      try {
        config.headers = { 'X-Login-Challenge': await calcularRetoLogin(credentials.email) };
      } catch {
        // sin Web Crypto: login sin cabecera (ver nota arriba)
      }
    }
    const res = await api.post('/auth/login', credentials, config);
    const data = unwrap(res);
    validate(data, ['accessToken']);
    setTokens(data.accessToken, data.refreshToken);
    return data;
  },

  /**
   * @param {string} refreshTok
   * @returns {Promise<LoginResponse>}
   */
  async refresh(refreshTok) {
    const res = await axios.post(`${API_BASE}/auth/refresh`, null, {
      headers: { Authorization: `Bearer ${refreshTok}` },
    });
    const data = unwrap(res);
    validate(data, ['accessToken']);
    setTokens(data.accessToken, data.refreshToken);
    return data;
  },

  /**
   * Logout con revocación en el servidor: envía el refresh token a
   * POST /api/auth/logout (fire-and-forget, no bloquea la UI ni dispara el
   * interceptor de refresh) y después borra los tokens locales. Si el
   * servidor no responde, los tokens locales se borran igualmente.
   */
  async logout() {
    const refreshTok = refreshToken;
    if (refreshTok) {
      axios.post(`${API_BASE}/auth/logout`, null, {
        headers: { Authorization: `Bearer ${refreshTok}` },
      }).catch(() => {
        // fire-and-forget: la sesión caducará por expiración si falla
      });
    }
    clearTokens();
  },
};

export const publicApi = {
  /** @returns {Promise<Servicio[]>} */
  async getServicios() {
    const res = await api.get('/public/servicios');
    const data = unwrap(res);
    return Array.isArray(data) ? data : [];
  },

  /** @returns {Promise<Trabajador[]>} */
  async getTrabajadores() {
    const res = await api.get('/public/trabajadores');
    const data = unwrap(res);
    return Array.isArray(data) ? data : [];
  },

  /**
   * Reseñas de la portada (sin auth), más recientes primero.
   * @returns {Promise<Resena[]>}
   */
  async getResenas() {
    const res = await api.get('/public/resenas');
    const data = unwrap(res);
    return Array.isArray(data) ? data : [];
  },

  /**
   * Galería «Resultados» (fotos de clientes), más recientes primero.
   * @returns {Promise<Resultado[]>}
   */
  async getResultados() {
    const res = await api.get('/public/resultados');
    const data = unwrap(res);
    return Array.isArray(data) ? data : [];
  },

  /**
   * @param {{servicioId: number, fecha: string, trabajadorId?: number}} params
   * @returns {Promise<DisponibilidadSlot[]>}
   */
  async getDisponibilidad(params) {
    const res = await api.post('/public/disponibilidad', params);
    const data = unwrap(res);
    return Array.isArray(data) ? data : [];
  },

  /**
   * Crea una reserva. El email del cliente es OPCIONAL: si se omite (o viene
   * vacío) se envía `clienteEmail: null` y NO se envía confirmación por correo;
   * si viene con contenido, el servidor responde 400 con `message` cuando el
   * formato no es válido. El formato también se valida inline en el formulario
   * (spec booking-contact-email-optional).
   * @param {{servicioId: number, trabajadorId: number, fecha: string, horaInicio: string, clienteNombre: string, clienteTelefono: string, clienteEmail?: string|null}} payload
   * @returns {Promise<{id: number, tokenCancelacion: string}>}
   */
  async createReserva(payload) {
    const res = await api.post('/public/reservas', payload);
    const data = unwrap(res);
    return validate(data, ['id', 'tokenCancelacion']);
  },

  /**
   * @param {{telefono: string, token: string}} params
   * @returns {Promise<Reserva[]>}
   */
  async getReservasByPhoneToken(params) {
    const res = await api.get('/public/reservas', { params });
    const data = unwrap(res);
    return Array.isArray(data) ? data : [];
  },

  /**
   * @param {string} token
   * @returns {Promise<Reserva>}
   */
  async cancelReserva(token) {
    const res = await api.post('/public/reservas/cancelar', { token });
    return unwrap(res);
  },

  /**
   * @param {{token: string, nuevaFecha: string, nuevaHoraInicio: string}} payload
   * @returns {Promise<Reserva>}
   */
  async reprogramarReserva(payload) {
    const res = await api.post('/public/reservas/reprogramar', payload);
    return unwrap(res);
  },
};

export const workerApi = {
  /**
   * Reservas de la semana (lun→sáb). Sin `fechaInicio` el backend devuelve la
   * semana en curso; con `fechaInicio` (lunes deseado, `YYYY-MM-DD`) devuelve
   * esa semana.
   * @param {string} [fechaInicio] ISO date YYYY-MM-DD del lunes de la semana
   * @returns {Promise<Reserva[]>}
   */
  async getReservasSemana(fechaInicio) {
    const params = fechaInicio ? { fechaInicio } : {};
    const res = await api.get('/worker/reservas/semana', { params });
    const data = unwrap(res);
    return Array.isArray(data) ? data : [];
  },

  /**
   * Reservas del trabajador autenticado en un rango de fechas (ambos extremos
   * inclusive), vía el mismo endpoint que la semana: el backend acepta un
   * `fechaFin` opcional para rangos mayores (p. ej. el mes en curso).
   * @param {string} fechaInicio ISO date YYYY-MM-DD
   * @param {string} fechaFin ISO date YYYY-MM-DD (inclusive)
   * @returns {Promise<Reserva[]>}
   */
  async getReservasRango(fechaInicio, fechaFin) {
    const res = await api.get('/worker/reservas/semana', { params: { fechaInicio, fechaFin } });
    const data = unwrap(res);
    return Array.isArray(data) ? data : [];
  },

  /**
   * @param {string} [fecha] ISO date YYYY-MM-DD
   * @returns {Promise<Reserva[]>}
   */
  async getReservasDia(fecha) {
    const params = fecha ? { fecha } : {};
    const res = await api.get('/worker/reservas', { params });
    const data = unwrap(res);
    return Array.isArray(data) ? data : [];
  },

  /**
   * Cambia el estado de una reserva. Al pasar a `COMPLETADA` el backend exige
   * además `metodoPago` (`VISA`|`EFECTIVO`); sin él responde 400 con un
   * `message` específico.
   * @param {number} reservaId
   * @param {string} estado PENDIENTE|CONFIRMADA|COMPLETADA|CANCELADA|NO_ASISTIO
   * @param {string} [metodoPago] VISA|EFECTIVO (requerido si estado=COMPLETADA)
   * @returns {Promise<Reserva>}
   */
  async cambiarEstado(reservaId, estado, metodoPago) {
    const params = { estado };
    if (metodoPago) params.metodoPago = metodoPago;
    const res = await api.put(`/worker/reservas/${reservaId}/estado`, null, {
      params,
    });
    return unwrap(res);
  },

  /** @returns {Promise<WorkerProfile>} */
  async getPerfil() {
    const res = await api.get('/worker/perfil');
    return unwrap(res);
  },

  /**
   * Actualiza el perfil del trabajador autenticado (nombre, email y foto).
   * El email duplicado responde con error y `message` en español.
   * @param {{nombre: string, email: string, foto?: string}} payload
   * @returns {Promise<WorkerProfile>} perfil actualizado
   */
  async updatePerfil(payload) {
    const res = await api.put('/worker/perfil', payload);
    return unwrap(res);
  },

  /**
   * Crea una cita para el trabajador autenticado: el backend toma el
   * trabajador del JWT e ignora cualquier id del cuerpo.
   * @param {{servicioId: number, fecha: string, horaInicio: string, clienteNombre: string, clienteTelefono: string, clienteEmail?: string|null}} payload
   * @returns {Promise<Reserva>} incluye `tokenCancelacion`
   */
  async createReserva(payload) {
    const res = await api.post('/worker/reservas', payload);
    const data = unwrap(res);
    return validate(data, ['id', 'tokenCancelacion']);
  },

  /** @returns {Promise<{reservasDelDia: Reserva[], totalReservas: number, horasOcupadas: number, ingresosDelDia: number}>} */
  async getDashboard(fecha) {
    const params = fecha ? { fecha } : {};
    const res = await api.get('/worker/dashboard', { params });
    return unwrap(res);
  },
};

export const adminApi = {
  /**
   * @param {string} start ISO date
   * @param {string} end ISO date
   * @returns {Promise<{totalReservas: number, reservasConfirmadas: number, reservasCompletadas: number, reservasCanceladas: number, noShows: number, ingresosTotales: number, ingresosVisa: number, ingresosEfectivo: number, ingresoPromedioPorReserva: number, tasaOcupacion: number, tasaNoShow: number}>}
   */
  async getStats(start, end) {
    const res = await api.get('/admin/dashboard', { params: { inicio: start, fin: end } });
    return unwrap(res);
  },

  /**
   * @param {string} start
   * @param {string} end
   * @param {number} limit
   * @returns {Promise<Array<{servicioId: number, servicioNombre: string, precio: number, totalReservas: number, ingresosGenerados: number}>>}
   */
  async getTopServicios(start, end, limit = 5) {
    const res = await api.get('/admin/dashboard/top-servicios', { params: { inicio: start, fin: end, limit } });
    const data = unwrap(res);
    return Array.isArray(data) ? data : [];
  },

  /** Workers CRUD (backend returns the full active list — filter/paginate client-side) */
  async getTrabajadores() {
    const res = await api.get('/admin/trabajadores');
    const data = unwrap(res);
    return Array.isArray(data) ? data : [];
  },

  async createTrabajador(payload) {
    const res = await api.post('/admin/trabajadores', payload);
    return unwrap(res);
  },

  async updateTrabajador(id, payload) {
    const res = await api.put(`/admin/trabajadores/${id}`, payload);
    return unwrap(res);
  },

  /**
   * Elimina un trabajador. Si tiene reservas el backend lo desactiva en vez
   * de fallar, y siempre responde con un `message` explicativo.
   * @returns {Promise<{success?: boolean, message?: string}>} cuerpo completo de la respuesta
   */
  async deleteTrabajador(id) {
    const res = await api.delete(`/admin/trabajadores/${id}`);
    return res.data;
  },

  /** Toggle active — backend has no PATCH endpoint, so PUT the full DTO */
  async toggleTrabajadorActivo(trabajador) {
    const res = await api.put(`/admin/trabajadores/${trabajador.id}`, {
      id: trabajador.id,
      nombre: trabajador.nombre,
      email: trabajador.email,
      activo: !trabajador.activo,
      // El update del backend reemplaza `foto` tal cual: sin enviarla se perdería
      foto: trabajador.foto || '',
    });
    return unwrap(res);
  },

  /** Services CRUD (backend returns the full list) */
  async getServicios() {
    const res = await api.get('/admin/servicios');
    const data = unwrap(res);
    return Array.isArray(data) ? data : [];
  },

  async createServicio(payload) {
    const res = await api.post('/admin/servicios', payload);
    return unwrap(res);
  },

  async updateServicio(id, payload) {
    const res = await api.put(`/admin/servicios/${id}`, payload);
    return unwrap(res);
  },

  /**
   * Elimina un servicio. Si tiene reservas el backend lo desactiva en vez
   * de fallar, y siempre responde con un `message` explicativo.
   * @returns {Promise<{success?: boolean, message?: string}>} cuerpo completo de la respuesta
   */
  async deleteServicio(id) {
    const res = await api.delete(`/admin/servicios/${id}`);
    return res.data;
  },

  /** Reservations (backend ignores filters — filter client-side) */
  async getAllReservas() {
    const res = await api.get('/admin/reservas');
    const data = unwrap(res);
    return Array.isArray(data) ? data : [];
  },

  async updateReserva(id, payload) {
    const res = await api.put(`/admin/reservas/${id}`, payload);
    return unwrap(res);
  },

  /** Reviews CRUD (admin-managed, shown on the landing page) */
  /** @returns {Promise<Resena[]>} */
  async getResenas() {
    const res = await api.get('/admin/resenas');
    const data = unwrap(res);
    return Array.isArray(data) ? data : [];
  },

  /**
   * @param {{autor: string, puntuacion: number, texto: string}} payload
   * @returns {Promise<Resena>}
   */
  async createResena(payload) {
    const res = await api.post('/admin/resenas', payload);
    return unwrap(res);
  },

  /**
   * @param {number} id
   * @param {{autor: string, puntuacion: number, texto: string}} payload
   * @returns {Promise<Resena>}
   */
  async updateResena(id, payload) {
    const res = await api.put(`/admin/resenas/${id}`, payload);
    return unwrap(res);
  },

  /** @param {number} id */
  async deleteResena(id) {
    await api.delete(`/admin/resenas/${id}`);
  },

  /** Results gallery CRUD (admin-managed photos, shown on the landing page) */
  /** @returns {Promise<Resultado[]>} */
  async getResultados() {
    const res = await api.get('/admin/resultados');
    const data = unwrap(res);
    return Array.isArray(data) ? data : [];
  },

  /**
   * @param {{url: string, titulo?: string}} payload
   * @returns {Promise<Resultado>}
   */
  async createResultado(payload) {
    const res = await api.post('/admin/resultados', payload);
    return unwrap(res);
  },

  /**
   * @param {number} id
   * @param {{url: string, titulo?: string}} payload
   * @returns {Promise<Resultado>}
   */
  async updateResultado(id, payload) {
    const res = await api.put(`/admin/resultados/${id}`, payload);
    return unwrap(res);
  },

  /** @param {number} id */
  async deleteResultado(id) {
    await api.delete(`/admin/resultados/${id}`);
  },

  /** Local image uploads (files live under `/uploads/{carpeta}`, see MediaService) */
  /**
   * Lista las imágenes de una carpeta.
   * @param {'trabajadores'|'servicios'|'resultados'} carpeta
   * @returns {Promise<MediaFile[]>}
   */
  async listMedia(carpeta) {
    const res = await api.get(`/admin/media/${encodeURIComponent(carpeta)}`);
    const data = unwrap(res);
    return Array.isArray(data) ? data : [];
  },

  /**
   * Sube una imagen (multipart, campo `archivo`).
   * El `Content-Type` se fuerza a `multipart/form-data` porque la instancia
   * define `application/json` por defecto y axios transformaría el FormData a JSON.
   * @param {'trabajadores'|'servicios'|'resultados'} carpeta
   * @param {File} file archivo seleccionado por el usuario
   * @returns {Promise<MediaFile>} `nombre` y `url` del archivo guardado
   */
  async uploadMedia(carpeta, file) {
    const body = new FormData();
    body.append('archivo', file);
    const res = await api.post(`/admin/media/${encodeURIComponent(carpeta)}`, body, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return unwrap(res);
  },

  /**
   * @param {'trabajadores'|'servicios'|'resultados'} carpeta
   * @param {string} nombre nombre del archivo dentro de la carpeta
   * @returns {Promise<void>}
   */
  async deleteMedia(carpeta, nombre) {
    await api.delete(
      `/admin/media/${encodeURIComponent(carpeta)}/${encodeURIComponent(nombre)}`
    );
  },
};

export default api;
