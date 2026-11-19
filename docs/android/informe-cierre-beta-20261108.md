# Informe de cierre de GeoLux Android beta: integración con Desktop

8 nov. 2026 · Redacta Andrés. Rodolfo no ha revisado este informe todavía: su
sección (C) solo recoge lo que consta en git, sin ponerle palabras.

Documentos de apoyo: `docs/android/glx-revision-android.md` (contrato y productos)
y `docs/android/edicion-sincronizacion.md` (etapa siguiente).

## Veredicto (I), primero

**No: GeoLux Android beta todavía no está terminada.** El código de importación
de los `.glx` funciona y está probado en JVM y en el emulador, pero faltan cuatro
cosas para declararla cerrada:

1. **Prueba en teléfono físico** con el paquete exacto de 1,07 GB (no tengo ni el
   teléfono ni ese archivo en este equipo).
2. **Aprobar la propuesta de contrato** (9 decisiones; recomiendo aprobarlas, ver
   la revisión) y pasarla a `SPEC.md`.
3. **Revisión de Rodolfo** de lo que toqué en su área: waypoints, visor 3D,
   pantallas de importación y Mi finca (ver C).
4. **Integrar mi trabajo:** #15–#17 ya están en `app-android-studio` (8 nov.); falta
   el PR `android/andres/integracion-beta → app-android-studio` y luego `→ main`.

Plan: §G y §H.

## A. Estado inicial (6–8 nov.)

**Terminado antes de esta etapa:** lector y validador `.geolux` v1, importación
atómica con política de reemplazo, ortomosaico como teselas, mapa con capas,
potreros, fichas y GPS, visor 3D, pantallas de importación, pruebas instrumentadas
(AND-A1…A8, R1–R8).

**Pendiente:** espacio libre, modelo móvil por `role`, fixture `.glx` de Desktop
en Android, textos `.glx`, informes PDF, fotos, tablas, prueba física.

**PR #14–#17** (consultados con `git`: el repositorio es privado, `gh` no está
instalado y el conector de GitHub pide autorización, así que **no pude ver su
estado abierto/cerrado, sus revisiones ni CI**):

| PR | Rama | Contenido | Depende de | Conflictos con `main` (4d930b8) |
|---|---|---|---|---|
| #14 | `android/andres/R8-pruebas` (= `app-android-studio`, 44d438a) | Pruebas instrumentadas de mapa y 3D (AND-R8), sobre todo AND-A1…A8 y R1–R7 | — | No |
| #15 | `android/rodolfo/R3-capas-botones` (aa7e0cc) | Hoja de capas y botones flotantes (04e) | #14 | No |
| #16 | `android/rodolfo/R3-etiquetas` (70fe0f9) | Etiquetas de potrero y waypoints numerados | #15 | No |
| #17 | `android/rodolfo/R3-ver-en-mapa` (a797142) | «Ver en el mapa» y «Ver ortomosaico» | #16 | No |

