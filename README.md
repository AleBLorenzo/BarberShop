# TheTankBarber 💈

Aplicación completa de gestión y reserva de citas para barbería: **landing pública con reseñas y contacto, reserva en línea, panel del trabajador y dashboard de administración**, con autenticación JWT y API REST.

| | |
| --- | --- |
| **Marca** | TheTankBarber (fuente única: `frontend/src/config/business.js` ↔ `app.business.name`) |
| **Backend** | Spring Boot 3.3 · Java 21 · Spring Security (JWT) · Spring Data JPA · PostgreSQL (dev y prod) |
| **Frontend** | React 19 · Vite 8 · React Router 7 · Axios · react-hook-form · date-fns · CSS custom properties |
| **Licencia** | MIT |

## Estructura del proyecto

```
BarberShop/
├── backend/          # API REST (Spring Boot, Maven Wrapper)
│   └── src/main/java/com/barbershop/backend/
│       ├── config/       # CORS, JPA, JWT, semillas de desarrollo
│       ├── controller/   # publico/ · worker/ · admin/
│       ├── service/      # Reserva, disponibilidad, cliente…
│       ├── repository/ · model/ · dto/ · security/
│       └── util/         # SlotGenerator (horarios)
├── frontend/         # SPA React + Vite (ver frontend/README.md)
├── LICENSE           # MIT
└── README.md
```

## Arranque rápido

**Requisitos:** JDK 21+, Node.js 18+, (no hace falta Maven: se usa `./mvnw`).

### 1. Backend — `http://localhost:8080`

```bash
cd backend
./mvnw spring-boot:run
```

Arranca con el perfil **`dev`** por defecto: base **PostgreSQL local** (`jdbc:postgresql://localhost:5432/barbershop`) y **datos de demo** sembrados automáticamente (`DataSeeder`).Levanta la base antes con `docker compose up -d` en `backend/` o usa una PostgreSQL nativa (ver `backend/README-dev-db.md`).

### 2. Frontend — `http://localhost:5173`

```bash
cd frontend
npm install
npm run dev
```

La URL de la API se configura en `frontend/.env`:

```bash
VITE_API_BASE_URL=http://localhost:8080/api
```

## Credenciales de demo (perfil `dev`)

| Rol | Email | Contraseña |
| --- | --- | --- |
| Administrador | `admin@barbershop.com` | `admin12345` |
| Trabajadora | `ana@barbershop.com` | `ana123456` |
| Trabajador | `luis@barbershop.com` | `luis123456` |

- El backend siembra **9 servicios** (catálogo real), 3 trabajadores, 4 fotos de ejemplo de la galería y reservas de la semana en curso (nunca en días cerrados).
- La cita demo de "Mis citas" imprime su **token de cancelación** como UUID en la consola del backend (cambia en cada arranque).
- PostgreSQL conserva los datos entre reinicios: el `DataSeeder` solo siembra la primera vez, cuando la base está vacía.

> **Nota sobre roles:** el backend no distingue roles de negocio — todo trabajador autenticado obtiene `ROLE_WORKER` y el acceso a `/api/admin/**` requiere ese rol. En la práctica, **cualquier login de staff accede a ambos paneles** (Administración ↔ Mi panel, enlazados entre sí en el sidebar).

- Ambos paneles incluyen **«Inicio»** como primer enlace del sidebar para volver a la portada pública.

## Funcionalidades

### Catálogo de servicios (sembrado en `dev`)

| Servicio | Precio | Duración |
| --- | --- | --- |
| Corte | 15 € | 30 min |
| Corte + barba | 20 € | 45 min |
| Corte clásico | 12 € | 30 min |
| Arreglo de barba | 12 € | 30 min |
| Niños | 15 € | 30 min |
| Afeitado tradicional | 10 € | 30 min |
| Cejas | 5 € | 15 min |
| Corte clásico con cejas | 17 € | 30 min |
| Barba con cejas | 17 € | 30 min |

