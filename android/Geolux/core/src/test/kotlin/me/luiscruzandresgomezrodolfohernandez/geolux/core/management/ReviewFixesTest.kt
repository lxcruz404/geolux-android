package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.io.File
import java.time.Instant
import java.time.LocalDate
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Correcciones de la revisión del 11 nov. 2026, con las mismas reglas que Desktop. */
class ReviewFixesTest {
    private val glx = File(System.getProperty("geolux.glxFixtures")!!, "finca-gestion-v1.glx")
    private val projectId = GeoluxReader.read(glx).data!!.project.id
    private val data = ManagementPackage.read(glx, projectId)
    private var n = 0
    private val ops = ManagementOps(now = { Instant.parse("2099-01-01T00:00:00Z") }, newId = { "f-${++n}" })

    private fun refused(fragment: String, block: () -> Unit) {
        try {
            block(); fail("debía rechazarse: $fragment")
        } catch (e: ValidationException) {
            assertTrue("«${e.message}» no menciona «$fragment»", e.message!!.contains(fragment))
        }
    }

    @Test
    fun unaAnulacionDeHoyCancelaUnGastoDeUnPeriodoPasado() {
        val (d1, old) = ops.recordTransaction(data, type = "expense", date = "2026-10-15", concept = "Viejo", category = "fences", amount = "500000")
        val (d2, _) = ops.voidTransaction(d1, old.id, "error", LocalDate.of(2026, 11, 11))
        val october = d2.transactions.filter { it.date.startsWith("2026-10") }
        val voided = d2.transactions.mapNotNull { it.voidsTransactionId }.toSet()
        assertEquals("0", Finance.summary(october, voided = voided).expenses)
        val report = ManagementReport.build(d2, "F", "2026-10-01", "2026-10-31")
        assertTrue(report.summary.contains("Gastos 0"))
    }

    @Test
    fun rutasDeAdjuntoFueraDeLaFincaSeRechazan() {
        for (bad in listOf("../../x.bat", "C:/Windows/x.bat", "adjuntos/00/otro.pdf")) {
            val evil = data.copy(attachments = listOf(Attachment("x", projectId, "worker", "w", bad, "image/jpeg", 1, "a".repeat(64))))
            refused("ruta de adjunto") { ops.merge(data, evil) }
        }
        assertTrue(ManagementValidation.safeAttachmentPath("adjuntos/bb/" + "b".repeat(64) + ".pdf", "b".repeat(64)))
        assertFalse(ManagementValidation.safeAttachmentPath("adjuntos/bb/" + "b".repeat(64) + ".pdf", "c".repeat(64)))
    }

    @Test
    fun valoresFueraDelContratoSeRechazan() {
        val bad = data.copy(transactions = data.transactions.map { it.copy(amount = "185000.00") })
        refused("canónico") { ops.merge(data, bad) }
    }

    @Test
    fun elMismoCodigoDeTrabajadorEnDosEquiposSeExplica() {
        val twin = data.workers.first().copy(id = "otro-id")
        refused("otro registro") { ops.merge(data, data.copy(workers = data.workers + twin)) }
    }

    @Test
    fun laFusionRecalculaElTamanoDeLosLotesIndividuales() {
        val herd = LivestockOps(ops)
        val bull = data.animals.first { it.tag == "2068-38" }
        val sold = herd.registerExit(data, bull.id, "sale", "2026-11-20")
        val merged = ops.merge(data, sold)
        val lot = merged.livestockLots.first { it.id == bull.lotId }
        assertEquals(0, lot.count)
    }

    @Test
    fun borrarUnTrabajadorYReusarSuCodigoEnElMismoPaqueteEsValido() {
        val w = data.workers.first()
        val incoming = data.copy(workers = listOf(
            w.copy(deletedAt = "2099-01-01T00:00:00+00:00", updatedAt = "2099-01-01T00:00:00+00:00"),
            w.copy(id = "nuevo", fullName = "Nuevo con el mismo código"),
        ))
        val merged = ops.merge(data, incoming)
        assertEquals(1, merged.workers.count { it.deletedAt == null })
    }
}
