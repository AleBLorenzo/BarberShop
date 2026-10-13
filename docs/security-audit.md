# Auditoría de seguridad — TheTankBarber

- **Fecha:** 2026-10-01
- **Nota (2026-10-12):** auditoría histórica, escrita cuando el backend usaba H2. Desde la migración `migrate-h2-to-postgresql` el runtime es 100 % PostgreSQL y la consola H2 ya no existe; las menciones a H2 describen el estado en esa fecha.
- **Nota (2026-10-12 — cierre):** el cambio [`harden-open-security-findings`](../openspec/changes/harden-open-security-findings/proposal.md) cierra/parcialmente cierra varios hallazgos de esta auditoría: **B-MED-3, B-MED-5, B-MED-6, B-BAJO-6, B-INFO-1** (cerrados) y **B-MED-10, B-BAJO-4, B-BAJO-7, B-INFO-2** (parcialmente cerrados). Detalle en §2 ("Cierre de hallazgos — 2026-10-12").
- **Alcance:** backend Spring Boot (`backend/`, API REST + JWT) y frontend React/Vite (`frontend/`, SPA)
- **Metodología:** revisión manual con checklist alineada a OWASP ASVS L1 + `npm audit` + revisión de versiones de dependencias (`versions:display-dependency-updates`). No se ejecutó DAST (ZAP) ni pentest externo: la superficie real es una API REST + SPA desplegada tras un dominio único.
- **Estado global:** 1 hallazgo crítico corregido en este mismo cambio; ver severidades abajo.
- **Repetir:** antes de cada despliegue (§"Cómo repetir esta auditoría" al final).

---

## 1. Dependencias (tarea 4.1)

### Frontend — `npm audit`

```
found 0 vulnerabilities
(información: info 0 · low 0 · moderate 0 · high 0 · critical 0)
```

### Backend — versiones declaradas y actualizaciones disponibles

| Componente | Versión actual | Última disponible | Notas |
|---|---|---|---|
| Spring Boot (parent) | 3.3.2 (jul 2024) | rama 3.3.x/3.4.x+ con parches posteriores | Actualizar a la última parche de la rama usada antes de producción |
| jjwt (api/impl/jackson) | 0.12.5 | 0.13.0 | 0.13 trae cambios de API; valorar en tarea de mantenimiento |
| PostgreSQL driver | 42.7.3 | 42.7.13 | Parches de la línea 42.7.x — recomendado |
| logback | 1.5.6 (via Boot) | 1.6.5 | Se actualiza subiendo el parent de Spring Boot |
| jackson | 2.17.2 (via Boot) | 2.22.x | Se actualiza subiendo el parent de Spring Boot |
| H2 (solo dev) | 2.2.224 | 2.5.252 | Base de desarrollo; no expuesta en producción (PostgreSQL) |
| springdoc-openapi | 2.5.0 | 3.1.1 | Swagger queda `permitAll` — ver hallazgo B-INFO-3 |

- **No se ejecutó** `mvn org.owasp:dependency-check-mojo` (requiere descarga de la base NVD; bloquea en entorno sin red garantizada). **Recomendación:** añadirlo al CI con actualización periódica de la caché NVD.
- **Conclusión:** sin CVEs conocidos *críticos* en dependencias directas; el riesgo se gestiona con la actualización de parche de Spring Boot antes de desplegar (hallazgo B-BAJO-1).
- **Actualizado (2026-10-12, cambio `harden-open-security-findings`):** Spring Boot parent → **3.3.13** (última parche 3.3.x), jjwt → **0.12.7** (se descarta 0.13 por cambios de API), springdoc → **2.6.0** (última 2.x compatible con Boot 3.3.x; 2.7+ exige Spring Framework 6.2+/Boot 3.4+) y `spring-boot-devtools` **excluido del fat jar**. La tabla de arriba describe el estado en la fecha de la auditoría.

---

## 2. Hallazgos backend (tarea 4.2)

Metodología: revisión estática del código fuente con checklist OWASP (JWT, protección de endpoints, validación, inyección, CORS, headers, errores, rate limiting, uploads, secretos en logs, IDOR), complementada con verificación dinámica por API (batería de re-test §5). Evidencias con `archivo:línea`.

