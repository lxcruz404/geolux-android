# GeoLux Android — DESIGN.md

> **Estado:** v0.2 · 14 de octubre de 2026. Sustituye a la propuesta v0.1 (7 oct) y al `DESIGN.md` AgroField M3 generado por Stitch.
> **Aprobación:** la paleta de Android está **pendiente de Luis** (`06_ANDROID_SPEC` §8b). Los valores **[Propuesta]** también.
> **Fuentes:** contrato `contracts/geolux-v1/SPEC.md`; manual 06, 07, 08 y 11; catálogos de Desktop (`core/element_types.py`, `core/line_categories.py`, `core/flight/models.py`, `ui/formatting.py`, `resources/map/bridge.js`).
> **Marcas:** **[Desktop]** = valor que existe en el código de Desktop. **[Desktop v0.22]** = valor del tema de Desktop anterior al rediseño café del 9 oct. **[Propuesta]** = valor nuevo para Android.
> **Pantallas de referencia:** `mockups/` (ver `mockups/REVISION.md`).

---

## 1. Producto

- App Android de **solo lectura**. Abre un archivo `.geolux` exportado desde GeoLux Desktop y permite consultar sin internet la finca: mapa, potreros, elementos, líneas, misiones, ortomosaico y modelo 3D.
- Usuario: ganadero o técnico en campo, al aire libre, muchas veces con una sola mano y sin señal.
- Pantallas: **Mis fincas**, **Importación**, **Mi finca**, **Mapa** (con fichas y capas), **Potreros**, **Detalle de potrero** y **3D** (§9).
- Fuera de alcance (06 §10): inicio de sesión, cuentas, edición o dibujo, sincronización, ganado, conexiones de agua, control del dron, captura desde el teléfono y mapas base descargados.

## 2. Principios

1. **Solo datos del contrato.** La app muestra lo que trae el `.geolux` v1, nada más. Si un dato falta: “No especificado” (07).
2. **Estados siempre claros:** cargando, vacío, error, sin permiso de GPS y éxito (07).
3. **Campo primero.** Alto contraste, objetivos táctiles de 48 dp o más, texto de contenido de 14 sp o más, acciones principales al alcance del pulgar.
4. **Mismo lenguaje que Desktop.** Nombres, etiquetas y formatos de Desktop (07: lenguaje visual compartido, no idéntico).
5. **Real frente a sintético.** Todo producto con `origin: "synthetic"` lleva la etiqueta ámbar «Sintético · datos de prueba» (SPEC §6, 11_3D).
6. **Material Design 3 nativo** (Jetpack Compose).

## 3. Color

### 3.1 Roles de Material 3

| Rol M3 | Claro | Oscuro | Origen |
|---|---|---|---|
| `primary` | `#2D6A4F` | `#3E8368` | [Desktop v0.22] `accent` |
| `onPrimary` | `#FFFFFF` | `#FFFFFF` | [Desktop v0.22] |
| `primaryContainer` | `#EAF4EF` | `#1E3B32` | [Desktop v0.22] `accent_soft_bg` |
| `onPrimaryContainer` | `#173C33` | `#9FDCC1` | [Desktop v0.22] |
| `background` | `#F3F6F5` | `#101614` | [Desktop v0.22] `bg_app` |
| `surface` | `#FFFFFF` | `#182420` | [Desktop v0.22] `bg_surface` |
| `surfaceVariant` | `#EEF4F1` | `#22302B` | Claro: [Desktop v0.22] · Oscuro: [Propuesta] |
| `onSurface` | `#1C2A25` | `#EAF2EE` | [Desktop] `text_primary` del mapa |
| `onSurfaceVariant` | `#5F7169` | `#AEC2BA` | [Desktop] |
| `outline` | `#DCE4E1` | `#2A3934` | [Desktop] `border` |
| `error` | `#B3261E` | `#F28B82` | Claro: [Desktop] `danger` · Oscuro: [Propuesta] (§11) |
| deshabilitado | `#8B9C95` | `#7C9088` | **Solo deshabilitados** (§11) |

Desktop pasó el 9 oct a café tierra y verde bosque (`docs/sistema-de-diseno.md`). Android conserva el verde de v0.22: decisión de Andrés, pendiente de Luis.

### 3.2 Chips de estado [Propuesta]

| Familia | Texto / fondo claro | Texto / fondo oscuro | Potrero | Plan de misión (`plan_status_label`) |
|---|---|---|---|---|
| Verde | `#2D6A4F` / `#EAF4EF` | `#9FDCC1` / `#1E3B32` | Activo | Válida, Exportada, Ejecutada, Completada |
| Ámbar | `#8A5300` / `#FFF4DF` | `#F5C46B` / `#3A2A0A` | En descanso | Con advertencias, Procesando, y la etiqueta «Sintético» |
| Rojo | `#B3261E` / `#FCE8E6` | `#F28B82` / `#3B1A18` | — | Inválida |
| Neutro | `#5F7169` / `#EEF4F1` | `#AEC2BA` / `#22302B` | Inactivo | Borrador |

