package me.luiscruzandresgomezrodolfohernandez.geolux.ui.map

import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Capas del mapa (AND-R3) a partir de finca-demo. */
class MapLayersTest {
    private val demo = FakeProjectRepository.demo
    private val layers = farmLayers(demo) { type -> if (type == "tank") "#0B7285" else "#868E96" }

    private fun features(json: String) = FeatureCollection.fromJson(json).features()!!

    @Test
    fun everyLayerHasOneFeaturePerObjectWithKindAndId() {
        assertEquals(1, features(layers.workArea).size)
        assertEquals(demo.zones.map { it.id }, features(layers.zones).map { it.getStringProperty("id") })
        assertEquals(demo.elements.map { it.id }, features(layers.elements).map { it.getStringProperty("id") })
        assertEquals(demo.lines.map { it.id }, features(layers.lines).map { it.getStringProperty("id") })
        assertEquals(demo.missions.map { it.id }, features(layers.missions).map { it.getStringProperty("id") })
        // La ruta se toca como su misión.
        assertEquals(demo.missionPaths.map { it.missionId }, features(layers.missionPaths).map { it.getStringProperty("id") })
        assertTrue(features(layers.zones).all { it.getStringProperty("kind") == FeatureKind.Zone.key })
        assertTrue(features(layers.elements).all { it.getStringProperty("kind") == FeatureKind.Element.key })
    }

    @Test
    fun stylePropertiesComeFromTheContract() {
        val zone = features(layers.zones).first()
        assertEquals(demo.zones.first().color, zone.getStringProperty("color"))
        val tank = features(layers.elements).first { it.getStringProperty("id") == "el-demo-1" }
        assertEquals("#0B7285", tank.getStringProperty("color"))
        val point = tank.geometry() as Point
        assertEquals(-70.19895, point.longitude(), 0.0)
        assertEquals(6.30105, point.latitude(), 0.0)
        assertEquals(setOf("fence", "pipeline"), features(layers.lines).map { it.getStringProperty("category") }.toSet())
    }

    @Test
    fun unknownLineCategoryIsDrawnAsOther() {
        assertEquals(LINE_STYLES.getValue("other"), lineStyle("no-existe"))
        // Desktop mide el trazo en px; MapLibre, en anchos de línea.
        assertEquals(listOf(2f, 1.5f), lineStyle("fence").dashInLineWidths())
        assertTrue(lineStyle("river").dash.isEmpty())
    }

    @Test
    fun boundsCoverTheFarmOrFallBackToTheOrtho() {
        val b = farmBounds(demo, null)!!
        assertTrue(b.west < b.east && b.south < b.north)
        val ortho = listOf(-70.3, 6.2, -70.1, 6.4)
        val empty = demo.copy(workArea = null, zones = emptyList(), lines = emptyList(), elements = emptyList(), missions = emptyList())
        assertEquals(-70.3, farmBounds(empty, ortho)!!.west, 0.0)
    }

    @Test
    fun gpsAccuracyIsConvertedToPixelsAtZoom0() {
        // A ~6° de latitud un píxel a zoom 0 mide ~77.8 km; 10 m de precisión ≈ 0.000129 px.
        assertEquals(77_840.0, metersPerPixelAtZoom0(6.3), 50.0)
        val gps = FeatureCollection.fromJson(gpsFeature(6.3, -70.2, 10f)).features()!!.single()
        assertNotNull(gps.getNumberProperty("accuracy_px_z0"))
        assertEquals(10 / metersPerPixelAtZoom0(6.3), gps.getNumberProperty("accuracy_px_z0").toDouble(), 1e-12)
    }

    @Test
    fun fichaFormats() {
        assertEquals("6.30105, -70.19895", formatCoordinates(6.30105, -70.19895))
        assertEquals("2.10 cm/px", formatNumber(2.1, "cm/px", 2))
        assertEquals("75 %", formatNumber(75.0, "%", 0))
        assertEquals("4 min", formatMinutes(262.0))
        assertEquals("1 min", formatMinutes(20.0))
    }
}
