package me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit

import me.luiscruzandresgomezrodolfohernandez.geolux.core.edit.EditException
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.FeatureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dibujo táctil (vértices, puntos medios, deshacer) y su traducción a FarmEditor. */
class EditSessionTest {
    private val demo = FakeProjectRepository.demo
    private val a = Position(-70.2100, 6.3100)
    private val b = Position(-70.2095, 6.3100)
    private val c = Position(-70.2095, 6.3105)
    private val d = Position(-70.2100, 6.3105)

    @Test
    fun dibujarUnPotreroTocandoElMapaYDeshacer() {
        var s = EditSession.new(EditTarget.Zone)
        s = s.tap(a).tap(b)
        assertFalse(s.canFinish) // un polígono necesita 3 vértices
        s = s.tap(c)
        assertTrue(s.canFinish)
        s = s.tap(d)
        assertEquals(listOf(a, b, c, d), s.points)
        s = s.undo()
        assertEquals(listOf(a, b, c), s.points)
        assertEquals(listOf(a, b, c, d), s.tap(d).points)
    }

    @Test
    fun unElementoSeColocaDondeSeToca() {
        val s = EditSession.new(EditTarget.Element).tap(a).tap(b)
        assertEquals(listOf(b), s.points) // el último toque manda
        assertTrue(s.midpoints().isEmpty())
    }

    @Test
    fun arrastrarUnVerticeEsUnSoloDeshacer() {
        var s = EditSession.new(EditTarget.Line).tap(a).tap(b)
        s = s.beginDrag()
        repeat(10) { i -> s = s.moveVertex(1, Position(b.lon, b.lat + i * 1e-5)) }
        assertEquals(Position(b.lon, b.lat + 9e-5), s.points[1])
        assertEquals(listOf(a, b), s.undo().points)
    }

    @Test
    fun elPuntoMedioCreaUnVerticeYSePuedeQuitar() {
        var s = EditSession.new(EditTarget.Zone).tap(a).tap(b).tap(c)
        // Polígono: 3 bordes, incluido el de cierre (c → a).
        assertEquals(listOf(0, 1, 2), s.midpoints().map { it.first })
        s = s.insertVertex(0, Position(-70.20975, 6.3099))
        assertEquals(4, s.points.size)
        assertEquals(1, s.selectedVertex)
        assertTrue(s.canRemoveSelected)
        s = s.removeSelected()
        assertEquals(listOf(a, b, c), s.points)
        // Con 3 vértices ya no se puede quitar ninguno.
        assertFalse(s.select(0).canRemoveSelected)
    }

    @Test
    fun cambiarLaFormaDeUnPotreroExistenteSinElVerticeDeCierre() {
        val zone = demo.zones.first()
        val s = EditSession.reshape(demo, FeatureKind.Zone, zone.id)!!
        assertEquals(zone.id, s.featureId)
        assertEquals(zone.polygon.first().dropLast(1), s.points)
        assertNull(EditSession.reshape(demo, FeatureKind.Mission, demo.missions.first().id)) // las misiones no se editan
    }

    @Test
    fun accionesCrearEditarMoverEliminarYDeshacer() {
        val fields = FeatureFields(name = "Potrero norte", status = "resting", color = "#1C7ED6", capacity = "12,5")
        val created = EditActions.createNew(EditTarget.Zone, listOf(a, b, c, d), fields)(demo)
        val zone = created.zones.last()
        assertEquals("Potrero norte", zone.name)
        assertEquals(12.5, zone.livestockCapacity!!, 0.0)
        assertEquals("En descanso", zone.statusLabel)

        val renamed = EditActions.updateDetails(EditTarget.Zone, zone.id, FeatureFields.of(created, EditTarget.Zone, zone.id)!!.copy(name = "Norte"))(created)
        assertEquals("Norte", renamed.zones.last().name)
        assertEquals(zone.polygon, renamed.zones.last().polygon) // editar datos no toca la forma

        val element = demo.elements.first()
        val moved = EditActions.saveShape(EditSession.reshape(demo, FeatureKind.Element, element.id)!!.tap(a))(demo)
        assertEquals(a, moved.elements.first().position)

        val deleted = EditActions.delete(FeatureKind.Zone, zone.id)(created)
        assertEquals(demo.zones, deleted.zones)
        // «Deshacer» tras eliminar: vuelven las capas de antes.
        assertEquals(created.zones, EditActions.restore(created)(deleted).zones)
    }

    @Test
    fun loQueNoAceptaDesktopNoSeGuarda() {
        assertThrows(EditException::class.java) {
            EditActions.createNew(EditTarget.Zone, listOf(a, b, c), FeatureFields(name = "   "))(demo)
        }
        assertThrows(EditException::class.java) {
            EditActions.createNew(EditTarget.Zone, listOf(a, b, c), FeatureFields(name = "X", capacity = "muchos"))(demo)
        }
        assertThrows(EditException::class.java) { EditActions.delete(FeatureKind.Mission, demo.missions.first().id)(demo) }
    }
}
