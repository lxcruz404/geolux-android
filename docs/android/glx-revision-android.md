# Revisión Android del contrato `.glx` (propuesta del 6 nov. 2026)

8 nov. 2026 · Andrés · rama `android/andres/beta-glx` (`0015e76`, sobre `main` 4d930b8,
que incluye c04b710).

Revisa `docs/proposals/glx-v1-contract-update.md` punto por punto desde el lector
Android. **No cambia `contracts/geolux-v1/SPEC.md`**: eso espera la aprobación
conjunta (§8 de la propuesta). Todo lo de esta rama funciona con o sin ella.

## 1. Postura de Android sobre las decisiones (§8 de la propuesta)

| # | Decisión | ¿Compatible con el lector actual? | ¿Cambia Android? | Recomendación de Andrés |
|---|---|---|---|---|
| 1 | `.glx` oficial, `.geolux` heredada | Sí: el lector decide por `manifest.json`, no por la extensión; el selector muestra `*/*` | Solo textos (hecho: «Importar paquete GeoLux (.glx)») | Aprobar |
| 2 | Fotos (`photos/index.json`, miniatura y vista; originales opcionales) | Sí: se verifican por hash y se ignoran | Para mostrarlas, sí (visor nuevo) | Aprobar, con `photos/index.json` en `required: false` como dice la propuesta |
| 3 | Informes PDF como `kind: report` con `bounds` | Sí | Hecho: visor PDF (rama `android/andres/visor-pdf`) | Aprobar |
| 4 | Tablas de análisis (`tables/`) | Sí: se verifican y se ignoran | Para mostrarlas, sí (pantalla nueva, pequeña) | Aprobar |
| 5 | Modelo móvil por `role` | Sí | Hecho: `model_mobile` → `model` → `model_compat` → nombre → `files[0]` | Aprobar |
| 6 | DSM, DTM y LAZ: se guardan pero no se muestran | Sí | No para guardarlos. Ver §3: hoy se extraen y ocupan ~490 MB sin uso | Aprobar, y decidir si Android los extrae (§3) |
| 7 | Comprobación de espacio en Android | — | Hecho (§2) | Aprobar la fórmula con la corrección del §2 |
| 8 | Seguir en `format_version: 1` | Sí: todo es aditivo | No | Aprobar |
| 9 | Copiar el fixture a `contracts/geolux-v1/fixtures/` | — | Las pruebas Android hoy lo leen de `tests/core/fixtures/glx-v1/` | Aprobar; al copiarlo, Android cambia una ruta en `core/` y `data/build.gradle.kts` |

## 2. Lo que Android ya hace con los `.glx` (rama `android/andres/beta-glx`)

- **Fixture de Desktop:** `GlxFixtureTest` lee `finca-demo-v1.glx` con `GeoluxReader`
  y lo compara con su `expected.json`: 0 errores, exactamente 5 avisos
  `synthetic_product` (esperados), los 5 productos con su `kind`, y para cada una
  de las entradas del manifiesto su `kind`, `role` y `compression` (y que el método
  del ZIP coincida con `compression`). Comprueba también que existen las rutas de
  `files` de cada foto y las tablas. Sin diferencias con `expected.json`.
- **Modelo 3D:** `Product.fileRoles` viene de `manifest.contents[].role`;
  `ProjectStore.productFile` elige `model_mobile`, si no `model`, si no
  `model_compat`; sin roles, un archivo `model_mobile.glb`; si no, `files[0]`.
  Además el visor lee el GLB directo a memoria nativa: el `OutOfMemoryError` del
  modelo de 151 MB venía de copiarlo antes a un `ByteArray` del heap (192 MB). Si
  aun así no carga, la pantalla lo dice en vez de cerrarse.
