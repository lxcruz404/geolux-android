package me.luiscruzandresgomezrodolfohernandez.geolux.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.luiscruzandresgomezrodolfohernandez.geolux.data.management.ManagementDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «Eliminar proyecto» con el repositorio real en el dispositivo: dos fincas importadas de
 * paquetes de Desktop (la demo, con ortomosaico, modelo 3D e informe; y otra con ganado,
 * actividades, recordatorio, trabajadores, compras y dinero, ya pasadas a Room). Se borra una
 * y la otra queda intacta en archivos y en Room; otra instancia no la vuelve a ver; la última
 * también se puede borrar. Datos aislados del APK de pruebas (no la app ni fincas reales).
 */
@RunWith(AndroidJUnit4::class)
class GeoluxProjectRepositoryDeleteTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val work = File(context.cacheDir, "delete-test")
    private val projects = File(context.filesDir, "projects")
    private val demo = File(work, "finca-demo-v1.glx")
    private val managed = File(work, "finca-ida-vuelta-android-v1.glx")

    @Before
    fun clean() {
        listOf(work, projects, File(context.cacheDir, "import")).forEach { it.deleteRecursively() }
        context.deleteDatabase("gestion.db")
        work.mkdirs()
        listOf(demo, managed).forEach { file ->
            instrumentation.context.assets.open(file.name).use { input -> file.outputStream().use { input.copyTo(it) } }
        }
    }

    private fun install(repository: GeoluxProjectRepository, file: File): String = runBlocking {
        val preview = repository.inspect(Uri.fromFile(file)) as ImportPreview.Ready
        val result = repository.import(preview, replace = false) as ImportResult.Imported
        result.projectId
    }

    private fun snapshot(dir: File): Map<String, Long> =
        dir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dir).path to it.length() }

    @Test
    fun eliminarUnaFincaBorraSusArchivosYSuGestionSinTocarLaOtra() = runBlocking<Unit> {
        val repository = GeoluxProjectRepository(context)
        val demoId = install(repository, demo)
        val managedId = install(repository, managed)
        // La gestión de las dos pasa a Room (como al abrir cada finca en la app).
        val before = repository.management(managedId)
        repository.management(demoId)
        assertEquals(1, before.animals.size)
        assertTrue(before.activities.isNotEmpty() && before.workers.isNotEmpty() && before.transactions.isNotEmpty())
        val store = ProjectStore(projects)
        val managedDir = store.dirFor(managedId)
        val managedFiles = snapshot(managedDir)
        val demoDir = store.dirFor(demoId)
        assertTrue(demoDir.walkTopDown().any { it.name.endsWith(".glb") })

        repository.deleteProject(demoId)

        // La lista se actualiza sola y la finca eliminada no queda en ninguna parte.
        assertEquals(listOf(managedId), repository.projects().first().map { it.id })
        assertFalse(demoDir.exists())
        assertTrue(projects.list()!!.none { it.startsWith(".deleting-") })
        val roomDb = ManagementDatabase.open(context)
        try {
            assertNull(roomDb.dao().state(demoId))
            // La otra finca: mismos archivos y la misma gestión en Room.
            assertEquals(managedFiles, snapshot(managedDir))
            assertEquals(before, repository.management(managedId))
            assertEquals(1, roomDb.dao().animals(managedId).size)
        } finally {
            roomDb.close()
        }
        // Al cerrar y abrir la app (otra instancia) no reaparece.
        val reopened = GeoluxProjectRepository(context)
        assertEquals(listOf(managedId), reopened.projects().first().map { it.id })
        assertTrue(runCatching { reopened.project(demoId) }.isFailure)

        // La última finca también se elimina: lista vacía, sin errores, y Room sin sus filas.
        reopened.deleteProject(managedId)
        assertTrue(reopened.projects().first().isEmpty())
        assertFalse(managedDir.exists())
        val db = ManagementDatabase.open(context)
        try {
            assertTrue(db.dao().animals(managedId).isEmpty())
            assertTrue(db.dao().activities(managedId).isEmpty())
            assertTrue(db.dao().events(managedId).isEmpty())
            assertNull(db.dao().state(managedId))
        } finally {
            db.close()
        }
        assertTrue(GeoluxProjectRepository(context).projects().first().isEmpty())
        // Eliminar algo que no está no rompe nada.
        assertTrue(runCatching { reopened.deleteProject(managedId) }.isFailure)
    }
}
