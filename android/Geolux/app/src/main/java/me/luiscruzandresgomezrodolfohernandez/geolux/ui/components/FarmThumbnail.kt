package me.luiscruzandresgomezrodolfohernandez.geolux.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Ring
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.elementTypeColor
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.parseHexColor
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.geo.boundsOf
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import androidx.compose.material3.MaterialTheme

/**
 * Miniatura de la finca dibujada con sus propias geometrías: área de trabajo,
 * potreros y elementos, con los estilos de DESIGN.md §7. No hay mapa base
 * (06 §10): el fondo es `surfaceVariant`. El ortomosaico llega con el mapa (AND-R3/R4).
 *
 * Con [highlightZoneId], ese potrero se dibuja seleccionado (borde 3 dp, relleno 28 %).
 */
@Composable
fun FarmThumbnail(
    data: ProjectData,
    description: String,
    modifier: Modifier = Modifier,
    highlightZoneId: String? = null,
) {
    val background = MaterialTheme.colorScheme.surfaceVariant
    val workAreaColor = GeoLuxTheme.colors.mapWorkArea
    val defaultZoneColor = GeoLuxTheme.colors.mapZoneDefault

    val bounds = remember(data) {
        boundsOf(
            (data.workArea?.polygon.orEmpty() + data.zones.flatMap { it.polygon }).flatten() +
                data.elements.map { it.position },
        )
    }

    Canvas(
        modifier = modifier
            .background(background)
            .semantics { contentDescription = description },
    ) {
        if (bounds == null) return@Canvas
        // Proyección plana: a escala de finca basta corregir la longitud por cos(lat).
        val midLat = Math.toRadians((bounds.north + bounds.south) / 2)
        val kx = cos(midLat)
        val pad = 16.dp.toPx()
        val spanX = (bounds.width * kx).coerceAtLeast(1e-9)
        val spanY = bounds.height.coerceAtLeast(1e-9)
        val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
        val offX = (size.width - spanX * scale) / 2
        val offY = (size.height - spanY * scale) / 2
        fun project(lon: Double, lat: Double) = Offset(
            x = (offX + (lon - bounds.west) * kx * scale).toFloat(),
            y = (offY + (bounds.north - lat) * scale).toFloat(),
        )
        fun pathOf(polygon: List<Ring>) = Path().apply {
            fillType = PathFillType.EvenOdd
            polygon.forEach { ring ->
                ring.forEachIndexed { i, p ->
                    val o = project(p.lon, p.lat)
                    if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                }
                close()
            }
        }

        // Orden de dibujo de §7: área de trabajo → potreros → elementos.
        data.workArea?.let { workArea ->
            val path = pathOf(workArea.polygon)
            drawPath(path, workAreaColor.copy(alpha = 0.03f))
            drawPath(
                path,
                workAreaColor.copy(alpha = 0.95f),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())),
                ),
            )
        }
        data.zones.forEach { zone ->
            val color = parseHexColor(zone.color, defaultZoneColor)
            val selected = zone.id == highlightZoneId
            val path = pathOf(zone.polygon)
            drawPath(path, color.copy(alpha = if (selected) 0.28f else 0.15f))
            drawPath(path, color, style = Stroke(width = (if (selected) 3 else 2).dp.toPx()))
        }
        data.elements.forEach { element ->
            drawElementDot(project(element.position.lon, element.position.lat), elementTypeColor(element.elementType))
        }
    }
}

private fun DrawScope.drawElementDot(center: Offset, color: Color) {
    drawCircle(Color.White, radius = 5.dp.toPx(), center = center)
    drawCircle(color, radius = 3.5.dp.toPx(), center = center)
}
