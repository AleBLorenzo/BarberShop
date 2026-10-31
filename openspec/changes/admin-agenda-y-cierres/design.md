## Context

Estado actual (verificado en el código):

- **Horario fijo en varios sitios**: `SlotGenerator` lee `app.business.opening-hour/closing-hour/slot-interval-minutes`; `ReservaService` vuelve a leer `opening-hour`, `closing-hour` y `closed-days` en `validarFecha`, `validarHora` y `diasCerrados()`; `consultarDisponibilidad` devuelve lista vacía si el día está en `closed-days`. En el frontend, `config/business.js#HORARIO_TEXTO` es texto fijo y `pages/worker/Dashboard.jsx` fija `DAY_LABELS` lun–sáb (`WEEK_DAYS = 6`); `WorkerController#getReservasSemana` usa por defecto `inicio + 5`. `StatsService.calcularTasaOcupacion` supone 12 h/día.
- **Disponibilidad**: `checkAndLockAvailability` hace un `existsConflictingReserva` y luego `findConflictingReservasForUpdate` (`PESSIMISTIC_WRITE` sobre las reservas activas del día). Si el día no tiene reservas no se bloquea ninguna fila (hueco protegido solo por `uk_reserva_no_double_booking`, que no cubre solapes con distinta hora de inicio).
- **Trabajadores**: `Trabajador` con `rol` ADMIN/WORKER y `activo`. El admin ya es un `Trabajador`; `findByActivoTrue()` lo incluye en la reserva pública y puede usar `/trabajador` ("Mi panel", `ProtectedRoute role="WORKER"` deja pasar a cualquier autenticado). No hay forma de que el admin se retire de la reserva pública sin desactivarse.
- **Cancelaciones**: solo el cliente cancela con token (`cancelarReserva`, sin aviso). El admin puede poner `estado=CANCELADA` vía `PUT /api/admin/reservas/{id}` (`updateReservaAdmin`), sin motivo ni notificación.
- **Email**: `EmailService` (modo log sin SMTP, nunca propaga) + `ReservaEmailListener` `@Async @TransactionalEventListener(AFTER_COMMIT)` con eventos `ReservaCreadaEvent`/`ReservaCompletadaEvent`. `Cliente.email` es opcional.
- **Ingresos — fuga detectada**: `StatsService.getWorkerDashboard` calcula `ingresosVisa/ingresosEfectivo` con `ingresosPorMetodoPago(fecha, fecha)`, que usa `ReservaRepository.sumIngresosCompletadasPorMetodoPagoBetween` **sin filtro de trabajador** → `GET /api/worker/dashboard` expone el desglose de toda la barbería (incluido el admin). `ingresosDelDia` sí filtra. El resto de `/api/worker/**` (`/reservas`, `/reservas/semana`) filtra por el trabajador del JWT y el panel calcula sus importes en cliente con esas listas (correcto). `/api/admin/dashboard` agrega global y está protegido por `hasRole("ADMIN")`.
- **Persistencia**: PostgreSQL con `spring.jpa.hibernate.ddl-auto=update` (H2 en tests). No hay Flyway/Liquibase: las columnas nuevas NOT NULL necesitan `DEFAULT` a nivel de columna o fallan sobre tablas con filas.

Restricciones: Spring Boot 3.3 / Java 21, Lombok, respuestas `ApiResponse<T>`, excepciones mapeadas en `GlobalExceptionHandler` (`IllegalArgumentException`→400, `SlotNotAvailableException`→409, `ResourceNotFoundException`→404, `SecurityException`→403), comentarios citando decisiones (Dn) y Conventional Commits en español.

## Goals / Non-Goals

**Goals:**
- Una única fuente de verdad (`AgendaService`) para la jornada efectiva de un trabajador en una fecha, usada por disponibilidad, validación, reprogramación, agenda del trabajador y conflictos.
- Horario general y por trabajador editables solo por ADMIN; siembra que reproduce el horario actual.
- Cierres (general / por trabajador) que cancelan citas de forma atómica y avisan al cliente (email o aviso telefónico gestionable).
- Bloqueos de horas libres propios para WORKER y ADMIN.
- Admin reservable con agenda e ingresos propios.
- Cerrar la fuga de ingresos y blindarla con tests de autorización.

**Non-Goals:**
- Jornadas partidas en el horario semanal (un tramo por día; los descansos se modelan con bloqueos puntuales). Ver Open Questions.
- Bloqueos recurrentes (p. ej. "todos los días 14–15").
- Reactivar citas al reabrir un día; reprogramación automática de citas canceladas.
- Notificaciones por SMS/WhatsApp; recordatorios.
- Recalcular `tasaOcupacion` con el horario real (se deja anotado como mejora).
- Migraciones versionadas (Flyway): se sigue con `ddl-auto=update`.