### Público (sin sesión)
- **Landing** (`/`): servicios, equipo, **resultados** (galería de fotos de clientes en blanco y negro, gestionada por URL desde el panel), **reseñas** (nota media, top 6 y botón «Escríbenos en Google Maps») y **contacto** real (dirección, teléfono, email, horario).
- **Reserva en 4 pasos**: servicio → trabajador → fecha y hora → confirmación con nombre, teléfono y **email obligatorio** (validado antes de crear la reserva).
  - Disponibilidad en tiempo real (slots por **duración del servicio**, rejilla alineada hacia atrás desde el cierre: con 9:00–20:00 la última cita es 19:30 con 30 min y 19:15 con 45 min), sin horas pasadas para hoy, con reserva de fila anti doble-reserva.
  - Recibo con todos los datos + **token de cancelación** (copiable, sobrevive recargas) y descarga **`.ics`** para añadir la cita al calendario del móvil con alarma 30 min antes.
- **Mis citas** (`/mis-citas`): consultar/cancelar/reprogramar con teléfono + token (`/reprogramar/:token`).
- **Horario real**: lunes a sábado 9:00–20:00; **domingos cerrado** (la API devuelve `[]` y rechaza crear/reprogramar con 400).
- **Páginas legales**: `/legal/privacidad`, `/legal/terminos`, `/legal/cookies` + aviso de cookies (una vez, `localStorage`).

### Trabajador (`/trabajador`, JWT)
- Dashboard con sus citas **agrupadas por día de la semana** (lunes→sábado), con **navegación por semanas** (anterior / actual / siguiente y rango de fechas visible); los domingos la barbería está cerrada.
- Cambio de estado de citas (confirmar, completar, **no asistió**, cancelar): al marcar **COMPLETADA** un modal pregunta **«¿Vino el cliente?»** y exige elegir el **método de pago (VISA / efectivo)**; con email se envía, una sola vez, una **invitación a dejar reseña** en Google Maps.
- Ingresos de la semana con **desglose por método de pago** (VISA vs. efectivo).
- Perfil (`/trabajador/perfil`) **editable**: nombre, email y foto (subida al momento).

### Administración (`/admin`, JWT)
- Dashboard con estadísticas (`/api/admin/dashboard?inicio&fin`), ingresos totales **desglosados por método de pago**, top servicios.
- CRUD de trabajadores (alta/baja, activo), servicios, reservas, **reseñas** y **resultados** (`/admin/resultados`: fotos de la galería por URL, con miniatura y validación `http(s)://`).
- **Borrado seguro**: si el servicio o el trabajador tiene reservas, el backend lo **desactiva** en vez de fallar y el toast muestra el mensaje concreto (p. ej. «El servicio tiene reservas; se desactivó»).
- Búsqueda, filtros y paginación en cliente (la API devuelve listas completas).

## API REST

Base: `http://localhost:8080/api` — respuestas con envoltorio `{ success, message, data, errorCode }`.

### Auth — `/api/auth`
| Método | Ruta | Descripción |
| --- | --- | --- |
| POST | `/login` | Login → access + refresh token |
| POST | `/refresh` | Renovar access token |

### Público — `/api/public`
| Método | Ruta | Descripción |
| --- | --- | --- |
| GET | `/servicios` · `/trabajadores` | Catálogo activo |
| GET | `/resenas` | Reseñas de la portada (más recientes primero) |
| GET | `/resultados` | Galería «Resultados» de la portada (más recientes primero) |
| POST | `/disponibilidad` | Slots (`fecha`, `servicioId`, `trabajadorId?`) — `[]` en días cerrados |
| POST | `/reservas` | Crear reserva (`clienteEmail` obligatorio en el formulario; devuelve `tokenCancelacion`) |
| GET | `/reservas?telefono&token` | Mis citas |
| POST | `/reservas/cancelar` · `/reservas/reprogramar` | Gestión con token |

