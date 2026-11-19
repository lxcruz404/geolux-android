package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Gestión escrita por Desktop (`tests/core/fixtures/glx-v1/finca-gestion-v1.glx`,
 * `test_management_glx.py`): Android la lee sin perder nada, la reescribe con los mismos
 * campos y valores que Desktop, y sus operaciones siguen las reglas del contrato.
 */
class ManagementPackageTest {
    private val glx = File(System.getProperty("geolux.glxFixtures")!!, "finca-gestion-v1.glx")
    private val report = GeoluxReader.read(glx)
    private val projectId = report.data!!.project.id
    private val data = ManagementPackage.read(glx, projectId)

    private var clock = Instant.parse("2099-01-01T00:00:00Z")
    private var counter = 0
    private val ops = ManagementOps(now = { clock }, newId = { "id-${++counter}" })

    private fun desktopRecords(): Map<String, JsonArray> = ZipFile(glx).use { zip ->
        ManagementCodec.DATASETS.mapNotNull { name ->
            zip.getEntry(ManagementCodec.pathOf(name))?.let { entry ->
                name to (Json.parseToJsonElement(zip.getInputStream(entry).readBytes().decodeToString()) as JsonObject)["records"] as JsonArray
            }
        }.toMap()
    }

    @Test
    fun elLectorV1AceptaElPaqueteYLaGestionSeLee() {
        assertTrue(report.errors.toString(), report.ok)
        assertEquals(1, data.workers.size)
        assertEquals(3, data.transactions.size)
        assertTrue(data.activities.any { it.deletedAt != null })
        assertTrue(data.activitySeries.isNotEmpty())
        assertTrue(data.financialCategories.isNotEmpty())
        val link = data.activityLinks.first { it.entity == "zone" }
        assertNotNull(report.data!!.zones.firstOrNull { it.id == link.entityId })
    }

    @Test
    fun reescribirDaLosMismosRegistrosQueDesktop() {
        val desktop = desktopRecords()
        val android = ManagementCodec.encodeRecords(data).filterValues { it.isNotEmpty() }.mapValues { JsonArray(it.value) }
        assertEquals(desktop.keys, android.keys)
        for ((name, rows) in desktop) {
            val mine = android.getValue(name)
            assertEquals(name, rows.size, mine.size)
            for ((theirs, ours) in rows.zip(mine)) assertEquals(name, theirs, ours)
        }
    }

    @Test
    fun losTotalesSonLosDeDesktop() {
        val summary = Finance.summary(data.transactions)
        assertEquals("185000", summary.expenses)
        assertEquals("0", summary.workerPayments) // el pago está anulado
    }

    @Test
    fun completarYComprarEnElTelefonoYFusionar() {
        val activity = ops.liveActivities(data).first { it.title == "Reparar cerca" }
        var phone = ops.setActivityStatus(data, activity.id, "done")
        val (withList, list) = ops.createList(phone, "Grapas", "hardware")
        val (withItem, item) = ops.addItem(withList, list.id, "Grapas", "2", "box", "9000")
        phone = ops.setItemStatus(withItem, item.id, "bought", "17500.5")
        assertEquals("1", "${phone.transactions.size - data.transactions.size + 1}") // comprado no es gasto
        val (paid, tx) = ops.confirmPayment(phone, listOf(item.id), "2026-11-16", "fences", "cash")
        assertEquals("17500.5", tx.amount)
        assertEquals("done", paid.purchaseLists.first { it.id == list.id }.status)

        val merged = ops.merge(data, paid)
        assertEquals("done", merged.activities.first { it.id == activity.id }.status)
        assertEquals(data.transactions.size + 1, merged.transactions.size)
        assertEquals("202500.5", Finance.summary(merged.transactions).expenses)
        // Lo que vuelve a Desktop es contrato puro: se puede decodificar otra vez.
        val again = ManagementCodec.decode(projectId, ManagementCodec.encode(merged))
        assertEquals(merged, again)
        // Volver a fusionar lo mismo no cambia nada.
        assertEquals(merged, ops.merge(merged, paid))
    }

    @Test
    fun reglasDelTelefono() {
        val geo = mapOf("zone" to report.data!!.zones.map { it.id }.toSet())
        expect("no existe en esta finca") { ops.createActivity(data, geo, "X", links = listOf("zone" to "ajeno")) }
        expect("hora necesita") { ops.createActivity(data, geo, "X", scheduledTime = "08:00") }
        val (d1, w) = ops.createWorker(data, "T-77", "Ana")
        expect("Ya hay") { ops.createWorker(d1, " t-77 ", "Otra") }
        val inactive = ops.setWorkerStatus(d1, w.id, "inactive")
        expect("activos") { ops.createActivity(inactive, geo, "X", assigneeWorkerId = w.id) }
        expect("necesita el trabajador") {
            ops.recordTransaction(data, type = "worker_payment", date = "2026-11-01", concept = "Pago", category = "labor", amount = "1")
        }
        val pay = data.transactions.first { it.type == "worker_payment" }
        expect("ya está anulado") { ops.voidTransaction(data, pay.id, "otra vez", LocalDate.of(2026, 11, 20)) }
        val (withTreatment, treatment) = ops.createActivity(data, geo, "Curar el toro 2068-38", "treatment",
            scheduledDate = "2026-11-15", scheduledTime = "08:00", remindMinutesBefore = 30)
        assertEquals("2026-11-15T07:30-05:00[America/Bogota]", ops.reminderAt(treatment).toString())
        assertTrue(ops.overdue(withTreatment, Instant.parse("2026-11-15T13:01:00Z")).any { it.id == treatment.id })
    }

    private fun expect(fragment: String, block: () -> Unit) {
        try {
            block()
            fail("debía rechazarse: $fragment")
        } catch (e: ValidationException) {
            assertTrue("«${e.message}» no menciona «$fragment»", e.message!!.contains(fragment))
        }
    }
}
