package me.luiscruzandresgomezrodolfohernandez.geolux.ui.map

import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabelPointTest {

    @Test
    fun centroDeUnCuadrado() {
        val square = listOf(listOf(Position(0.0, 0.0), Position(2.0, 0.0), Position(2.0, 2.0), Position(0.0, 2.0), Position(0.0, 0.0)))
        assertEquals(Position(1.0, 1.0), labelPoint(square))
    }

    @Test
    fun sinPerderPrecisionConLongitudesGrandes() {
        // La Ceiba de finca-demo: −70.2…−70.197, 6.3…6.302. Antes de restar el primer
        // vértice, el resultado se desviaba ~2 cm.
        val ceiba = labelPoint(FakeProjectRepository.demo.zones.first().polygon)!!
        assertEquals(-70.1985, ceiba.lon, 1e-9)
        assertEquals(6.301, ceiba.lat, 1e-9)
    }

    @Test
    fun sinAnillo() {
        assertNull(labelPoint(emptyList()))
    }
}