### Trabajador — `/api/worker` (JWT)
| Método | Ruta | Descripción |
| --- | --- | --- |
| GET | `/perfil` | Datos del trabajador logueado |
| PUT | `/perfil` | Actualizar nombre, email y foto (`{ nombre, email, foto? }`) |
| GET | `/reservas` · `/reservas/semana?fechaInicio` | Citas por fecha / semana (lun→sáb; `fechaInicio` = lunes `YYYY-MM-DD`, por defecto la semana en curso) |
| GET | `/dashboard` | Resumen para el panel (incluye `ingresosVisa` / `ingresosEfectivo`) |
| POST | `/reservas` | Crear cita — el trabajador sale del JWT (ignora el id del cuerpo) |
| PUT | `/reservas/{id}/estado` | Cambiar estado — al pasar a `COMPLETADA` es obligatorio `metodoPago=VISA\|EFECTIVO` |

### Administración — `/api/admin` (JWT)
| Recurso | Endpoints |
| --- | --- |
| `/clientes` | GET/POST · GET/PUT/DELETE `/{id}` |
| `/trabajadores` | GET/POST · GET/PUT/DELETE `/{id}` — DELETE: con reservas **desactiva** y devuelve `message` |
| `/servicios` | GET/POST · GET/PUT/DELETE `/{id}` — DELETE: con reservas **desactiva** y devuelve `message` |
| `/resenas` | GET/POST · GET/PUT/DELETE `/{id}` (puntuación 1–5 validada) |
| `/resultados` | GET/POST · GET/PUT/DELETE `/{id}` (URL `https?://…` ≤ 500 o ruta `/uploads/…`) |
| `/media/{carpeta}` | GET (listar) · POST `multipart archivo` (subir ≤ 5 MB) · DELETE `/{nombre}` — `carpeta`: `trabajadores`\|`servicios`\|`resultados` |
| `/reservas` | GET/POST · GET/PUT/DELETE `/{id}` |
| Estadísticas | GET `/dashboard` · GET `/dashboard/top-servicios` |

## Configuración relevante (`backend/src/main/resources/`)

| Propiedad | Valor por defecto | Descripción |
| --- | --- | --- |
| `app.jwt.secret` | secreto por defecto (`JWT_SECRET` env.) | Clave HS256 (≥256 bits) |
| `app.jwt.expiration` / `refresh-expiration` | 24 h / 7 días | Vigencia de tokens |
| `app.business.opening-hour` / `closing-hour` | 9 / 20 | Horario de la barbería (lun–sáb) |
| `app.business.closed-days` | `SUNDAY` | Días cerrados (lista de `DayOfWeek`, coma separada) |
| `app.business.slot-interval-minutes` | 30 | Rejilla mínima de huecos |
| `app.business.name` | `TheTankBarber` | Marca visible: correos, `.ics`, títulos |
| `app.business.maps-url` | enlace de Google Maps | Mismo enlace que el botón de la web |
| `app.booking.max-advance-days` | 60 | Antelación máxima |
| `app.cors.allowed-origins` | `localhost:5173` etc. | Orígenes permitidos |
| `app.security.login.lockout.*` | `5` / `900` / `2` / `86400` (`APP_SECURITY_LOGIN_LOCKOUT_*`) | Lockout progresivo por cuenta con backoff exponencial (§ abajo) |
| `app.security.bot.honeypot.enabled` | `true` (`APP_SECURITY_BOT_HONEYPOT_ENABLED`) | Honeypot `website` en el login (tolerado si falta) |
| `app.security.bot.challenge.enabled` | `false` (`APP_SECURITY_BOT_CHALLENGE_ENABLED`) | Reto `X-Login-Challenge` (solo la SPA lo envía) |
| `app.security.turnstile.enabled` | `false` (`APP_SECURITY_TURNSTILE_*`) | CAPTCHA Turnstile opcional (sin claves = sin widget) |
| `app.security.login.audit.enabled` | `true` (`APP_SECURITY_LOGIN_AUDIT_ENABLED`) | Auditoría `event=login_failed` de cada fallo de login |
| `app.client-dedupe.merge-duplicates` | `true` (`CLIENT_DEDUPE_MERGE_DUPLICATES` env.) | Fusiona duplicados históricos de clientes al arrancar |
| `app.mail.enabled` | `true` (`MAIL_ENABLED` env.) | Habilita el correo de confirmación |
| `app.mail.from` | `notificaciones@barbershop.local` (`MAIL_FROM` env.) | Remitente |