## Decisions

### D1 — Dos tablas de horario en vez de una con `trabajador_id` nulo
`HorarioBarberia` (`horario_barberia`: `id`, `dia_semana` VARCHAR enum `DayOfWeek` único, `abierto` boolean, `hora_apertura`, `hora_cierre` nullable si cerrado) y `HorarioTrabajador` (`horario_trabajador`: `id`, `trabajador_id` FK, `dia_semana`, `trabaja` boolean, `hora_inicio`, `hora_fin`; único `(trabajador_id, dia_semana)`).
- *Por qué*: en PostgreSQL un `UNIQUE (trabajador_id, dia_semana)` no impide duplicados con `trabajador_id NULL`, y con `ddl-auto` no podemos declarar índices parciales. Dos tablas dan restricciones reales y semántica clara (envolvente vs. override).
- *Alternativa descartada*: tabla única con `trabajador_id` nulo para el general (unicidad solo en servicio).
- Semántica: el horario del trabajador **hereda** el general si no hay fila para ese día y **debe estar contenido** en él (validado al guardar el trabajador y revalidado al reducir el general: si el general se reduce y deja un override fuera, el override se recorta en el cálculo — intersección — y se informa como aviso en la respuesta).

### D2 — `AgendaService` como fuente única de la jornada efectiva
Nuevo `service/AgendaService` con:
- `Optional<Jornada> jornadaEfectiva(Trabajador t, LocalDate fecha)` → `Jornada(inicio, fin, List<Franja> bloqueos, String motivoCierre)`; vacía si: `!t.activo`, día general cerrado, override `trabaja=false`, `CierreDia` general o del trabajador en la fecha.
- `Map<LocalDate, DiaAgenda> agenda(Trabajador t, LocalDate desde, LocalDate hasta)` para `/api/worker/agenda` y `/api/public/dias-cerrados` (consultas por rango, sin N+1: carga horarios, cierres y bloqueos del rango de una vez).
- `List<ReservaConflictoDTO> conflictos(LocalDate desde, LocalDate hasta)` para `/api/admin/agenda/conflictos` y para la respuesta de los PUT de horario.
- `SlotGenerator` deja de leer `opening/closing-hour`: `generateSlots(LocalTime apertura, LocalTime cierre, int duracion)` y `generateSlotsForWorker(apertura, cierre, duracion, List<ReservaSlot> ocupados)`; bloqueos y reservas se pasan juntos como `ReservaSlot` ocupados. Se mantiene el algoritmo actual (alineado hacia atrás desde el cierre, paso = máx(duración, intervalo)); `slot-interval-minutes` sigue siendo propiedad.
- `ReservaService`: `validarFecha` conserva pasado/antelación y delega el "cerrado" a `AgendaService`; `validarHora(trabajador, fecha, hora, duración)` valida contra la jornada; se eliminan `diasCerrados()`, `openingHour`, `closingHour`, `closedDaysProp`. `consultarDisponibilidad` usa `jornadaEfectiva` por trabajador.
- *Alternativa descartada*: añadir más condiciones dispersas en `ReservaService` — perpetúa la duplicación que causa el problema actual.

### D3 — Bloqueo pesimista por trabajador (`SELECT … FOR UPDATE` sobre `trabajadores`)
`TrabajadorRepository.findByIdForUpdate(id)` con `@Lock(PESSIMISTIC_WRITE)`. `checkAndLockAvailability` lo adquiere **primero**, después comprueba citas activas, `CierreDia` y `BloqueoHorario` (y jornada). `CierreService.crear`, `BloqueoService.crear` y `AgendaService.guardarHorario*` adquieren el mismo lock para los trabajadores afectados en orden ascendente de id (cierre general: todos los trabajadores) antes de leer/cancelar citas.
- *Por qué*: serializa reserva vs. cierre/bloqueo sin depender de que existan filas de reservas (corrige además el hueco actual del lock "por filas existentes"). El orden fijo evita interbloqueos.
- Se mantiene `findConflictingReservasForUpdate` y la unique constraint como defensa adicional.
- *Alternativa descartada*: advisory locks de PostgreSQL (no portables a H2 de tests) o aislamiento SERIALIZABLE (reintentos en toda la app).

