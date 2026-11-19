package me.luiscruzandresgomezrodolfohernandez.geolux.core.edit

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exportar desde Android una finca editada como paquete v1 (escenario E de la misión). */
class PackageWriterTest {
    private val fixtures = File(System.getProperty("geolux.fixtures")!!)
    private val demo = File(fixtures, "finca-demo.geolux")
    private val glx = File(System.getProperty("geolux.glxFixtures")!!, "finca-demo-v1.glx")

    private fun export(original: File, edit: (me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData) -> me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData): Pair<File, me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData> {
        val data = edit(GeoluxReader.read(original).data!!)
        val out = Files.createTempFile("exportado", ".glx").toFile()
        out.outputStream().use { PackageWriter.write(original, data, it, appVersion = "prueba") }
        return out to data
    }

    private val newZone = listOf(Position(-70.2, 6.31), Position(-70.1995, 6.31), Position(-70.1995, 6.3105), Position(-70.2, 6.3105))

    @Test
    fun loEditadoViajaYElPaqueteEsValido() {
        val (file, edited) = export(demo) { d ->
            var x = FarmEditor.createZone(d, "Potrero de Android", newZone, status = "resting")
            x = FarmEditor.createLine(x, "Cerca nueva", "fence", newZone.take(2))
            FarmEditor.deleteElement(x, d.elements.first().id)
        }
        val report = GeoluxReader.read(file)
        assertTrue(report.errors.toString(), report.errors.isEmpty())
        val back = report.data!!
        assertEquals(edited.zones, back.zones)
        assertEquals(edited.lines, back.lines)
        assertEquals(edited.elements, back.elements)
        assertEquals(edited.workArea, back.workArea)
        assertEquals(edited.missions, back.missions)
        assertEquals(edited.products, back.products)
        // Es otro paquete de la misma finca: Desktop o Android preguntarán si reemplazar.
        assertNotEquals(GeoluxReader.read(demo).data!!.packageId, back.packageId)
        assertEquals(edited.project.id, back.project.id)
    }

    @Test
    fun elManifiestoVaPrimeroYLoQueNoSeEditoSeCopiaTalCual() {
        val (file, _) = export(glx) { FarmEditor.createZone(it, "Nuevo", newZone) }
        ZipFile(file).use { out ->
            ZipFile(glx).use { original ->
                assertEquals("manifest.json", out.entries().nextElement().name)
                assertEquals(original.entries().toList().map { it.name }.toSet(), out.entries().toList().map { it.name }.toSet())
                for (entry in original.entries()) {
                    if (entry.name == "manifest.json" || entry.name.startsWith("layers/")) continue
                    val copy = out.getEntry(entry.name)
                    assertEquals(entry.name, entry.method, copy.method) // mismo método de compresión
                    assertArrayEquals(entry.name, original.getInputStream(entry).readBytes(), out.getInputStream(copy).readBytes())
                }
            }
            val manifest = out.getInputStream(out.getEntry("manifest.json")).readBytes().toString(Charsets.UTF_8)
            assertTrue(manifest.contains("\"app\": \"GeoLux Android\""))
        }
        val report = GeoluxReader.read(file)
        assertTrue(report.errors.toString(), report.errors.isEmpty())
        assertEquals(GeoluxReader.read(glx).data!!.zones.size + 1, report.data!!.zones.size)
    }
}