Si cambias el puerto u origen del frontend, actualiza `app.cors.allowed-origins` — sin eso el navegador bloquea todas las llamadas (los fallos CORS no aparecen con `curl`).

### Deduplicación de clientes en el arranque

La identidad del cliente es su **teléfono normalizado** (solo dígitos, sin `+34`/`34`) y su **email en minúsculas**: reservar varias veces con formatos distintos (`612345678`, `+34612345678`, `612-345-678`, `Foo@x.com`) reutiliza siempre la misma ficha. Como no hay Flyway (`ddl-auto=update`), esa normalización se aplica al arrancar mediante `ClientDedupeBackfill` (`ApplicationRunner`, transaccional e idempotente):

- Completa la columna única `clientes.telefono_normalizado` en las filas existentes (el teléfono original tal como lo escribió el usuario **no se toca**).
- Si varias fichas históricas comparten el mismo teléfono en distinto formato, las **fusiona**: conserva la que tiene más reservas (empate → la más antigua → id menor), le completa `nombre`/`email` solo si estaban vacíos, **re-apunta sus reservas** (`reservas.cliente_id`) y borra las redundantes. Cada fusión se registra en log con todos los ids y sus conteos (`Fusión de clientes duplicados: …` / `Clientes fusionados: N`).
- Sin duplicados ni formatos sin normalizar no escribe nada; en arranques sucesivos tampoco (idempotente).

| Propiedad | Efecto |
| --- | --- |
| `app.client-dedupe.merge-duplicates=true` (por defecto) | Normaliza **y** fusiona duplicados. |
| `app.client-dedupe.merge-duplicates=false` | **Kill-switch**: solo normaliza; nunca borra ni re-apunta fichas. |

> ⚠️ **Antes del primer despliegue con la fusión activa**, haz una copia de seguridad — la fusión es la única parte destructiva (mueve reservas y borra fichas):
>
> ```bash
> pg_dump -Fc -d <nombre_base> -f barbershop_antes_dedupe.dump
> ```
>
> Rollback: revertir el código basta — la columna nueva es aditiva e inocua para la versión anterior (las fusiones hechas no se deshacen).

### Seguridad del login: lockout progresivo, anti-bot y auditoría

Endurecimiento añadido por el cambio `harden-sql-injection-anti-bot` (detalle, greps y Open Questions en [docs/security-audit.md](docs/security-audit.md) §6):

- **Lockout progresivo por cuenta** (bucket solo email, sin IP): 5 fallos → bloqueo **429 + `Retry-After`** con `code=ACCOUNT_LOCKED` (15 min), y cada fallo posterior durante el bloqueo lo alarga ×2 (6.º → 30 min, 7.º → 1 h, … tope 24 h). Un login acertado lo resetea. La comprobación ocurre **antes** de verificar la contraseña; los buckets `email+IP` e `IP` preexistentes no cambian. Ajustable por `APP_SECURITY_LOGIN_LOCKOUT_*`.
- **Capas anti-bot**: honeypot `website` **ON** por defecto (relleno → 401 genérico sin verificar credenciales), reto `X-Login-Challenge` y CAPTCHA Turnstile **OFF** por defecto (se activan por env var `APP_SECURITY_BOT_CHALLENGE_ENABLED` / `APP_SECURITY_TURNSTILE_ENABLED` + `VITE_*` en build), fingerprint `suspect_ua` informativo y `limit_req` de nginx (5 r/min + `burst=5`) solo en `/api/auth/login`.
- **Auditoría**: una línea WARN por intento fallido — `event=login_failed email=… ip=… reason=…` (`BAD_CREDENTIALS` · `ACCOUNT_LOCKED` · `RATE_LIMITED` · `HONEYPOT` · `CHALLENGE_FAILED`) — más `event=login_ok` en los aciertos. Desactivable con `APP_SECURITY_LOGIN_AUDIT_ENABLED=false`.
- **SQL injection**: sin vulnerabilidad hallada; congelada por tests de regresión (`SqlInjectionRegressionTest`, `SqlPayloadEndpointTest`) y escaping de comodines `%`/`_` en las búsquedas `LIKE`.
- **nginx**: al tocar `deploy/nginx-barbershop.conf`, validar siempre con `sudo nginx -t` **antes de** `sudo systemctl reload nginx`.