### D4 — `CierreDia` con `trabajador_id` nullable y unicidad en servicio
`CierreDia` (`cierres_dia`: `id`, `fecha`, `trabajador_id` nullable FK, `motivo` VARCHAR(255), `creado_por_id` FK trabajador, `created_at`; índice `(fecha)`). La unicidad (un general por fecha; uno por trabajador y fecha) se comprueba en `CierreService` **bajo el lock de D3** (para el general, lock de todos los trabajadores), por lo que no hay carrera. Distinto de `BloqueoHorario` porque un cierre **cancela** citas y un bloqueo **no puede pisarlas**.
- `CierreService.crear(req, adminId)` en una transacción: lock → valida → guarda cierre → selecciona activas afectadas → `ReservaService.cancelarPorNegocio(reserva, motivo, ADMIN)` para cada una → devuelve `CierreResultadoDTO{cierre, canceladas, porEmail, porTelefono, avisos}`. `preview` reutiliza la misma selección sin escribir. `eliminar` borra el cierre (solo fecha ≥ hoy) sin tocar citas.

### D5 — `BloqueoHorario` propio del trabajador, rechazo ante solapes
`BloqueoHorario` (`bloqueos_horario`: `id`, `trabajador_id` NOT NULL, `fecha`, `hora_inicio`, `hora_fin`, `motivo`, `created_at`; índice `(trabajador_id, fecha)`). `WorkerController` toma siempre el trabajador del JWT (patrón de `crearReserva`). Crear: lock D3 → fecha ≥ hoy, fin > inicio, dentro de la jornada efectiva (sin cierre) → 409 (`SlotNotAvailableException` con detalle) si solapa reservas activas u otro bloqueo. Borrado ajeno → `ResourceNotFoundException` (404, no revela existencia). El admin tiene `GET/DELETE /api/admin/bloqueos?trabajadorId=&desde=&hasta=`.

### D6 — Cancelación por negocio centralizada en `ReservaService.cancelarPorNegocio`
- `Reserva` añade `motivo_cancelacion` VARCHAR(255) nullable y `cancelada_por` VARCHAR(20) nullable (enum `CanceladaPor { CLIENTE, ADMIN }`); `cancelarReserva` (token) pasa a registrar `CLIENTE`.
- `cancelarPorNegocio` solo acepta PENDIENTE/CONFIRMADA (400 si no), pone CANCELADA + motivo + `ADMIN`, crea el `AvisoCancelacion` y publica `ReservaCanceladaPorNegocioEvent(ReservaResponse snapshot, String email, Long avisoId, String motivo)`.
- `POST /api/admin/reservas/{id}/cancelar` y `updateReservaAdmin` (cuando `estado` pasa de activo a CANCELADA) delegan aquí, de modo que no haya un camino de cancelación del admin sin aviso.
- `ReservaDTO`/`ReservaResponse` exponen `motivoCancelacion` y `canceladaPor` para "Mis citas".

### D7 — `AvisoCancelacion` como cola gestionable (email + teléfono)
`AvisoCancelacion` (`avisos_cancelacion`: `id`, `reserva_id` FK única, `canal` EMAIL|TELEFONO, `estado` PENDIENTE_ENVIO|ENVIADO|FALLIDO|PENDIENTE|AVISADO, `motivo`, `created_at`, `avisado_at`, `avisado_por_id`, `error` VARCHAR(255)).
- Con email → `EMAIL/PENDIENTE_ENVIO`; sin email → `TELEFONO/PENDIENTE`.
- `ReservaEmailListener.onReservaCanceladaPorNegocio` (`@Async @TransactionalEventListener(AFTER_COMMIT)`) llama a `EmailService.enviarCancelacion(...)`, que pasa a **devolver `boolean`** (true si enviado o modo log; false si excepción), y luego `AvisoCancelacionService.marcarResultadoEnvio(avisoId, ok, error)` en una transacción nueva (`REQUIRES_NEW`): ENVIADO o FALLIDO.
- Pendientes de acción = `TELEFONO/PENDIENTE` + `EMAIL/FALLIDO` (+ `EMAIL/PENDIENTE_ENVIO` con más de 15 min, por si el proceso murió antes de enviar). `PUT /api/admin/avisos/{id}/avisado` → AVISADO con admin y fecha.
- *Por qué una tabla y no solo filtrar reservas canceladas sin email*: permite marcar "avisado", recoger fallos de SMTP y auditar quién avisó.
- Modo log cuenta como enviado para no inundar el panel en desarrollo (documentado).

