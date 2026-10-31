## ADDED Requirements

### Requirement: Ingresos desglosados por trabajador para el admin
El sistema SHALL exponer `GET /api/admin/ingresos?inicio=&fin=` (solo ADMIN) que devuelva, para cada trabajador (incluido el admin y trabajadores inactivos con reservas en el rango), el total de ingresos, el desglose VISA, efectivo y sin método registrado, y el número de citas completadas, junto a los totales globales. El criterio de ingresos MUST ser el mismo que el del dashboard actual: total = citas COMPLETADA y CONFIRMADA; desglose por método = solo COMPLETADA.

#### Scenario: Admin consulta ingresos del mes
- **WHEN** el ADMIN pide los ingresos del 1 al 31 de octubre
- **THEN** recibe una fila por trabajador con total, VISA, efectivo y nº de completadas, y la suma de filas coincide con el total global de `/api/admin/dashboard` en ese rango

#### Scenario: Trabajador intenta ver ingresos de todos
- **WHEN** un WORKER llama a `GET /api/admin/ingresos`
- **THEN** el sistema responde 403

### Requirement: Cada trabajador solo ve sus propios ingresos
Todo endpoint bajo `/api/worker/**` que devuelva importes SHALL calcularlos exclusivamente con las reservas del trabajador autenticado (identificado por el token), sin aceptar parámetros que permitan elegir otro trabajador ni agregar a nivel de barbería. En particular, `GET /api/worker/dashboard` SHALL calcular `ingresosVisa` e `ingresosEfectivo` filtrando por el trabajador autenticado (hoy suma toda la barbería).

#### Scenario: Desglose del dashboard aislado
- **WHEN** Ana tiene ese día una cita COMPLETADA pagada con VISA de 15 € y Luis otra de 20 €, y Ana llama a `GET /api/worker/dashboard`
- **THEN** `ingresosVisa` es 15 € (no 35 €)

#### Scenario: El trabajador no ve los ingresos del admin
- **WHEN** el admin tiene citas COMPLETADA ese día y Ana consulta su dashboard o sus reservas semanales
- **THEN** ningún importe de la respuesta incluye las citas del admin

#### Scenario: Parámetro de otro trabajador ignorado
- **WHEN** Ana llama a `GET /api/worker/dashboard?trabajadorId={idLuis}` o `GET /api/worker/reservas/semana?trabajadorId={idLuis}`
- **THEN** la respuesta contiene solo datos de Ana

### Requirement: El admin ve sus ingresos propios en su panel
Cuando el ADMIN usa "Mi panel" (`/trabajador`), los ingresos mostrados SHALL ser solo los de sus propias citas, igual que para cualquier trabajador; los ingresos de la barbería y por trabajador SHALL mostrarse solo en el dashboard de administración.

#### Scenario: Admin en su panel de trabajador
- **WHEN** el ADMIN abre `/trabajador`
- **THEN** las tarjetas de ingresos de día, semana y mes reflejan solo sus citas

### Requirement: Tests de autorización de ingresos
La suite de tests SHALL incluir pruebas de integración que verifiquen: 403 para WORKER en `/api/admin/ingresos`, `/api/admin/dashboard`, `/api/admin/avisos`, `/api/admin/cierres` y `/api/admin/horario`; aislamiento del desglose en `/api/worker/dashboard` con dos trabajadores y el admin con citas el mismo día; y que `/api/worker/**` ignora identificadores de otro trabajador.

#### Scenario: Ejecución de la suite
- **WHEN** se ejecuta `./mvnw test`
- **THEN** los tests de aislamiento de ingresos y matriz de autorización de los nuevos endpoints pasan