Forman una pila (#14 ⊂ #15 ⊂ #16 ⊂ #17); se integran en ese orden. Sobre #17 pasan
las 30 pruebas instrumentadas y las unitarias (5 nov.). La rama
`android/rodolfo/R3` (sin PR conocido) es una versión anterior y paralela del mapa
que **choca** con #14 (3 archivos) y duplica `FarmMap`: no debe integrarse.
Falta que Rodolfo lo confirme.

## B. Trabajo de Andrés

Ramas publicadas en GitHub (8 nov.), apiladas sobre #17 (ya en `app-android-studio`):

| Rama | Commit | Qué hace |
|---|---|---|
| `android/andres/importacion-espacio-y-modelo` | b9304fb | Espacio libre antes de importar (pantalla «No hay espacio»), `model_mobile.glb` por nombre, informes rotulados «Informe» |
| `android/andres/visor-pdf` | 9391b7d | Visor de informes PDF en Mi finca (`PdfRenderer`, pellizco), sección «Informes» |
| `android/andres/beta-glx` | 1795ddd (merge de `main`), 0015e76, documentación | `main` integrado; fixture `.glx`; modelo por `role`; espacio desde el manifiesto; GLB fuera del heap; textos `.glx` |
| `android/andres/waypoints-legibles` | 37b3e90 (sobre #17, independiente de las otras tres) | Waypoints sin solapes en el Mapa (área de Rodolfo; ver C) |
| `android/andres/visor-3d-piso` | 654449b (sobre `beta-glx`) | Barra de progreso en MB al importar; visor 3D con el terreno sobre el piso (los GLB del motor traen Z arriba, SPEC §6), giro sobre el eje vertical sin pasar por debajo y botonera con flechas (área de Rodolfo) |
| `android/andres/integracion-beta` | `visor-3d-piso` + `waypoints-legibles` | Todo junto: lo que va en el PR hacia `app-android-studio` |

**Correcciones:**
- `OutOfMemoryError` del 3D: el GLB se copiaba a un `ByteArray` en el heap (192 MB);
  ahora va directo a memoria nativa. Prueba con un archivo del 90 % del heap.
- La elección del modelo ya no depende de la posición en `files`.
- Desbordamiento en el cálculo de espacio con mucho espacio libre (lo encontraron
  las pruebas nuevas).
- Mi finca volvía arriba al cerrar un informe.

**Probado a mano en el emulador** con el paquete de La Giralda del 5 nov. a las
16:20 (663 MB, **no** el de 1,07 GB): vista previa con «INFORME», importación,
Mi finca, los dos PDF (11 y 6 páginas), 3D, mapa, cerrar y volver a abrir la app.

## C. Trabajo de Rodolfo

Según git, sus últimos commits son del 28 oct. (#15–#17). En esta etapa no hay
commits suyos. Encontrado al probar con datos reales, en su área:

- **Waypoints amontonados:** con la misión real de 363 puntos, los números se
  solapaban, tapaban el ortomosaico y una etiqueta de potrero (commit 70fe0f9, #16).
  **Corregido por Andrés** en `android/andres/waypoints-legibles` (37b3e90): se
  dibujan solo los que caben a 28 dp, con su número original, inicio y final
  primero, y nunca sobre una etiqueta. Comprobado en el emulador con La Giralda
  (finca entera: sin solapes, etiquetas legibles). No comprobado en el emulador:
  que al acercar aparezcan todos (lo cubre una prueba unitaria). El waypoint 363
  no aparece con la finca entera; probablemente queda bajo la etiqueta
  «Potrero casa», que tiene prioridad (no comprobado). Esa rama:
  86 pruebas aprobadas (30 instrumentadas, 56 unitarias). Pendiente de que
  Rodolfo la revise.
- La tarjeta de «Mis fincas» dice «Área: No especificado» mientras Mi finca
  muestra 23,65 ha (usa el área del área de trabajo cuando falta la del proyecto).
- Toqué pantallas suyas (importación, Mi finca, textos) solo lo necesario para
  estas funciones; conviene que lo revise antes de integrar.

## D. Integración Desktop–Android

- **Compatibilidad con `main`:** `main` (c04b710 + #20) se fusionó en
  `android/andres/beta-glx` sin conflictos y todas las pruebas pasan sobre el
  resultado. Ninguno de los PR #14–#17 choca con `main`. `main` no contiene
  `android/`.
- **Contrato:** todo lo que exporta Desktop es aditivo para el lector v1; no hizo
  falta cambiar `format_version`. No modifiqué `contracts/`.
- **Fixture `finca-demo-v1.glx`:** 0 errores, exactamente 5 avisos
  `synthetic_product` (esperados), productos, `kind`, `role` y `compression` de
  todas las entradas iguales a `expected.json`; hashes verificados; referencias de
  fotos y tablas resueltas. **Sin diferencias.**
- **Paquete real de 1,07 GB:** no lo tengo. Lo validó Luis en JVM y en emulador con
  44d438a (importación en 13 s). Yo probé el de 663 MB con el código nuevo:
  revisión ~15 s e instalación ~20 s en el emulador (medido desde fuera, con la
  lectura de pantalla incluida).
- **Limitaciones:** solo se muestran el primer ortomosaico y el primer modelo; DSM,
  DTM, LAZ, fotos, fichas de procesamiento y tablas no se muestran; Android extrae
  ~640 MB de productos que no usa (ver la revisión, §3).

## E. Prueba física

**No realizada.** No hay ningún teléfono físico conectado a este equipo y el
archivo `La Giralda 2026-11-05 1500.glx` no está en él. Ninguna cifra de este
informe es de un teléfono.

## F. Pruebas (8 nov., código final de `android/andres/integracion-beta`)

| Conjunto | Total | Aprobadas | Fallidas | Omitidas |
|---|---|---|---|---|
| Unitarias JVM `:core` | 13 | 13 | 0 | 0 |
| Unitarias JVM `:data` | 18 | 18 | 0 | 0 |
| Unitarias JVM `:app` | 50 | 50 | 0 | 0 |
| Instrumentadas `:data` (emulador API 37) | 8 | 8 | 0 | 0 |
| Instrumentadas `:app` (emulador API 37) | 46 | 46 | 0 | 0 |
| **Total** | **135** | **135** | **0** | **0** |

Cubren: paquete válido, no-ZIP, hash y tamaño incorrectos, manifiesto ausente o
inválido, archivo obligatorio ausente, rutas inseguras, versión nueva, ids
duplicados, coordenadas fuera de rango, espacio insuficiente (antes y después de
copiar, límite exacto), reintento, copia pendiente cancelada, reemplazo,
reemplazo fallido con la finca anterior intacta, instalación interrumpida,
reapertura desde el almacén, modelo por `role` en cualquier orden, compatibilidad
con paquetes sin `role`, GLB mayor que el heap, informes PDF, avance en bytes de la
copia, la comprobación y la instalación.

**No cubiertas:** cancelar *durante* la instalación (la interfaz no lo permite a
propósito: es atómica), un paquete grande dentro de una prueba automática, y que
Filament cargue de verdad un GLB de 151 MB.

**CI:** no existe CI para Android en el repositorio (`ci.yml` excluye `android/**`;
`glx-contrato.yml` solo corre Python). Todas las cifras son de ejecuciones locales.

## G. Pendientes

**Para declarar la beta terminada:**
1. Prueba física con el paquete de 1,07 GB (procedimiento de la misión, §6).
2. Aprobar la propuesta de contrato y pasarla a `SPEC.md`; copiar el fixture a
   `contracts/` (cambio de una ruta en Android).
3. Revisión de Rodolfo de los waypoints y del visor 3D (en `integracion-beta`).
4. Fusionar el PR `integracion-beta → app-android-studio` y después
   `app-android-studio → main`. #15–#17 ya se integraron (fast-forward, 8 nov.).
5. Revisión de Rodolfo de los cambios en sus pantallas.

**Recomendado para la beta, pequeño:** no extraer `model.glb`, DSM, DTM ni LAZ
(ahorra ~640 MB por finca y baja la necesidad de espacio en lo mismo).

**Hecho el 8 nov.:** barra de progreso con megabytes al revisar e importar («350.6 MB
de 632.0 MB»), probada en el emulador con el paquete de 663 MB.

**Versiones futuras:** visor de fotos, tablas y fichas de procesamiento (medio);
DSM/DTM (mejor con teselas preparadas por Desktop) y LAZ (alto); edición y
sincronización (`edicion-sincronizacion.md`).

**Decisiones de Luis:** las 9 de la propuesta; si fotos y tablas entran en la beta;
si Android debe dejar de extraer DSM, DTM y LAZ.

## H. Necesidades del equipo

| Qué | Para qué | Por qué bloquea | Alternativa | Recomendación |
|---|---|---|---|---|
| Un teléfono Android físico (Android 9+, idealmente uno de 4 GB de RAM y otro de 3 GB, con ≥4 GB libres) | Prueba física | Sin él no se puede afirmar que funcione en un teléfono | Ninguna: el emulador no sirve como prueba física | Prestar o comprar uno de gama media |
| El archivo `La Giralda 2026-11-05 1500.glx` (SHA-256 `10a25c53…dfe5c`) | Misma prueba que la de Luis | No está en este equipo | Exportarlo de nuevo cambiaría el `package_id` y el contenido | Compartirlo por un medio privado, nunca por GitHub |
| Aprobación de la propuesta de contrato | Pasarla a `SPEC.md` | `SPEC.md` no se cambia sin ella | Seguir con la propuesta como referencia | Aprobar las 9 decisiones |
| Acceso a GitHub (instalar `gh` o autorizar el conector) | Ver estado, revisiones y CI de los PR | Sin él no puedo abrir PR ni ver su estado | Hacerlo desde el navegador | Autorizar |
| Coordinación con Rodolfo | Waypoints, revisión de sus pantallas, descartar `R3` | Son cambios en su código | — | Una reunión corta antes de integrar |
