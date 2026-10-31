## ADDED Requirements

### Requirement: Horario semanal general de la barbería
El sistema SHALL mantener un horario semanal general de la barbería con una entrada por cada día de la semana (lunes a domingo) que indique si el día está abierto y, si lo está, su hora de apertura y de cierre. La hora de cierre MUST ser posterior a la de apertura. Solo un usuario con rol ADMIN SHALL poder consultarlo para edición y modificarlo mediante `GET/PUT /api/admin/horario`.

#### Scenario: El admin modifica el horario general
- **WHEN** un ADMIN envía `PUT /api/admin/horario` con el sábado abierto de 10:00 a 14:00
- **THEN** el sistema guarda el cambio, responde 200 con los 7 días del horario y la disponibilidad pública del sábado pasa a ofrecer huecos solo entre 10:00 y 14:00

#### Scenario: Horario inválido
- **WHEN** un ADMIN envía un día abierto con cierre anterior o igual a la apertura, o sin horas
- **THEN** el sistema responde 400 y no modifica ningún día

#### Scenario: Un trabajador intenta modificar el horario general
- **WHEN** un usuario con rol WORKER llama a `PUT /api/admin/horario`
- **THEN** el sistema responde 403 y el horario no cambia

### Requirement: Horario por defecto equivalente al actual
Si no existe horario general almacenado, el sistema SHALL sembrarlo al arrancar a partir de `app.business.opening-hour`, `app.business.closing-hour` y `app.business.closed-days`, de modo que con la configuración actual quede lunes a sábado de 9:00 a 20:00 y domingo cerrado. La siembra MUST ser idempotente y no sobrescribir un horario ya existente.

#### Scenario: Primer arranque tras el despliegue
- **WHEN** la aplicación arranca con la tabla de horario general vacía y la configuración por defecto
- **THEN** quedan guardados lunes–sábado abiertos 09:00–20:00 y domingo cerrado, y la disponibilidad es idéntica a la de antes del cambio

#### Scenario: Reinicio con horario personalizado
- **WHEN** la aplicación arranca y el admin ya había cambiado el horario general
- **THEN** el horario guardado se conserva sin cambios

### Requirement: Horario semanal por trabajador
El sistema SHALL permitir al ADMIN definir, para cada trabajador y día de la semana, un horario propio (día libre, o apertura/cierre) mediante `GET/PUT /api/admin/trabajadores/{id}/horario`. Un día sin horario propio SHALL heredar el horario general. El horario propio MUST quedar contenido dentro del horario general de ese día; si la barbería cierra ese día, el trabajador no trabaja aunque tenga horario propio. Los trabajadores NO SHALL poder modificar su horario semanal.

#### Scenario: Día libre semanal de un trabajador
- **WHEN** el ADMIN marca el lunes como día libre de Ana
- **THEN** la disponibilidad pública de cualquier lunes no ofrece huecos de Ana y sí del resto de trabajadores

#### Scenario: Horario propio fuera del general
- **WHEN** el ADMIN asigna a Luis el martes de 08:00 a 15:00 y el general del martes es 09:00–20:00
- **THEN** el sistema responde 400 indicando que el horario del trabajador debe estar dentro del horario de la barbería

#### Scenario: Volver a heredar el horario general
- **WHEN** el ADMIN elimina el horario propio del miércoles de Luis
- **THEN** el miércoles de Luis sigue el horario general

#### Scenario: Un trabajador intenta cambiar su horario semanal
- **WHEN** un WORKER llama a `PUT /api/admin/trabajadores/{suId}/horario`
- **THEN** el sistema responde 403

### Requirement: Reducir un horario no cancela citas existentes
Cuando un cambio de horario general o por trabajador deje citas activas futuras (PENDIENTE o CONFIRMADA) fuera del nuevo horario, el sistema SHALL guardar el cambio sin cancelar ni modificar esas citas y SHALL devolver en la respuesta la lista de citas en conflicto. El ADMIN SHALL poder consultar en cualquier momento las citas activas futuras que quedan fuera del horario efectivo mediante `GET /api/admin/agenda/conflictos`.

#### Scenario: Reducción con citas fuera de horario
- **WHEN** el ADMIN cambia el cierre del viernes de 20:00 a 18:00 y existe una cita CONFIRMADA el próximo viernes a las 19:00
- **THEN** el cambio se guarda, la cita sigue CONFIRMADA, la respuesta incluye esa cita en `conflictos` y deja de ofrecerse cualquier hueco nuevo después de las 18:00

#### Scenario: Consulta de conflictos
- **WHEN** el ADMIN llama a `GET /api/admin/agenda/conflictos`
- **THEN** recibe las citas activas desde hoy que caen fuera del horario efectivo de su trabajador, en un día cerrado o sobre un bloqueo, con cliente, teléfono, fecha y hora

### Requirement: Horario público visible
El sistema SHALL exponer el horario general en `GET /api/public/horario` (sin autenticación) para que la web muestre el horario real; el frontend MUST usar el texto estático de `config/business.js` solo como respaldo si la petición falla.

#### Scenario: La portada muestra el horario actualizado
- **WHEN** el ADMIN cambia el sábado a 10:00–14:00 y un visitante abre la portada
- **THEN** el bloque de horario muestra el sábado de 10:00 a 14:00
