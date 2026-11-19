package me.luiscruzandresgomezrodolfohernandez.geolux.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espacio libre antes de importar, con el repositorio real y el espacio simulado.
 * Con el manifiesto al principio (finca-demo y el `.glx` de Desktop) se decide antes
 * de copiar; sin él, después de validar la copia.
 */
@RunWith(AndroidJUnit4::class)
class GeoluxProjectRepositorySpaceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val work = File(context.cacheDir, "space-test")
    private val staging = File(context.cacheDir, "import")
    private val projects = File(context.filesDir, "projects")
    private val demo = File(work, "finca-demo.geolux")
    private val glx = File(work, "finca-demo-v1.glx")

    @Before
    fun clean() {
        listOf(work, staging, projects).forEach { it.deleteRecursively() }
        work.mkdirs()
        listOf(demo, glx).forEach { file ->
            instrumentation.context.assets.open(file.name).use { input -> file.outputStream().use { input.copyTo(it) } }
        }
    }

    private fun repository(free: () -> Long) = GeoluxProjectRepository(context, freeSpace = free)

    private fun inspect(repository: GeoluxProjectRepository, file: File = demo) =
        runBlocking { repository.inspect(Uri.fromFile(file)) }

    private fun peek(file: File) = file.inputStream().use(GeoluxReader::peekManifest)!!

    /** Lo que pide el primer filtro: copia en caché, copia instalada y productos, más la holgura. */
    private fun neededBeforeCopy(file: File) = peek(file).let { withSpaceMargin(2 * it.declaredBytes + it.productBytes) }

    private fun stagingIsEmpty() = staging.listFiles().orEmpty().isEmpty()

    @Test
    fun sinEspacioNoEmpiezaACopiarYDiceCuantoHaceFalta() {
        for (file in listOf(demo, glx)) {
            val preview = inspect(repository { 0 }, file) as ImportPreview.NoSpace
            assertEquals(file.name, neededBeforeCopy(file), preview.neededBytes)
            assertEquals(0L, preview.availableBytes)
            assertTrue(stagingIsEmpty())
        }
    }

    @Test
    fun elLimiteExactoAlcanzaYUnByteMenosNo() {
        for (file in listOf(demo, glx)) {
            projects.deleteRecursively()
            val needed = neededBeforeCopy(file)
            assertTrue(inspect(repository { needed - 1 }, file) is ImportPreview.NoSpace)

            val repository = repository { needed }
            val ready = inspect(repository, file) as ImportPreview.Ready
            assertEquals(ImportResult.Imported(ready.projectId), runBlocking { repository.import(ready, replace = false) })
        }
    }

    @Test
    fun unPaqueteYaImportadoSoloNecesitaSuCopia() {
        val repository = repository { Long.MAX_VALUE }
        val first = inspect(repository) as ImportPreview.Ready
        runBlocking { repository.import(first, replace = false) }

        // Cabe la copia para comprobarlo, no una instalación nueva.
        val justTheCopy = withSpaceMargin(peek(demo).declaredBytes)
        val again = inspect(repository(free = { justTheCopy })) as ImportPreview.Ready
        assertEquals(ImportDecision.ALREADY_IMPORTED, again.decision)

        projects.deleteRecursively()
        assertTrue(inspect(repository(free = { justTheCopy })) is ImportPreview.NoSpace)
    }

    @Test
    fun sinManifiestoAlPrincipioSeMideDespuesDeValidar() {
        // El mismo paquete con manifest.json al final: no hay estimación previa.
        val reordered = File(work, "manifiesto-al-final.geolux")
        ZipFile(demo).use { zip ->
            ZipOutputStream(reordered.outputStream()).use { out ->
                val entries = zip.entries().toList().sortedBy { it.name == "manifest.json" }
                for (entry in entries) {
                    out.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
        assertEquals(null, reordered.inputStream().use(GeoluxReader::peekManifest))

        val preview = inspect(repository { 0 }, reordered) as ImportPreview.NoSpace
        // La copia se hizo y se borró: cuenta en lo necesario y en lo disponible.
        val copy = reordered.length()
        assertEquals(withSpaceMargin(copy + ProjectStore(File(work, "store")).spaceToInstall(reordered)), preview.neededBytes)
        assertEquals(copy, preview.availableBytes)
        assertTrue(stagingIsEmpty())
    }

    @Test
    fun laCopiaPendienteSeBorraAntesDeMedirOtraVez() {
        var free = Long.MAX_VALUE
        val repository = repository { free }
        inspect(repository) // Queda una copia pendiente.
        assertEquals(1, staging.listFiles().orEmpty().size)

        free = 0
        assertTrue(inspect(repository) is ImportPreview.NoSpace)
        assertTrue(stagingIsEmpty())
    }

    @Test
    fun reintentarTrasUnRechazoImportaNormalmente() {
        assertTrue(inspect(repository { 0 }) is ImportPreview.NoSpace)
        val repository = repository { Long.MAX_VALUE }
        val ready = inspect(repository) as ImportPreview.Ready
        assertEquals(ImportResult.Imported(ready.projectId), runBlocking { repository.import(ready, replace = false) })
        assertTrue(stagingIsEmpty())
    }

    @Test
    fun informaElAvanceDeLaCopiaLaComprobacionYLaInstalacion() {
        val seen = mutableListOf<ImportProgress>()
        val repository = repository { Long.MAX_VALUE }
        val ready = runBlocking { repository.inspect(Uri.fromFile(glx)) { seen += it } } as ImportPreview.Ready
        runBlocking { repository.import(ready, replace = false) { seen += it } }
        // Los pasos van en orden y cada uno termina en su total.
        assertEquals(listOf(ImportStep.Copy, ImportStep.Verify, ImportStep.Install), seen.map { it.step }.distinct())
        val declared = peek(glx).declaredBytes
        // file:// no informa el tamaño: la copia usa el total del manifiesto.
        assertEquals(declared, seen.last { it.step == ImportStep.Copy }.totalBytes)
        assertEquals(declared, seen.last { it.step == ImportStep.Verify }.doneBytes)
        val install = seen.last { it.step == ImportStep.Install }
        assertEquals(install.totalBytes, install.doneBytes)
    }
}
