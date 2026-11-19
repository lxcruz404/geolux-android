# Ampliación de GeoLux Android Beta: edición de mapas y paridad con Desktop

9 nov. 2026 · Andrés. Diseño y decisiones **antes** de implementar, como pide la
misión «Ampliación funcional y paridad con GeoLux Desktop». Rama:
`android/andres/edicion-mapas` (sobre `android/andres/integracion-beta`).

## 1. Punto de partida

- Android importa `.glx`/`.geolux`, valida, guarda el paquete intacto en
  `filesDir/projects/<hash>/package.geolux` y extrae los productos. Las capas se
  leen del paquete cada vez: **no hay ningún dato editable**.
- El mapa (MapLibre 13.7.1) se arma en código: fondo liso + ortomosaico (teselas
  XYZ extraídas del MBTiles) + capas GeoJSON. **Sin mapa base**: lo prohibía
  06 §10 para la Beta de lectura; esta misión lo cambia.
- 3D: SceneView 4.18 (Filament 1.71) abre `model_mobile.glb` por `role`.

## 2. Hallazgos

### 2.1 Desktop: entidades del módulo de mapas (la referencia)

| Entidad (Desktop) | Geometría | Atributos editables | Reglas de Desktop | En el `.glx` |
|---|---|---|---|---|
| Área de trabajo (`WorkArea`) | Polígono, **una por finca** | — (área y perímetro calculados) | polígono simple | `work_area.geojson` |
| Potrero (`Zone`) | Polígono | nombre (oblig.), descripción, color, estado (activo/en descanso/inactivo), capacidad de animales | polígono simple, **no se solapa con otro potrero** (compartir borde sí) | `zones.geojson` |
| Línea (`MapLine`) | LineString | categoría (12: cerca, lindero, cerca eléctrica, división, camino principal, camino interno, carretera, canal, río, tubería, sendero, otra), nombre (oblig.), descripción | ≥ 2 vértices; longitud calculada | `lines.geojson` |
| Elemento (`MapElement`) | Punto | tipo (21: punto de interés, tanque, bebedero, corral, casa, bodega, camino/acceso, infraestructura, otro, establo, galpón, manga, pozo, jagüey, reservorio, bomba, molino, fuente, transformador, poste, puerta, embarcadero, punto de acceso), nombre (oblig.), descripción | — | `elements.geojson` |
| Conexión de agua (`ElementConnection`) | arista entre elementos (+ tubería opcional) | tipo, descripción, ruta | ambos extremos en la finca | **No** (`excluded: water_connections`) |
| Misión | Polígono + ruta | (planificador) | — | solo lectura |

Cálculos (`core/geo_measure.py`): proyección local equirectangular (111 320 m/°
y cos de la latitud media), fórmula del área de Gauss, perímetro y longitud en
esa proyección. Validación de polígono simple (≥ 3 vértices distintos, sin
cruces propios, área ≥ 0,01 m²) y solape entre potreros con tolerancia de 1 mm.
Android los reproduce **con el mismo algoritmo**, para que un área calculada en
el teléfono sea la misma que daría Desktop.

### 2.2 Mapas base de Desktop (`core/base_layers.py`)

- **Estándar:** OpenStreetMap, `https://tile.openstreetmap.org/{z}/{x}/{y}.png`,
  ODbL, atribución «© OpenStreetMap contributors».
- **Satélite Esri World Imagery:**
  `https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}?blankTile=false`,
  máximo nativo 19 (17 seguro), atribución «Esri, Vantor, Earthstar Geographics,
  and the GIS User Community». Desktop lo marca **`offline_allowed=False`** y
  **`commercial_use_allowed=False`**: «Uso no comercial o de evaluación, con
  atribución. […] No se permite descargar para uso offline».
- Ambos son Web Mercator (EPSG:3857), igual que las teselas del ortomosaico que
  exporta Desktop (MBTiles `crs: EPSG:3857`). No hay reproyección de rásteres en
  el teléfono: todo es 3857 y las capas vectoriales están en WGS84.

### 2.3 Ortomosaico recortado

Desktop ya genera recortes (`ResultType.ORTHOMOSAIC_CROP`, GeoTIFF en el mismo
CRS y tamaño de píxel, con sus propias teselas), pero el exportador `.glx`
(`exchange_service._results`) solo elige `ORTHOMOSAIC`. **El recortado nunca
llega al teléfono.** Android, además, solo muestra el primer ortomosaico.

### 2.4 Calidad del 3D: dónde se pierde (medido)

| Modelo | `model.glb` (motor) | `model_mobile.glb` (lo que abre Android) |
|---|---|---|
| La Giralda | 34 texturas, **3 de 24576 × 24576 px**, 1945 Mpx (7,8 GB en RGBA8) | todas ≤ **1024 px**, 32 Mpx |
| Country Colors | 33 texturas, 1 de 24576 px, 711 Mpx | ≤ 1024 px, 26 Mpx |

1. **Origen:** el exportador de Desktop reduce todas las texturas a 1024 px
   (`MOBILE_TEXTURE_SIZE`) para no agotar el heap de 192 MB (OOM del 5 nov.). La
   textura grande pierde 24× de resolución lineal. Desktop, en cambio, usa el
   modelo del motor y solo reduce lo que pasa de `MAX_TEXTURE_SIZE` del GPU
   (16384 en un PC típico), sin mipmaps (`viewer.js`).
