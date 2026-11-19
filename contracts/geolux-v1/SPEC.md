# Contrato de intercambio `.geolux` v1

Estado: **implementado en Desktop (exportación y lector de referencia en
Python); pendiente de implementar en Android** (Andrés y Rodolfo).
Base: `docs/GEOLUX_MANUAL_OFICIAL/.../02_DATA_CONTRACT.md` (manual v2.0) y el
plan maestro, fase B1 (9 de octubre de 2026).

## 1. Propósito y alcance

Llevar los datos de **un proyecto (finca)** del escritorio al teléfono para
consultarlos sin conexión en GeoLux Android. Solo lectura en Android (decisión
D5): no hay sincronización ni paquete de retorno en v1.

El paquete es **selectivo**: nunca contiene la base de datos del escritorio,
rutas locales, perfiles de dron, parámetros internos de exportación DJI ni
datos de otros proyectos.

| Incluido en v1 | No incluido en v1 |
|---|---|
| Proyecto (identidad, nombre, ubicación, centro, fechas) | Base SQLite, rutas del equipo |
| Área de trabajo (Polygon) | Lotes de ganado y cualquier dato ganadero nuevo (animales, eventos) |
| Potreros (Polygon) con estado y capacidad de carga | Conexiones de agua (candidatas a v1.1) |
| Elementos de infraestructura y agua (Point) | Conjuntos de datos importados (GeoTIFF…) |
| Cercas, linderos, tuberías, caminos (LineString) | Paquetes de mapas offline (MBTiles de teselas base) |
| Misiones de vuelo planificadas: área (Polygon) y ruta (LineString) con su resumen | Fotografías |
| Productos fotogramétricos cuando existan: ortomosaico MBTiles y modelo GLB con metadatos | |

## 2. Archivo

- Extensión `.geolux`; es un **ZIP** (método `deflate` o `stored`).
- Codificación de texto: UTF-8 sin BOM. JSON estándar (RFC 8259).
- Rutas dentro del ZIP con `/`, relativas, sin `..`, sin rutas absolutas y sin
  mayúsculas distintas para el mismo nombre. Un lector **rechaza** cualquier
  entrada que no cumpla esto (protección contra «zip slip»).

```
manifest.json                      obligatorio
project.json                       obligatorio
layers/work_area.geojson           obligatorio (puede no tener features)
layers/zones.geojson               obligatorio (puede no tener features)
layers/elements.geojson            obligatorio (puede no tener features)
layers/lines.geojson               obligatorio (puede no tener features)
layers/missions.geojson            obligatorio (puede no tener features)
layers/mission_paths.geojson       obligatorio (puede no tener features)
products/<product_id>/meta.json    por producto
products/<product_id>/ortho.mbtiles  opcional
products/<product_id>/model.glb      opcional
```

## 3. `manifest.json`

```json
{
  "format": "geolux-package",
  "format_version": 1,
  "min_reader_version": 1,
  "package_id": "uuid v4",
  "created_at": "2026-10-09T23:00:00+00:00",
  "generator": {"app": "GeoLux Desktop", "app_version": "0.1.0", "db_schema_version": 2},
  "project": {"id": "uuid", "farm_id": null, "name": "Finca La Esperanza"},
  "crs": "EPSG:4326",
  "contents": [
    {"path": "project.json", "kind": "project", "schema": "geolux.project/1",
     "required": true, "size_bytes": 512, "sha256": "…"},
    {"path": "layers/zones.geojson", "kind": "layer", "dataset": "zones",
     "schema": "geolux.zones/1", "geometry": "Polygon", "feature_count": 4,
     "required": true, "size_bytes": 2048, "sha256": "…"}
  ],
  "excluded": ["livestock", "photos", "datasets", "offline_basemaps", "water_connections"]
}
```

| Campo | Regla |
|---|---|
| `format` | Exactamente `"geolux-package"` |
| `format_version` | Entero. Cambia solo con un cambio incompatible |
| `min_reader_version` | El lector rechaza el paquete si su versión soportada es menor |
| `package_id` | UUID nuevo en cada exportación |
| `project.id` | El id estable del proyecto en Desktop; es la clave para detectar duplicados al importar |
| `contents[]` | Lista de **todos** los archivos del ZIP salvo `manifest.json`. `sha256` en hexadecimal minúsculas y `size_bytes` exactos |
| `schema` | `geolux.<conjunto>/<versión>` de cada archivo |
| `excluded` | Lo que v1 deja fuera a propósito, para que Android no lo espere |

**Evolución sin romper:** campos nuevos, conjuntos nuevos con `required: false`
o productos nuevos no cambian `format_version`; los lectores ignoran lo que no
conocen. Quitar o cambiar el significado de un campo, o volver obligatorio un
conjunto nuevo, sí lo cambia (2), y un lector de v1 lo rechaza con un mensaje
claro.

## 4. `project.json` (`geolux.project/1`)

`id`, `name`, `description`, `location`, `farm_id` (o `null`),
`center` (`{"lat", "lon"}` o `null`), `area` (`{"value", "unit"}` o `null`),
`created_at`, `updated_at`.

## 5. Capas GeoJSON

`FeatureCollection` en WGS84 (EPSG:4326, `[lon, lat]`). Las coordenadas son
las mismas que guarda Desktop, sin redondear ni reproyectar. Cada `Feature`
lleva `id` (el id estable de Desktop, también en `properties.id`).

