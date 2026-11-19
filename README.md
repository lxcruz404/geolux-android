# GeoLux Android

Aplicación Android de GeoLux para la gestión geoespacial y agropecuaria de
fincas: abre los paquetes `.glx` / `.geolux` de una finca y permite ver el
mapa (potreros, cercas, infraestructura, agua, ortomosaico), el ganado y la
gestión de la finca en el celular.

## Estructura

| Carpeta | Contenido |
| --- | --- |
| `android/Geolux/` | Proyecto de Android Studio (Kotlin, Jetpack Compose). Módulos `:app`, `:core` (lógica pura en Kotlin) y `:data` (Room). |
| `android/paquetes-prueba/` | Paquetes `.geolux` de prueba para abrir en la app. |
| `contracts/` | Especificación de los formatos que lee la app (`geolux-v1`, `geolux-management-v1`) y sus fixtures. |
| `tests/core/fixtures/glx-v1/` | Paquetes `.glx` sintéticos que usan las pruebas de Android. |
| `docs/` | Documentación de la app Android y de su integración. |

Las pruebas leen `contracts/` y `tests/core/fixtures/glx-v1/` con rutas
relativas desde `android/Geolux`, así que conviene mantener esta estructura.

## Requisitos

- Android Studio reciente con JDK 21
- Android SDK (minSdk 28)

## Compilar y probar

Abrir `android/Geolux` en Android Studio, o desde la terminal:

```bash
cd android/Geolux
./gradlew :core:build :data:testDebugUnitTest :app:assembleDebug
```

El APK de depuración queda en `android/Geolux/app/build/outputs/apk/debug/`.

## Integración continua

`.github/workflows/android.yml` corre las pruebas y genera el APK de
depuración en cada push a `main` y en cada pull request.

## Documentación

- [`docs/android/README.md`](docs/android/README.md)
- [`docs/android/DESIGN.md`](docs/android/DESIGN.md)
- [`docs/android-integracion.md`](docs/android-integracion.md)
- Contrato de paquetes: [`contracts/geolux-v1/SPEC.md`](contracts/geolux-v1/SPEC.md)
