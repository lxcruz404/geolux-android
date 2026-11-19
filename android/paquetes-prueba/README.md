# Paquetes para probar «Reemplazar» (SPEC §8)

Son `contracts/geolux-v1/fixtures/finca-demo.geolux` con otro `package_id` y otra fecha
en `manifest.json` (el manifiesto no lleva hash, así que siguen siendo válidos). Misma
finca, otro paquete: al importarlos después de `finca-demo` la app pregunta si reemplazar.

| Archivo | `created_at` | Qué debe mostrar la app |
|---|---|---|
| `finca-demo-mas-antiguo.geolux` | 2026-05-15 | Reemplazar / Cancelar, con la etiqueta «Más antiguo» |
| `finca-demo-mas-nuevo.geolux` | 2026-10-20 | Reemplazar / Cancelar, sin etiqueta |

```bash
adb push contracts/geolux-v1/fixtures/finca-demo.geolux android/paquetes-prueba/*.geolux /sdcard/Download/
```

Importa primero `finca-demo.geolux` y después uno de estos.
