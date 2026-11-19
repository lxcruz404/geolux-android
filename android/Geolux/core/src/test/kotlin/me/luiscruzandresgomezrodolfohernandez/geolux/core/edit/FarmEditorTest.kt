package me.luiscruzandresgomezrodolfohernandez.geolux.core.edit

import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edición de la finca con las reglas de Desktop, y su formato en disco (SPEC §5). */
class FarmEditorTest {
    private val demo = GeoluxReader.read(File(System.getProperty("geolux.fixtures")!!, "finca-demo.geolux")).data!!

    /** Finca vacía de capas editables: como un proyecto sin datos dibujados. */
    private val empty = demo.copy(workArea = null, zones = emptyList(), elements = emptyList(), lines = emptyList())

    /** Cuadrado de [side] m con la esquina suroeste en (lon, lat). */
    private fun square(lon: Double, lat: Double, side: Double): List<Position> {
        val dLat = side / 111_320.0
        val dLon = side / (111_320.0 * cos(lat * PI / 180))
        return listOf(Position(lon, lat), Position(lon + dLon, lat), Position(lon + dLon, lat + dLat), Position(lon, lat + dLat))
    }

    private fun roundTrip(data: ProjectData) = GeoluxReader.readEditedLayers(LayersJson.write(data), demo)

    @Test
    fun crearUnPotreroCalculaSusMedidasYSeGuardaCerrado() {
        val data = FarmEditor.createZone(empty, "  Potrero nuevo ", square(-70.2, 6.3, 100.0), description = "Pasto", color = "#2A9D8F", status = "resting", livestockCapacity = 12.0)
        val zone = data.zones.single()
        assertEquals("Potrero nuevo", zone.name)
        assertEquals("En descanso", zone.statusLabel)
        assertEquals(10_000.0, zone.areaM2, 10.0)
        assertEquals(400.0, zone.perimeterM, 0.5)
        assertEquals(zone.polygon.single().first(), zone.polygon.single().last()) // anillo cerrado
        // Vuelve igual del disco, con el formato del contrato.
        assertEquals(data.zones, roundTrip(data).zones)
    }

    @Test
    fun dosPotrerosPuedenCompartirBordePeroNoArea() {
        val a = square(-70.2, 6.3, 100.0)
        val data = FarmEditor.createZone(empty, "A", a)
        // Vecino que comparte el borde este de A: se acepta.
        val neighbour = listOf(a[1], Position(a[1].lon + (a[1].lon - a[0].lon), a[1].lat), Position(a[2].lon + (a[1].lon - a[0].lon), a[2].lat), a[2])
        val both = FarmEditor.createZone(data, "B", neighbour)
        assertEquals(2, both.zones.size)
        // Uno que se mete dentro de A: se rechaza con el mensaje de Desktop.
        val message = assertThrows(EditException::class.java) { FarmEditor.createZone(both, "C", square(-70.19995, 6.30005, 30.0)) }.message!!
        assertTrue(message, message.startsWith("El potrero se superpone con \"A\"."))
        // Cambiar la forma de A sobre sí misma no choca consigo.
        FarmEditor.updateZoneGeometry(both, both.zones.first().id, square(-70.2, 6.3, 90.0))
    }

    @Test
    fun reglasComoDesktop() {
        assertEquals("El nombre de la zona es obligatorio.", assertThrows(EditException::class.java) { FarmEditor.createZone(empty, " ", square(-70.2, 6.3, 50.0)) }.message)
        val bowTie = square(-70.2, 6.3, 50.0).let { listOf(it[0], it[2], it[1], it[3]) }
        assertTrue(assertThrows(EditException::class.java) { FarmEditor.createZone(empty, "X", bowTie) }.message!!.startsWith("El polígono se cruza consigo mismo"))
        assertEquals("Se necesitan al menos 2 vértices para medir una distancia.", assertThrows(EditException::class.java) { FarmEditor.createLine(empty, "Cerca", "fence", listOf(Position(-70.2, 6.3))) }.message)
        assertEquals("Selecciona un tipo de línea válido.", assertThrows(EditException::class.java) { FarmEditor.createLine(empty, "Cerca", "muro", square(-70.2, 6.3, 50.0)) }.message)
        assertEquals("Selecciona un tipo de elemento válido.", assertThrows(EditException::class.java) { FarmEditor.createElement(empty, "Tanque", "nave", Position(-70.2, 6.3)) }.message)
        assertEquals("La latitud debe estar entre -90 y 90.", assertThrows(EditException::class.java) { FarmEditor.createElement(empty, "Tanque", "tank", Position(-70.2, 91.0)) }.message)
        assertEquals("La capacidad ganadera no puede ser negativa.", assertThrows(EditException::class.java) { FarmEditor.createZone(empty, "P", square(-70.2, 6.3, 50.0), livestockCapacity = -1.0) }.message)
    }

    @Test
    fun lineasYElementosSeCreanEditanMuevenYEliminan() {
        val path = listOf(Position(-70.2, 6.3), Position(-70.2, 6.3 + 1000.0 / 111_320.0))
        var data = FarmEditor.createLine(empty, "Cerca norte", "electric_fence", path, "Nueva")
        val line = data.lines.single()
        assertEquals("Cerca eléctrica", line.categoryLabel)
        assertEquals(1000.0, line.lengthM, 0.5)
        data = FarmEditor.updateLineDetails(data, line.id, "Vía principal", "main_road", "")
        assertEquals("Camino principal", data.lines.single().categoryLabel)
        data = FarmEditor.updateLineGeometry(data, line.id, path + Position(-70.199, 6.31))
        assertEquals(3, data.lines.single().path.size)

        data = FarmEditor.createElement(data, "Bebedero 1", "trough", Position(-70.199, 6.301), "")
        val element = data.elements.single()
        assertEquals("Bebedero", element.typeLabel)
        data = FarmEditor.moveElement(data, element.id, Position(-70.198, 6.302))
        assertEquals(Position(-70.198, 6.302), data.elements.single().position)

        val back = roundTrip(data)
        assertEquals(data.lines, back.lines)
        assertEquals(data.elements, back.elements)

        data = FarmEditor.deleteLine(FarmEditor.deleteElement(data, element.id), line.id)
        assertTrue(data.lines.isEmpty() && data.elements.isEmpty())
        assertEquals("La línea no existe.", assertThrows(EditException::class.java) { FarmEditor.deleteLine(data, line.id) }.message)
    }

    @Test
    fun areaDeTrabajoUnaSolaPorFinca() {
        var data = FarmEditor.saveWorkArea(empty, square(-70.2, 6.3, 200.0))
        val id = data.workArea!!.id
        data = FarmEditor.saveWorkArea(data, square(-70.2, 6.3, 300.0))
        assertEquals(id, data.workArea!!.id) // cambiar la forma conserva el id
        assertEquals(90_000.0, data.workArea!!.areaM2, 50.0)
        assertEquals(data.workArea, roundTrip(data).workArea)
        data = FarmEditor.deleteWorkArea(data)
        assertNull(data.workArea)
        assertNull(roundTrip(data).workArea) // borrada en disco también, no vuelve la del paquete
    }

    @Test
    fun lasCapasDelPaqueteDeDesktopSobrevivenElViajeIdaYVuelta() {
        // Escribir las capas de finca-demo y volver a leerlas no cambia nada.
        val back = roundTrip(demo)
        assertEquals(demo.workArea, back.workArea)
        assertEquals(demo.zones, back.zones)
        assertEquals(demo.elements, back.elements)
        assertEquals(demo.lines, back.lines)
        assertEquals(demo.missions, back.missions) // las misiones no se tocan
    }
}
