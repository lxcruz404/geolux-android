# Informe: ampliación funcional de GeoLux Android Beta

10 nov. 2026 · Andrés. Rama `android/andres/edicion-mapas` (sobre
`android/andres/integracion-beta`); cambio de Desktop en la rama
`escritorio/glx-ortomosaico-recortado` (sobre `main`). Diseño y decisiones
previas: `docs/android/ampliacion-beta-edicion-mapas.md`.

## 1. Resumen

| Requisito | Estado | Qué se hizo |
|---|---|---|
| 1. Calidad del 3D | Hecho, con un límite físico | Causa medida y corregida en Android: texturas a lo máximo que admite el teléfono (antes 1024 px fijos) y render fiel |
| 2. Ortomosaico original y recortado | Hecho en Android; Desktop en rama aparte, falta aprobación | Exportador de Desktop los manda como productos separados; Android los muestra, cada uno apagable, con sus límites |
| 3. Mapas satelitales de Desktop | Hecho | Esri World Imagery y OpenStreetMap con las mismas URL y atribuciones que Desktop, con y sin finca, caché de lo visto |
| 4. Crear, editar y eliminar | Hecho para lo que exporta Desktop | Potreros, líneas (cercas, linderos, vías…), elementos y área de trabajo, con las reglas de Desktop; guardado en el teléfono y exportación `.glx` |
| 5. Alineación satélite–ortomosaico | Comprobado visualmente | Todo en Web Mercator (EPSG:3857), sin reproyectar; La Giralda coincide sobre Esri |
| «Mis fincas» sin área | Corregido | Usa el área de trabajo cuando `project.json` no trae área, igual que Mi finca |

## 2. Cambios por requisito

### 2.1 Calidad del 3D (requisito 1)

**Dónde se perdía (medido el 9 nov.):**

| Modelo | `model.glb` (motor, el que ve Desktop) | `model_mobile.glb` (lo que abría Android) |
|---|---|---|
| La Giralda | 34 texturas, 3 de 24576 × 24576 px | todas ≤ 1024 px |
| Country Colors | 33 texturas, 1 de 24576 px | ≤ 1024 px |

1. El exportador de Desktop reduce todas las texturas a 1024 px para el teléfono
   (`MOBILE_TEXTURE_SIZE`), por el OutOfMemoryError del 5 nov.
2. SceneView aplicaba tone mapping *Filmic*, bloom, SSAO y FXAA a un modelo
   *unlit*: cambian los colores y suavizan la foto. Desktop pinta la textura tal
   cual (`viewer.js`) y solo reduce lo que pasa de `MAX_TEXTURE_SIZE` del GPU.

**Corrección (Android, sin tocar Desktop):** el teléfono prepara, una vez y con
avance, una copia del **modelo del motor** con cada textura al mayor tamaño que
admite su GPU (`GL_MAX_TEXTURE_SIZE`) y cabe en una quinta parte de su RAM, con
mipmaps; la geometría se copia byte a byte. Las texturas de 24576 px se leen por
franjas (`BitmapRegionDecoder`) sin decodificarlas enteras. Render: tone mapping
lineal, sin bloom ni SSAO, MSAA 4×. La ficha dice qué tamaño se está viendo.

**Resultado:** en el emulador (4 GB de RAM), La Giralda pasa de 1024 a **2048 px**
(preparada en ~8 s). En un teléfono de 8 GB y GPU de 8192–16384 px sube sola a
4096–8192. Comparación de la misma zona de la textura mayor: copia de Desktop
1024 px (borrosa) · preparada en el teléfono 2048 px (techos y pasto legibles) ·
motor 24576 px (Desktop).

**Límite físico (honesto):** ningún GPU de teléfono dibuja una textura de 24576
px y las de La Giralda ocupan 7,8 GB sin comprimir; la igualdad exacta con
Desktop no es posible en un teléfono. Lo que sí se consigue: lo máximo que cada
teléfono puede mostrar, sin reducción innecesaria.

### 2.2 Ortomosaico original y recortado (requisito 2)

