package me.luiscruzandresgomezrodolfohernandez.geolux.ui.map

import android.content.Context
import me.luiscruzandresgomezrodolfohernandez.geolux.data.TileSource

/**
 * Mapas base, con las **mismas URLs y atribuciones que GeoLux Desktop**
 * (`core/base_layers.py`): OpenStreetMap como mapa estándar y Esri World
 * Imagery como satélite. Los dos son Web Mercator (EPSG:3857), igual que las
 * teselas del ortomosaico, así que se superponen sin reproyectar nada.
 *
 * Condiciones de uso (ver docs/android/ampliacion-beta-edicion-mapas.md §3.3):
 * - Esri: uso no comercial o de evaluación, con atribución; Desktop no permite
 *   descargarlo para uso sin conexión (`offline_allowed=False`).
 * - OSM: atribución obligatoria y nada de descargas masivas (política de teselas).
 * Por eso aquí solo se usa la caché HTTP normal de MapLibre (lo ya visto, con
 * las cabeceras del servidor) y nunca se descarga una zona en bloque.
 */
enum class BaseMap(
    val key: String,
    /** Plantilla XYZ; MapLibre acepta {z}, {x} e {y} en cualquier orden (Esri usa {z}/{y}/{x}). */
    val tiles: String?,
    val maxZoom: Int,
    /** Texto de atribución que se ve siempre sobre el mapa. */
    val attribution: String,
) {
    Satellite(
        key = "satellite_esri",
        tiles = "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}?blankTile=false",
        maxZoom = 19,
        attribution = "Imágenes: Esri, Vantor, Earthstar Geographics, and the GIS User Community",
    ),
    Standard(
        key = "standard",
        tiles = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        maxZoom = 19,
        attribution = "© OpenStreetMap contributors",
    ),

    /** Sin mapa base: solo los datos de la finca sobre un fondo liso (como la Beta de lectura). */
    None(key = "none", tiles = null, maxZoom = 0, attribution = ""),
    ;

    companion object {
        val DEFAULT = Satellite

        fun fromKey(key: String?): BaseMap = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Un ortomosaico listo para el mapa: sus teselas locales y su extensión [w, s, e, n] en WGS84. */
data class OrthoLayer(val productId: String, val tiles: TileSource, val bounds: List<Double>)

/** El mapa base elegido se recuerda entre sesiones (preferencia del usuario, no dato de la finca). */
object BaseMapPreference {
    private const val PREFS = "geolux_mapa"
    private const val KEY = "mapa_base"

    fun load(context: Context): BaseMap =
        BaseMap.fromKey(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun save(context: Context, baseMap: BaseMap) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, baseMap.key).apply()
    }
}
