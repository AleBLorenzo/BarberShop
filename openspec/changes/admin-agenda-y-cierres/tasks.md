## 1. Modelo de datos y siembra (backend)

- [x] 1.1 Crear entidades `model/HorarioBarberia` y `model/HorarioTrabajador` (D1) con sus repositorios (`HorarioBarberiaRepository.findByDiaSemana`, `HorarioTrabajadorRepository.findByTrabajador_Id`, `deleteByTrabajador_IdAndDiaSemana`) y unique constraints (`dia_semana`; `trabajador_id, dia_semana`)
- [x] 1.2 Crear `model/CierreDia` + `CierreDiaRepository` (consultas por fecha, por rango y por `(fecha, trabajador)` incluido el general con `trabajador IS NULL`) (D4)
- [x] 1.3 Crear `model/BloqueoHorario` + `BloqueoHorarioRepository` (por trabajador y rango, solapes por trabajador/fecha/horas) (D5)
- [x] 1.4 Crear `model/AvisoCancelacion` con enums `CanalAviso` y `EstadoAviso` + `AvisoCancelacionRepository` (pendientes de acción, contador) (D7)
- [x] 1.5 Añadir a `Reserva` `motivoCancelacion` y `canceladaPor` (enum `CanceladaPor`, nullable) y exponerlos en `ReservaDTO`, `ReservaResponse` y `ReservaMapper` (D6)
- [x] 1.6 Añadir `Trabajador.reservable` con `@Builder.Default` y `columnDefinition = "boolean default true"`; incluirlo en `TrabajadorDTO`, `TrabajadorMapper` y `TrabajadorService.update` (D8)
- [x] 1.7 Añadir `TrabajadorRepository.findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`), `findAllForUpdateOrderById` (activos, orden por id) y `findByActivoTrueAndReservableTrue` (D3, D8)
- [x] 1.8 Crear `config/HorarioDefaultsInitializer` (ApplicationRunner, `@Order` antes de `DataSeeder`) que siembre lun–sáb 09–20 y domingo cerrado desde `app.business.*` si la tabla está vacía; comentar en `application.properties` que esas propiedades son solo de siembra (D9)
- [x] 1.9 Test: arranque con BD vacía siembra 7 filas; segundo arranque no las duplica ni sobrescribe

## 2. Motor de agenda y disponibilidad

- [x] 2.1 Refactorizar `util/SlotGenerator` a `generateSlots(apertura, cierre, duracion)` y `generateSlotsForWorker(apertura, cierre, duracion, ocupados)` manteniendo la rejilla alineada hacia atrás; actualizar `SlotGeneratorTest` (casos actuales con 09:00–20:00 + jornada reducida 09:00–14:00)
- [x] 2.2 Crear `service/AgendaService` con `jornadaEfectiva(trabajador, fecha)` (horario general/override con intersección, día cerrado, cierres, inactivo, bloqueos) y `agenda(trabajador, desde, hasta)` cargando el rango en bloque (D2)
- [x] 2.3 Añadir `AgendaService.diasCerrados(desde, hasta, trabajadorId)` (rango ≤ 62 días; sin trabajador = días sin ningún trabajador reservable con jornada)
- [x] 2.4 Refactorizar `ReservaService`: eliminar `openingHour/closingHour/closedDaysProp/diasCerrados()`; `validarFecha` + `validarHora(trabajador, …)` contra `AgendaService` con mensajes "La barbería está cerrada…" / "El trabajador no trabaja ese día" / fuera de jornada
- [x] 2.5 `consultarDisponibilidad`: usar `findByActivoTrueAndReservableTrue`, jornada efectiva por trabajador y bloqueos como ocupados; mantener el filtro de huecos pasados de hoy
- [x] 2.6 `checkAndLockAvailability`: adquirir `findByIdForUpdate` primero y comprobar después citas activas, cierres, bloqueos y jornada (409 en solape con cita/bloqueo); aplicar también en `reprogramarReserva`
- [x] 2.7 `crearReservaPublica`: rechazar trabajador no reservable (400); `crearReservaParaTrabajador` sin esa restricción
- [x] 2.8 `TrabajadorService.findAllActivos` (público) filtra `reservable`; revisar otros usos de `findByActivoTrue`
- [x] 2.9 Tests de `AgendaService` y `ReservaService`: martes por defecto = 09–20, domingo vacío, override, día libre semanal, cierre general, cierre de trabajador, bloqueo, reserva/reprogramación en día cerrado (400) y sobre bloqueo (409), no reservable (400)

