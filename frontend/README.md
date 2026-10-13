# BarberShop — Frontend

Aplicación React + Vite para la barbería: landing pública, reserva de citas en 4 pasos, panel del trabajador y dashboard de administración, protegidos con JWT.

## Requisitos

- Node.js 18+
- Backend Spring Boot ejecutándose en `http://localhost:8080` (ver `../backend`)

## Puesta en marcha

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

## Scripts

| Script          | Descripción                                     |
| --------------- | ----------------------------------------------- |
| `npm run dev`   | Servidor de desarrollo con HMR                  |
| `npm run build` | Build de producción en `dist/`                  |
| `npm run lint`  | ESLint (reglas de react-hooks y react-refresh)  |
| `npm run preview` | Vista previa del build en `dist/`             |

## Variables de entorno

Copia `.env` (incluido por defecto) y ajusta según tu entorno:

```bash
VITE_API_BASE_URL=http://localhost:8080/api   # URL base de la API Spring Boot
```

## Rutas principales

| Ruta                   | Descripción                                     |
| ---------------------- | ----------------------------------------------- |
| `/`                    | Landing pública (servicios, equipo, contacto)   |
| `/reservar/servicio`   | Reserva — paso 1: servicio                      |
| `/reservar/trabajador` | Reserva — paso 2: trabajador (o cualquiera)     |
| `/reservar/fecha-hora` | Reserva — paso 3: fecha y hora                  |
| `/reservar/confirmar`  | Reserva — paso 4: datos del cliente y confirmación |
| `/mis-citas`           | Consulta de citas con teléfono + token          |
| `/reprogramar/:token`  | Reprogramación de una cita existente            |
| `/login`               | Acceso de trabajadores y administración         |
| `/trabajador`          | Panel semanal del trabajador (protegido)        |
| `/admin`               | Dashboard de administración (rol ADMIN)         |

## Arquitectura

- **Routing**: `react-router-dom` v6 con layouts por rol (`PublicLayout`, `DashLayout`) y `ProtectedRoute`
- **Estado**: React Context (`AuthContext`, `BookingContext`) + hooks propios, sin Redux
- **HTTP**: axios con interceptor de peticiones (token Bearer) y de respuesta (refresh 401 con cola de reintentos)
- **Formularios**: `react-hook-form` con validación española de teléfono
- **Estilos**: CSS puro con variables de diseño en `App.css` (sin frameworks)
- **Fechas**: `date-fns` con locale `es`
- **Errores de tipo**: JSDoc en lugar de TypeScript (`src/services/api.js` define los tipos de la API)