| ID | Sev. | Hallazgo | Estado |
|---|---|---|---|
| B-ALTO-1 | Alto | Secreto JWT con valor por defecto hardcodeado en el repo (`application.properties:25`) → forjación de tokens si falta `JWT_SECRET` | **CORREGIDO** |
| B-ALTO-2 | Alto | Consola H2 tras `permitAll` + perfil `dev` por defecto + `frameOptions` deshabilitado globalmente | **CORREGIDO/CONTENIDO** ↓ |
| B-MED-3 | Medio | Sin revocación/logout de JWT (access 24 h · refresh 7 d) | **CERRADO (2026-10-12)** ↓ |
| B-MED-4 | Medio | Refresh sin validar tipo de token ni cuenta activa; token inválido → 500 | **CORREGIDO** |
| B-MED-5 | Medio | Sin rate limiting ni lockout en `POST /api/auth/login` | **CERRADO (2026-10-12)** ↓ |
| B-MED-6 | Medio | Sin límite/captcha en reserva pública → spam y mail-bombing a terceros | **CERRADO (2026-10-12)** ↓ |
| B-MED-7 | Medio | Token de cancelación registrado en log (`ReservaService`) | **CORREGIDO** |
| B-MED-8 | Medio | `X-Frame-Options` deshabilitado en toda la API, no solo en H2 | **CORREGIDO** |
| B-MED-9 | Medio | Logging verboso (`show-sql`, `hibernate.SQL=DEBUG`, `BasicBinder=TRACE`, `security=DEBUG`) activo en prod | **CORREGIDO** |
| B-MED-10 | Medio | Escritura "último gana" sobre ficha de cliente existente solo con conocer el teléfono (PII + desvío de invitaciones) | **CERRADO parcial (2026-10-12)** ↓ |
| B-BAJO-1 | Bajo | Upload admite SVG sin validar content-type (`MediaService`) | **CORREGIDO** |
| B-BAJO-2 | Bajo | Cualquier WORKER podía borrar fotos de compañeros (DELETE en la excepción de rol) | **CORREGIDO** |
| B-BAJO-3 | Bajo | Excepciones de framework → 500; `printStackTrace()`; `SecurityException` → 500 en vez de 403 | **CORREGIDO** |
| B-BAJO-4 | Bajo | `DataSeeder` imprime credenciales y token demo (perfil `dev`) | **CERRADO parcial (2026-10-12)** ↓ |
| B-BAJO-5 | Bajo | `/api/public/trabajadores` exponía `email` y `rol` de los empleados | **CORREGIDO** |
| B-BAJO-6 | Bajo | Origen CORS de túnel trycloudflare efímero en la allowlist por defecto | **CERRADO (2026-10-12)** ↓ |
| B-BAJO-7 | Bajo | Headers incompletos: sin `Referrer-Policy`/CSP/`Permissions-Policy` | **CORREGIDO (2026-10-12, era parcial)** ↓ |
| B-BAJO-8 | Bajo | Inyección de CR en el adjunto `.ics` vía `clienteNombre` | **CORREGIDO** |
| B-INFO-1 | Info | Swagger/OpenAPI en `permitAll` en cualquier perfil | **CERRADO (2026-10-12)** ↓ |
| B-INFO-2 | Info | Dependencias: Spring Boot 3.3.2, jjwt 0.12.5, springdoc 2.5.0, devtools en runtime | **CERRADO parcial (2026-10-12)** ↓ |
| B-INFO-3 | Info | Claves de `.env` sin efecto (`JWT_EXPIRATION`, `SHOW_SQL`, `DB_HOST/PORT/NAME`, `SERVER_PORT`, `UPLOAD_DIR`) | **CORREGIDO parcial** ↓ |
| B-INFO-4 | Info | Enumeración marginal de cuenta desactivada en login (400 vs 401) | Aceptado (impacto mínimo) |

### Detalle de remediaciones aplicadas (todas verificadas por API)

