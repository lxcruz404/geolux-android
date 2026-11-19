package me.luiscruzandresgomezrodolfohernandez.geolux.ui.map

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.data.LocationState
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Polygon

private const val TAG = "GeoLuxMapa"

/** Margen alrededor de la finca al encuadrarla, en píxeles. */
private const val FIT_PADDING_PX = 48

/** Sin nada que encuadrar ni centro de la finca: Colombia entera. */
private val COLOMBIA = LatLng(4.6, -74.1)
private const val COLOMBIA_ZOOM = 4.5

/** Capas que responden al toque, de arriba abajo: gana la primera que tenga algo. */
private val TOUCH_LAYERS = listOf("elementos", "lineas-toque", "rutas-mision", "potreros-relleno", "misiones-relleno", "area-toque")

/** Lo que el usuario tocó en el mapa. */
data class MapSelection(val kind: FeatureKind, val id: String)


/**
 * Mapa de la finca: mapa base ([baseMap], Esri o OpenStreetMap), los ortomosaicos
 * ([orthos], cada uno apagable con [hiddenOrthos]) y sus contornos, y encima
 * área de trabajo, potreros, misiones, líneas, elementos, el boceto que se está
 * dibujando ([sketch]) y la posición GPS ([location]).
 *
 * El estilo solo se rehace al cambiar el mapa base o los ortomosaicos; los
 * datos de la finca se actualizan en su sitio, así que editar no hace parpadear
 * el mapa ni mueve la cámara.
 *
 * Al tocar: si [onMapTap] devuelve true (modo de dibujo) el toque es suyo; si
 * no, se elige lo tocado ([onSelect], null = vacío). [selected] se resalta.
 */
