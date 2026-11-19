package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.io.File
import java.time.Instant
import java.time.LocalDate
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Ganadería en el teléfono con las reglas de Desktop, sobre el ganado que escribió Desktop
 * en `finca-gestion-v1.glx` (toro 2068-38 activo con pesaje y vacuna; vaca 1150 vendida y
 * con un pesaje anulado). Escenario A del plan, lado Android.
 */
class LivestockTest {
    private val glx = File(System.getProperty("geolux.glxFixtures")!!, "finca-gestion-v1.glx")
    private val projectId = GeoluxReader.read(glx).data!!.project.id
    private val data = ManagementPackage.read(glx, projectId)
    private var n = 0
    private val ops = ManagementOps(now = { Instant.parse("2099-01-01T00:00:00Z") }, newId = { "tel-${++n}" })
    private val herd = LivestockOps(ops)
    private val bull get() = data.animals.first { it.tag == "2068-38" }
    private val cow get() = data.animals.first { it.tag == "1150" }

    private fun expect(fragment: String, block: () -> Unit) {
        try {
            block(); fail("debía rechazarse: $fragment")
        } catch (e: ValidationException) {
            assertTrue("«${e.message}» no menciona «$fragment»", e.message!!.contains(fragment))
        }
    }

    @Test
    fun elEstadoSaleDeLosEventosComoEnDesktop() {
        assertEquals("active", herd.state(data, bull.id).status)
        assertEquals(bull.lotId, herd.state(data, bull.id).lotId)
        assertEquals("sold", herd.state(data, cow.id).status)
        assertNull(herd.state(data, cow.id).lotId)
        // La caché que trajo Desktop coincide con lo deducido.
        assertEquals(data, ops.rebuildAnimals(data))
        // El pesaje anulado de la vaca no cuenta.
        assertTrue(Livestock.weightCurve(data.animalEvents.filter { it.animalId == cow.id }).isEmpty())
    }

    @Test
    fun escenarioA_pesajeSanidadMovimientoYGananciaDiaria() {
        val lotId = bull.lotId!!
        val (withLot, other) = herd.createIndividualLot(data, "bovine", null)
        var d = herd.recordWeighing(withLot, bull.id, "2026-11-21", "432.5")
        d = herd.recordHealth(d, bull.id, "2026-11-22", "treatment", product = "Lo que registra el usuario", route = "subcutaneous",
            nextDate = "2026-12-22")
        d = herd.move(d, bull.id, other.id, "2026-11-23")
        assertEquals(other.id, d.animals.first { it.id == bull.id }.lotId)
        assertEquals(0, herd.activeCount(d, lotId))
        val curve = Livestock.weightCurve(d.animalEvents.filter { it.animalId == bull.id })
        assertEquals(listOf(412.5, 432.5), curve.map { it.kg })
        assertNull(curve.first().dailyGainKg) // sin pesaje anterior no hay ganancia
        assertEquals(1.0, curve.last().dailyGainKg!!, 1e-9) // 20 kg en 20 días
        // Lo que viaja de vuelta se vuelve a leer igual.
        assertEquals(d, ManagementCodec.decode(projectId, ManagementCodec.encode(d)))
    }

    @Test
    fun reglasDeDesktop() {
        expect("Ya existe un animal") { herd.registerEntry(data, " 2068-38 ", "bovine", "purchase", "2026-11-20", bull.lotId!!) }
        expect("agregado") {
            herd.registerEntry(data, "9999", "bovine", "purchase", "2026-11-20", data.livestockLots.first { it.mode == "aggregate" }.id)
        }
        expect("anterior al alta") { herd.recordWeighing(data, bull.id, "2026-09-01", "300") }
        expect("entre 0 y 2000") { herd.recordWeighing(data, bull.id, "2026-11-20", "2500") }
        expect("producto") { herd.recordHealth(data, bull.id, "2026-11-20", "vaccination") }
        expect("ya tiene una baja") { herd.registerExit(data, cow.id, "death", "2026-11-20") }
        expect("Solo se puede mover") { herd.move(data, cow.id, bull.lotId!!, "2026-11-20") }
        val entry = data.animalEvents.first { it.animalId == cow.id && it.eventType == Livestock.PURCHASE }
        expect("Anula primero") { herd.voidEvent(data, entry.id, "error", LocalDate.of(2026, 11, 20)) }
        val (withCalf, calf) = herd.registerEntry(data, "2068-39", "bovine", "birth", "2026-11-20", bull.lotId!!, sex = "female")
        assertEquals("2026-11-20", calf.birthDate)
        assertEquals("active", herd.state(withCalf, calf.id).status)
    }

    @Test
    fun fusionarRechazaUnaHistoriaImposible() {
        val sold = herd.registerExit(data, bull.id, "sale", "2026-11-20")
        val (withLot, other) = herd.createIndividualLot(data, "bovine", null)
        val moved = herd.move(withLot, bull.id, other.id, "2026-11-25")
        try {
            ops.merge(sold, moved)
            fail("una venta seguida de un movimiento no es una vida válida")
        } catch (e: ValidationException) {
            assertTrue(e.message!!.contains("2068-38"))
        }
    }
}