- **Desktop** (`escritorio/glx-ortomosaico-recortado`, 1 commit): el exportador
  incluye los recortes verificados (`ResultType.ORTHOMOSAIC_CROP`, fase F) como
  productos `orthomosaic` propios, justo después de su original, con dos campos
  opcionales nuevos en `meta.json`: `variant` (`full` | `cropped`) y
  `source_product_id`. Mismo CRS de teselas, sus propios bounds. Prueba nueva:
  `test_the_cut_orthomosaic_travels_apart_after_its_original`.
- **Contrato:** cambio aditivo (SPEC §3, «Evolución sin romper»); un lector v1 lo
  ignora y el primer producto sigue siendo el completo. **No se tocó `SPEC.md`:
  falta la aprobación de Luis.**
- **Android:** lee los dos campos; la hoja de capas lista cada ortomosaico con
  «Original, sin recortar» o «Recortado en GeoLux Desktop», cada uno con su
  interruptor, más «Límites de los ortomosaicos» (contorno de cada uno).

### 2.3 Mapas base (requisito 3)

- Satélite Esri World Imagery y mapa estándar OpenStreetMap, **mismas URL y
  atribuciones que `core/base_layers.py` de Desktop**; se eligen en «Capas del
  mapa» y se recuerdan. Atribución siempre visible.
- Funcionan sin finca abierta («Explorar el mapa» en Mis fincas) y con fincas
  sin productos.
- **Caché:** la de MapLibre, ampliada a 300 MB: guarda lo ya visto con las
  cabeceras del servidor. **No** se descarga ninguna zona en bloque: Desktop marca
  Esri `offline_allowed=False` y la política de teselas de OSM prohíbe la descarga
  masiva. Sin Internet se ve lo ya visto, con un aviso.
- La barra superior dice el estado real de la red («Con Internet» / «Sin conexión»).

### 2.4 Crear, editar y eliminar (requisito 4)

Entidades del módulo de mapas de Desktop que viajan en el `.glx`:

| Entidad | Crear | Datos | Forma | Eliminar | Reglas de Desktop |
|---|---|---|---|---|---|
| Potrero | dibujar polígono | nombre, descripción, estado, color (paleta de Desktop), capacidad | mover/agregar/quitar vértices | sí, con confirmación | nombre obligatorio, polígono simple, sin solaparse con otro potrero |
| Línea (12 categorías: cerca, lindero, cerca eléctrica, división, caminos, carretera, canal, río, tubería, sendero, otra) | trazar | nombre, categoría, descripción | sí | sí | ≥ 2 vértices, categoría del catálogo |
| Elemento (23 tipos) | tocar el mapa | nombre, tipo, descripción | mover | sí | tipo del catálogo, coordenadas en rango |
| Área de trabajo (una por finca) | dibujar | — | sí | sí | polígono simple |

Interacción táctil: botón «Agregar al mapa»; tocar para agregar vértices;
arrastrar vértices; arrastrar el punto medio de un borde para crear uno; tocar
un vértice para quitarlo; Deshacer, Cancelar, Siguiente/Guardar; ficha de cada
objeto con Editar datos, Cambiar forma (Mover) y Eliminar; «Deshacer» en el aviso
después de guardar. Área, perímetro y longitud con **el mismo algoritmo que
Desktop** (`geo_measure.py`), con sus mismos casos de prueba.

**Persistencia:** las capas editadas se guardan en la carpeta de la finca
(`layers/`, formato SPEC §5), con guardado atómico; el paquete importado no se
modifica. **Exportar `.glx`** (Mi finca): paquete v1 con `package_id` nuevo, el
generador «GeoLux Android» y todo lo demás copiado tal cual.

**No incluido:** conexiones de agua (Desktop no las exporta: `excluded`),
misiones (planificador, fuera de alcance), «simplificar vértices» y «límites
compartidos» (operaciones de Desktop que no se portaron).

### 2.5 Alineación (requisito 5)

Desktop exporta el ortomosaico como teselas Web Mercator (`crs: EPSG:3857`,
`bounds` en WGS84); Esri y OSM también son Web Mercator; las capas vectoriales
van en WGS84 sin redondear ni reproyectar (SPEC §5) y MapLibre las proyecta. No
hay ninguna transformación propia de Android que pueda desplazar nada. Las filas
TMS del MBTiles se invierten a XYZ (prueba `xyzRowFlipsTmsRows`). Cada
ortomosaico declara su CRS de origen (`source_crs`), que ya resolvió Desktop.

