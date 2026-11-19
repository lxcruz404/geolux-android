package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import me.luiscruzandresgomezrodolfohernandez.geolux.core.edit.PackageWriter
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paso 2 de la ida y vuelta (`tests/core/test_glx_round_trip.py`): el teléfono lee la finca
 * que exportó Desktop, comprueba animal, lote, actividad vinculada con recordatorio,
 * trabajador y compra pagada; la modifica como lo haría la app y exporta el paquete que
 * Desktop vuelve a importar. Con `GEOLUX_WRITE_GLX_FIXTURES=1` escribe
 * `finca-ida-vuelta-android-v1.glx`; si no, lo escribe en un temporal y lo relee.
 */
class RoundTripTest {
    private val dir = File(System.getProperty("geolux.glxFixtures")!!)
    private val glx = File(dir, "finca-ida-vuelta-v1.glx")
    private val project = GeoluxReader.read(glx).data!!
    private val ops = ManagementOps(now = { Instant.parse("2099-01-01T00:00:00Z") }, newId = { UUID.randomUUID().toString() })
    private val herd = LivestockOps(ops)
    private val geo = mapOf(
        "zone" to project.zones.map { it.id }.toSet(),
        "map_line" to project.lines.map { it.id }.toSet(),
        "map_element" to project.elements.map { it.id }.toSet(),
        "work_area" to setOfNotNull(project.workArea?.id),
    )

    @Test
    fun loDeDesktopLlegaEnteroYLoDelTelefonoSale() {
        val desktop = ManagementPackage.read(glx, project.project.id)
        // Paso 2a: todo llegó con sus relaciones.
        val bull = desktop.animals.single()
        assertEquals("2068-38", bull.tag)
        assertEquals("Brahman", bull.breed)
        val lot = desktop.livestockLots.single()
        assertEquals(lot.id, bull.lotId)
        assertEquals(listOf(412.5), Livestock.weightCurve(desktop.animalEvents).map { it.kg })
        val activity = desktop.activities.single()
        assertEquals(30, activity.remindMinutesBefore)
        assertEquals("America/Bogota", activity.timeZone)
        val links = desktop.activityLinks.filter { it.activityId == activity.id }.associate { it.entity to it.entityId }
        assertEquals(mapOf("animal" to bull.id, "livestock_lot" to lot.id, "zone" to project.zones.single().id), links)
        assertEquals(desktop.workers.single().id, activity.assigneeWorkerId)
        val payment = desktop.transactions.single()
        assertEquals("85000", payment.amount)
        assertEquals(listOf(payment.id), desktop.purchaseItems.mapNotNull { it.transactionId })

        // Paso 2b: lo que se hace en el campo con la app.
        var d = desktop
        d = herd.recordWeighing(d, bull.id, "2026-11-21", "431")
        d = herd.recordHealth(d, bull.id, "2026-11-20", "vaccination", product = "Aftosa (registrado por el usuario)")
        val (d1, fattening) = herd.createIndividualLot(d, "bovine", project.zones.single().id, "Engorde (teléfono)"); d = d1
        d = herd.move(d, bull.id, fattening.id, "2026-11-22")
        d = ops.setActivityStatus(d, activity.id, "done")
        d = ops.createActivity(d, geo, "Pesar 2068-38", "other", "medium", scheduledDate = "2026-11-27", scheduledTime = "07:00",
            remindMinutesBefore = 60, links = listOf("animal" to bull.id)).first
        d = ops.createWorker(d, "T-2", "Ana Gómez").first
        d = ops.voidTransaction(d, payment.id, "Se pagó con otra factura", LocalDate.parse("2026-11-22")).first

        val out = if (System.getenv("GEOLUX_WRITE_GLX_FIXTURES") == "1") File(dir, "finca-ida-vuelta-android-v1.glx")
        else Files.createTempFile("ida-vuelta", ".glx").toFile()
        out.outputStream().use { PackageWriter.write(glx, project, it, appVersion = "android-prueba", management = d) }

        // El paquete del teléfono es válido y trae exactamente lo editado.
        val report = GeoluxReader.read(out)
        assertTrue(report.errors.toString(), report.ok)
        val back = ManagementPackage.read(out, project.project.id)
        assertEquals(d, back)
        assertEquals(desktop.animalEvents.size + 3, back.animalEvents.size)
        assertEquals(fattening.id, back.animals.single().lotId)
        assertEquals(2, back.activities.size)
        assertEquals(0, Finance.summary(back.transactions).let { BigDecimalZero(it.expenses) })
    }

    private fun BigDecimalZero(text: String) = java.math.BigDecimal(text).signum()
}
