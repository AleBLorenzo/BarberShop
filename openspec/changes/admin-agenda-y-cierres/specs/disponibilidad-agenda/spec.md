## ADDED Requirements

### Requirement: Jornada efectiva de un trabajador en una fecha
El sistema SHALL calcular la jornada efectiva de un trabajador en una fecha como: horario propio del día (o el general si no tiene), vacía si la barbería cierra ese día de la semana, si hay un cierre general en esa fecha, si hay un cierre del trabajador en esa fecha o si el trabajador está inactivo; a la jornada resultante se le restan sus bloqueos de esa fecha. Este cálculo MUST ser la única fuente de verdad para disponibilidad, validación de reservas, reprogramaciones y agenda del trabajador.

#### Scenario: Día normal sin cambios
- **WHEN** no hay horarios propios, cierres ni bloqueos y se consulta un martes
- **THEN** la jornada es 09:00–20:00, igual que antes del cambio

#### Scenario: Domingo con configuración por defecto
- **WHEN** se consulta un domingo con el horario sembrado por defecto
- **THEN** la jornada está vacía para todos los trabajadores

### Requirement: Disponibilidad pública según la jornada efectiva
`POST /api/public/disponibilidad` SHALL generar huecos dentro de la jornada efectiva de cada trabajador con la rejilla actual (alineada hacia atrás desde el cierre, paso = máx(duración, intervalo)), marcando como no disponibles los que se solapen con citas activas o bloqueos, y omitiendo los huecos ya pasados si la fecha es hoy. Sin `trabajadorId`, SHALL considerar solo trabajadores activos y reservables.

#### Scenario: Horario reducido de un trabajador
- **WHEN** Luis trabaja el jueves de 09:00 a 14:00 y se pide disponibilidad de un servicio de 30 min
- **THEN** el último hueco de Luis es 13:30–14:00

#### Scenario: Bloqueo en mitad de la jornada
- **WHEN** Ana tiene un bloqueo de 13:00 a 14:00
- **THEN** los huecos de Ana que se solapan con 13:00–14:00 aparecen como no disponibles

#### Scenario: Día cerrado
- **WHEN** se pide disponibilidad para una fecha con cierre general
- **THEN** la respuesta es una lista vacía

### Requirement: Días cerrados para el calendario público
El sistema SHALL exponer `GET /api/public/dias-cerrados?desde=&hasta=&trabajadorId=` (rango máximo de 62 días) que devuelva las fechas sin jornada efectiva (para el trabajador indicado o, sin él, para todos los trabajadores reservables), para que el selector de fecha y la reprogramación indiquen "Cerrado" y no ofrezcan esas fechas.

#### Scenario: Calendario con festivo
- **WHEN** el cliente abre el selector de fecha y el día 8 hay cierre general
- **THEN** el día 8 aparece deshabilitado o marcado como cerrado

### Requirement: Validación de reserva y reprogramación contra la agenda
Al crear una reserva (pública o desde el panel) y al reprogramarla con token, el sistema SHALL rechazar con 400 la fecha si el trabajador no tiene jornada ese día (indicando "La barbería está cerrada" o "El trabajador no trabaja ese día") y la hora si el servicio no cabe completo dentro de la jornada efectiva; y SHALL rechazar con 409 si se solapa con una cita activa o un bloqueo.

#### Scenario: Reserva en día cerrado por petición directa a la API
- **WHEN** se envía `POST /api/public/reservas` para una fecha con cierre del trabajador elegido
- **THEN** el sistema responde 400 y no crea la reserva

#### Scenario: Reprogramar a una franja bloqueada
- **WHEN** un cliente reprograma su cita a una hora que se solapa con un bloqueo del trabajador
- **THEN** el sistema responde 409 y la cita mantiene su fecha y hora originales

### Requirement: Serialización de reservas frente a cierres y bloqueos
La comprobación de disponibilidad (`checkAndLockAvailability`) SHALL adquirir un bloqueo pesimista sobre la fila del trabajador antes de comprobar citas, cierres y bloqueos, y la creación de cierres, bloqueos y cambios de horario SHALL adquirir el mismo bloqueo sobre los trabajadores afectados (en orden ascendente de id), de modo que ninguna reserva se confirme en una franja cerrada o bloqueada por una operación concurrente.

#### Scenario: Cierre concurrente con una reserva
- **WHEN** un cliente confirma una reserva para el día 8 mientras el ADMIN crea el cierre general del día 8
- **THEN** o la reserva se rechaza, o se crea y queda cancelada (con aviso) por el cierre; nunca queda una cita activa en un día cerrado

### Requirement: Trabajadores reservables
`Trabajador` SHALL tener un indicador `reservable` (por defecto verdadero, también para filas existentes). Solo los trabajadores activos y reservables SHALL aparecer en `GET /api/public/trabajadores` y en la disponibilidad pública, y una reserva pública para un trabajador no reservable SHALL rechazarse con 400. El panel del trabajador (incluido el admin desde "Mi panel") SHALL poder seguir creando citas propias aunque no sea reservable. El ADMIN SHALL poder cambiar este indicador para cualquier trabajador, incluido él mismo, desde la gestión de trabajadores.

#### Scenario: El admin se pone como reservable
- **WHEN** el ADMIN marca su propio trabajador como reservable y activo
- **THEN** aparece en el selector público de trabajadores y recibe reservas según su horario

#### Scenario: El admin se retira de la reserva pública
- **WHEN** el ADMIN desmarca "reservable" en su ficha
- **THEN** deja de aparecer en el selector y en la disponibilidad pública, y sus citas existentes se mantienen