- **Espacio:** antes de copiar nada, `GeoluxReader.peekManifest` lee solo
  `manifest.json` (primera entrada) y calcula
  `copia en caché + copia instalada + productos extraídos` con la holgura de la
  propuesta (5 %, mínimo 100 MiB). Un paquete ya importado solo necesita su copia.
  Si el manifiesto no va primero, se mide después de validar la copia. Si no
  alcanza, la pantalla 02e dice cuánto hace falta y cuánto hay.
  **Corrección a la propuesta §3:** la fórmula `2 × totals.bytes + Σ products/`
  es correcta, pero conviene escribir que el margen se aplica al total y que, con
  la finca ya importada, se cuenta como ocupada hasta el final (la instalación es
  atómica: la anterior se borra solo cuando la nueva está completa).
- **Instalación atómica** (sin cambios desde AND-A5): la finca nueva se prepara en
  `.staging-*`; si falla, la anterior sigue intacta y no queda nada a medias; al
  arrancar, `recover()` limpia lo que dejó una instalación interrumpida.

## 3. Qué valida, guarda y muestra Android

Estado de la rama `android/andres/beta-glx`. «Guarda» = está en el teléfono tras importar.

| Recurso | Valida (hash y tamaño) | Guarda | Muestra |
|---|---|---|---|
| Proyecto, área, potreros, elementos, líneas | Sí | Sí (paquete copiado) | Sí |
| Misiones y su ruta (`layers/`) | Sí | Sí | Sí (mapa y ficha) |
| Ortomosaico (primer `orthomosaic`) | Sí | Se extrae y se convierte en teselas | Sí |
| Modelo 3D | Sí | Se extraen **los dos** GLB | Sí, `model_mobile` por `role` |
| Informes PDF (`report`) | Sí | Se extraen | Sí, en Mi finca → Informes (rama `visor-pdf`) |
| DSM, DTM (`dsm`, `dtm`) | Sí | Se extraen (~331 MB en La Giralda) | No; en la vista previa salen como «Producto» |
| Nube de puntos (`point_cloud`) | Sí | Se extrae (~156 MB en La Giralda) | No; «Producto» |
| Fotos (`photos/`) | Sí | Solo dentro del paquete copiado | No (la ficha de misión muestra el número de fotos del plan) |
| Fichas de misión y procesamiento (`missions/`, `jobs/`) | Sí | Solo dentro del paquete | No |
| Tablas de análisis (`tables/`) | Sí | Solo dentro del paquete | No |

**Espacio que se puede recuperar sin tocar el contrato:** Android extrae todo lo
que hay bajo `products/`, también lo que no muestra: `model.glb` (151 MB, que no
debe abrir si hay `model_mobile`), DSM, DTM y LAZ (~487 MB). Todo sigue dentro del
paquete copiado, así que extraer solo lo que se muestra ahorraría unos 640 MB por
finca en el paquete de 1,07 GB, y la necesidad de espacio bajaría en lo mismo. Es
un cambio interno de Android; lo dejo propuesto, no hecho, porque depende de la
decisión 6.

## 4. Lo que implicaría mostrar lo que falta

| Visor | Trabajo | Riesgo |
|---|---|---|
| Fotos | Leer `photos/index.json`; extraer miniatura y vista bajo demanda del paquete guardado (como las teselas); rejilla por misión y vista ampliada; puntos en el mapa con su `lat/lon`. Sin dependencias nuevas | Medio: 367 fotos ≈ 100 MB de vistas |
| Tablas | Leer `tables/<id>.json` (`columns` + `rows`) y mostrarlas como tabla con unidades | Bajo |
| Fichas de misión y procesamiento | Leer `missions/<id>.json` y `jobs/<id>.json` y ampliar la ficha 04d | Bajo |
| DSM y DTM | Leer GeoTIFF (no hay lector en Android), reproyectar y colorear; o que Desktop exporte teselas coloreadas | Alto; mejor que Desktop prepare teselas |
| LAZ | Descompresor LAZ nativo y visor de nubes de puntos | Alto; etapa posterior |

## 5. Pendiente de Desktop o de la decisión conjunta

- El paquete de La Giralda del 5 nov. lleva `photos/index.json` con `required: true`;
  la propuesta lo pasa a `false`. Android acepta los dos.
- `requires` y `profile` no los usa Android todavía; no hace falta para la beta.