El color nunca va solo: el chip siempre lleva el texto.

### 3.3 Colores del mapa [Desktop]

| Uso | Claro | Oscuro |
|---|---|---|
| Área de trabajo | `#C9762B` | `#E0A458` |
| Potrero sin color propio | `#2A9D8F` | `#4FB8A8` |
| Misiones | `#4C6EF5` (borde de waypoint `#364FC7`) | igual |

## 4. Tipografía

- **Familia:** Roboto, la del sistema Android (Desktop usa Segoe UI, la del sistema Windows).
- **Pesos:** 400 texto, 500 botones y pestañas, 600 títulos y cifras.
- Tamaños en **sp**, respetando el tamaño de letra del sistema.

| Estilo M3 | Tamaño | Peso | Uso |
|---|---|---|---|
| `displaySmall` | 36 sp | 600 | Cifra de un dato clave (“245.30”) |
| `headlineSmall` | 24 sp | 600 | Nombre de la finca |
| `titleLarge` | 22 sp | 600 | Barra superior |
| `titleMedium` | 16 sp | 600 | Título de tarjeta y de sección |
| `bodyLarge` | 16 sp | 400 | Texto principal |
| `bodyMedium` | 14 sp | 400 | Texto secundario (mínimo para contenido) |
| `labelLarge` | 14 sp | 500 | Botones y pestañas |
| `labelMedium` | 12 sp | 600 | Solo chips |

## 5. Forma, espaciado y elevación

- **Radios:** 8 dp chips; 12 dp tarjetas y botones; 16 dp diálogos; 28 dp borde superior de hojas inferiores.
- **Espaciado** en múltiplos de 4 dp: margen de pantalla 16, padding de tarjeta 16, entre tarjetas 12, entre secciones 24.
- **Táctil:** mínimo 48 × 48 dp. Botones flotantes del mapa 48 dp.
- **Elevación:** tarjetas planas con borde `outline`. Solo llevan sombra los botones flotantes, las hojas inferiores y los marcadores.

## 6. Iconografía

Material Symbols Rounded, 24 dp, peso 400.

| Uso | Icono |
|---|---|
| Navegación: Mi finca · Mapa · Potreros · 3D | `landscape` · `map` · `grid_view` · `view_in_ar` |
| Importar archivo | `upload_file` |
| Capas · Mi ubicación · Ver toda la finca | `layers` · `my_location` · `zoom_out_map` |
| Disponible sin conexión | `offline_pin` |
| Éxito · Advertencia · Error | `check_circle` · `warning` · `error` |

**Elementos del mapa:** se reutilizan los glifos SVG de Desktop (`iconGlyphFor` en `bridge.js`) como VectorDrawable, para que cada tipo se vea igual en las dos apps.

## 7. Estilo del mapa

Mismos números que Desktop (px de Leaflet), en dp.

| Capa | Estilo |
|---|---|
| Ortomosaico | Ráster debajo de todo. **No hay mapa base de calles ni satélite** (06 §10): sin ortomosaico, fondo neutro `surfaceVariant`. |
| Área de trabajo | Borde naranja (§3.3), 1.5 dp, opacidad 95 %, trazo 8-5, relleno 3 %, halo claro. |
| Potreros | Color del potrero (o `#2A9D8F`), borde 2 dp, relleno 15 %, etiqueta «nombre · área». Seleccionado: borde 3 dp, relleno 28 %. |
| Líneas | Color, trazo y grosor según la categoría (§7.1). Seleccionada: grosor + 2 dp. |
| Elementos | Gota de 32 dp con el color del tipo (§7.2), borde blanco 2 dp, glifo de 16 dp. Seleccionado: anillo de 3 dp `primary`. |
| Misiones | `#4C6EF5`. Área: trazo 6-4, relleno 6 %. Ruta: línea continua de 2 dp con waypoints numerados (círculo, borde `#364FC7`). |
| Posición GPS | [Propuesta] Punto azul de la librería de mapa con halo de precisión. |

**Orden de dibujo** (de abajo arriba): ortomosaico → área de trabajo → potreros → misiones → líneas → elementos → GPS.

### 7.1 Categorías de línea [Desktop]

