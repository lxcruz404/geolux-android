package me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/** Área táctil de cada manija (48 dp, DESIGN.md §5) y tamaño dibujado. */
private val TOUCH = 48.dp
private val VERTEX = 22.dp
private val MIDPOINT = 14.dp

/**
 * Manijas de la geometría en edición, encima del mapa: cada vértice se arrastra
 * con el dedo (y se toca para elegirlo y poder quitarlo); el punto medio de
 * cada borde se arrastra para crear un vértice nuevo. Se recolocan con cada
 * movimiento de cámara ([cameraTick]). Fuera de las manijas el mapa sigue
 * respondiendo (mover, acercar, tocar para agregar).
 */
@Composable
fun VertexHandles(
    map: MapLibreMap,
    cameraTick: Int,
    session: EditSession,
    onSession: (EditSession) -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") cameraTick // leerlo recompone con la cámara
    val update by rememberUpdatedState(onSession)
    // La última sesión, sin esperar a que Compose recomponga: el primer movimiento de un
    // arrastre llega antes que la recomposición y, con la sesión de antes, borraba el
    // historial («Deshacer») o el vértice recién insertado.
    val latest = remember { arrayOf(session) }
    latest[0] = session
    fun change(f: (EditSession) -> EditSession) {
        val next = f(latest[0])
        latest[0] = next
        update(next)
    }
    val density = LocalDensity.current
    val half = with(density) { TOUCH.toPx() / 2 }
    fun screen(p: Position): Offset = map.projection.toScreenLocation(LatLng(p.lat, p.lon)).let { Offset(it.x, it.y) }
    fun geo(o: Offset): Position = map.projection.fromScreenLocation(android.graphics.PointF(o.x, o.y)).let { Position(it.longitude, it.latitude) }

    Box(Modifier.fillMaxSize()) {
        session.midpoints().forEach { (after, mid) ->
            val at = screen(mid)
            Handle(
                at = at, half = half, size = MIDPOINT, fill = Color.White.copy(alpha = 0.85f), border = Color(0xFF1C2A25),
                tag = "punto_medio_$after", description = "Agregar vértice",
                onDragStart = { change { it.insertVertex(after, mid) } },
                onDrag = { offset -> change { it.moveVertex(after + 1, geo(offset)) } },
                onTap = null,
            )
        }
        session.points.forEachIndexed { i, p ->
            val at = screen(p)
            val selected = session.selectedVertex == i
            Handle(
                at = at, half = half, size = VERTEX,
                fill = if (selected) Color(0xFFFFD43B) else Color.White, border = Color(0xFF1C2A25),
                tag = "vertice_$i", description = "Vértice ${i + 1}",
                onDragStart = { change { it.beginDrag().select(i) } },
                onDrag = { offset -> change { it.moveVertex(i, geo(offset)) } },
                onTap = { change { it.select(if (it.selectedVertex == i) null else i) } },
            )
        }
    }
}

@Composable
private fun Handle(
    at: Offset,
    half: Float,
    size: androidx.compose.ui.unit.Dp,
    fill: Color,
    border: Color,
    tag: String,
    description: String,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onTap: (() -> Unit)?,
) {
    val start by rememberUpdatedState(at)
    val dragStart by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val tap by rememberUpdatedState(onTap)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset { IntOffset((at.x - half).roundToInt(), (at.y - half).roundToInt()) }
            .size(TOUCH)
            .testTag(tag)
            .semantics { contentDescription = description }
            .pointerInput(tag) {
                // Posición en pantalla del dedo = la de la manija al empezar + lo arrastrado.
                var position = Offset.Zero
                detectDragGestures(
                    onDragStart = { position = start; dragStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        position += amount
                        drag(position)
                    },
                )
            }
            .pointerInput(tag + "_tap") { detectTapGestures(onTap = { tap?.invoke() }) },
    ) {
        Box(Modifier.size(size).background(fill, CircleShape).border(2.dp, border, CircleShape))
    }
}