Todas las claves son opcionales y sus defaults son los de la tabla de arriba: rollback por env var sin redeploy de código.

### Correo de confirmación de cita

Al crear una reserva con `clienteEmail` (**obligatorio y validado en el formulario**, formato validado en servidor → 400 si falta o es inválido) se envía, **tras el commit y en segundo plano** (`@Async` + `@TransactionalEventListener(AFTER_COMMIT)`), un HTML con todos los datos de la cita y un adjunto **`cita.ics`** (`text/calendar`) para añadirla al calendario del cliente con recordatorio 30 min antes. El cliente se resuelve teléfono → email → crear (nunca se duplica por email).

Configuración SMTP **exclusivamente por variables de entorno** (p. ej. `backend/.env`, **no commiteado**): nunca escribas credenciales reales en el repositorio; sin `MAIL_HOST` la app funciona en modo log.

| Variable | Descripción |
| --- | --- |
| `MAIL_HOST` | SMTP. **Vacío por defecto → modo log**: no se intenta enviar y se registra `[MODO LOG] ...` |
| `MAIL_PORT` | Por defecto `587` (STARTTLS) |
| `MAIL_USER` / `MAIL_PASS` | Credenciales SMTP (solo desde el entorno) |
| `MAIL_AUTH` / `MAIL_STARTTLS` | `false` / `true` por defecto |
| `MAIL_ENABLED` | `false` desactiva el envío aunque haya SMTP |
| `MAIL_FROM` | Remitente |

Cualquier fallo SMTP se traga con `log.warn`: **la reserva nunca falla por un correo**.

Además, cuando el trabajador marca una reserva como **COMPLETADA** (transición de entrada, una sola vez) se envía una **invitación a dejar reseña** con botón al enlace de `app.business.maps-url` — el mismo que muestra la web.

### Datos de contacto y marca (fuente única)

| Dato | Valor |
| --- | --- |
| Marca | **TheTankBarber** (`app.business.name` ↔ `frontend/src/config/business.js`) |
| Email | `richelbarber2024@gmail.com` |
| Teléfono | `653812324` |
| Dirección | Av. Antonio Dominguez, 20, 38650 Arona, Santa Cruz de Tenerife |
| Google Maps | `https://maps.app.goo.gl/12BLsdYKtLRCxFRe8` (`app.business.maps-url`) |
| Horario | Lunes a sábado 9:00–20:00 · **domingos cerrado** |

> **KEEP IN SYNC:** al cambiar cualquiera de estos valores, actualiza `frontend/src/config/business.js`, `backend/.../application.properties`, los asuntos/cuerpos de `EmailService` y este README.

### Reseñas

Las reseñas son **locales** (CRUD de administración en `/admin/resenas`, sin API de Google Places): la portada muestra nota media, top 6 y un botón «Escríbenos en Google Maps» que apunta al enlace real. Si no hay reseñas, la sección muestra un estado vacío amable.

### Resultados (galería)

La galería «Resultados» muestra trabajos a clientes en la landing (sección *Nuestros resultados*) **en su color original** (el blanco y negro es solo para la interfaz; petición del cliente 30-sep-2026). Las fotos se gestionan por **URL externa o archivo local**: CRUD en `/admin/resultados` con validación `https?://…` (≤ 500 caracteres) o ruta `/uploads/resultados/…`, y título opcional (≤ 100). Si una imagen no carga, se reserva el hueco con un contenedor gris y la cuadrícula no se rompe. El seeder incluye 4 fotos de ejemplo (`picsum.photos`), sustituibles desde el panel.

### Fotos locales (trabajadores, servicios y galería)

Todas las fotos se guardan **en local**, en carpetas del backend (creadas automáticamente al arrancar y fuera de git):

| Contenido | Carpeta |
| --- | --- |
| Fotos de los trabajadores | `backend/uploads/trabajadores/` |
| Fotos de los cortes (servicios) | `backend/uploads/servicios/` |
| Fotos de la galería «Resultados» | `backend/uploads/resultados/` |