## 3. Horarios (admin)

- [x] 3.1 DTOs `HorarioDiaDTO`, `HorarioGuardadoDTO{dias, conflictos, avisos}`, `ReservaConflictoDTO`
- [x] 3.2 `AgendaService.getHorarioBarberia/guardarHorarioBarberia` con validación (7 días, cierre > apertura) y lock de todos los trabajadores; devolver conflictos sin cancelar citas
- [x] 3.3 `AgendaService.getHorarioTrabajador/guardarHorarioTrabajador` (PUT reemplaza; día ausente = hereda; validar contención en el general; lock del trabajador; devolver conflictos)
- [x] 3.4 `AgendaService.conflictos(desde, hasta)` (citas activas futuras fuera de jornada, en día cerrado o sobre bloqueo)
- [x] 3.5 Endpoints `GET/PUT /api/admin/horario`, `GET/PUT /api/admin/trabajadores/{id}/horario`, `GET /api/admin/agenda/conflictos`, `GET /api/public/horario` en `AdminController`/`PublicController`
- [x] 3.6 Tests: horario inválido 400, reducción con cita fuera → cita sigue CONFIRMADA y aparece en conflictos, override fuera del general 400

## 4. Cancelación por negocio y avisos

- [x] 4.1 `ReservaService.cancelarPorNegocio(reserva, motivo, adminId)` (solo PENDIENTE/CONFIRMADA, si no 400): CANCELADA + motivo + `CanceladaPor.ADMIN`, crea `AvisoCancelacion` (EMAIL/PENDIENTE_ENVIO o TELEFONO/PENDIENTE) y publica `event/ReservaCanceladaPorNegocioEvent` (D6, D7)
- [x] 4.2 `cancelarReserva` (token) registra `CanceladaPor.CLIENTE` sin aviso
- [x] 4.3 `updateReservaAdmin`: si el estado pasa de activo a CANCELADA, delegar en `cancelarPorNegocio`
- [x] 4.4 `EmailService.enviarCancelacion(ReservaResponse, email, motivo)` devolviendo `boolean`, con plantilla HTML (negocio, servicio, trabajador, fecha larga, hora, motivo, enlace a reservar de nuevo)
- [x] 4.5 `ReservaEmailListener.onReservaCanceladaPorNegocio` (`@Async`, `AFTER_COMMIT`) + `AvisoCancelacionService.marcarResultadoEnvio` (`REQUIRES_NEW`): ENVIADO/FALLIDO
- [x] 4.6 `AvisoCancelacionService.pendientes()` (TELEFONO/PENDIENTE, EMAIL/FALLIDO, EMAIL/PENDIENTE_ENVIO > 15 min), `contarPendientes()`, `marcarAvisado(id, adminId)`; DTO `AvisoCancelacionDTO` (cliente, teléfono, cita, servicio, trabajador, motivo, canal, estado)
- [x] 4.7 Endpoints `POST /api/admin/reservas/{id}/cancelar`, `GET /api/admin/avisos?pendientes=true`, `GET /api/admin/avisos/pendientes/count`, `PUT /api/admin/avisos/{id}/avisado`
- [x] 4.8 Tests: cliente con email → email enviado y aviso ENVIADO; sin email → aviso TELEFONO sin email (estilo `NoEmailNoNotificationTest`); SMTP que falla → FALLIDO y aparece en pendientes; rollback → sin email; cancelar COMPLETADA → 400; cancelación por token → sin aviso

## 5. Cierres de día

