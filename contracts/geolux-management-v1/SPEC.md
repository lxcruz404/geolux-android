# Contrato de gestión de finca v1 (`geolux.management/1`)

11 nov. 2026 · Propuesta para revisión de Luis, Andrés y Rodolfo. Describe los
datos de gestión que comparten GeoLux Desktop y GeoLux Android: actividades,
recordatorios, compras, trabajadores, finanzas y adjuntos, y cómo viajan en
el paquete `.glx`. La ganadería ya existe en Desktop (migraciones 4 y 6) y se
incorpora aquí con sus reglas actuales, sin cambiarlas.

Los catálogos (claves y etiquetas) están en `catalogs.json`, que es la fuente
única: las pruebas de cada plataforma comprueban que sus constantes coinciden.

## 1. Convenciones

| Tema | Regla |
|---|---|
| Identificador | UUID v4 en texto, generado por el dispositivo que crea el registro. Nunca se reutiliza ni se cambia. Excepciones deterministas (UUID v5, espacio `6f1c7a52-3b1e-4c5e-9a43-0c5f6e8d2b10`): el día de una repetición, `occurrence:<series_id>:<AAAA-MM-DD>`, y una categoría propia, `category:<project_id>:<key>`; así dos equipos que los crean por su cuenta producen el mismo registro. |
| Ámbito | Todo registro pertenece a un proyecto (`project_id`), que en Android es la finca importada. Nada se comparte entre proyectos. |
| Identificación visible | Texto libre (`tag` del animal, `code` del trabajador). Admite letras, números y separadores (`2068-38`). Única dentro del proyecto, sin distinguir mayúsculas ni espacios de los extremos. Nunca se convierte a número. |
| Marca de tiempo | `created_at`, `updated_at`, `deleted_at`: ISO 8601 en UTC con segundos (`2026-11-11T12:00:00+00:00`). |
| Fecha de un hecho | `YYYY-MM-DD` (fecha local de la finca): nacimiento, pesaje, pago, compra. |
| Fecha y hora programada | `scheduled_date` (`YYYY-MM-DD`) + `scheduled_time` opcional (`HH:MM`, 24 h) + `time_zone` (IANA, p. ej. `America/Bogota`). Así un recordatorio a las 8:00 sigue siendo a las 8:00 aunque el teléfono cambie de zona. |
| Dinero | `amount` en texto decimal con punto (`"125000.50"`), nunca número de coma flotante; `currency` ISO 4217 (por defecto `COP`). Python `Decimal`, Kotlin `BigDecimal`. Siempre positivo; el sentido lo da `direction`. |
| Cantidades y pesos | Texto decimal (`"2.5"`). Pesos en kilogramos. |
| Borrado | Los registros editables se marcan con `deleted_at` (lápida) y no se borran físicamente mientras puedan viajar en un paquete. Los movimientos financieros y los eventos del ganado nunca se borran: se anulan. |
| Adjuntos | Ruta relativa a la carpeta del proyecto, siempre `adjuntos/<sha[0:2]>/<sha256><.ext>` (cualquier otra se rechaza al importar), + SHA-256 + tamaño. Un mismo archivo se guarda una sola vez por proyecto. |

## 2. Entidades

```
project 1─* activity *─* (animal | livestock_lot | zone | map_line | map_element | work_area | purchase_list | purchase_item | worker)   vía activity_link
activity *─1 activity_series (recurrencia)        activity *─0..1 worker (responsable)
purchase_list 1─* purchase_item ─0..1 financial_transaction
worker 1─* financial_transaction (pagos, anticipos)
financial_transaction *─0..1 activity | purchase_list | worker
attachment *─1 (activity | purchase_item | financial_transaction | worker | animal)
financial_category: catálogo + categorías propias del proyecto
```

### 2.1 `activity`

| Campo | Tipo | Regla |
|---|---|---|
| `id`, `project_id` | texto | — |
| `title` | texto | Obligatorio, 1–200 caracteres. |
| `description`, `notes` | texto | Opcional. |
| `category` | clave | `activity_category` o `custom:<texto>`. |
| `status` | clave | `activity_status`. `done` exige `completed_at`; volver a `pending` lo borra. |
| `priority` | clave | `activity_priority`; por defecto `medium`. |
| `scheduled_date`, `scheduled_time`, `time_zone` | ver §1 | `scheduled_date` opcional (actividad sin fecha). |
| `remind_minutes_before` | entero o null | null = sin recordatorio. |
| `assignee_worker_id` | texto o null | Trabajador del mismo proyecto, activo al asignar. |
| `series_id`, `occurrence_date` | texto o null | Si viene de una recurrencia. `(series_id, occurrence_date)` es único: nunca se generan duplicados. |
| `completed_at` | marca de tiempo o null | — |
| `created_at`, `updated_at`, `deleted_at` | §1 | — |

Vencida = `status` en `pending`/`in_progress` y fecha+hora programada anterior a ahora en su `time_zone`.

### 2.2 `activity_link`