| Archivo | Geometría | Propiedades |
|---|---|---|
| `work_area.geojson` | Polygon | `id`, `area_m2`, `perimeter_m` |
| `zones.geojson` | Polygon | `id`, `name`, `description`, `status` (`active`/`resting`/`inactive`), `status_label`, `color`, `area_m2`, `perimeter_m`, `livestock_capacity` (o `null`) |
| `elements.geojson` | Point | `id`, `element_type`, `type_label`, `name`, `description` |
| `lines.geojson` | LineString | `id`, `category`, `category_label`, `name`, `description`, `length_m` |
| `missions.geojson` | Polygon (área de vuelo) | `id`, `name`, `plan_status`, `plan_status_label`, `altitude_m`, `gsd_cm_px`, `speed_mps`, `front_overlap_percent`, `side_overlap_percent`, `photo_count`, `waypoint_count`, `total_distance_m`, `estimated_time_s`, `battery_verdict` (o `null`), `planned_at` |
| `mission_paths.geojson` | LineString (ruta de waypoints en orden) | `id` (= id de la misión), `mission_id`, `altitude_m`, `waypoint_count` |

Una misión sin área no aparece en `missions.geojson`; sin waypoints, no
aparece en `mission_paths.geojson`. Las etiquetas (`*_label`) son el texto en
español que muestra Desktop, para que Android no tenga que traducir claves.

## 6. Productos (`products/<product_id>/`)

`meta.json` (`geolux.product/1`): `id`, `kind` (`orthomosaic` o `model3d`),
`mission_id` (o `null`), `name`, `created_at`, `origin` (`"real"` o
`"synthetic"`), `crs`, `bounds` (`[oeste, sur, este, norte]` en EPSG:4326),
`files` (lista de rutas del producto), y para modelos 3D `origin_point`
(`{"lat","lon","alt_m"}`) y `units` (`"m"`), porque un GLB solo no es un
contrato GIS (manual 11).

- **Ortomosaico:** MBTiles 1.3 en esquema TMS, `format` `png` o `jpg`.
- **Modelo 3D:** glTF 2.0 binario (`.glb`).
- Un producto `synthetic` existe solo para pruebas; Android debe mostrarlo
  marcado como tal.

Desktop incluye, por misión, el último ortomosaico y el último modelo 3D
**verificados** (20 oct. 2026, cierre de la Beta):

- Ortomosaico: las mismas teselas PNG que muestra el mapa de Desktop, en
  MBTiles 1.3 (`crs` `EPSG:3857`, `bounds` en EPSG:4326).
- Modelo 3D: el `.glb` que produjo el motor, sin modificar. `origin_point` es
  su origen local (`CESIUM_RTC`) convertido a WGS 84; los vértices están en
  metros respecto a ese punto, con los ejes este/norte/arriba del sistema UTM
  del procesamiento. NodeODM suele comprimir la malla con
  `KHR_draco_mesh_compression`: un lector que no lo admita debe avisar en vez
  de mostrar un modelo vacío.
- Un producto que no se puede ubicar (sin sistema de coordenadas u origen) no
  se incluye y Desktop lo dice al exportar; nunca se adivina su posición.

Estos cambios no alteran el formato: usan los campos ya definidos.

## 7. Validación al importar (Android y lector de Python)

Errores (el paquete se **rechaza**, nada se importa):

| Código | Mensaje (es) |
|---|---|
| `not_a_package` | El archivo no es un paquete GeoLux (no es un ZIP válido). |
| `manifest_missing` / `manifest_invalid` | Falta `manifest.json` o no es JSON válido. |
| `wrong_format` | El archivo no es un paquete GeoLux. |
| `too_new` | El paquete se creó con una versión más nueva de GeoLux (formato N); actualiza la aplicación. |
| `unsafe_path` | El paquete contiene rutas no permitidas. |
| `file_missing` | Falta el archivo obligatorio X. |
| `hash_mismatch` / `size_mismatch` | El archivo X está dañado o fue modificado. |
| `invalid_json` / `invalid_geojson` | X no es un GeoJSON válido (motivo). |
| `coordinates_out_of_range` | X tiene coordenadas fuera de rango. |
| `duplicate_id` | X repite el identificador Y. |

Advertencias (se importa, y se informa): `undeclared_file` (archivo no listado,
se ignora), `unknown_dataset` (conjunto opcional desconocido, se ignora),
`synthetic_product`.

## 8. Política de importación (no sobrescribir en silencio)

Clave: `project.id`. Si el teléfono ya tiene ese proyecto:

1. Si `package_id` es el mismo: «Este paquete ya está importado»; no se hace
   nada.
2. Si es otro paquete del mismo proyecto: se pregunta al usuario, mostrando
   fechas `created_at` de ambos, entre **Reemplazar** (con confirmación) y
   **Cancelar**. Nunca se reemplaza sin esa respuesta.
3. Un paquete con `created_at` anterior al importado se señala como «más
   antiguo» en esa pregunta.

La importación es atómica: o queda el paquete nuevo completo, o el anterior
intacto.

## 9. Fixture compartido

`contracts/geolux-v1/fixtures/finca-demo.geolux` y su contenido expandido en
`fixtures/finca-demo/` (para revisarlo en un diff). Generado por
`tools/build_geolux_fixture.py`; contiene datos sintéticos de una finca de
prueba y un ortomosaico y un modelo 3D **sintéticos**. `expected.json`
resume lo que cualquier lector debe obtener (número de features por capa, ids
y valores clave). Python lo verifica en `tests/core/test_b1_geolux_contract.py`;
Android debe tener una prueba unitaria que lea el mismo archivo y compare con
`expected.json`. Los paquetes inválidos de `fixtures/invalid/` deben
rechazarse con el código indicado en su nombre.