### D8 — `Trabajador.reservable` con DEFAULT a nivel de columna
`@Builder.Default @Column(name = "reservable", nullable = false, columnDefinition = "boolean default true") private Boolean reservable = true;` — con `ddl-auto=update` Hibernate emite `ALTER TABLE … ADD COLUMN reservable boolean default true not null`, válido en PostgreSQL y H2 con filas existentes. Por defecto `true` reproduce el comportamiento actual (el admin ya aparece). `TrabajadorRepository.findByActivoTrueAndReservableTrue()` sustituye a `findByActivoTrue()` en `TrabajadorService.findAllActivos` (uso público) y en `consultarDisponibilidad`; `crearReservaPublica` rechaza no reservables (400); `crearReservaParaTrabajador` no lo exige. `TrabajadorDTO` y `TrabajadorMapper` incluyen `reservable`; `PublicController` no lo oculta (no es sensible). Admin edita el flag en `Workers.jsx`.

### D9 — Siembra del horario general (`HorarioDefaultsInitializer`)
`config/HorarioDefaultsInitializer` (`ApplicationRunner`, todos los perfiles, antes de `DataSeeder` vía `@Order`): si `horario_barberia` está vacía, inserta 7 filas a partir de `app.business.opening-hour`, `closing-hour` y `closed-days` (lun–sáb 09–20, domingo cerrado). Idempotente. Las propiedades quedan como valores de siembra (comentado en `application.properties`). Los trabajadores sin filas en `horario_trabajador` heredan → comportamiento idéntico.

### D10 — Ingresos: corregir la fuga y endpoint admin por trabajador
- Nueva consulta `ReservaRepository.sumIngresosCompletadasPorMetodoPagoTrabajadorBetween(trabajador, start, end)`; `getWorkerDashboard` la usa. `ingresosPorMetodoPago` global queda privado y solo para `getDashboardStats`.
- Nueva consulta agrupada `SELECT r.trabajador.id, r.trabajador.nombre, r.estado, r.metodoPago, SUM(s.precio), COUNT(r) … GROUP BY …` → `StatsService.getIngresosPorTrabajador(start, end)` → `List<IngresoTrabajadorDTO>{trabajadorId, nombre, rol, total, visa, efectivo, sinMetodo, completadas}` + totales en `IngresosResumenDTO`. Mismo criterio que el dashboard (total = COMPLETADA+CONFIRMADA; desglose solo COMPLETADA).
- Regla: nada bajo `/api/worker/**` acepta `trabajadorId` para lectura; siempre `getTrabajador(userDetails)`.
- `AdminController` sigue protegido por `/api/admin/** hasRole("ADMIN")`; además se añade `@PreAuthorize("hasRole('ADMIN')")` a nivel de clase (defensa en profundidad; hoy no hay `@EnableMethodSecurity` en `SecurityConfig`, se añade — `GlobalExceptionHandler` ya mapea `AuthorizationDeniedException`).
- UI: `workerLinks` deja de mostrar "Administración" a no-admins (cosmético, la API ya da 403).

### D11 — Endpoints nuevos (todos devuelven `ApiResponse<T>`)
| Método y ruta | Rol | Servicio |
|---|---|---|
| `GET/PUT /api/admin/horario` | ADMIN | `AgendaService.get/guardarHorarioBarberia` → `HorarioGuardadoDTO{dias, conflictos}` |
| `GET/PUT /api/admin/trabajadores/{id}/horario` | ADMIN | `AgendaService.get/guardarHorarioTrabajador` (PUT reemplaza; día ausente = hereda) |
| `GET /api/admin/agenda/conflictos?desde&hasta` | ADMIN | `AgendaService.conflictos` (por defecto hoy → +60 días) |
| `GET /api/admin/cierres?desde&hasta`, `GET /api/admin/cierres/preview?fecha&trabajadorId`, `POST /api/admin/cierres`, `DELETE /api/admin/cierres/{id}` | ADMIN | `CierreService` |
| `POST /api/admin/reservas/{id}/cancelar` `{motivo}` | ADMIN | `ReservaService.cancelarPorNegocio` |
| `GET /api/admin/avisos?pendientes=true`, `GET /api/admin/avisos/pendientes/count`, `PUT /api/admin/avisos/{id}/avisado` | ADMIN | `AvisoCancelacionService` |
| `GET /api/admin/ingresos?inicio&fin` | ADMIN | `StatsService.getIngresosPorTrabajador` |
| `GET/DELETE /api/admin/bloqueos` | ADMIN | `BloqueoService` |
| `GET/POST/DELETE /api/worker/bloqueos` | WORKER/ADMIN (propios) | `BloqueoService` |
| `GET /api/worker/agenda?desde&hasta` | WORKER/ADMIN (propia) | `AgendaService.agenda` |
| `GET /api/public/horario` | público | `AgendaService.getHorarioBarberia` |
| `GET /api/public/dias-cerrados?desde&hasta&trabajadorId` | público (rango ≤ 62 días) | `AgendaService.diasCerrados` |

