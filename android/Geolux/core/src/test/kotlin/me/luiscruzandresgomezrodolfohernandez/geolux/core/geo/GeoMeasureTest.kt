package me.luiscruzandresgomezrodolfohernandez.geolux.core.geo

import kotlin.math.PI
import kotlin.math.cos
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los mismos casos que `tests/core/test_geo_measure.py` de Desktop. */
class GeoMeasureTest {
    private val metersPerDegreeLat = 111_320.0

    private fun p(lon: Double, lat: Double) = Position(lon, lat)

    /** Cuadrado de [sideM] m construido con desplazamientos reales en metros. */
    private fun squareRing(centerLat: Double, centerLon: Double, sideM: Double): List<Position> {
        val dLat = sideM / 2 / metersPerDegreeLat
        val dLon = sideM / 2 / (metersPerDegreeLat * cos(centerLat * PI / 180))
        return listOf(
            p(centerLon - dLon, centerLat - dLat), p(centerLon + dLon, centerLat - dLat),
            p(centerLon + dLon, centerLat + dLat), p(centerLon - dLon, centerLat + dLat),
        )
    }

    @Test
    fun areaYPerimetroDeUnCuadradoCercaDelEcuador() {
        val (area, perimeter) = GeoMeasure.polygonAreaPerimeter(squareRing(6.0, -70.0, 1000.0))
        assertEquals(1_000_000.0, area, 1_000.0)
        assertEquals(4000.0, perimeter, 4.0)
    }

    @Test
    fun elAreaTieneEnCuentaLaLongitudEnLatitudesAltas() {
        val (area, _) = GeoMeasure.polygonAreaPerimeter(squareRing(60.0, 10.0, 1000.0))
        assertEquals(1_000_000.0, area, 5_000.0)
    }

    @Test
    fun anilloCerradoYAbiertoDanLoMismo() {
        val ring = squareRing(6.0, -70.0, 100.0)
        assertEquals(GeoMeasure.polygonAreaPerimeter(ring), GeoMeasure.polygonAreaPerimeter(ring + ring.first()))
    }

    @Test
    fun menosDeTresVerticesNoTienenArea() {
        assertThrows(GeoValidationException::class.java) { GeoMeasure.polygonAreaPerimeter(listOf(p(0.0, 0.0), p(1.0, 1.0))) }
        assertThrows(GeoValidationException::class.java) { GeoMeasure.polygonAreaPerimeter(emptyList()) }
    }

    @Test
    fun longitudDeUnaLineaRectaDeUnKilometro() {
        val dLon = 1000.0 / (metersPerDegreeLat * cos(6.0 * PI / 180))
        assertEquals(1000.0, GeoMeasure.polylineLength(listOf(p(-70.0, 6.0), p(-70.0 + dLon, 6.0))), 1.0)
        val dLat = 500.0 / metersPerDegreeLat
        assertEquals(1000.0, GeoMeasure.polylineLength(listOf(p(-70.0, 6.0), p(-70.0, 6.0 + dLat), p(-70.0, 6.0 + 2 * dLat))), 1.0)
    }

    @Test
    fun unaLineaNuncaSeCierraSola() {
        val points = listOf(p(0.0, 0.0), p(0.01, 0.0), p(0.01, 0.01))
        assertTrue(GeoMeasure.polylineLength(points) < GeoMeasure.polygonAreaPerimeter(points).second)
        assertThrows(GeoValidationException::class.java) { GeoMeasure.polylineLength(listOf(p(0.0, 0.0))) }
    }

    private val square = listOf(p(0.0, 0.0), p(10.0, 0.0), p(10.0, 10.0), p(0.0, 10.0))

    @Test
    fun puntoDentroDelPoligono() {
        assertTrue(GeoMeasure.pointInPolygon(p(5.0, 5.0), square))
        assertFalse(GeoMeasure.pointInPolygon(p(50.0, 50.0), square))
        assertFalse(GeoMeasure.pointInPolygon(p(-5.0, 5.0), square))
        assertTrue(GeoMeasure.pointInPolygon(p(0.0, 0.0), square)) // un vértice cuenta como dentro
        assertTrue(GeoMeasure.pointInPolygon(p(5.0, 5.0), square + square.first()))
        assertFalse(GeoMeasure.pointInPolygon(p(5.0, 5.0), listOf(p(0.0, 0.0), p(1.0, 1.0))))
        val lShape = listOf(p(0.0, 0.0), p(10.0, 0.0), p(10.0, 5.0), p(5.0, 5.0), p(5.0, 10.0), p(0.0, 10.0))
        assertTrue(GeoMeasure.pointInPolygon(p(2.0, 2.0), lShape))
        assertFalse(GeoMeasure.pointInPolygon(p(8.0, 8.0), lShape))
    }

