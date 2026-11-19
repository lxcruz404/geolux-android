# Estado de la integración con GeoLux Android

Actualizado el 14 de octubre de 2026 (reparto Android aprobado por Luis).

## Situación

- **Todavía no hay código Android en el repositorio.** El trabajo se hace en la
  rama `app-android-studio` (carpeta `android/`), que ya tiene todo `main`.
- Cada carpeta y cada tarea tiene un solo responsable, Andrés o Rodolfo:
  `docs/GEOLUX_MANUAL_OFICIAL/GEOLUX_MANUAL_OFICIAL_NUEVO/06_ANDROID_SPEC.md`
  §3–5.
- Claude ayuda a los dos, con una sesión por carril y sin tocar la carpeta del
  otro (Luis, 14 oct 2026). La restricción anterior queda sustituida.

## Lo que Desktop ya entrega

| Pieza | Dónde | Estado |
|---|---|---|
| Especificación del paquete `.geolux` v1 | `contracts/geolux-v1/SPEC.md` | Escrita |
| Exportación desde un proyecto | Detalle del proyecto → «Exportar para Android» | Implementada y probada |
| Lector y validador de referencia | `src/geolux/core/exchange/package.py` | Implementado y probado |
| Fixture compartido | `contracts/geolux-v1/fixtures/finca-demo.geolux` + `expected.json` | Generado por `tools/build_geolux_fixture.py`; una prueba comprueba que no queda desactualizado |
| Paquetes inválidos | `contracts/geolux-v1/fixtures/invalid/<código>.geolux` | 9 casos, uno por código de error |
| Política de importación | SPEC §8 y `import_decision` | Implementada en Python como referencia |

## Lo que necesita Android

Las tareas, sus responsables y sus pruebas están en `06_ANDROID_SPEC` §5:
de AND-A1 a AND-A8 para Andrés (lector, importación, almacenamiento, GPS) y de
AND-R1 a AND-R8 para Rodolfo (interfaz, mapa, 3D). El ortomosaico y el GLB del
fixture son **sintéticos** y deben verse marcados como tales.

## Material pendiente de Andrés

Después de consolidar las ramas en `main` (13 de octubre de 2026), Andrés
entregará los mockups y la guía de Android actualizada. En
`docs/android/README.md` está la ruta que corresponde a cada archivo.

## Pendiente fuera de Android

- Desktop todavía no genera ortomosaicos ni modelos 3D reales (fases A4–A5),
  así que los paquetes reales no llevan productos hasta entonces.
- Las conexiones de agua y los lotes de ganado no están en v1 (lotes, por la
  decisión de no ampliar la Beta Android con datos ganaderos). Añadirlos sería
  una versión 1.1 con `required: false`, previa aprobación.