- [x] 5.1 DTOs `CierreCreateRequest{fecha, trabajadorId?, motivo}`, `CierreDTO`, `CierreResultadoDTO{cierre, canceladas, porEmail, porTelefono}`, `CierrePreviewDTO`
- [x] 5.2 `service/CierreService.crear` transaccional: lock (trabajador o todos por id asc), fecha ≥ hoy (400), duplicado (409), guardar cierre, cancelar afectadas vía `cancelarPorNegocio`, devolver resumen (D4)
- [x] 5.3 `CierreService.preview`, `listar(desde, hasta)` y `eliminar(id)` (solo futuros; no reactiva citas)
- [x] 5.4 Endpoints `GET /api/admin/cierres`, `GET /api/admin/cierres/preview`, `POST /api/admin/cierres` (201), `DELETE /api/admin/cierres/{id}`
- [x] 5.5 Tests: cierre general con 3 citas (2 con email, 1 sin) → resumen 3/2/1; cierre de trabajador no afecta a otros; COMPLETADA intacta; duplicado 409; fecha pasada 400; reabrir no reactiva; disponibilidad vacía tras cierre; concurrencia reserva vs. cierre (dos hilos) nunca deja cita activa en día cerrado

## 6. Bloqueos de horas libres y agenda del trabajador

- [x] 6.1 DTOs `BloqueoCreateRequest{fecha, horaInicio, horaFin, motivo}`, `BloqueoDTO`, `DiaAgendaDTO{fecha, trabaja, inicio, fin, motivoCierre, bloqueos}`
- [x] 6.2 `service/BloqueoService.crear(trabajadorId, req)` con lock D3: validaciones 400, solape con citas activas u otros bloqueos → 409 con detalle; `listarPropios`, `eliminarPropio` (ajeno → 404), `listarAdmin`, `eliminarAdmin` (D5)
- [x] 6.3 Endpoints `GET/POST/DELETE /api/worker/bloqueos` (trabajador SIEMPRE del JWT), `GET /api/worker/agenda?desde&hasta`, `GET/DELETE /api/admin/bloqueos`
- [x] 6.4 `WorkerController#getReservasSemana`: por defecto lunes→domingo (`inicio + 6`); actualizar Javadoc
- [x] 6.5 Tests: bloqueo crea hueco no disponible; sobre cita → 409; `trabajadorId` ajeno en el cuerpo ignorado; borrar bloqueo ajeno → 404; admin crea bloqueo propio desde `/api/worker/bloqueos`

## 7. Ingresos y autorización

- [x] 7.1 `ReservaRepository.sumIngresosCompletadasPorMetodoPagoTrabajadorBetween` y usarla en `StatsService.getWorkerDashboard` (corrige la fuga del desglose global) (D10)
- [x] 7.2 Consulta agrupada por trabajador y `StatsService.getIngresosPorTrabajador(start, end)` → `IngresosResumenDTO{filas: IngresoTrabajadorDTO[], totales}` con el mismo criterio que `getDashboardStats`
- [x] 7.3 Endpoint `GET /api/admin/ingresos?inicio&fin`
- [x] 7.4 `@EnableMethodSecurity` en `SecurityConfig` y `@PreAuthorize("hasRole('ADMIN')")` a nivel de clase en `AdminController`, verificando que la excepción POST `/api/admin/media/trabajadores` para WORKER sigue funcionando (excluir ese método o anotarlo con `hasAnyRole`)
- [x] 7.5 Test `IngresosIsolationTest`: Ana (VISA 15 €), Luis (VISA 20 €) y admin (efectivo 30 €) el mismo día → `/api/worker/dashboard` de Ana devuelve visa 15 y efectivo 0; `?trabajadorId=` ajeno ignorado en `/dashboard` y `/reservas/semana`; `/api/admin/ingresos` suma 65 y coincide con `/api/admin/dashboard`
- [x] 7.6 Ampliar `AuthorizationMatrixTest`: 401 sin token y 403 WORKER en `/api/admin/ingresos`, `/api/admin/horario`, `/api/admin/cierres`, `/api/admin/avisos`, `/api/admin/bloqueos`, `/api/admin/reservas/{id}/cancelar`, `/api/admin/trabajadores/{id}/horario`; 200 ADMIN; 200 WORKER y ADMIN en `/api/worker/bloqueos` y `/api/worker/agenda`; públicos sin token en `/api/public/horario` y `/api/public/dias-cerrados`