| Categoría | Color | Trazo | Grosor |
|---|---|---|---|
| Cerca | `#6D4C41` | 4-3 | 2 |
| Lindero | `#B71C1C` | 10-4 | 3 |
| Cerca eléctrica | `#F9A825` | 2-2 | 2 |
| División | `#8D6E63` | 6-2 | 2 |
| Camino principal | `#424242` | continuo | 4 |
| Camino interno | `#757575` | continuo | 3 |
| Carretera | `#212121` | continuo | 5 |
| Canal | `#0277BD` | 2-4 | 2 |
| Río / arroyo | `#0288D1` | continuo | 3 |
| Tubería | `#546E7A` | 1-5 | 2 |
| Sendero | `#8D6E63` | 2-2 | 1 |
| Otra línea | `#455A64` | 6-3 | 2 |

### 7.2 Tipos de elemento [Desktop]

| Agua | Manejo | Infraestructura | General |
|---|---|---|---|
| Tanque `#0B7285` | Corral `#8D6E63` | Bodega `#5F3DC4` | Punto de interés `#5C6BC0` |
| Bebedero `#1C7ED6` | Establo `#6D4C41` | Camino / acceso `#F08C00` | Casa `#D9480F` |
| Pozo `#0C8599` | Manga de manejo `#7048E8` | Galpón `#5D4037` | Otro `#868E96` |
| Jagüey `#0C8599` | | Transformador `#F59F00` | |
| Reservorio `#1098AD` | | Poste `#868E96` | |
| Bomba `#5C7CFA` | | Puerta / portón `#AE3EC9` | |
| Molino de viento `#845EF7` | | Embarcadero `#1971C2` | |
| Fuente / nacimiento `#15AABF` | | Punto de acceso `#37B24D` | |

## 8. Componentes (Material 3)

| Componente | Especificación |
|---|---|
| Barra superior | «GEOLUX», nombre de la pantalla y chip «Sin conexión». Sin avatar ni nube. |
| Barra de navegación inferior | Dentro de una finca: **Mi finca · Mapa · Potreros · 3D**. Siempre fija abajo. |
| Tarjeta de finca | Miniatura del mapa, nombre (`titleMedium`), ubicación, área, potreros, misiones, «Creado el dd/mm/aaaa», botón «Abrir finca». |
| Tarjeta de dato clave | Etiqueta (`labelLarge`, `onSurfaceVariant`), cifra (`displaySmall`) y unidad. Cuadrícula de 2 columnas. |
| Fila de lista | 56–72 dp: punto de color o ícono; título y subtítulo; chip de estado al final. |
| Navegación | **navigation-compose** con rutas tipadas (`ui/navigation/Routes.kt`, `@Serializable`). Grafo principal: Mis fincas → Explorar el mapa o una finca; dentro de la finca, un segundo `NavHost` con una ruta por pestaña y los detalles (informe, detalle de potrero). La barra inferior guarda y recupera el estado de cada pestaña; tocar la pestaña activa vuelve a su raíz. Atrás: detalle → pestaña → Mi finca → Mis fincas. |
| Listados | `RecyclerList` (`ui/components/RecyclerList.kt`): RecyclerView con `ListAdapter` + DiffUtil por clave y un `ViewHolder` por fila; cada fila es un `ComposeView` con el mismo diseño Compose. Cabeceras y filtros van como filas y se desplazan con la lista. |
| Formulario | Diálogo `FormDialog`: «Guardar» valida y guarda; si hay error lo muestra dentro del diálogo y conserva lo escrito; solo se cierra al guardar. |
| Chip de estado | `labelMedium`, radio 8 dp, colores de §3.2. |
| Hoja inferior | Ficha del objeto tocado, capas e importación. Con asa de arrastre. Va **encima** de la barra inferior, nunca la contiene. |
| Botones flotantes del mapa | Capas, Mi ubicación, Ver toda la finca. |
| Interruptores | Uno por capa en la hoja «Capas del mapa». |
| Progreso | Lineal, con pasos nombrados, durante la importación. |
| Aviso | Tarjeta ámbar (advertencia) o roja (error) con ícono, texto y acción si aplica. |

## 9. Pantallas

Imágenes en `mockups/`. Lo que una imagen muestre y aquí no esté, se ignora (`mockups/REVISION.md`).

