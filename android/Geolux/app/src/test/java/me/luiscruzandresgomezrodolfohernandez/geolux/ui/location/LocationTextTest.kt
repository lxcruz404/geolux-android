package me.luiscruzandresgomezrodolfohernandez.geolux.ui.location

import me.luiscruzandresgomezrodolfohernandez.geolux.data.LocationState
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationTextTest {

    private val demo = FakeProjectRepository.demo

    @Test
    fun precision() {
        assertEquals("±5 m", formatAccuracy(5f))
        assertEquals("±13 m", formatAccuracy(12.6f))
        assertEquals("±1 m", formatAccuracy(0.3f))
    }

    @Test
    fun coordenadas() {
        assertEquals("6.30100, -70.19850", formatCoordinates(6.301, -70.1985))
    }

    @Test
    fun dondeEstasEnFincaDemo() {
        // La posición por defecto de FakeLocationSource cae en La Ceiba.
        assertEquals(PlaceInFinca.InZone("Potrero La Ceiba"), placeOf(LocationState.Fix(6.301, -70.1985, 5f), demo))
        // Junto a la casa: dentro del área de trabajo, fuera de los potreros.
        assertEquals(PlaceInFinca.InWorkArea, placeOf(LocationState.Fix(6.2995, -70.2005, 5f), demo))
        assertEquals(PlaceInFinca.Outside, placeOf(LocationState.Fix(4.6, -74.08, 5f), demo))
    }
}
