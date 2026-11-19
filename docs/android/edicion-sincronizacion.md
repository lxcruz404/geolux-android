# Edición en Android y sincronización con Desktop (propuesta de etapa siguiente)

8 nov. 2026 · Andrés. Propuesta para discutir; **no implementada**. Primero se
cierra la beta de importación.

## 1. Qué se quiere

Durante una visita a la finca, desde el teléfono y sin conexión: crear y modificar
potreros, registrar cercas, tanques, bebederos, tuberías, construcciones e
infraestructura, actualizar datos, y llevar esos cambios a GeoLux Desktop.

## 2. Arquitectura recomendada

**Desktop sigue siendo la fuente de verdad. Android guarda cambios, no copias.**

1. **Base local en Android (Room/SQLite)** con dos partes:
   - el *estado importado* del paquete, de solo lectura, como hoy;
   - un **diario de cambios**: una fila por operación (`create`, `update`,
     `delete`) sobre un elemento, con `change_id` (UUID), `entity` (zona,
     elemento, línea…), `entity_id`, la versión del elemento que se editó
     (`base_version`), los campos nuevos, la geometría nueva, autor, dispositivo
     y fecha.
   Lo que ve el usuario es el estado importado con el diario aplicado encima.
2. **Identificadores estables:** los elementos nuevos nacen en Android con UUID;
   los existentes conservan su id de Desktop. Nunca se reutilizan ids.
3. **Versiones por elemento en Desktop:** cada zona, elemento y línea gana un
   número de versión (o fecha de modificación) que viaja en el paquete `.glx`.
   Es lo que permite saber si Desktop cambió algo después de exportar.
4. **Paquete de retorno** (`.glxr` o `.glx` con perfil `cambios`): ZIP con
   `manifest.json` (dispositivo, `package_id` del que partió, fechas, hashes) y
   `changes.json` (el diario), más los adjuntos nuevos (fotos de campo). Se
   comparte con «Compartir» de Android (correo, cable, nube); no hace falta
   servidor para la primera versión.
5. **Importación en Desktop:** Desktop lee el paquete de retorno, aplica cada
   cambio cuyo `base_version` coincide con su versión actual y presenta los
   **conflictos** (el elemento cambió en Desktop después de exportar, o se borró)
   en una pantalla de revisión: aceptar Android, conservar Desktop o combinar.
   Nada se aplica sin que el usuario lo vea.
6. Después de sincronizar, Desktop exporta un `.glx` nuevo y Android lo importa
   («Reemplazar»); el diario aplicado se vacía.

## 3. Cómo se evitan los conflictos

- Versión por elemento + `base_version` en cada cambio (control optimista).
- Cambios a nivel de elemento y de campo, no de finca entera: dos personas pueden
  editar potreros distintos sin chocar.
- Las geometrías se validan en Android con las mismas reglas que Desktop (anillos
  cerrados, coordenadas en rango, fronteras compartidas) antes de guardarse.
- Un paquete de retorno se aplica una sola vez (`change_id` y `package_id`
  registrados en Desktop).

## 4. Cambios de contrato

- Contrato nuevo `geolux-changes` v1 para el paquete de retorno (no cabe en v1:
  va en sentido contrario).
- En el `.glx`: `version` (o `updated_at`) por feature en las capas y un
  `package_id` que Android recuerde como base. Es aditivo: `format_version` 1
  puede seguir.
- Catálogo común de tipos de elemento y categorías de línea (ya existe en
  Desktop: `core/line_categories.py`) para que Android no invente tipos.

## 5. Trabajo por persona (estimación gruesa)

| Quién | Trabajo |
|---|---|
| Luis (Desktop) | Versiones por elemento; exportarlas; importador del paquete de retorno; pantalla de conflictos; pruebas con paquetes de retorno de fixture |
| Andrés (datos Android) | Room y diario de cambios; aplicar el diario sobre el estado importado; validación de geometrías; escritor del paquete de retorno; fixture y pruebas JVM del contrato nuevo |
| Rodolfo (UI Android) | Herramientas de dibujo en el mapa (punto, línea, polígono, mover vértices), formularios de cada tipo, lista de cambios pendientes, «Enviar a Desktop» |
| Los tres | Contrato `geolux-changes` v1, fixture compartido y orden de las entregas |

## 6. Orden sugerido

1. Contrato y fixture del paquete de retorno.
2. Versiones por elemento en Desktop y en el `.glx`.
3. Diario de cambios en Android con un caso simple (crear un elemento puntual).
4. Importación en Desktop con revisión de conflictos.
5. Líneas y polígonos, luego adjuntos.
