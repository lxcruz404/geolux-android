package me.luiscruzandresgomezrodolfohernandez.geolux.ui.map

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Qué waypoints caben sin solaparse (FarmOverlay). */
class VisibleWaypointsTest {
    private val spacing = 28f
    private val width = 1000f
    private val height = 2000f

    private fun choose(points: List<Offset>) = visibleWaypoints(points, spacing, width, height)

    @Test
    fun unaRutaCortaYSeparadaSeVeEntera() {
        // Como la misión de finca-demo vista de cerca: puntos a 60 px.
        val points = List(8) { Offset(100f + it * 60f, 500f) }
        assertEquals((0..7).toList(), choose(points))
    }

    @Test
    fun unaRutaDensaSeAclaraSinSolapesYConservaInicioYFinal() {
        // Como La Giralda con la finca entera: 363 puntos en zigzag, a 3 px.
        val points = List(363) { i -> Offset(50f + (i % 121) * 7f, 300f + (i / 121) * 9f) }
        val chosen = choose(points)
        assertTrue(chosen.size in 2 until 100)
        assertEquals(0, chosen.first())
        assertEquals(362, chosen.last())
        for (a in chosen) for (b in chosen) if (a < b) {
            assertTrue("$a y $b se solapan", (points[a] - points[b]).getDistance() >= spacing)
        }
    }

    @Test
    fun losQueEstanFueraDePantallaNoCuentan() {
        val points = listOf(Offset(-500f, 10f), Offset(200f, 200f), Offset(210f, 205f), Offset(5000f, 10f))
        // Inicio y final fuera: se elige el primero visible; el que está pegado a él no.
        assertEquals(listOf(1), choose(points))
    }

    @Test
    fun sinPuntosNiUno() {
        assertEquals(emptyList<Int>(), choose(emptyList()))
        assertEquals(listOf(0), choose(listOf(Offset(10f, 10f))))
    }
}