    @Test
    fun segmentos() {
        assertTrue(GeoMeasure.segmentsIntersect(p(0.0, 0.0), p(10.0, 10.0), p(0.0, 10.0), p(10.0, 0.0)))
        assertFalse(GeoMeasure.segmentsIntersect(p(0.0, 0.0), p(1.0, 0.0), p(0.0, 5.0), p(1.0, 5.0)))
        assertTrue(GeoMeasure.segmentsIntersect(p(0.0, 0.0), p(5.0, 0.0), p(5.0, 0.0), p(5.0, 5.0)))
        assertTrue(GeoMeasure.segmentsIntersect(p(0.0, 0.0), p(5.0, 0.0), p(3.0, 0.0), p(8.0, 0.0)))
        assertFalse(GeoMeasure.segmentsIntersect(p(0.0, 0.0), p(2.0, 0.0), p(3.0, 0.0), p(5.0, 0.0)))
        // Cruce propio (misma fórmula que `_segments_cross` de Desktop): dos tramos colineales no se cruzan.
        assertTrue(GeoMeasure.segmentsCross(p(0.0, 0.0), p(10.0, 10.0), p(0.0, 10.0), p(10.0, 0.0)))
        assertFalse(GeoMeasure.segmentsCross(p(0.0, 0.0), p(5.0, 0.0), p(3.0, 0.0), p(8.0, 0.0)))
    }

    private val shared = listOf(p(10.0, 0.0), p(20.0, 0.0), p(20.0, 10.0), p(10.0, 10.0))
    private val overlapping = listOf(p(5.0, 5.0), p(15.0, 5.0), p(15.0, 15.0), p(5.0, 15.0))
    private val far = listOf(p(100.0, 100.0), p(110.0, 100.0), p(110.0, 110.0), p(100.0, 110.0))

    @Test
    fun solapeDePotreros() {
        assertTrue(GeoMeasure.polygonsOverlap(square, overlapping))
        assertFalse(GeoMeasure.polygonsOverlap(square, far))
        assertFalse(GeoMeasure.polygonsOverlap(square, shared)) // compartir borde no es solaparse
        val outer = listOf(p(0.0, 0.0), p(20.0, 0.0), p(20.0, 20.0), p(0.0, 20.0))
        val inner = listOf(p(5.0, 5.0), p(15.0, 5.0), p(15.0, 15.0), p(5.0, 15.0))
        assertTrue(GeoMeasure.polygonsOverlap(outer, inner))
        assertTrue(GeoMeasure.polygonsOverlap(inner, outer))
        assertFalse(GeoMeasure.polygonsOverlap(listOf(p(0.0, 0.0), p(1.0, 1.0)), square))
        assertTrue(GeoMeasure.polygonsOverlap(square + square.first(), overlapping + overlapping.first()))
        assertTrue(GeoMeasure.polygonsOverlap(square, square)) // dos contornos idénticos
    }

    @Test
    fun soloSeAceptanPoligonosSimples() {
        GeoMeasure.validateSimplePolygon(squareRing(6.0, -70.0, 100.0))
        // Pajarita: los bordes se cruzan.
        val bowTie = listOf(p(0.0, 0.0), p(0.001, 0.001), p(0.001, 0.0), p(0.0, 0.001))
        assertEquals(
            "El polígono se cruza consigo mismo. Corrige los vértices para que los bordes no se crucen.",
            assertThrows(GeoValidationException::class.java) { GeoMeasure.validateSimplePolygon(bowTie) }.message,
        )
        assertThrows(GeoValidationException::class.java) { GeoMeasure.validateSimplePolygon(listOf(p(0.0, 0.0), p(0.001, 0.0), p(0.002, 0.0))) }
        assertThrows(GeoValidationException::class.java) { GeoMeasure.validateSimplePolygon(listOf(p(0.0, 0.0), p(0.001, 0.0), p(0.001, 0.0))) }
        // Un doble toque (vértice repetido seguido) no es un defecto.
        val ring = squareRing(6.0, -70.0, 100.0)
        GeoMeasure.validateSimplePolygon(listOf(ring[0], ring[1], ring[1], ring[2], ring[3]))
    }

    @Test
    fun catalogosComoDesktop() {
        assertEquals(23, Catalogs.elementTypes.size)
        assertEquals(12, Catalogs.lineCategories.size)
        assertEquals("Cerca eléctrica", Catalogs.lineCategoryLabel("electric_fence"))
        assertEquals("En descanso", Catalogs.zoneStatusLabel("resting"))
        assertEquals("clave_nueva", Catalogs.elementTypeLabel("clave_nueva"))
    }
}