## 3. Pruebas

### 3.1 Automatizadas

Ejecución final (10 nov., emulador API 37, código de `android/andres/edicion-mapas`):

| Conjunto | Total | Aprobadas | Fallidas |
|---|---|---|---|
| Unitarias JVM `:core` | 40 | 40 | 0 |
| Unitarias JVM `:data` | 22 | 22 | 0 |
| Unitarias JVM `:app` | 57 | 57 | 0 |
| Instrumentadas `:data` | 8 | 8 | 0 |
| Instrumentadas `:app` | 49 | 49 | 0 |
| **Total** | **176** | **176** | **0** |

Desktop (rama `escritorio/glx-ortomosaico-recortado`, Python 3.13): 90 aprobadas de
las pruebas del exportador y del contrato; falla `test_the_fixture_matches_what_the_current_exporter_produces`,
igual que sobre `main` sin cambios en este equipo (el MBTiles depende de la versión
de SQLite; el CI la corre en Windows). `ruff`: sin avisos.

Nuevas: geometría (mismos casos que Desktop), edición con las reglas de Desktop,
ida y vuelta de las capas, persistencia (reabrir, guardado interrumpido,
reemplazo, capa ilegible), sesión de dibujo y acciones, mapa base y ortomosaicos
en el estilo, hoja de capas, variante de ortomosaico, texturas del GLB
(tamaños, elección y reescritura), exportación `.glx` (lector de Android y
fixture `.glx`). Desktop: exportación del recortado.

### 3.2 Manuales (emulador Pixel 10 Pro XL, API 37, 4 GB; no es un teléfono físico)

| Escenario | Resultado |
|---|---|
| A. Sin productos | Paquete de Desktop sin productos (área de trabajo y 2 potreros): mapa estándar y satélite Esri; potrero, cerca, vía (camino principal) y tanque creados; descripción del tanque editada; forma del potrero cambiada; cerca eliminada con confirmación; tras cerrar y reabrir: 3 potreros, 1 línea, 1 elemento, como debía |
| B. Ortomosaicos | Original y recortado (paquete de Desktop) como capas separadas, cada una apagable, con límites. La Giralda: ortomosaico sobre Esri sin desplazamiento visible (invernaderos y caminos continuos en el borde) |
| C. 3D | La Giralda: 2048 px en vez de 1024, ~8 s la primera vez; rotar, mover, acercar. Comparación de texturas en §2.1 |
| D. Sin Internet | Modo avión: finca, ortomosaico, capas y satélite de lo ya visto; elemento creado y guardado; persiste al cerrar y reabrir |
| E. Compatibilidad | La Giralda exportada desde Android (663 MB, ~14 s): el lector de referencia de Desktop, `android_v1_problem` y `check_container` la aceptan; 750 de 750 entradas no editadas idénticas; `package_id` nuevo y mismo proyecto. **Desktop no tiene importador** de paquetes (solo `read_package` e `import_decision`) |

**Encontrado al probar:** tocar una línea en el mapa nunca la elegía (error anterior a esta ampliación: la capa táctil tenía opacidad 0 y MapLibre no la consulta). Corregido, con prueba. Las cercas casi no se veían sobre el satélite: ahora llevan un contorno blanco translúcido, sin cambiar los colores de Desktop.

Persistencia probada también con La Giralda: crear un potrero, cerrar la app a la
fuerza, reabrir (4 potreros, el nuevo en la miniatura); cambiar su forma;
deshacer un arrastre; eliminar; crear un elemento y deshacerlo.

## 4. Lo que depende de Desktop o de otras personas

1. **Aprobar** `variant`/`source_product_id` (Luis) e integrar
   `escritorio/glx-ortomosaico-recortado`.
2. **Importador de paquetes en Desktop** (escenario E completo): hoy Desktop no
   puede abrir en su base de datos un `.glx`, venga de Android o de Desktop.
3. **Conexiones de agua**: si deben editarse en Android, el exportador tiene que
   incluirlas (hoy `excluded`) y ampliar el contrato.
4. **Uso comercial de Esri**: Desktop lo marca no comercial; lo decide Luis.
5. **Prueba en teléfono físico** de todo lo anterior (no hay teléfono aquí).
6. Revisión de Rodolfo: mapa, edición y visor 3D son de su área.