## 8. Frontend — API y administración

- [x] 8.1 `services/api.js`: añadir `adminApi.{getHorario, saveHorario, getHorarioTrabajador, saveHorarioTrabajador, getConflictos, getCierres, previewCierre, crearCierre, eliminarCierre, cancelarReserva, getAvisosPendientes, contarAvisos, marcarAvisado, getIngresos, getBloqueos, eliminarBloqueo}`, `workerApi.{getAgenda, getBloqueos, crearBloqueo, eliminarBloqueo}`, `publicApi.{getHorario, getDiasCerrados}`
- [x] 8.2 Nueva `pages/admin/Horarios.jsx`: horario general (7 días, abierto/cerrado, horas) y por trabajador (hereda / día libre / horas); tras guardar, lista de conflictos con "Cancelar y avisar"
- [x] 8.3 Nueva `pages/admin/Cierres.jsx`: listado por mes, formulario (toda la barbería o trabajador, fecha, motivo) con vista previa obligatoria y confirmación; sección "Clientes por avisar" con nombre, `tel:` teléfono, cita, motivo y botón "Avisado"
- [x] 8.4 `pages/admin/Dashboard.jsx`: tabla "Ingresos por trabajador" (total, VISA, efectivo, completadas) para el rango seleccionado
- [x] 8.5 `pages/admin/Reservations.jsx`: acción "Cancelar y avisar" con modal de motivo (usa el endpoint nuevo) y mostrar motivo/canceladaPor en canceladas
- [x] 8.6 `pages/admin/Workers.jsx`: toggle "Reservable (aparece en la web)"
- [x] 8.7 `routes/AppRoutes.jsx`: rutas `/admin/horarios` y `/admin/cierres`, enlaces en `adminLinks` (con contador de avisos pendientes) y ocultar "Administración" en `workerLinks` para no-admins

## 9. Frontend — panel del trabajador y web pública

- [x] 9.1 `pages/worker/Dashboard.jsx`: sustituir `DAY_LABELS`/`WEEK_DAYS` fijos por los días de `/api/worker/agenda` (semana lun–dom, marcando cerrados con motivo y ocultando/desactivando días sin jornada); no permitir crear citas en días cerrados
- [x] 9.2 Panel del trabajador: sección "Mis horas libres" (lista de bloqueos del rango, modal "Bloquear horas" con fecha/inicio/fin/motivo, manejo de 409 con citas en conflicto, eliminar)
- [x] 9.3 Verificar que las tarjetas de ingresos del panel siguen calculándose solo con las reservas propias (también para el admin en "Mi panel")
- [x] 9.4 `pages/public/DateTimePicker.jsx` y `pages/public/Reschedule.jsx`: consultar `dias-cerrados` y mostrar "Cerrado" / impedir seleccionar esos días; manejar el 400 de día cerrado
- [x] 9.5 `pages/public/MyAppointments.jsx`: estado "Cancelada por la barbería" + motivo + enlace a reservar; sin acciones de reprogramar/cancelar
- [x] 9.6 `pages/public/Landing.jsx`: horario desde `/api/public/horario` con `HORARIO_TEXTO` de `config/business.js` como respaldo (actualizar comentario KEEP IN SYNC)

## 10. Verificación y documentación

- [x] 10.1 `./mvnw test` en verde (incluidos los tests nuevos y los existentes: `SlotGeneratorTest`, `PublicBookingWithoutEmailTest`, `AuthorizationMatrixTest`…)
- [x] 10.2 `npm run lint` y `npm run build` en `frontend/` sin errores
- [x] 10.3 Prueba manual: arrancar contra una copia de la BD, comprobar que un martes y un domingo dan la misma disponibilidad que antes; crear cierre con cita sin email y verla en "Clientes por avisar"
- [x] 10.4 Actualizar README (horario configurable, cierres, avisos, propiedades `app.business.*` como siembra, modo log del email de cancelación)