### D12 — Frontend
- `services/api.js`: métodos nuevos en `adminApi`, `workerApi`, `publicApi`.
- Admin: `pages/admin/Horarios.jsx` (tabla 7 días del general + selector de trabajador con override/"hereda"; tras guardar muestra conflictos con acción "Cancelar y avisar"); `pages/admin/Cierres.jsx` (calendario de cierres, formulario general/por trabajador con vista previa obligatoria antes de confirmar, sección "Clientes por avisar" con teléfono `tel:` y botón "Avisado"); `Dashboard.jsx` añade tabla "Ingresos por trabajador"; `Reservations.jsx` añade "Cancelar y avisar" con modal de motivo; `Workers.jsx` añade toggle "Reservable". `AppRoutes.jsx`: rutas `/admin/horarios` y `/admin/cierres`, enlaces nuevos (con contador de avisos pendientes).
- Worker (`pages/worker/Dashboard.jsx`): días de la semana desde `/api/worker/agenda` (sustituye `DAY_LABELS` fijo; `getReservasSemana` por defecto pasa a lunes→domingo), días cerrados marcados con motivo, bloqueos visibles y modal "Bloquear horas" + eliminar; la creación de citas no ofrece días cerrados.
- Público: `DateTimePicker.jsx` y `Reschedule.jsx` consultan `dias-cerrados` para el mes visible y muestran "Cerrado" en lugar de "sin huecos"; `MyAppointments.jsx` muestra "Cancelada por la barbería" + motivo + enlace a reservar; `Landing.jsx` pinta el horario de `/api/public/horario` con `HORARIO_TEXTO` como respaldo.

## Risks / Trade-offs

- [El lock por trabajador serializa todas las reservas de un mismo barbero] → la contención es mínima (barbería pequeña, transacciones cortas); el cierre general bloquea todos los trabajadores solo durante la cancelación.
- [`ddl-auto=update` no aplica `DEFAULT` si la columna ya existe con otra definición, ni elimina columnas] → columnas nuevas nullable salvo `reservable` (con `columnDefinition`); probar el arranque contra una copia de la BD de producción antes de desplegar.
- [Email "enviado" en modo log puede ocultar que nadie avisó al cliente en un entorno sin SMTP] → log `[MODO LOG]` explícito; en producción SMTP está configurado. Documentado en README.
- [El proceso muere entre el commit y el envío asíncrono → aviso queda en PENDIENTE_ENVIO] → se lista como pendiente pasados 15 min.
- [Reducir el horario general deja overrides de trabajador fuera de la envolvente] → el cálculo usa la intersección y la respuesta del PUT lo advierte; el admin puede corregirlos.
- [Desajuste entre el texto del horario del frontend y el real] → la portada lee `/api/public/horario`; `HORARIO_TEXTO` solo como respaldo; los textos legales/README con el horario se revisan.
- [Cambiar `getReservasSemana` por defecto a 7 días] → solo afecta a la vista; el frontend ya pasa `fechaInicio/fechaFin` explícitos.
- [Zonas horarias] → se mantiene `LocalDate.now()` del servidor como hoy (comportamiento actual).

## Migration Plan

1. Desplegar backend: `ddl-auto=update` crea `horario_barberia`, `horario_trabajador`, `cierres_dia`, `bloqueos_horario`, `avisos_cancelacion` y añade `trabajadores.reservable` (default true) y `reservas.motivo_cancelacion`, `reservas.cancelada_por` (nullable). `HorarioDefaultsInitializer` siembra el horario actual.
2. Verificar: disponibilidad de un martes y de un domingo idéntica a la anterior; `GET /api/public/horario` devuelve lun–sáb 9–20.
3. Desplegar frontend.
4. Rollback: volver a la versión anterior del backend/frontend; las tablas y columnas nuevas se ignoran (la versión anterior no las mapea y `ddl-auto=update` no las borra). Cierres/bloqueos creados dejarían de aplicarse: avisar al admin.

## Open Questions

- ¿Hacen falta jornadas partidas en el horario semanal (p. ej. 9–14 y 16–20) en vez de modelar la comida como bloqueo diario? El diseño permite añadir un segundo tramo más adelante.
- ¿Deben los trabajadores poder ver (solo lectura) los cierres de compañeros? Por ahora solo ven los suyos y los generales.
- ¿Se quiere que el admin pueda crear bloqueos para otros trabajadores, o basta con cierres de día? Por ahora solo listar/eliminar.
- Antelación mínima para que un trabajador bloquee horas el mismo día (por ahora: cualquier franja futura sin citas).