- **B-ALTO-1** — `application.properties`: `app.jwt.secret=${JWT_SECRET}` **sin valor por defecto** → la app no arranca si falta el secreto (fail-fast). Verificado: el valor del `.env` actual no coincide con el antiguo default del repo (no estaba en uso) y `JWT_SECRET` no aparece en logs. *Rotación adicional recomendada al desplegar por primera vez en prod.*
- **B-ALTO-2** — Contención en profundidad: (1) `spring.h2.console.settings.web-allow-others=false` explícito — **verificado dinámicamente**: la consola responde desde IP LAN con *"remote connections are disabled"*; (2) en prod la consola está desactivada (`application-prod.properties`); (3) `X-Frame-Options` ahora aplicado salvo a la propia consola (ver B-MED-8). Resto aceptado: el `permitAll` de `/h2-console/**` permanece para comodidad dev (la ruta no existe en prod).
- **B-MED-4** — `JwtService` emite claim `type` (`access`/`refresh`); `JwtAuthenticationFilter` solo autentica `access`; `AuthService.refreshToken()` valida `type=refresh`, cuenta activa y traduce excepciones JJWT a **401** (antes 500). *Efecto: los tokens emitidos antes del cambio caducan (re-login).* Verificado: access en `/refresh` → 401, refresh válido → 200, token basura → 401.
- **B-MED-7** — `log.info("Reserva creada: ID={}")` sin el token de cancelación.
- **B-MED-8** — Writer propio: `X-Frame-Options: DENY` en todas las respuestas excepto `/h2-console/**`. Verificado con `curl -I`.
- **B-MED-9** — `spring.jpa.show-sql=${SHOW_SQL:false}` (clave del `.env` ya existente, ahora efectiva) y niveles DEBUG/TRACE movidos de `application.properties` a `application-dev.properties`. Prod: INFO, sin SQL ni parámetros.
- **B-BAJO-1** — `svg` eliminado de `EXTENSIONES_PERMITIDAS`.
- **B-BAJO-2** — La excepción de rol para media es ahora solo `POST /api/admin/media/trabajadores`; el `DELETE` cae en `hasRole("ADMIN")`. Verificado: worker DELETE → 403, worker POST → 200. (Ninguna UI llamaba a `deleteMedia`, cero regresión.)
- **B-BAJO-3** — `printStackTrace()` → `log.error(..., ex)` (`@Slf4j`); handler `SecurityException` → 403; handlers para 405 (método), 415 (media type), 400 (JSON ilegible / `@PathVariable` no convertible). Verificado: 405/400/400 correctos.
- **B-BAJO-5** — `PublicController.getTrabajadores` devuelve `email=null` y `rol=null` (el flujo público solo usa nombre/foto/descripción/`activo`).
- **B-BAJO-7 (parcial)** — Añadido `Referrer-Policy: no-referrer` (mitiga F-BAJO-2: el token de los enlaces de email nunca sale como cabecera `Referer`). Verificado con `curl -I`. *CSP y `Permissions-Policy` quedan documentados como hardening de prod (CSP exige diseño de la SPA).*
- **B-BAJO-8** — `IcsBuilder.escapeIcs` normaliza `\r` suelto además de `\r\n`.

### Cierre de hallazgos — cambio `harden-open-security-findings` (2026-10-12)

Cierre atendido por el cambio OpenSpec [`harden-open-security-findings`](../openspec/changes/harden-open-security-findings/proposal.md) (ver su `design.md` D1–D9). No se reescribe el detalle histórico de arriba; este bloque registra el nuevo estado:

- **B-MED-3 → CERRADO** — Revocación de JWT implementada: tabla `sesion_auth` con claim `sid` en access y refresh, `POST /api/auth/logout` idempotente, validación de sesión en el filtro y en `refresh`, y revocación al desactivar una cuenta. *Efecto: los tokens legacy sin `sid` exigen re-login.* Tests: `JwtLogoutRevocationTest`, `AuthorizationMatrixTest`.
- **B-MED-5 → CERRADO** — Rate limiting en login (`RateLimiterService` en memoria): 5 fallos por cuenta+IP y 20 por IP cada 900 s, solo cuenta fallos, reset al acertar; 429 con `Retry-After`. Test: `LoginRateLimitTest`.
- **B-MED-6 → CERRADO** — Anti-spam en reserva pública: 10 peticiones/hora por IP + techo diario global de 100; 429 sin crear reserva ni enviar email. Tests: `BookingSpamTest`.
- **B-MED-10 → CERRADO parcial** — `ClienteService.findOrCreateByTelefono` ya no sobrescribe ("último gana"): solo completa vacíos, nunca pisa nombre/email existentes. Test: `ClientConsolidationTest`. *Pendiente: confirmación del propietario de la regla de negocio.*
- **B-BAJO-4 → CERRADO parcial** — El seeder imprime credenciales solo con `app.demo.print-credentials=true` (default `false`, controlado por env `DEMO_PRINT_CREDENTIALS`). Test: `DataSeederHygieneTest`. *Resto: decisión de packaging del perfil `dev` en prod sigue pendiente.*
- **B-BAJO-6 → CERRADO** — Allowlist CORS ahora por env `CORS_ALLOWED_ORIGINS` con default SOLO localhost/127.0.0.1; el origen trycloudflare salió del repositorio (queda documentado en `.env.example`). Test: `CorsAllowlistTest`.
- **B-BAJO-7 → CORREGIDO (era parcial)** — Backend: `Permissions-Policy`, HSTS y CSP (`default-src 'none'` en `/api/**`) además del ya existente `Referrer-Policy` y `X-Frame-Options: DENY`. nginx: cabeceras `always` a nivel `server` (CSP de la SPA con Google Fonts permitido); HSTS comentado hasta existir TLS. Tests: `SecurityHeadersTest`. *Verificación visual de la CSP en navegador: pendiente del usuario.*
- **B-INFO-1 → CERRADO** — `springdoc.api-docs.enabled=${API_DOCS_ENABLED:true}` y `false` en el perfil `prod` (`/v3/api-docs` y `/swagger-ui.html` → 404); location de swagger eliminada de nginx. Tests: `ApiDocsProdTest`, `ApiDocsDevTest`.
- **B-INFO-2 → CERRADO parcial** — Bumps efectuados uno a uno con la suite verde tras cada uno: Boot 3.3.13, jjwt 0.12.7, springdoc 2.6.0, devtools fuera del fat jar. *Resto: `versions:display-plugin-updates` y OWASP dependency-check en CI (recomendación 6).*

### Aceptaciones con justificación

- **B-MED-3 (sin revocación de JWT)** — Implementar denylist/`jti`/refresh persistido es un rediseño del subsistema de autenticación fuera del alcance de este cambio. Mitigación existente: expiración de access token reducida vía `.env` a **1 h** (antes hardcodeado 24 h; ahora `JWT_EXPIRATION=3600000` efectivo y `expiresIn` coherente), refresh con rotación, `activo=false` surte efecto inmediato en el filtro. **Trabajo futuro recomendado: logout con revocación.**
- **B-MED-5 (rate limiting login)** — Un limitador correcto requiere decisión de diseño (bucket por IP vs cuenta, backoff, integración con despliegue detrás de proxy) y su propia batería de pruebas; meterlo "en marcha" en este cambio sería código de seguridad sin red de pruebas suficiente. **Queda como recomendación prioritaria #1 del informe.**
- **B-MED-6 (spam de reservas)** — El límite real son los huecos disponibles (60 días); mitigaciones futuras: rate-limit por IP en `/api/public/reservas`, honeypot/CAPTCHA y techo diario de envíos SMTP. **Recomendación prioritaria #2.**
- **B-MED-10 (PII "último gana")** — Es el comportamiento de consolidación de clientes del flujo público: cambiarlo exige regla de negocio (¿sobrescribir con verificación? ¿solo completar vacíos?) que debe decidir el propietario. **Pendiente de decisión del usuario.**
- **B-BAJO-4 (credenciales en consola)** — Solo con `@Profile("dev")` y pensado para el flujo de trabajo local (los logins de prueba se usan a diario). En prod el perfil es `dev`→recomendación de empaquetado abajo.
- **B-BAJO-6 (CORS trycloudflare)** — Origen exacto (no wildcard) con `allowCredentials=false`; es el túnel de demos del desarrollador. Requiere limpieza al definir el dominio de producción.
- **B-INFO-1/2/4** — Swagger desactivable con `springdoc.api-docs.enabled=false` en prod; dependencias sin CVEs críticos conocidos (§1); enumeración de cuenta desactivada solo explotable con la contraseña correcta.

### Checklist OK (evidencia `archivo:línea`)

