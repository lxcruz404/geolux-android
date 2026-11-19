package me.luiscruzandresgomezrodolfohernandez.geolux.ui.map

import kotlin.math.PI
import kotlin.math.cos
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Ring
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.geo.Bounds
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.geo.boundsOf
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

// Capas de la finca para MapLibre (AND-R3, DESIGN.md §7). Cada feature lleva
// `kind` e `id` para saber qué se tocó, y lo que el estilo necesita (color,
// categoría). Sin textos sobre el mapa: MapLibre necesita fuentes (glyphs) y la
// app no las trae; los nombres se ven en la ficha al tocar.

/** Lo que el usuario puede tocar en el mapa. */
enum class FeatureKind(val key: String) { Zone("zone"), Element("element"), Line("line"), Mission("mission"), WorkArea("work_area") }

/** Color por defecto de un potrero sin color propio (DESIGN.md §3.3). */
const val ZONE_FALLBACK_COLOR = "#2A9D8F"

internal val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

/** Estilo de una categoría de línea (DESIGN.md §7.1). [dash] en px, como Desktop; vacío = continua. */
data class LineStyle(val color: String, val dash: List<Float>, val width: Float)

val LINE_STYLES: Map<String, LineStyle> = mapOf(
    "fence" to LineStyle("#6D4C41", listOf(4f, 3f), 2f),
    "boundary" to LineStyle("#B71C1C", listOf(10f, 4f), 3f),
    "electric_fence" to LineStyle("#F9A825", listOf(2f, 2f), 2f),
    "division" to LineStyle("#8D6E63", listOf(6f, 2f), 2f),
    "main_road" to LineStyle("#424242", emptyList(), 4f),
    "internal_road" to LineStyle("#757575", emptyList(), 3f),
    "highway" to LineStyle("#212121", emptyList(), 5f),
    "canal" to LineStyle("#0277BD", listOf(2f, 4f), 2f),
    "river" to LineStyle("#0288D1", emptyList(), 3f),
    "pipeline" to LineStyle("#546E7A", listOf(1f, 5f), 2f),
    "path" to LineStyle("#8D6E63", listOf(2f, 2f), 1f),
    "other" to LineStyle("#455A64", listOf(6f, 3f), 2f),
)

/** Una categoría desconocida se dibuja como «Otra línea», igual que Desktop. */
fun lineStyle(category: String): LineStyle = LINE_STYLES[category] ?: LINE_STYLES.getValue("other")

/** MapLibre mide `line-dasharray` en anchos de línea, no en px: se divide por el ancho. */
fun LineStyle.dashInLineWidths(): List<Float> = dash.map { it / width }

private fun point(p: Position) = Point.fromLngLat(p.lon, p.lat)
private fun line(path: List<Position>) = LineString.fromLngLats(path.map(::point))
private fun polygon(rings: List<Ring>) = Polygon.fromLngLats(rings.map { ring -> ring.map(::point) })

private fun Feature.tag(kind: String, id: String) = apply {
    addStringProperty("kind", kind)
    addStringProperty("id", id)
}

/** GeoJSON de cada capa, por nombre de fuente. */
data class FarmLayers(
    val workArea: String,
    val zones: String,
    val missions: String,
    val missionPaths: String,
    val lines: String,
    val elements: String,
)

fun farmLayers(data: ProjectData, elementColor: (String) -> String): FarmLayers {
    fun collection(features: List<Feature>) = FeatureCollection.fromFeatures(features).toJson()
    return FarmLayers(
        workArea = collection(listOfNotNull(data.workArea?.let { Feature.fromGeometry(polygon(it.polygon)).tag("work_area", it.id) })),
        zones = collection(
            data.zones.map { zone ->
                Feature.fromGeometry(polygon(zone.polygon)).tag(FeatureKind.Zone.key, zone.id).apply {
                    addStringProperty("color", zone.color.takeIf { HEX_COLOR.matches(it) } ?: ZONE_FALLBACK_COLOR)
                }
            },
        ),
        missions = collection(data.missions.map { Feature.fromGeometry(polygon(it.area)).tag(FeatureKind.Mission.key, it.id) }),
        missionPaths = collection(data.missionPaths.map { Feature.fromGeometry(line(it.path)).tag(FeatureKind.Mission.key, it.missionId) }),
        lines = collection(
            data.lines.map { l ->
                Feature.fromGeometry(line(l.path)).tag(FeatureKind.Line.key, l.id).apply {
                    addStringProperty("category", if (l.category in LINE_STYLES) l.category else "other")
                    addNumberProperty("ancho", lineStyle(l.category).width)
                }
            },
        ),
        elements = collection(
            data.elements.map { e ->
                Feature.fromGeometry(point(e.position)).tag(FeatureKind.Element.key, e.id).apply {
                    addStringProperty("color", elementColor(e.elementType))
                }
            },
        ),
    )
}

/** Encuadre inicial: toda la geometría de la finca; si no hay, los bounds del ortomosaico. */
fun farmBounds(data: ProjectData, orthoBounds: List<Double>?): Bounds? = boundsOf(
    data.workArea?.polygon.orEmpty().flatten() +
        data.zones.flatMap { it.polygon.flatten() } +
        data.lines.flatMap { it.path } +
        data.elements.map { it.position } +
        data.missions.flatMap { it.area.flatten() },
) ?: orthoBounds?.takeIf { it.size == 4 }?.let { (w, s, e, n) -> Bounds(w, s, e, n) }

/** Punto GPS con la precisión en px a zoom 0; el estilo la escala con el zoom (×2 por nivel). */
fun gpsFeature(lat: Double, lon: Double, accuracyM: Float): String {
    val feature = Feature.fromGeometry(Point.fromLngLat(lon, lat))
    feature.addNumberProperty("accuracy_px_z0", accuracyM / metersPerPixelAtZoom0(lat))
    return FeatureCollection.fromFeature(feature).toJson()
}

/** Metros por píxel de Web Mercator (teselas de 512 px de MapLibre) a zoom 0. */
fun metersPerPixelAtZoom0(lat: Double): Double = 2 * PI * 6_378_137.0 * cos(lat * PI / 180) / 512.0

fun emptyCollection(): String = FeatureCollection.fromFeatures(emptyList()).toJson()
