## ADDED Requirements

### Requirement: Registro de aviso por cada cancelación del negocio
Cada cita cancelada por el ADMIN (individualmente o por un cierre) SHALL generar exactamente un aviso asociado a la reserva, con canal `EMAIL` si el cliente tiene email o `TELEFONO` si no lo tiene, el motivo y la fecha de creación. Las cancelaciones hechas por el propio cliente con su token NO SHALL generar aviso.

#### Scenario: Cliente con email
- **WHEN** el ADMIN cancela una cita de un cliente con email
- **THEN** se crea un aviso con canal EMAIL en estado PENDIENTE_ENVIO

#### Scenario: Cliente sin email
- **WHEN** el ADMIN cancela una cita de un cliente sin email
- **THEN** se crea un aviso con canal TELEFONO en estado PENDIENTE y no se intenta enviar ningún email

#### Scenario: Cancelación por el cliente
- **WHEN** el cliente cancela su propia cita en `/api/public/reservas/cancelar`
- **THEN** no se crea ningún aviso

### Requirement: Email de cancelación al cliente
Para los avisos con canal EMAIL, el sistema SHALL enviar tras el commit y de forma asíncrona un correo de cancelación con el nombre del negocio, servicio, trabajador, fecha y hora de la cita, el motivo (si existe) y un enlace para reservar de nuevo. El envío MUST NOT afectar a la transacción de cancelación. Si el envío tiene éxito (o el sistema está en modo log sin SMTP) el aviso SHALL pasar a ENVIADO; si falla, SHALL pasar a FALLIDO y quedar visible para aviso telefónico.

#### Scenario: Envío correcto
- **WHEN** se confirma la cancelación de una cita de un cliente con email y el SMTP responde correctamente
- **THEN** el cliente recibe el correo y el aviso queda en ENVIADO

#### Scenario: Fallo del SMTP
- **WHEN** el envío del correo lanza una excepción
- **THEN** la cita sigue CANCELADA, el aviso queda en FALLIDO y aparece en la lista de clientes por avisar

#### Scenario: Cancelación revertida por rollback
- **WHEN** la transacción de cancelación (o de cierre) hace rollback
- **THEN** no se envía ningún correo

### Requirement: Lista de clientes por avisar en el panel del admin
El ADMIN SHALL ver en `GET /api/admin/avisos?pendientes=true` los avisos que requieren acción manual (canal TELEFONO en estado PENDIENTE o canal EMAIL en estado FALLIDO), con nombre y teléfono del cliente, fecha, hora, servicio, trabajador y motivo, ordenados por fecha de la cita. El ADMIN SHALL poder marcarlos como avisados con `PUT /api/admin/avisos/{id}/avisado`, registrando quién y cuándo. Un aviso avisado no SHALL volver a aparecer en la lista de pendientes.

#### Scenario: Marcar como avisado
- **WHEN** el ADMIN llama al cliente y pulsa "Avisado" en el aviso 7
- **THEN** el aviso pasa a AVISADO con la fecha y el admin que lo marcó, y desaparece de la lista de pendientes

#### Scenario: Contador visible
- **WHEN** existen 2 avisos pendientes y el ADMIN abre su panel
- **THEN** la navegación del admin muestra un indicador con 2 clientes por avisar

#### Scenario: Un trabajador no ve los avisos
- **WHEN** un WORKER llama a `GET /api/admin/avisos`
- **THEN** el sistema responde 403