@Composable
fun FarmMap(
    data: ProjectData,
    location: LocationState?,
    selected: MapSelection?,
    background: Color,
    elementColor: (String) -> String,
    onSelect: (MapSelection?) -> Unit,
    modifier: Modifier = Modifier,
    baseMap: BaseMap = BaseMap.None,
    orthos: List<OrthoLayer> = emptyList(),
    hiddenOrthos: Set<String> = emptySet(),
    controller: FarmMapController? = null,
    /** Se llama cuando el estilo está cargado y la cámara en su encuadre inicial. */
    onReady: () -> Unit = {},
    visibleLayers: Set<MapLayerGroup> = MapLayerGroup.entries.toSet(),
    sketch: Sketch? = null,
    onMapTap: ((Position) -> Boolean)? = null,
    /** Etiquetas de potrero y waypoints en Compose (se ocultan al dibujar, para ver bien). */
    showOverlay: Boolean = true,
    /** Contenido Compose extra sobre el mapa (manijas de vértices); recibe el mapa y un contador que cambia con la cámara. */
    editOverlay: (@Composable (MapLibreMap, Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnMapTap by rememberUpdatedState(onMapTap)
    val currentData by rememberUpdatedState(data)
    val currentElementColor by rememberUpdatedState(elementColor)
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var cameraTick by remember { mutableIntStateOf(0) }
    var cameraFitted by remember { mutableStateOf(false) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { message -> Log.e(TAG, "Fallo al cargar el mapa: $message") }
        }
    }

    // MapView necesita los eventos de la Activity (start, resume, pause…).
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller?.map = null
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    // El estilo se rehace si cambia el mapa base o los ortomosaicos; la cámara solo se encuadra la primera vez.
    LaunchedEffect(baseMap, orthos, background) {
        val styleJson = farmStyleJson(farmLayers(currentData, currentElementColor), MapStyleConfig(baseMap, orthos, background))
        mapView.getMapAsync { map ->
            loadedStyle = null
            map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                loadedStyle = style
                if (!cameraFitted) {
                    map.moveCamera(initialCamera(currentData, orthos))
                    cameraFitted = true
                }
                currentOnReady()
            }
        }
    }

    // Una sola vez: gestos y toque (si se registrara en cada estilo, se duplicaría).
    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            controller?.map = map
            map.uiSettings.isRotateGesturesEnabled = false
            // La atribución del mapa base la dibuja GeoLux, siempre visible y legible; el logo y el
            // botón (i) de MapLibre la tapaban (MapLibre, BSD, no exige mostrarlos).
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = false
            map.addOnCameraMoveListener { cameraTick++ }
            map.addOnCameraIdleListener { cameraTick++ }
            mapRef = map
            map.addOnMapClickListener { latLng ->
                val tap = currentOnMapTap
                if (tap != null && tap(Position(lon = latLng.longitude, lat = latLng.latitude))) return@addOnMapClickListener true
                currentOnSelect(map.featureAt(map.projection.toScreenLocation(latLng)))
                true
            }
        }
    }

    // Datos de la finca: se reemplazan en las fuentes, sin rehacer el estilo.
    LaunchedEffect(loadedStyle, data) {
        val style = loadedStyle ?: return@LaunchedEffect
        val layers = farmLayers(data, elementColor)
        mapOf(
            "area" to layers.workArea, "potreros" to layers.zones, "misiones" to layers.missions,
            "rutas" to layers.missionPaths, "lineas" to layers.lines, "elementos" to layers.elements,
        ).forEach { (id, json) -> style.getSourceAs<GeoJsonSource>(id)?.setGeoJson(json) }
    }

    // Capas encendidas en la hoja «Capas del mapa»; se reaplica si el estilo se rehace.
    LaunchedEffect(loadedStyle, visibleLayers, hiddenOrthos) {
        loadedStyle?.let { applyLayerVisibility(it, visibleLayers, hiddenOrthos) }
    }

    LaunchedEffect(loadedStyle, sketch) {
        loadedStyle?.getSourceAs<GeoJsonSource>("boceto")?.setGeoJson(sketchGeoJson(sketch))
    }

    // Posición GPS: se actualiza la fuente sin rehacer el estilo.
    LaunchedEffect(loadedStyle, location) {
        val style = loadedStyle ?: return@LaunchedEffect
        val fix = location as? LocationState.Fix
        style.getSourceAs<GeoJsonSource>("gps")
            ?.setGeoJson(fix?.let { gpsFeature(it.lat, it.lon, it.accuracyM) } ?: emptyCollection())
    }

    // Resaltado del objeto elegido (DESIGN.md §7: borde 3 dp y relleno 28 % en potreros; anillo en elementos).
    LaunchedEffect(loadedStyle, selected) {
        val style = loadedStyle ?: return@LaunchedEffect
        fun idOf(kind: FeatureKind) = Expression.literal(selected?.takeIf { it.kind == kind }?.id ?: "")
        style.getLayerAs<LineLayer>("potreros-seleccion")?.setFilter(Expression.eq(Expression.get("id"), idOf(FeatureKind.Zone)))
        style.getLayerAs<LineLayer>("lineas-seleccion")?.setFilter(Expression.eq(Expression.get("id"), idOf(FeatureKind.Line)))
        style.getLayerAs<CircleLayer>("elementos-seleccion")?.setFilter(Expression.eq(Expression.get("id"), idOf(FeatureKind.Element)))
        style.getLayerAs<LineLayer>("area-seleccion")?.setFilter(Expression.eq(Expression.get("id"), idOf(FeatureKind.WorkArea)))
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        val map = mapRef
        if (showOverlay && map != null && loadedStyle != null) FarmOverlay(map, data, visibleLayers, cameraTick)
        if (editOverlay != null && map != null && loadedStyle != null) editOverlay(map, cameraTick)
        // Atribución del mapa base siempre visible (condición de Esri y de OpenStreetMap).
        if (baseMap.attribution.isNotEmpty()) {
            Text(
                text = baseMap.attribution,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF1C2A25),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = 2.dp)
                    .widthIn(max = 260.dp)
                    .background(Color.White.copy(alpha = 0.75f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                    .testTag("atribucion_mapa_base"),
            )
        }
    }
}

/** Encuadre inicial: los datos de la finca, sus ortomosaicos, su centro o, sin nada, Colombia. */
private fun initialCamera(data: ProjectData, orthos: List<OrthoLayer>) =
    farmBounds(data, orthos.firstOrNull()?.bounds ?: (data.products.firstOrNull { it.isOrthomosaic && !it.isCropped } ?: data.products.firstOrNull { it.isOrthomosaic })?.bounds)
        ?.let { b ->
            if (b.width < 1e-9 || b.height < 1e-9) {
                CameraUpdateFactory.newLatLngZoom(LatLng(b.south, b.west), 17.0)
            } else {
                CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(b.north, b.east, b.south, b.west), FIT_PADDING_PX)
            }
        }
        ?: data.project.center?.let { CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), 15.0) }
        ?: CameraUpdateFactory.newLatLngZoom(COLOMBIA, COLOMBIA_ZOOM)

private fun MapLibreMap.featureAt(point: android.graphics.PointF): MapSelection? {
    for (layer in TOUCH_LAYERS) {
        val feature = queryRenderedFeatures(point, layer).firstOrNull() ?: continue
        val kind = FeatureKind.entries.firstOrNull { it.key == feature.getStringProperty("kind") } ?: continue
        return MapSelection(kind, feature.getStringProperty("id") ?: continue)
    }
    return null
}

private inline fun <reified T : Layer> Style.getLayerAs(id: String): T? = getLayer(id) as? T

/** Lo que define el estilo además de los datos: mapa base, ortomosaicos y color de fondo. */
data class MapStyleConfig(
    val baseMap: BaseMap = BaseMap.None,
    val orthos: List<OrthoLayer> = emptyList(),
    val background: Color = Color.White,
)

