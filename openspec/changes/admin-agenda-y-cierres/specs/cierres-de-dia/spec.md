## ADDED Requirements

### Requirement: Cierre de toda la barbería en una fecha
El ADMIN SHALL poder cerrar la barbería en una fecha concreta (festivo) con un motivo opcional mediante `POST /api/admin/cierres` sin `trabajadorId`. La fecha MUST ser hoy o futura. Mientras el cierre exista, ningún trabajador SHALL tener disponibilidad ese día. No SHALL existir dos cierres generales para la misma fecha.

#### Scenario: Cerrar un festivo
- **WHEN** el ADMIN crea un cierre general para el 8 de diciembre con motivo "Festivo"
- **THEN** el sistema responde 201 con el cierre y un resumen de citas canceladas, y la disponibilidad pública de ese día queda vacía para todos los trabajadores

#### Scenario: Cierre duplicado
- **WHEN** el ADMIN intenta crear un segundo cierre general para la misma fecha
- **THEN** el sistema responde 409

#### Scenario: Fecha pasada
- **WHEN** el ADMIN intenta crear un cierre para ayer
- **THEN** el sistema responde 400

### Requirement: Cierre del día de un trabajador
El ADMIN SHALL poder cancelar el día de trabajo de un trabajador concreto (incluido él mismo) en una fecha mediante `POST /api/admin/cierres` con `trabajadorId`. Ese día el trabajador no SHALL tener disponibilidad; el resto de trabajadores no se ven afectados. No SHALL existir dos cierres del mismo trabajador en la misma fecha.

#### Scenario: Día libre puntual de un trabajador
- **WHEN** el ADMIN cierra el día 15 para Ana
- **THEN** el día 15 no hay huecos de Ana y sí del resto de trabajadores

### Requirement: Las citas afectadas por un cierre se cancelan
Al crear un cierre, el sistema SHALL cancelar en la misma transacción todas las citas PENDIENTE o CONFIRMADA afectadas (todas las del día si es general; las del trabajador si es individual), registrando `canceladaPor = ADMIN` y el motivo del cierre, y SHALL disparar el aviso al cliente de cada una. Las citas COMPLETADA, NO_ASISTIO o ya CANCELADA no SHALL modificarse. La respuesta MUST incluir el número de citas canceladas, cuántas se notificarán por email y cuántas quedan pendientes de aviso telefónico.

#### Scenario: Cierre con citas
- **WHEN** el ADMIN cierra la barbería un día con 3 citas CONFIRMADA (2 clientes con email, 1 sin email)
- **THEN** las 3 citas pasan a CANCELADA con el motivo, se encolan 2 emails de cancelación, se crea 1 aviso telefónico pendiente y la respuesta indica `canceladas=3, email=2, telefono=1`

#### Scenario: Atomicidad
- **WHEN** falla la cancelación de alguna cita durante la creación del cierre
- **THEN** no se crea el cierre ni se cancela ninguna cita, y no se envía ningún email

### Requirement: Vista previa antes de cerrar
El sistema SHALL ofrecer `GET /api/admin/cierres/preview?fecha=&trabajadorId=` que devuelva, sin modificar nada, las citas activas que se cancelarían (cliente, teléfono, si tiene email, trabajador, hora, servicio).

#### Scenario: Previsualizar un cierre
- **WHEN** el ADMIN pide la vista previa del cierre de Luis el día 20
- **THEN** recibe la lista de citas activas de Luis ese día y ninguna cita cambia de estado

### Requirement: Listado y reapertura de cierres
El ADMIN SHALL poder listar los cierres en un rango (`GET /api/admin/cierres?desde=&hasta=`) y eliminar un cierre futuro (`DELETE /api/admin/cierres/{id}`). Eliminar un cierre SHALL restaurar la disponibilidad de ese día pero NO SHALL reactivar las citas canceladas.

#### Scenario: Reabrir un día cerrado por error
- **WHEN** el ADMIN elimina el cierre general del día 8
- **THEN** el día 8 vuelve a ofrecer huecos según el horario y las citas canceladas por el cierre siguen CANCELADA

### Requirement: Cancelación individual de una cita por el admin
El ADMIN SHALL poder cancelar cualquier cita PENDIENTE o CONFIRMADA indicando un motivo opcional mediante `POST /api/admin/reservas/{id}/cancelar`, con `canceladaPor = ADMIN` y aviso al cliente. Cuando `PUT /api/admin/reservas/{id}` cambie el estado a CANCELADA desde un estado activo, el sistema SHALL aplicar el mismo flujo (incluido el aviso). Cancelar una cita ya CANCELADA o COMPLETADA SHALL responder 400.

#### Scenario: Cancelar una cita desde el panel
- **WHEN** el ADMIN cancela la cita 42 con motivo "Barbero enfermo"
- **THEN** la cita queda CANCELADA con ese motivo y el cliente recibe el aviso por el canal que corresponda

#### Scenario: Cancelar una cita completada
- **WHEN** el ADMIN intenta cancelar una cita COMPLETADA
- **THEN** el sistema responde 400 y la cita no cambia

#### Scenario: Un trabajador no puede usar la cancelación del admin
- **WHEN** un WORKER llama a `POST /api/admin/reservas/{id}/cancelar`
- **THEN** el sistema responde 403

### Requirement: El cliente ve la cancelación por el negocio
En "Mis citas" y en la reprogramación pública, una cita cancelada por el negocio SHALL mostrarse como cancelada por la barbería con su motivo, y NO SHALL poder reprogramarse ni cancelarse de nuevo con el token.

#### Scenario: Cliente consulta una cita cancelada por cierre
- **WHEN** el cliente consulta su cita en `/mis-citas` tras un cierre
- **THEN** ve el estado "Cancelada por la barbería", el motivo y un enlace para reservar de nuevo

#### Scenario: Intento de reprogramar una cita cancelada por el negocio
- **WHEN** el cliente llama a `POST /api/public/reservas/reprogramar` con el token de esa cita
- **THEN** el sistema responde con error de token y la cita sigue CANCELADA