1. **JWT**: HS256 con clave ≥256 bits (`JwtService:37,58`), `parseSignedClaims` valida firma+exp y rechaza `alg=none` (jjwt 0.12.x); expiración configurable (ahora vía `.env`); filtro ignora tokens inválidos sin romper la cadena; **tokens nunca en logs**.
2. **Autoridad por rol**: `UserDetails` reconstruido desde BD en cada petición con `ROLE_`+`rol` → cambios de rol/`activo` inmediatos.
3. **Endpoints**: `/api/admin/**`→`hasRole(ADMIN)`; `/api/worker/**`→WORKER∪ADMIN; media POST→WORKER∪ADMIN; `anyRequest().authenticated()`; sesión STATELESS; 401/403 JSON escritos sin `sendError`; **sin actuator**; CSRF off coherente con Bearer+CORS sin credenciales.
4. **Validación**: `@Valid` en prácticamente todos los endpoints (excepción: `POST /api/worker/reservas` re-valida server-side en `ReservaService:92-124`).
5. **Inyección**: 0 `nativeQuery`, 0 SpEL, 0 concat SQL; los 14 `@Query` usan JPQL parametrizado; `LIKE` con `CONCAT` parametrizado.
6. **CORS**: sin wildcard (rechazado explícitamente), `allowCredentials=false`, aplicado solo a `/api/**`.
7. **Uploads**: carpetas+extensiones whitelist (ahora sin SVG), 5 MB + límite multipart 6 MB, nombres sanitizados `[^A-Za-z0-9._-]`, sin path traversal, handler de tamaño → 400.
8. **Errores**: respuestas sin stacktraces ni SQL; `DataIntegrityViolation` saneado; `BadCredentials` → 401 genérico; validación → mapa de campos.
9. **Secretos en logs**: verificado por ejecución (§5): 0 claves SMTP, 0 hashes, 0 `password`, 0 tokens JWT, 0 valores `MAIL_PASS`.
10. **IDOR reservas**: token `UUID.randomUUID()` (122 bits) con índice único; lookup exige teléfono+token con 404 idéntico; cambio de estado verifica pertenencia vía JWT; `trabajadorId` del body ignorado (se usa el del token). **Sin IDOR.**
11. **Escalada de privilegios**: `WorkerPerfilUpdateRequest` sin `rol`/`activo`; rol solo editable por ADMIN en `update()`; `create()` nace WORKER; mapper ignora `passwordHash`.
12. **Contraseñas**: BCrypt, `@Size(min=8)` en creación, jamás en respuestas.

### No verificable estáticamente (resuelto por verificación dinámica o pendiente)

- Consola H2 ante orígenes remotos → **verificado dinámicamente: bloqueada** ("remote connections are disabled").
- Mapeo de excepciones de framework a 500 → **verificado: ahora 405/415/400/403**.
- Inyección CR en `.ics` → corregida sin ejecución (normalización determinista).
- No se ejecutó DAST ni fuzzing (fuera de alcance, §Metodología).

## 3. Hallazgos frontend (tarea 4.3)

Metodología: revisión estática de `frontend/src` con checklist OWASP (XSS, almacenamiento de tokens, secretos en bundle, open redirects, enlaces externos, CDN). Ejecutada por revisor independiente; evidencias con `archivo:línea`.

| ID | Sev. | Hallazgo | Estado |
|---|---|---|---|
| F-MED-1 | Medio | Open redirect en login: `?redirect=` usado sin validar (`Login.jsx:25-36`) | **CORREGIDO** |
| F-MED-2 | Medio | Refresh token en `localStorage` (legible por JS; access token sí está solo en memoria, `api.js:109-110`) | Aceptado con justificación ↓ |
| F-BAJO-1 | Bajo | `mediaUrl()` no filtraba esquemas peligrosos en `foto`/`url` (`api.js:23-28`) | **CORREGIDO** |
| F-BAJO-2 | Bajo | `tokenCancelacion` viaja en query/path (`/reprogramar/:token`, `?telefono=&token=`) — visible en logs de servidor/proxies e historial | Aceptado con justificación ↓ |
| F-BAJO-3 | Bajo | `vite.config.js`: `preview.allowedHosts: ['.trycloudflare.com']` (wildcard) — solo afecta a `vite preview` local, no a producción | Aceptado (solo dev) |
| F-INFO-1 | Info | `ErrorBoundary` imprimía el objeto error completo (podía contener `config.headers.Authorization`) | **CORREGIDO** |
| F-INFO-2 | Info | Google Fonts sin SRI en `index.html:7-9` (CSS externo) | Aceptado (recomendación: auto-hospedar) |
| F-INFO-3 | Info | Sin meta CSP en `index.html` — las cabeceras reales se configuran en backend (ver B-INFO cabeceras) | Ver backend |
| F-INFO-4 | Info | `Login.jsx` `minLength: 4` en cliente | Resuelto: el backend **sí** impone `@Size(min=8)` en creación (`TrabajadorCreateRequest.java:26`) |

