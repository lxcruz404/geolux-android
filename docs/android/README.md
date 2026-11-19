# Material de Android (entrada de Andrés y Rodolfo)

Carpeta preparada el 13 de octubre de 2026, al consolidar todas las ramas en
`main`. Aquí va el material de Android que Andrés entregará después de la
integración. Desde el 14 de octubre ya están `DESIGN.md` y `mockups/`; el
código irá en `android/`.

## Dónde colocar cada cosa

| Material | Ruta | Formato |
|---|---|---|
| Sistema de diseño y pantallas | `docs/android/DESIGN.md` | Markdown |
| Mockups entregados por Andrés | `docs/android/mockups/` | PNG o PDF exportados, uno por pantalla, con el nombre de la pantalla (`01-mis-fincas.png`…); qué ignorar en `mockups/REVISION.md` |
| Enlace al archivo de diseño, si lo hay | `docs/android/mockups/ENLACES.md` | Una línea por archivo, con fecha y versión |
| Código de la app | `android/` en la raíz del repositorio, rama `app-android-studio` | Proyecto de Android Studio en Kotlin; responsables por carpeta y tareas en `06_ANDROID_SPEC` §3–5 |

Exporta los mockups en un tamaño razonable (menos de 1 MB por imagen), igual
que las demás imágenes del repositorio. No subas fotos ni productos reales
pesados (`12_GIT_WORKFLOW`).

## Lo que ya existe y no debe romperse

- **Contrato `.geolux` v1:** `contracts/geolux-v1/SPEC.md`. Es la única vía de
  datos entre Desktop y Android en la Beta. Si la guía nueva pide más datos
  (por ejemplo, ganado o conexiones de agua), tiene que ser una versión 1.1
  con campos `required: false`, aprobada antes. No se puede cambiar la v1.
- **Fixtures compartidos:** `contracts/geolux-v1/fixtures/finca-demo.geolux`,
  `expected.json` y `fixtures/invalid/`. Las pruebas de Desktop
  (`tests/core/test_b1_geolux_contract.py`) comprueban que siguen al día, y el
  lector de Kotlin debe pasar los mismos casos.
- **Especificación del manual:** `docs/GEOLUX_MANUAL_OFICIAL/GEOLUX_MANUAL_OFICIAL_NUEVO/06_ANDROID_SPEC.md`
  (alcance de la Beta, responsabilidades y pruebas) y `02_DATA_CONTRACT.md`.
- **Estado de la integración:** `docs/android-integracion.md`.
- **Maqueta previa:** el 7 de octubre se hizo una maqueta en Figma que no está
  en el repositorio. Si los mockups de Andrés la sustituyen, conviene decirlo
  en `mockups/ENLACES.md` para que nadie trabaje sobre la versión antigua.

## Cuando llegue el material

1. Copia los archivos en las rutas de arriba, en una rama nueva creada desde
   `main`.
2. Si la guía contradice el contrato v1 o el manual, apunta la diferencia en
   `docs/android-integracion.md` y decide antes de programar.
3. Pasa la suite de Desktop (`python -m pytest`) y `ruff check .` antes del PR.