| # | Pantalla | Contenido | Tarea |
|---|---|---|---|
| 01 | Mis fincas | Botón «Importar archivo .geolux», tarjetas de finca | AND-R2 |
| 01b | Mis fincas vacío | «Aún no hay fincas en el teléfono» + importar | AND-R2 |
| 02a | Vista previa | Archivo, finca, fecha de creación, conteos por capa, productos con su origen; Importar / Cancelar | AND-R7 |
| 02b | Importando | 3 pasos con progreso; «Finca importada» + «Abrir finca» | AND-R7 |
| 02c | Ya importado | «Este paquete ya está importado» + «Abrir finca» (SPEC §8.1) | AND-R7 |
| 02d | Reemplazar | Fechas de ambos paquetes, «Más antiguo» si aplica, Cancelar / Reemplazar con confirmación (SPEC §8.2–3) | AND-R7 |
| 02e | Error | Mensajes de SPEC §7 en lenguaje claro + «Elegir otro archivo» | AND-R7 |
| 03 | Mi finca | Miniatura, área, perímetro, potreros, instalaciones por tipo, misiones | AND-R2 |
| 04a | Mapa | Todas las capas de §7, botones flotantes | AND-R3 |
| 04b | Ficha de potrero | Nombre, estado, área, perímetro, capacidad, descripción, «Ver detalle» | AND-R3 |
| 04c | Ficha de elemento | Tipo, nombre, descripción, potrero que lo contiene, coordenadas, «Centrar» | AND-R3 |
| 04d | Ficha de misión | Nombre, estado del plan, fecha y todos los datos de `missions.geojson`; «Ver ortomosaico» | AND-R3 |
| 04e | Capas | Interruptores: Ortomosaico, Área de trabajo, Potreros, Elementos, Líneas, Misiones | AND-R3 |
| 04f | GPS | «Activa la ubicación», «Buscando señal GPS…», punto con «Precisión ±N m» | AND-R5 |
| 05 | Potreros | Resumen, filtros por estado, tarjetas con área, estado y capacidad | AND-R2 |
| 06 | Detalle de potrero | Datos del potrero, descripción, elementos dentro y líneas del potrero, «Ver en el mapa» | AND-R2 |
| 07 | 3D | Visor GLB, etiqueta de origen, controles, ayuda de gestos, nombre y fecha del modelo. Sin modelo: «Esta finca no incluye modelo 3D.» | AND-R6 |

## 10. Contenido y formato

- **Idioma:** español de Colombia, tuteo, frases cortas. La UI nunca muestra términos internos (“hash”, “schema_version”, códigos de error): dice qué pasó y qué hacer.
- **Terminología:**
  - «Finca» es solo texto de UI; en código y contrato es `Project`.
  - Entidades: Finca, Área de trabajo, Potrero, Elemento, Línea, Misión, Ortomosaico, Modelo 3D.
  - **Nunca «lote»** (en Desktop es ganado): siempre «potrero».
  - Estados de potrero: Activo, En descanso, Inactivo.
  - Estado de misión: se muestra `plan_status_label` tal cual (Borrador, Válida, Con advertencias, Inválida, Exportada, Ejecutada, Procesando, Completada).
  - Tipos, categorías y estados: siempre la etiqueta `*_label` del contrato, sin traducir.
- **Formatos [Desktop]** (`ui/formatting.py`):
  - Fecha `dd/mm/aaaa` en hora local.
  - Área `245.30 ha`, 2 decimales; menos de 0.01 ha, en m².
  - Longitud `850 m`, o `2.30 km` desde 1 km.
  - Porcentaje `85 %`, con espacio.
- **Decimal con punto**, como Desktop. Cambiarlo a coma sería en las dos apps a la vez.
- **Valores ausentes [Desktop]:** “No especificado”, “Sin definir” (capacidad).

## 11. Accesibilidad

- **Contraste (WCAG):** `onSurface` sobre `surface` 14.9:1, `onSurfaceVariant` 5.2:1, `primary` con texto blanco 6.4:1. Los chips de §3.2 superan 4.5:1.
- **`#8B9C95` da 2.9:1 sobre blanco:** solo para deshabilitados. «No especificado» usa `onSurfaceVariant`.
- **Rojo en modo oscuro:** `#E5484D` da 4.1:1 sobre la superficie oscura; para texto se usa `#F28B82` (6.7:1).
- **No depender solo del color:** líneas por trazo, elementos por glifo, estados por texto.
- Botones de solo ícono con `contentDescription`.
- Con la letra del sistema al 200 % no se corta ningún texto.
- **Modo oscuro:** sigue al sistema y usa la columna «Oscuro» de §3.

## 12. Pendiente

- **Luis:** aprobar la paleta (§3.1) y los valores [Propuesta]; decidir punto o coma decimal; decidir si el modo oscuro entra en la Beta.
- Librerías ya decididas (06 §8): MapLibre Native para el mapa, SceneView para el 3D.

## 13. Uso con Stitch

Carga este archivo como sistema de diseño. Pide las pantallas de §9 con datos de ejemplo fijos (ver el prompt en `mockups/REVISION.md`) y recuerda las prohibiciones de §1 y §10. No cargues otro `DESIGN.md` a la vez: Stitch mezcla los dos.
