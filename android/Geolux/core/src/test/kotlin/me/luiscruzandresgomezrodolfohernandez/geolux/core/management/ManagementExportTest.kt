package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.luiscruzandresgomezrodolfohernandez.geolux.core.edit.PackageWriter
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El `.glx` que exporta el teléfono lleva la gestión editada: el paquete sigue siendo válido
 * (tamaños y SHA-256 al día), la gestión se vuelve a leer igual y un conjunto vacío deja de viajar.
 */
class ManagementExportTest {
    private val glx = File(System.getProperty("geolux.glxFixtures")!!, "finca-gestion-v1.glx")
    private val project = GeoluxReader.read(glx).data!!
    private var counter = 0
    private val ops = ManagementOps(now = { Instant.parse("2099-01-01T00:00:00Z") }, newId = { "nuevo-${++counter}" })

    private fun export(management: ManagementData): File {
        val out = Files.createTempFile("gestion", ".glx").toFile()
        out.outputStream().use { PackageWriter.write(glx, project, it, appVersion = "prueba", management = management) }
        return out
    }

    private fun manifest(file: File): JsonObject = ZipFile(file).use { zip ->
        Json.parseToJsonElement(zip.getInputStream(zip.getEntry("manifest.json")).readBytes().decodeToString()).jsonObject
    }

    @Test
    fun laGestionEditadaViajaYElPaqueteEsValido() {
        val original = ManagementPackage.read(glx, project.project.id)
        val (withWorker, _) = ops.createWorker(original, "T-200", "Nueva en el campo")
        val (edited, _) = ops.recordTransaction(withWorker, type = "expense", date = "2026-11-20", concept = "Grapas",
            category = "fences", amount = "12000")
        val out = export(edited)
        val report = GeoluxReader.read(out)
        assertTrue(report.errors.toString(), report.ok)
        assertEquals(edited, ManagementPackage.read(out, project.project.id))
        val m = manifest(out)
        val contents = m.getValue("contents").jsonArray.map { it.jsonObject }
        val totals = m.getValue("totals").jsonObject
        assertEquals(contents.sumOf { it.getValue("size_bytes").jsonPrimitive.long }, totals.getValue("bytes").jsonPrimitive.long)
        assertEquals(contents.size.toLong(), totals.getValue("files").jsonPrimitive.long)
        assertEquals(ZipFile(out).use { it.size() - 1 }, contents.size)
    }

    @Test
    fun unConjuntoVacioDejaDeViajarYUnoNuevoSeAgrega() {
        val original = ManagementPackage.read(glx, project.project.id)
        val noSeries = original.copy(activitySeries = emptyList(), activities = original.activities.map { it.copy(seriesId = null) })
        val (withAttachment, _) = noSeries.let { d ->
            d.copy(attachments = listOf(Attachment("adj-1", d.projectId, "worker", d.workers.first().id, "adjuntos/aa/" + "a".repeat(64) + ".jpg",
                "image/jpeg", 10, "a".repeat(64), "x.jpg", "2099-01-01T00:00:00+00:00"))) to Unit
        }
        val out = export(withAttachment)
        assertTrue(GeoluxReader.read(out).ok)
        val paths = manifest(out).getValue("contents").jsonArray.map { it.jsonObject.getValue("path").jsonPrimitive.content }
        assertFalse(ManagementCodec.pathOf("activity_series") in paths)
        assertTrue(ManagementCodec.pathOf("attachments") in paths)
        assertEquals(withAttachment, ManagementPackage.read(out, project.project.id))
    }

    @Test
    fun sinGestionElPaqueteQuedaComoAntes() {
        val out = Files.createTempFile("sin", ".glx").toFile()
        out.outputStream().use { PackageWriter.write(glx, project, it, appVersion = "prueba") }
        assertEquals(ManagementPackage.read(glx, project.project.id), ManagementPackage.read(out, project.project.id))
    }
}
