package me.luiscruzandresgomezrodolfohernandez.geolux.ui.map

import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Ring
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatArea
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/** Por debajo de este zoom no se dibujan etiquetas ni números: con la finca entera se amontonan. */
private const val LABELS_MIN_ZOOM = 14.0

/** Distancia mínima entre centros de waypoints: el círculo (22 dp) y un hueco. */
private val WAYPOINT_SPACING = 28.dp

/** Azules de las misiones (DESIGN.md §3.3). */
private val MissionBlue = Color(0xFF4C6EF5)
private val WaypointBorder = Color(0xFF364FC7)

/**
 * Texto sobre el mapa: etiquetas «nombre · área» de los potreros y waypoints
 * numerados de las rutas (DESIGN.md §7). MapLibre necesitaría fuentes (glyphs)
 * locales para dibujar texto y la app no las trae, así que se dibujan en Compose
 * encima del mapa y se recolocan con cada movimiento de cámara ([cameraTick]).
 *
 * Las etiquetas van en la parte de arriba de cada potrero (en el centro suelen
 * estar los elementos) y, si dos chocan, se dibuja solo la primera. De los
 * waypoints se dibujan los que caben sin solaparse ([visibleWaypoints]) y nunca
 * encima de una etiqueta: una ruta real tiene cientos y tapaba el mapa. Al
 * acercar aparecen todos; cada uno conserva su número.
 */
@Composable
internal fun FarmOverlay(map: MapLibreMap, data: ProjectData, visible: Set<MapLayerGroup>, cameraTick: Int) {
    @Suppress("UNUSED_EXPRESSION") cameraTick // Leerlo hace que se recomponga al mover la cámara.
    if (map.cameraPosition.zoom < LABELS_MIN_ZOOM) return
    val projection = map.projection
    fun screen(p: Position): PointF = projection.toScreenLocation(LatLng(p.lat, p.lon))
    val anchors = mutableListOf<Anchor>()
    val spacingPx = with(LocalDensity.current) { WAYPOINT_SPACING.toPx() }

    if (MapLayerGroup.Zones in visible) {
        data.zones.forEach { zone ->
            val center = labelPoint(zone.polygon) ?: return@forEach
            val north = zone.polygon.firstOrNull()?.maxOfOrNull { it.lat } ?: return@forEach
            val color = Color(android.graphics.Color.parseColor(zone.color.takeIf { HEX_COLOR.matches(it) } ?: ZONE_FALLBACK_COLOR))
            anchors += Anchor(screen(Position(lon = center.lon, lat = north)), isLabel = true) {
                ZoneLabel("${zone.name} · ${formatArea(zone.areaM2)}", color)
            }
        }
    }
    if (MapLayerGroup.Missions in visible) {
        val width = map.width.toFloat()
        val height = map.height.toFloat()
        data.missionPaths.forEach { path ->
            val points = path.path.map { screen(it).let { s -> Offset(s.x, s.y) } }
            visibleWaypoints(points, spacingPx, width, height).forEach { i ->
                anchors += Anchor(PointF(points[i].x, points[i].y), isLabel = false) { Waypoint(i + 1) }
            }
        }
    }

    val labelGapPx = with(LocalDensity.current) { 8.dp.toPx() }
    Layout(content = { anchors.forEach { it.content() } }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Las etiquetas van antes en la lista: se colocan primero y los waypoints no las tapan.
            val labels = mutableListOf<RectF>()
            placeables.forEachIndexed { i, p ->
                val anchor = anchors[i]
                val left = anchor.at.x - p.width / 2f
                val top = if (anchor.isLabel) anchor.at.y + labelGapPx else anchor.at.y - p.height / 2f
                val rect = RectF(left, top, left + p.width, top + p.height)
                if (labels.any { RectF.intersects(it, rect) }) return@forEachIndexed
                if (anchor.isLabel) labels += rect
                p.place(left.roundToInt(), top.roundToInt())
            }
        }
    }
}

/**
 * Índices de los waypoints de una ruta que se dibujan: los que están en pantalla
 * ([width] × [height], con un margen de [spacing]) y a [spacing] o más de los ya
 * elegidos, para que los círculos no se solapen. El primero y el último de la ruta
 * van siempre que estén en pantalla: son el inicio y el final. El resto, en orden.
 */
internal fun visibleWaypoints(points: List<Offset>, spacing: Float, width: Float, height: Float): List<Int> {
    fun onScreen(p: Offset) = p.x >= -spacing && p.y >= -spacing && p.x <= width + spacing && p.y <= height + spacing
    val minDistance2 = spacing * spacing
    val chosen = mutableListOf<Int>()
    val order = listOf(0, points.lastIndex) + (1 until points.lastIndex)
    for (i in order.distinct()) {
        if (i !in points.indices || !onScreen(points[i])) continue
        if (chosen.all { (points[it] - points[i]).getDistanceSquared() >= minDistance2 }) chosen += i
    }
    return chosen.sorted()
}

/** Algo dibujado sobre el mapa en un punto de la pantalla. */
private class Anchor(val at: PointF, val isLabel: Boolean, val content: @Composable () -> Unit)

@Composable
private fun ZoneLabel(text: String, color: Color) {
    Row(
        modifier = Modifier
            .shadow(2.dp, RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Waypoint(number: Int) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(MissionBlue, CircleShape)
            .border(1.5.dp, WaypointBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Centroide del anillo exterior (fórmula del área con signo), para colocar la
 * etiqueta de un potrero. Las cuentas van respecto al primer vértice: con
 * longitudes como −70 los productos se cancelan y se pierden centímetros.
 * Si el anillo es degenerado, el promedio de sus vértices.
 */
fun labelPoint(polygon: List<Ring>): Position? {
    val ring = polygon.firstOrNull()?.takeIf { it.size >= 3 } ?: return null
    val origin = ring.first()
    var area = 0.0
    var cx = 0.0
    var cy = 0.0
    for (i in 0 until ring.lastIndex) {
        val ax = ring[i].lon - origin.lon
        val ay = ring[i].lat - origin.lat
        val bx = ring[i + 1].lon - origin.lon
        val by = ring[i + 1].lat - origin.lat
        val cross = ax * by - bx * ay
        area += cross
        cx += (ax + bx) * cross
        cy += (ay + by) * cross
    }
    if (abs(area) < 1e-18) return Position(ring.map { it.lon }.average(), ring.map { it.lat }.average())
    return Position(lon = origin.lon + cx / (3 * area), lat = origin.lat + cy / (3 * area))
}
