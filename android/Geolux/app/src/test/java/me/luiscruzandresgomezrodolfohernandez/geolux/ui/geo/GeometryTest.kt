package me.luiscruzandresgomezrodolfohernandez.geolux.ui.geo

import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {

    private val square = listOf(
        listOf(Position(0.0, 0.0), Position(10.0, 0.0), Position(10.0, 10.0), Position(0.0, 10.0), Position(0.0, 0.0)),
    )
    private val squareWithHole = square + listOf(
        listOf(Position(4.0, 4.0), Position(6.0, 4.0), Position(6.0, 6.0), Position(4.0, 6.0), Position(4.0, 4.0)),
    )

    @Test
    fun puntoDentroYFuera() {
        assertTrue(Position(5.0, 5.0).isInside(square))
        assertFalse(Position(15.0, 5.0).isInside(square))
        assertFalse(Position(5.0, 5.0).isInside(squareWithHole))
        assertTrue(Position(2.0, 2.0).isInside(squareWithHole))
        assertFalse(Position(1.0, 1.0).isInside(emptyList()))
    }

    @Test
    fun lineaDentroQueCruzaYFuera() {
        assertTrue(listOf(Position(1.0, 1.0), Position(2.0, 2.0)).touches(square))
        assertTrue(listOf(Position(-5.0, 5.0), Position(15.0, 5.0)).touches(square))
        assertTrue(listOf(Position(0.0, 10.0), Position(10.0, 10.0)).touches(square))
        assertFalse(listOf(Position(20.0, 20.0), Position(30.0, 30.0)).touches(square))
    }

    @Test
    fun fincaDemo() {
        // finca-demo: el tanque y el bebedero están en La Ceiba; la casa queda fuera de los dos potreros.
        val demo = FakeProjectRepository.demo
        val laCeiba = demo.zones.first { it.id == "zone-demo-1" }
        val elBajo = demo.zones.first { it.id == "zone-demo-2" }
        val inCeiba = demo.elements.filter { it.position.isInside(laCeiba.polygon) }.map { it.id }
        assertEquals(listOf("el-demo-1", "el-demo-2"), inCeiba)
        assertTrue(demo.elements.none { it.position.isInside(elBajo.polygon) })
        // La cerca norte recorre el borde de los dos potreros; la tubería va dentro de La Ceiba.
        assertEquals(listOf("line-demo-1", "line-demo-2"), demo.lines.filter { it.path.touches(laCeiba.polygon) }.map { it.id })
        assertEquals(listOf("line-demo-1"), demo.lines.filter { it.path.touches(elBajo.polygon) }.map { it.id })
    }

    @Test
    fun encuadre() {
        assertEquals(Bounds(0.0, 0.0, 10.0, 10.0), boundsOf(square.first()))
        assertEquals(null, boundsOf(emptyList()))
    }
}