`(activity_id, entity, entity_id)` único. `entity` en `link_entity`. El
`entity_id` es el id que ya usa la capa o el módulo (zona, línea, elemento,
animal, lote): no se duplican entidades geográficas.

### 2.3 `activity_series`

`frequency` (`daily`/`weekly`/`monthly`), `interval` (≥ 1), `weekdays`
(semanal, 1 = lunes … 7 = domingo), `start_date`, `until_date` o `count`, y
la plantilla (título, categoría, prioridad, hora, enlaces). Las ocurrencias se
generan como actividades concretas hasta 60 días por delante; generar dos
veces el mismo día no crea otra. Mensual en día 29–31: el último día del mes
cuando el mes es más corto.

### 2.4 `purchase_list` y `purchase_item`

Lista: `name`, `list_type` (`purchase_list_type`), `description`,
`planned_date`, `responsible_worker_id`, `status` (`purchase_list_status`),
`notes`. Artículo: `list_id`, `name`, `quantity` (decimal > 0), `unit`
(`unit`), `category`, `description`, `estimated_price`, `actual_price`
(dinero, opcionales), `status` (`purchase_item_status`), `transaction_id`
(null hasta que la persona confirma el pago), `notes`.

Regla: marcar comprado **no** crea un gasto. El gasto se crea solo cuando la
persona lo confirma, con el valor real, y entonces se enlaza
(`transaction_id`). Una lista pasa a `done` cuando todos sus artículos están
`bought` o `cancelled`.

### 2.5 `worker`

`code` (identificación visible, única en el proyecto), `full_name`, `role`,
`start_date`, `phone` (opcional), `status` (`worker_status`), `notes`. No se
guardan más datos personales.

### 2.6 `financial_transaction`

| Campo | Regla |
|---|---|
| `type` | `transaction_type`. |
| `direction` | `in`, `out` o `none`, uno de los permitidos por su tipo (`catalogs.json`). `none` (transferencias) no cuenta en el balance. |
| `date`, `concept`, `category`, `amount`, `currency`, `payment_method` | Obligatorios salvo `payment_method`. `category` en `financial_category` o una propia del proyecto. |
| `counterparty`, `responsible`, `reference`, `notes` | Opcionales. |
| `worker_id` | Obligatorio en `worker_payment` y `worker_advance`. |
| `activity_id`, `purchase_list_id` | Opcionales. |
| `voids_transaction_id` | Una anulación apunta al original; el original queda en el historial como anulado y ninguno de los dos cuenta en los totales. |

Totales: ingresos = Σ `in`; gastos = Σ `out`; balance = ingresos − gastos;
solo movimientos no anulados, en la moneda pedida (no se convierten monedas). Una
anulación lleva la fecha del día en que se hizo: el total de un período pasado debe
excluir los originales anulados en cualquier fecha.

### 2.7 `financial_category` propia

`id`, `project_id`, `key` (`custom:<slug>`), `label`, `archived`. Las del
catálogo no se pueden borrar; las propias se archivan.

### 2.8 `attachment`

`owner_entity`, `owner_id`, `path` (relativa al proyecto), `media_type`,
`size_bytes`, `sha256`, `created_at`, `deleted_at`.

## 3. Transporte en el `.glx`

Aditivo dentro de `format_version 1`: un lector v1 ignora entradas de un
`kind` que no conoce.

```
management/activities.json           kind: management, schema: geolux.management/1
management/activity_links.json
management/activity_series.json
management/purchase_lists.json
management/purchase_items.json
management/workers.json
management/transactions.json
management/financial_categories.json
management/attachments.json
management/files/<sha256>.<ext>      kind: management_file (adjuntos, opcionales)
```

Cada archivo: `{"schema": "geolux.management/1", "records": [ … ]}` con los
campos de §2 en `snake_case`. Todas las entradas con `required: false`.

### 3.1 Fusión al importar

- Registros editables: gana el `updated_at` más reciente; una lápida
  (`deleted_at`) cuenta como edición.
- Movimientos financieros y eventos del ganado: unión por `id` (nunca se
  editan); las anulaciones viajan como movimientos nuevos.
- Un registro cuyo `project_id` no es el del paquete se rechaza, y también uno que
  apunte a un registro de otra finca o inexistente.
- Mismo trabajador (código), día de repetición o categoría con distinto id: se rechaza
  con un mensaje que pide resolverlo (los dos últimos casi nunca ocurren gracias a los
  ids deterministas).
- Una compra pagada en dos equipos deja un movimiento de compra que ningún artículo
  usa: no se borra; se avisa para que la persona anule el duplicado.
- Las anulaciones se escriben después de su original; la ganadería se valida como
  historia completa (una vida imposible se rechaza).
- La importación es atómica: se aplica todo o nada.

## 4. Pendiente de decidir

- Transporte de la ganadería en el `.glx` (`management/animals.json`,
  `animal_events.json`, `livestock_lots.json`): mismas reglas de §3.1; Desktop
  es hoy la única fuente.
- Moneda por defecto por proyecto.
