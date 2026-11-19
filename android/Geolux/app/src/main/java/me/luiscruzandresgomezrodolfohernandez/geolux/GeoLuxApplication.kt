package me.luiscruzandresgomezrodolfohernandez.geolux

import android.app.Application
import android.util.Log
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineManager
import me.luiscruzandresgomezrodolfohernandez.geolux.data.AndroidLocationSource
import me.luiscruzandresgomezrodolfohernandez.geolux.data.GeoluxProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.data.LocationSource
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ProjectRepository

/**
 * Inyección de dependencias manual (06 §8, sin Hilt): la app crea aquí una sola
 * vez lo que comparten las pantallas. Desde AND-A8 el repositorio es el real:
 * lee los `.geolux` importados en `filesDir/projects/`. FakeProjectRepository
 * queda solo para previews y pruebas.
 */
class GeoLuxApplication : Application() {
    val projectRepository: ProjectRepository by lazy { GeoluxProjectRepository(this) }

    /** Posición real del teléfono (AND-A7). FakeLocationSource queda para pruebas y previews. */
    val locationSource: LocationSource by lazy { AndroidLocationSource(this) }

    override fun onCreate() {
        super.onCreate()
        // Caché de teselas del mapa base: lo ya visto (con las cabeceras del servidor) sirve
        // sin Internet. 300 MB en vez de los 50 por defecto; nunca se descarga una zona en
        // bloque (condiciones de Esri y de OpenStreetMap, ver BaseMaps.kt).
        MapLibre.getInstance(this)
        OfflineManager.getInstance(this).setMaximumAmbientCacheSize(
            AMBIENT_CACHE_BYTES,
            object : OfflineManager.FileSourceCallback {
                override fun onSuccess() = Unit
                override fun onError(message: String) { Log.w("GeoLuxMapa", "Caché de teselas: $message") }
            },
        )
    }

    private companion object {
        const val AMBIENT_CACHE_BYTES = 300L * 1024 * 1024
    }
}