2. **Render:** SceneView aplica por defecto tone mapping *Filmic*, bloom, SSAO y
   FXAA. El modelo de ODM es *unlit* (la luz ya está en las fotos): Desktop pinta
   el color de la textura tal cual; Android lo altera y lo suaviza.
3. La geometría es la misma (mismos vértices en los dos archivos).

## 3. Decisiones

### 3.1 Persistencia de la edición (sin cambiar el contrato)

- El paquete importado **no se modifica nunca** (es el original de Desktop).
- Las capas editables se guardan en la carpeta de la finca como
  `layers/<capa>.geojson`, **en el mismo formato de SPEC §5** (mismas
  propiedades, `id` en el feature y en `properties.id`, etiquetas `*_label` en
  español). La primera edición copia las capas del paquete; desde entonces la
  finca se lee de ahí. Escritura atómica (archivo `.part` + renombre), así una
  muerte de la app nunca deja una capa a medias.
- Ids nuevos: UUID v4, como Desktop. Medidas recalculadas en el teléfono con el
  algoritmo de Desktop; nunca se fían de la interfaz.
- Reimportar un paquete de la misma finca (Reemplazar) **avisa** si hay cambios
  locales sin exportar: se perderían.

### 3.2 Exportar desde Android

«Exportar .glx» escribe un paquete v1 nuevo: las mismas entradas que el
importado (productos, fotos, `project.json`…), las capas editadas, y un
manifiesto con `package_id` nuevo, `created_at` actual, `generator.app =
"GeoLux Android"` y `size_bytes`/`sha256` recalculados. Lo lee el lector de
Desktop (`read_package`) sin cambios. **Sin sincronización** (la misión la deja
para después).

### 3.3 Mapas base

- Estándar (OSM) y Satélite (Esri) con las **mismas URLs y atribuciones que
  Desktop**, elegibles en «Capas del mapa». Por defecto: Satélite.
- **Caché:** la caché HTTP de MapLibre (teselas ya vistas, respeta las cabeceras
  del servidor). **No** se descarga en bloque ninguna zona: Desktop prohíbe el
  uso offline de Esri y la política de teselas de OSM prohíbe la descarga
  masiva. Sin conexión se ve lo que ya estaba en caché; lo demás queda en el
  color de fondo, con un aviso.
- Atribución siempre visible en el mapa.
- **Uso comercial:** Esri lo excluye sin licencia (ver 2.2). Lo decide Luis.
- Mapa sin finca: «Explorar el mapa» desde Mis fincas.

### 3.4 Ortomosaicos original y recortado (propuesta de contrato, aditiva)

- Android muestra **todos** los productos `orthomosaic`, cada uno con su
  interruptor y su contorno (límites del producto), encima del mapa base.
- Desktop: el exportador incluye también los recortes verificados como producto
  `kind: "orthomosaic"` con dos campos **opcionales nuevos** en su `meta.json`:
  `"variant": "full" | "cropped"` y `"source_product_id"` (el original). Un lector
  v1 que no los conozca los ignora (SPEC §3, «Evolución sin romper»: campos nuevos no cambian `format_version` y los lectores ignoran lo que no conocen): no
  cambia `format_version`. **Pendiente de aprobación de Luis**; no se toca
  `SPEC.md` hasta entonces.

### 3.5 3D

- Render fiel: tone mapping lineal, sin bloom, SSAO ni FXAA; MSAA 4× para los
  bordes.
- Texturas: si el paquete trae `model.glb`, Android prepara una copia **en el
  teléfono** (una vez, al abrir el 3D) con cada textura al máximo que admite
  ese GPU (`GL_MAX_TEXTURE_SIZE`) **y** cabe en un presupuesto de memoria según
  la RAM del teléfono, con mipmaps. Si falla, `model_mobile.glb` como hasta ahora.
  No requiere cambios en Desktop. Límite físico honesto: ningún teléfono
  dibuja una textura de 24576 px (el máximo habitual es 4096–16384) ni cabe
  7,8 GB de texturas sin comprimir.

### 3.6 Fuera de alcance (como dice la misión)

Procesamiento, planificador, misiones (solo lectura), sincronización, cuentas.
**Conexiones de agua:** Desktop no las exporta (`excluded`), así que Android no
puede editarlas sin ampliar el contrato. Queda documentado como pendiente.

## 4. Etapas y pruebas

1. Geometría y catálogos de Desktop en Kotlin (pruebas con los mismos casos que
   `geo_measure`), área en Mis fincas.
2. Mapas base, mapa sin finca, caché.
3. Almacén editable + operaciones (crear, editar, mover, eliminar) con las
   reglas de Desktop. Pruebas JVM e instrumentadas de persistencia.
4. Interfaz táctil: dibujar, mover vértices, deshacer, propiedades, eliminar con
   confirmación.
5. Varios ortomosaicos y sus límites; exportador de Desktop (rama aparte).
6. 3D.
7. Exportar `.glx`; escenarios A–E de la misión e informe.