Dos formas de añadirlas:

1. **Desde el panel** (recomendada): Admin → *Trabajadores* o *Servicios* → alta/edición → campo **Foto** → *Elegir archivo* (se sube al momento) u *…o elegir de la carpeta* (lista lo que ya copiaste). En *Resultados* ocurre igual junto al campo de URL.
2. **Copiando archivos** a la carpeta de la tabla anterior: aparecen en el desplegable del panel. Mejor sin espacios ni caracteres especiales en el nombre (si los tienen, el sistema los sanea al subir; los copiados a mano se sirven igual).

Los archivos se publican como `GET /uploads/{carpeta}/{archivo}` (públicos, sin token; los endpoints de gestión requieren JWT). Límite de subida: **5 MB** por archivo, formatos `jpg/jpeg/png/webp/gif/svg/avif`.

### Logo

El logo oficial (**The Tank Barber**, disco negro sobre blanco) está en `frontend/public/logo.png` y lo usa el componente `Logo` en la cabecera pública y el sidebar, con reserva en cadena: `/logo.png` → `/logo.svg` → el texto `✂ TheTankBarber` si aún no hay archivo. También es el **favicon**. Para cambiarlo: reemplaza el archivo manteniendo el nombre (o dime el formato y lo cambio yo).

### Diseño visual (blanco y negro)

Identidad **monocroma minimalista** en todas las superficies (pública y paneles): tokens en `frontend/src/App.css` (`--accent: #0a0a0a`, fondo blanco, el modo oscuro invierte el par), sin colores saturados —badges y toasts en gris, el texto comunica el estado— y tipografía **Oswald** (titulares) + **Archivo** (cuerpo) vía Google Fonts con fallback a `system-ui`. **La fotografía se muestra en color original** (sin filtros): el B/N aplica solo a la interfaz. El dorado `#b8860b` se reserva para los **correos** y `frontend/index.html` fija `theme-color` negro. Contraste reforzado: controles con borde `--border-strong` (≥3:1 sobre blanco) y el CTA «Reservar Cita» del hero usa texto negro literal para no perderse en modo oscuro.

### Páginas legales y cookies

- `/legal/privacidad` — tratamiento real de datos (nombre, teléfono obligatorios; email opcional solo para confirmación/invitación), derechos RGPD y contacto.
- `/legal/terminos` — reservas por disponibilidad real, token como única vía de cancelación/reprogramación, antelación máxima 60 días.
- `/legal/cookies` — **no hay cookies** (ni propias ni de terceros): solo `localStorage` (`refreshToken`) y `sessionStorage` (`bookingDraft`, `lastReserva`), reflejado exactamente en la tabla de la política. La política declara además los recursos de terceros reales (Google Fonts; Turnstile condicional, OFF por defecto).
- Sin **banner de cookies**: al no haber cookies no esenciales, no se requiere consentimiento (LSSI-CE art. 22.2). La política se descubre desde el enlace «Política de cookies» del footer (`/legal/cookies`).

## Scripts del frontend

| Script | Descripción |
| --- | --- |
| `npm run dev` | Desarrollo con HMR (`:5173`) |
| `npm run build` | Build de producción en `dist/` |
| `npm run lint` | ESLint |
| `npm run preview` | Vista previa del build |

Documentación detallada del frontend: [`frontend/README.md`](frontend/README.md).

## Notas para desarrollo

- **Devtools:** el backend usa Spring DevTools; tras `./mvnw compile` se reinicia solo. Si editas Java con **VS Code** y el backend muere con `NoClassDefFoundError`, comprueba `grep -rl "Unresolved compilation" target/classes/` — la Java Language Server puede pisar clases con stubs de error; cura con `./mvnw clean compile` (o *Java: Clean Java Language Server Workspace*).
- **Cambios en el frontend con dev server activo:** si Vite sirve código viejo tras ediciones rápidas, reinícialo — compara `curl http://localhost:5173/src/...` con el disco para detectar caché stale.

## Licencia

[MIT](LICENSE) © Jorge Alejandro Brito Lorenzo
