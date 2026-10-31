## ADDED Requirements

### Requirement: El trabajador bloquea sus propias horas libres
Un usuario autenticado con rol WORKER o ADMIN SHALL poder crear, listar y eliminar bloqueos puntuales de su propia agenda mediante `POST/GET/DELETE /api/worker/bloqueos`. El trabajador del bloqueo MUST tomarse siempre del token JWT, ignorando cualquier `trabajadorId` del cuerpo. Un bloqueo tiene fecha, hora de inicio, hora de fin y motivo opcional; la hora de fin MUST ser posterior a la de inicio y la fecha MUST ser hoy o futura.

#### Scenario: Bloquear un descanso
- **WHEN** Ana crea un bloqueo el día 12 de 13:00 a 14:00 con motivo "Comida"
- **THEN** el sistema responde 201 y la disponibilidad pública de Ana ese día no ofrece huecos que se solapen con 13:00–14:00

#### Scenario: El admin bloquea sus propias horas
- **WHEN** el ADMIN crea un bloqueo desde "Mi panel"
- **THEN** el bloqueo se asocia a su propio trabajador y afecta solo a su disponibilidad

#### Scenario: Intento de bloquear la agenda de otro
- **WHEN** Ana envía `POST /api/worker/bloqueos` con `trabajadorId` de Luis
- **THEN** el bloqueo se crea para Ana, no para Luis

#### Scenario: Rango inválido
- **WHEN** un trabajador envía un bloqueo con fin anterior al inicio o en una fecha pasada
- **THEN** el sistema responde 400

### Requirement: Un bloqueo no puede pisar citas activas ni otros bloqueos
El sistema SHALL rechazar con 409 un bloqueo que se solape con una cita PENDIENTE o CONFIRMADA del mismo trabajador, indicando las citas en conflicto, o que se solape con otro bloqueo existente del mismo trabajador. La comprobación MUST hacerse bajo el mismo bloqueo pesimista que la creación de reservas para evitar carreras.

#### Scenario: Bloqueo sobre una cita
- **WHEN** Luis intenta bloquear de 10:00 a 12:00 y tiene una cita CONFIRMADA a las 11:00
- **THEN** el sistema responde 409 con la cita de las 11:00 y no crea el bloqueo

#### Scenario: Reserva y bloqueo simultáneos
- **WHEN** un cliente reserva las 10:00 de Luis a la vez que Luis bloquea 10:00–11:00
- **THEN** solo una de las dos operaciones tiene éxito y la otra recibe 409

### Requirement: Solo el propietario elimina o ve sus bloqueos
Un WORKER SHALL ver y eliminar únicamente sus propios bloqueos. Eliminar un bloqueo ajeno o inexistente SHALL responder 404 sin revelar su existencia. El ADMIN SHALL poder consultar y eliminar los bloqueos de cualquier trabajador mediante `GET/DELETE /api/admin/bloqueos`.

#### Scenario: Eliminar un bloqueo ajeno
- **WHEN** Ana llama a `DELETE /api/worker/bloqueos/{id}` sobre un bloqueo de Luis
- **THEN** el sistema responde 404 y el bloqueo de Luis sigue existiendo

#### Scenario: Listado propio
- **WHEN** Ana llama a `GET /api/worker/bloqueos?desde=&hasta=`
- **THEN** recibe solo sus bloqueos en ese rango

### Requirement: El trabajador ve su agenda efectiva
El sistema SHALL exponer `GET /api/worker/agenda?desde=&hasta=` que devuelva, para el trabajador autenticado y cada fecha del rango, si trabaja, su horario efectivo, los cierres que le afectan (general o propio, con motivo) y sus bloqueos. El panel del trabajador SHALL usar esta información para mostrar los días laborables reales (en lugar de lunes–sábado fijo) y marcar días cerrados y franjas bloqueadas.

#### Scenario: Semana con festivo
- **WHEN** Ana abre su panel en una semana con el jueves cerrado por festivo
- **THEN** el jueves aparece como "Cerrado — Festivo" y no se puede crear una cita ni un bloqueo ese día

#### Scenario: Domingo abierto
- **WHEN** el ADMIN abre los domingos y Ana abre su panel
- **THEN** la vista semanal incluye el domingo