/** Contorno de cada ortomosaico ([w, s, e, n]): su extensión, para ver dónde termina. */
internal fun orthoBoundsGeoJson(orthos: List<OrthoLayer>): String = FeatureCollection.fromFeatures(
    orthos.filter { it.bounds.size == 4 }.map { o ->
        val (w, s, e, n) = o.bounds
        val ring = listOf(w to s, e to s, e to n, w to n, w to s).map { (lon, lat) -> org.maplibre.geojson.Point.fromLngLat(lon, lat) }
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply { addStringProperty("id", o.productId) }
    },
).toJson()

/** Estilo MapLibre completo: fondo, mapa base, ortomosaicos y las capas de DESIGN.md §7, en su orden de dibujo. */
internal fun farmStyleJson(layers: FarmLayers, config: MapStyleConfig = MapStyleConfig()): String {
    fun geojson(json: String) = JSONObject().put("type", "geojson").put("data", JSONObject(json))
    fun layer(id: String, type: String, source: String, paint: JSONObject, filter: JSONArray? = null) =
        JSONObject().put("id", id).put("type", type).put("source", source).put("paint", paint)
            .also { if (filter != null) it.put("filter", filter) }
    fun arr(vararg values: Any) = JSONArray(values.toList())
    val get = { key: String -> arr("get", key) }

    val sources = JSONObject()
        .put("area", geojson(layers.workArea))
        .put("potreros", geojson(layers.zones))
        .put("misiones", geojson(layers.missions))
        .put("rutas", geojson(layers.missionPaths))
        .put("lineas", geojson(layers.lines))
        .put("elementos", geojson(layers.elements))
        .put("ortomosaicos-limites", geojson(orthoBoundsGeoJson(config.orthos)))
        .put("boceto", geojson(emptyCollection()))
        .put("gps", geojson(emptyCollection()))
    config.baseMap.tiles?.let { url ->
        sources.put(
            "base",
            JSONObject().put("type", "raster").put("tiles", arr(url)).put("tileSize", 256)
                .put("maxzoom", config.baseMap.maxZoom).put("attribution", config.baseMap.attribution),
        )
    }
    config.orthos.forEach {
        sources.put(
            "ortho-${it.productId}",
            JSONObject().put("type", "raster").put("tiles", arr(it.tiles.urlTemplate))
                .put("minzoom", it.tiles.minZoom).put("maxzoom", it.tiles.maxZoom).put("tileSize", 256)
                .also { s -> if (it.bounds.size == 4) s.put("bounds", JSONArray(it.bounds)) },
        )
    }

    val bg = String.format("#%06X", config.background.toArgb() and 0xFFFFFF)
    val out = JSONArray()
    out.put(JSONObject().put("id", "fondo").put("type", "background").put("paint", JSONObject().put("background-color", bg)))
    if (config.baseMap.tiles != null) out.put(JSONObject().put("id", "mapa-base").put("type", "raster").put("source", "base"))
    config.orthos.forEach { out.put(JSONObject().put("id", ORTHO_LAYER_PREFIX + it.productId).put("type", "raster").put("source", "ortho-${it.productId}")) }
    // Contorno de los ortomosaicos: blanco fino discontinuo, legible sobre el satélite.
    out.put(layer("ortomosaico-limites", "line", "ortomosaicos-limites", JSONObject().put("line-color", "#FFFFFF").put("line-width", 1.5).put("line-opacity", 0.9).put("line-dasharray", arr(3, 2))))
    // Área de trabajo: naranja discontinua, 1.5 dp, relleno 3 %.
    out.put(layer("area-relleno", "fill", "area", JSONObject().put("fill-color", "#C9762B").put("fill-opacity", 0.03)))
    out.put(layer("area-borde", "line", "area", JSONObject().put("line-color", "#C9762B").put("line-width", 1.5).put("line-opacity", 0.95).put("line-dasharray", arr(8 / 1.5, 5 / 1.5))))
    out.put(layer("area-seleccion", "line", "area", JSONObject().put("line-color", "#C9762B").put("line-width", 4), arr("==", get("id"), "")))
    // Potreros: color propio, borde 2 dp, relleno 15 %; el elegido, borde 3 dp.
    out.put(layer("potreros-relleno", "fill", "potreros", JSONObject().put("fill-color", get("color")).put("fill-opacity", 0.15)))
    out.put(layer("potreros-borde", "line", "potreros", JSONObject().put("line-color", get("color")).put("line-width", 2)))
    out.put(layer("potreros-seleccion", "line", "potreros", JSONObject().put("line-color", get("color")).put("line-width", 4), arr("==", get("id"), "")))
    // Misiones: área azul con trazo 6-4 y relleno 6 %; ruta continua de 2 dp.
    out.put(layer("misiones-relleno", "fill", "misiones", JSONObject().put("fill-color", "#4C6EF5").put("fill-opacity", 0.06)))
    out.put(layer("misiones-borde", "line", "misiones", JSONObject().put("line-color", "#4C6EF5").put("line-width", 1.5).put("line-dasharray", arr(6 / 1.5, 4 / 1.5))))
    out.put(layer("rutas-mision", "line", "rutas", JSONObject().put("line-color", "#4C6EF5").put("line-width", 2)))
    // Contorno blanco translúcido bajo cada línea: sobre el satélite, una cerca café discontinua
    // de 2 dp casi no se veía. Los colores y trazos siguen siendo los de Desktop.
    out.put(layer("lineas-contorno", "line", "lineas", JSONObject().put("line-color", "#FFFFFF").put("line-opacity", 0.7).put("line-width", arr("+", get("ancho"), 3))))
    // Líneas: una capa por categoría, porque el trazo discontinuo no puede depender de una propiedad.
    LINE_STYLES.forEach { (category, style) ->
        val paint = JSONObject().put("line-color", style.color).put("line-width", style.width)
        if (style.dash.isNotEmpty()) paint.put("line-dasharray", JSONArray(style.dashInLineWidths()))
        out.put(layer("lineas-$category", "line", "lineas", paint, arr("==", get("category"), category)))
    }
    out.put(layer("lineas-seleccion", "line", "lineas", JSONObject().put("line-color", "#2D6A4F").put("line-width", 6).put("line-opacity", 0.6), arr("==", get("id"), "")))
    // Líneas casi invisibles y anchas para que tocar una cerca fina o el borde del área sea fácil
    // (DESIGN.md §5). Opacidad 0.01 y no 0: MapLibre no devuelve en queryRenderedFeatures lo
    // que no dibuja, y con 0 tocar una línea nunca la elegía.
    out.put(layer("lineas-toque", "line", "lineas", JSONObject().put("line-color", "#000000").put("line-opacity", 0.01).put("line-width", 24)))
    out.put(layer("area-toque", "line", "area", JSONObject().put("line-color", "#000000").put("line-opacity", 0.01).put("line-width", 24)))
    // Elementos: círculo con el color del tipo y borde blanco; el elegido, anillo verde.
    out.put(layer("elementos", "circle", "elementos", JSONObject().put("circle-color", get("color")).put("circle-radius", 9).put("circle-stroke-color", "#FFFFFF").put("circle-stroke-width", 2)))
    out.put(layer("elementos-seleccion", "circle", "elementos", JSONObject().put("circle-color", "rgba(0,0,0,0)").put("circle-radius", 13).put("circle-stroke-color", "#2D6A4F").put("circle-stroke-width", 3), arr("==", get("id"), "")))
    // Boceto: lo que se está dibujando, en amarillo para que se vea sobre el satélite.
    out.put(layer("boceto-relleno", "fill", "boceto", JSONObject().put("fill-color", "#FFD43B").put("fill-opacity", 0.25), arr("==", get("parte"), "area")))
    out.put(layer("boceto-linea", "line", "boceto", JSONObject().put("line-color", "#FFD43B").put("line-width", 3), arr("!=", get("parte"), "vertice")))
    out.put(layer("boceto-vertices", "circle", "boceto", JSONObject().put("circle-color", "#FFFFFF").put("circle-radius", 7).put("circle-stroke-color", "#1C2A25").put("circle-stroke-width", 2), arr("==", get("parte"), "vertice")))
    // GPS: halo de precisión (metros → px según el zoom) y, para no confundirlo con un
    // elemento, punto azul dentro de un anillo blanco con borde oscuro. MapLibre exige que
    // "zoom" vaya en un interpolate de primer nivel: los 16 px mínimos van en cada extremo.
    val px0 = arr("get", "accuracy_px_z0")
    val accuracyRadius = arr(
        "interpolate", arr("exponential", 2), arr("zoom"),
        0, arr("+", 16, px0),
        24, arr("+", 16, arr("*", px0, Math.pow(2.0, 24.0))),
    )
    out.put(layer("gps-precision", "circle", "gps", JSONObject().put("circle-color", "#4C6EF5").put("circle-opacity", 0.18).put("circle-radius", accuracyRadius)))
    out.put(layer("gps-anillo", "circle", "gps", JSONObject().put("circle-color", "#FFFFFF").put("circle-radius", 11).put("circle-stroke-color", "#1C2A25").put("circle-stroke-width", 1.5)))
    out.put(layer("gps-punto", "circle", "gps", JSONObject().put("circle-color", "#4C6EF5").put("circle-radius", 6)))

    return JSONObject().put("version", 8).put("sources", sources).put("layers", out).toString()
}
