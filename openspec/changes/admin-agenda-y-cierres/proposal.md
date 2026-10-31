## Why

El horario de TheTankBarber está fijado en código y propiedades (lunes a sábado 9:00–20:00, domingo cerrado: `app.business.*`, `SlotGenerator`, `ReservaService.validarHora/diasCerrados`, `config/business.js`, la semana de 6 días del panel del trabajador). El administrador no puede cerrar un festivo, dar el día libre a un barbero, cancelar citas avisando al cliente ni ajustar horarios, y los trabajadores no pueden bloquear sus horas libres. Además, al revisar los ingresos se ha detectado una fuga: `GET /api/worker/dashboard` devuelve el desglose VISA/efectivo de **toda** la barbería (`StatsService.ingresosPorMetodoPago` no filtra por trabajador), así que cualquier trabajador ve los ingresos del admin y de sus compañeros.

## What Changes

- **Horario configurable**: horario semanal general de la barbería (por día de la semana: abierto/cerrado, apertura, cierre) y horario semanal por trabajador (opcional, hereda el general; siempre dentro del general). Solo el ADMIN lo edita. Al primer arranque se siembra el horario actual (lun–sáb 9–20, domingo cerrado) a partir de `app.business.*`, de modo que el comportamiento no cambia hasta que el admin lo toque.
- **Cierres de día**: el admin puede cerrar toda la barbería en una fecha (festivo) o cerrar el día de un trabajador concreto. Las citas activas (PENDIENTE/CONFIRMADA) afectadas se cancelan en la misma operación, con vista previa previa a confirmar.
- **Cancelación de citas por el admin** con motivo y aviso al cliente (endpoint dedicado; el `PUT /api/admin/reservas/{id}` que pase a CANCELADA reutiliza el mismo flujo).
- **Aviso al cliente**: email de cancelación (asíncrono, tras commit) si el cliente dejó email; si no lo dejó o el envío falla, se crea un aviso pendiente que el admin ve en el panel (nombre, teléfono, cita, motivo) y marca como "avisado" tras llamar.
- **Horas libres / bloqueos puntuales**: cada trabajador (incluido el admin) puede bloquear franjas propias en una fecha (descanso, médico…). No se permite bloquear sobre citas activas.
- **Admin reservable**: nuevo indicador `reservable` en `Trabajador` (por defecto `true`, reproduce el comportamiento actual) para que el admin decida si aparece en la reserva pública; el admin gestiona su agenda, horas libres e ingresos propios desde "Mi panel".
- **Disponibilidad**: la disponibilidad pública, la validación de reserva/reprogramación y `checkAndLockAvailability` pasan a respetar horario efectivo, cierres y bloqueos, con bloqueo pesimista por trabajador para serializar reservas y cierres/bloqueos concurrentes.
- **Reducción de horario sin cancelar**: si el admin reduce un horario y quedan citas fuera, NO se cancelan; se devuelven/muestran como conflictos para que el admin decida.
- **Ingresos aislados**: nuevo `GET /api/admin/ingresos` con desglose por trabajador (solo ADMIN). **Corrección de seguridad**: el dashboard del trabajador calcula el desglose solo con sus reservas. Tests de autorización que lo garantizan.
- **UI**: páginas admin de Horarios y Cierres/Avisos, ingresos por trabajador en el dashboard admin, "Cancelar y avisar" en Reservas, toggle "Reservable" en Trabajadores; en el panel del trabajador, días y horas según el horario real y gestión de "Mis horas libres"; en la reserva pública, días cerrados marcados y horario textual dinámico.

## Capabilities

### New Capabilities
- `horario-laboral`: horario semanal general y por trabajador, editable solo por ADMIN, con siembra por defecto y detección de conflictos al reducirlo.
- `cierres-de-dia`: cierre de la barbería o del día de un trabajador, con vista previa y cancelación de las citas afectadas; cancelación individual de citas por el admin.
- `aviso-cancelacion-cliente`: notificación al cliente de cancelaciones hechas por el negocio (email o aviso telefónico pendiente marcable como avisado).
- `bloqueos-horas-libres`: bloqueos puntuales de franjas propias por trabajador/admin.
- `disponibilidad-agenda`: cálculo de huecos y validación de reservas/reprogramaciones según horario efectivo, cierres, bloqueos y trabajadores reservables, sin dobles reservas.
- `ingresos-por-trabajador`: ingresos desglosados por trabajador para el ADMIN y aislamiento estricto de los ingresos de cada trabajador.

### Modified Capabilities
<!-- No existen specs previas en openspec/specs/: todo se introduce como capacidad nueva. -->

## Impact

- **Backend** (`com.barbershop.backend`): nuevas entidades `HorarioBarberia`, `HorarioTrabajador`, `CierreDia`, `BloqueoHorario`, `AvisoCancelacion`; nuevos campos `Trabajador.reservable`, `Reserva.motivoCancelacion`, `Reserva.canceladaPor`. Nuevos servicios `AgendaService`, `CierreService`, `BloqueoService`, `AvisoCancelacionService`; cambios en `ReservaService`, `SlotGenerator`, `StatsService`, `EmailService`, `ReservaEmailListener`, `TrabajadorRepository`, `ReservaRepository`, `AdminController`, `WorkerController`, `PublicController`. Nuevo evento `ReservaCanceladaPorNegocioEvent`.
- **API nueva**: `/api/admin/horario`, `/api/admin/trabajadores/{id}/horario`, `/api/admin/agenda/conflictos`, `/api/admin/cierres` (+ `/preview`), `/api/admin/reservas/{id}/cancelar`, `/api/admin/avisos`, `/api/admin/ingresos`, `/api/admin/bloqueos`, `/api/worker/bloqueos`, `/api/worker/agenda`, `/api/public/horario`, `/api/public/dias-cerrados`.
- **API modificada**: `/api/worker/dashboard` (desglose filtrado — corrección de fuga), `/api/public/trabajadores` y `/api/public/disponibilidad` (filtran `reservable`), `PUT /api/admin/reservas/{id}` (CANCELADA notifica).
- **Base de datos**: tablas nuevas y columnas añadidas vía `ddl-auto=update`; `reservable` con `DEFAULT true`; siembra de horario general desde `app.business.*` (las propiedades quedan solo como valores de siembra).
- **Frontend** (`frontend/src`): `services/api.js`, `pages/admin/*` (nuevas `Horarios.jsx`, `Cierres.jsx`; cambios en `Dashboard.jsx`, `Reservations.jsx`, `Workers.jsx`), `pages/worker/Dashboard.jsx`, `pages/public/DateTimePicker.jsx`, `Reschedule.jsx`, `MyAppointments.jsx`, `Landing.jsx`, `routes/AppRoutes.jsx`, `config/business.js` (texto de horario como respaldo).
- **Email**: nueva plantilla de cancelación; sin cambios de dependencias.