### Detalle de remediaciones aplicadas

- **F-MED-1 (open redirect)** — `Login.jsx`: el parámetro `redirect` ahora solo se acepta si empieza por `/` y no por `//` (rutas internas); cualquier otro valor cae a `/trabajador`. Cubre ambos usos (`<Navigate>` y `navigate()`).
- **F-BAJO-1 (esquemas)** — `api.js mediaUrl()`: cualquier `javascript:`/`data:`/`vbscript:` devuelve `null`; el resto de valores se comportan igual que antes.
- **F-INFO-1 (consola)** — `ErrorBoundary`: solo se loguea `error.message` + `componentStack`, nunca el objeto Axios completo.

### Aceptaciones con justificación

- **F-MED-2 — refresh token en localStorage**: cambiarlo a cookie `HttpOnly` exige rediseñar el flujo de refresh del backend (cookie + CSRF + `Set-Cookie` en login) y tocar todos los consumidores. Mitigaciones existentes: access token solo en memoria, rotación de refresh en cada renovación (`api.js:217-218`), borrado en logout, y la XSS como vector queda muy reducida (0 sinks XSS — ver Checklist OK). **Decisión: aceptado;** trabajo futuro recomendado si la app expone datos de alto valor.
- **F-BAJO-2 — token en URL**: es el mecanismo de los enlaces de cancelación/reprogramación que llegan por email (diseño del cambio anterior); moverlo a header/body rompería los enlaces ya emitidos. Mitigación recomendada: cabecera `Referrer-Policy: no-referrer` en backend + caducidad del token (24h, ya activa: `app.booking.token-expiration-hours=24`).
- **F-BAJO-3 — allowedHosts**: el wildcard `.trycloudflare.com` es intencional (demos con túnel del desarrollador); no afecta al bundle de producción.
- **F-INFO-2 — Google Fonts sin SRI**: SRI no es viable con Fonts (respuesta variable por UA); auto-hospedar las fuentes queda como mejora.

### Checklist OK (evidencia `archivo:línea`)

1. **XSS**: 0 coincidencias de `dangerouslySetInnerHTML|innerHTML|eval|new Function|document.write|javascript:` en todo `frontend/`. Contenido de usuario (reseñas `Landing.jsx:205-208`, `descripcion` en `WorkerSelector.jsx`, `Profile.jsx`, `Workers.jsx`) renderizado como texto React (escapado). Sin librerías markdown/rich-text en `package.json`. Escape ICS correcto por RFC 5545 (`utils/ics.js`).
2. **Tokens**: access token solo en memoria (`api.js:109`), header Authorization; refresh con rotación single-flight (`api.js:174-218,289`); logout borra (`api.js:143-147`, `AuthContext.jsx:76-79`); ningún token de autenticación en query/path.
3. **Secretos en bundle**: única variable es `VITE_API_BASE_URL` (`api.js:3`); `frontend/.env` contiene solo esa clave (verificado) y está git-ignorado (`.gitignore:26-28`); 0 claves/secretos hardcodeados.
4. **Open redirects**: único punto era `Login.jsx:36` (corregido); el resto de `navigate()` son literales estáticos (14 llamadas); interceptor de refresh usa la ruta actual con `encodeURIComponent`.
5. **Autorización UI**: `isAdmin = user?.rol === 'ADMIN'` con rol real del backend; `user` vive solo en `useState` — nada fabricable desde `localStorage`.
6. **Interceptors**: refresh con cola single-flight (sin bucle), redirect en 401 solo fuera de `/login`, sin `withCredentials` → CSRF N/A (auth por header Bearer).
7. **Enlaces externos**: 0 `postMessage`/`window.open`; los 3 `target="_blank"` llevan `rel="noopener noreferrer"`.
8. **CDN**: sin JS de terceros; solo CSS de Google Fonts (F-INFO-2).

