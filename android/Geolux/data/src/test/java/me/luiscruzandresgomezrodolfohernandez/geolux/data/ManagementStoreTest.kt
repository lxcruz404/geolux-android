package me.luiscruzandresgomezrodolfohernandez.geolux.data

import java.io.File
import java.nio.file.Files
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementCodec
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fuentes de la gestión que quedan en archivos tras pasar a Room: la del paquete y la que
 * guardaba la versión anterior en `management/`, que se conserva renombrada tras migrarla.
 */
class ManagementStoreTest {
    private val glx = File(System.getProperty("geolux.glxFixtures")!!, "finca-gestion-v1.glx")
    private val data = GeoluxReader.read(glx).data!!
    private val id = data.project.id
    private val root = Files.createTempDirectory("fincas").toFile()

    @Test
    fun paqueteYArchivosDeLaVersionAnterior() {
        val store = ProjectStore(root)
        store.install(glx, data)
        assertEquals(ManagementPackage.read(glx, id), store.packageManagement(id))
        assertNull(store.legacyManagement(id))
        // Lo que dejaba la versión anterior en management/:
        val legacy = File(store.dirFor(id), ProjectStore.MANAGEMENT).apply { mkdirs() }
        ManagementCodec.encode(store.packageManagement(id)!!).forEach { (name, text) -> File(legacy, "$name.json").writeText(text) }
        assertEquals(store.packageManagement(id), ProjectStore(root).legacyManagement(id))
        store.markLegacyMigrated(id, stamp = "2026-11-11T120000")
        assertFalse(legacy.exists())
        val backup = store.legacyBackups(id).single() // copia fuera de la finca, no se borra
        assertTrue(File(backup, "workers.json").isFile)
        assertNull(store.legacyManagement(id))
        // Reinstalar la finca no toca la copia.
        store.install(glx, data)
        assertEquals(listOf(backup), store.legacyBackups(id))
    }

    @Test
    fun unaFincaNoImportadaNoTieneGestion() {
        assertNull(ProjectStore(root).packageManagement("no-existe"))
        assertNull(ProjectStore(root).legacyManagement("no-existe"))
    }
}