### No verificable estáticamente

- Comportamiento de `navigate()` con `?redirect=//evil.com` en react-router-dom 7 (la remediación lo cierra en cualquier caso).
- Cabeceras HTTP (CSP/HSTS/Referrer-Policy), revocación real de refresh y logs de query-string con `?token=` → se cubren en §2 (backend).

## 4. Resumen y estado (tarea 4.4)

### Totales

| Sev. | Backend | Frontend | Corregidos | Aceptados (con justificación) |
|---|---|---|---|---|
| Crítico | 0 | 0 | — | — |
| Alto | 2 | 0 | **2** | 0 |
| Medio | 8 | 2 | **5** (B-4,7,8,9 + F-1) | 5 |
| Bajo | 7 | 3 | **6** (B-1,2,3,5,8 + B-7 parcial, F-1) | 4 |
| Info | 4 | 5 | 2 (F-1, F-INFO-4 resuelto) + B-INFO-3 parcial | resto |

- **Hallazgo raíz del cambio**: el control de acceso roto (cualquier peluquero era admin de facto) — detectado en la fase de implementación y corregido con el modelo de roles `Rol` (D8 del design); las auditorías posteriores lo dan por verificado (checklist §2.3).

### Recomendaciones pendientes (orden de prioridad)

> **Actualización (2026-10-12, cambio `harden-open-security-findings`):** las recomendaciones 1–5 quedan **atendidas** por ese cambio (rate limiting en login, anti-spam en reserva pública, logout con revocación de JWT, regla de consolidación de clientes "completar vacíos" —pendiente de confirmación del propietario— y endurecimiento de producción completo: perfil prod con Swagger off, CSP/HSTS/Permissions-Policy, CORS por env, devtools fuera del fat jar y Boot actualizado a 3.3.13). Solo queda pendiente la 6 (**CI**: `npm audit` + OWASP dependency-check + batería de autorización). El listado original se conserva abajo tal como se redactó.

1. **Rate limiting en login** (B-MED-5) — bucket por IP+cuenta con backoff.
2. **Límite anti-spam en reserva pública** (B-MED-6) — IP + honeypot + techo diario de emails.
3. **Logout con revocación de JWT** (B-MED-3) — denylist `jti` o refresh persistido con rotación real.
4. **Decisión de negocio sobre PII de clientes** (B-MED-10) — sobrescribir, completar o verificar por email.
5. **Endurecimiento de producción**: perfil `prod` obligatorio (consola H2 off, `springdoc.api-docs.enabled=false`), CSP + HSTS + `Permissions-Policy`, quitar origen trycloudflare de CORS, limpiar `.env.example` (claves sin efecto: `DB_HOST/PORT/NAME`, `SERVER_PORT`, `UPLOAD_DIR`), excluir `spring-boot-devtools` del artefacto, actualizar Spring Boot a la última parche 3.3.x+.
6. **CI**: `npm audit` + OWASP dependency-check + la batería de autorización de §5.

### Verificación dinámica de cierre (resumen)

- Autorización: 401 sin token · 403 rol insuficiente · 200 ADMIN · perfil propio 200 · media POST 200 / DELETE 403 worker.
- Refresh: access rechazado (401) · refresh OK (200) · basura 401 (no 500).
- Reserva pública completa con envío de email confirmado en log.
- Headers: `X-Frame-Options: DENY` (API) / ausente (H2) · `Referrer-Policy: no-referrer`.
- H2: local 200 (dev) · remoto bloqueado por H2.
- Códigos: 405 / 415 / 400 / 403 correctos.
- Logs: 0 secretos (SMTP, `MAIL_PASS`, bcrypt, `password`, tokens) · 0 `printStackTrace`.

## 5. Cómo repetir esta auditoría

1. `cd frontend && npm audit`
2. `cd backend && ./mvnw versions:display-dependency-updates` (y opcionalmente OWASP dependency-check en CI)
3. Repasar los checklists §2/§3 de este documento contra el código nuevo desde la última auditoría.
4. Re-test funcional de autorización: sin token → 401, rol WORKER en `/api/admin/**` → 403, ADMIN → 200.
