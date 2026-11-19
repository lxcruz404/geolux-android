package me.luiscruzandresgomezrodolfohernandez.geolux.data

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import me.luiscruzandresgomezrodolfohernandez.geolux.core.edit.FarmEditor
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Política de importación (SPEC §8) e instalación atómica del almacén (AND-A5). */
class ProjectStoreTest {
    private val fixtures = File(System.getProperty("geolux.fixtures")!!)
    private val demo = File(fixtures, "finca-demo.geolux")
    private val root = Files.createTempDirectory("geolux-store").toFile()

    private fun read(file: File): ProjectData = GeoluxReader.read(file).data!!

    /** Otro paquete de la misma finca: solo cambian package_id y created_at (el manifiesto no lleva hash). */
    private fun otherPackage(packageId: String, createdAt: String): File {
        val out = File(root.parentFile, "otro-${System.nanoTime()}.geolux")
        ZipFile(demo).use { zip ->
            ZipOutputStream(out.outputStream()).use { zos ->
                for (entry in zip.entries()) {
                    var bytes = zip.getInputStream(entry).readBytes()
                    if (entry.name == "manifest.json") {
                        bytes = bytes.toString(Charsets.UTF_8)
                            .replace("6c1f1d3e-4f0a-4b8e-9d55-2f6a0c9b7e10", packageId)
                            .replace("\"created_at\": \"2026-10-09T12:00:00+00:00\"", "\"created_at\": \"$createdAt\"")
                            .toByteArray()
                    }
                    zos.putNextEntry(ZipEntry(entry.name))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }
        return out
    }

    @Test
    fun newPackageInstallsAndSamePackageIsAlreadyImported() {
        val store = ProjectStore(root)
        val data = read(demo)
        assertEquals(ImportDecision.NEW to null, store.decide(data))
        store.install(demo, data)
        assertEquals(listOf(data.project.id), store.list().map { it.project.id })
        assertEquals(ImportDecision.ALREADY_IMPORTED to data.packageCreatedAt, store.decide(data))
        // Productos extraídos para el mapa y el visor 3D (A6).
        assertTrue(File(store.dirFor(data.project.id), "products/ortho-demo/ortho.mbtiles").isFile)
        // Persiste: otra instancia lo encuentra en disco.
        assertEquals(data.packageId, ProjectStore(root).load(data.project.id)?.packageId)
    }

    @Test
    fun otherPackageOfSameProjectAsksAndReplaces() {
        val store = ProjectStore(root)
        val first = read(demo)
        store.install(demo, first)

        val older = otherPackage("11111111-2222-4333-8444-555555555555", "2026-05-15T12:00:00+00:00")
        val second = read(older)
        val (decision, existing) = store.decide(second)
        assertEquals(ImportDecision.ASK_REPLACE, decision)
        assertEquals(first.packageCreatedAt, existing)
        assertTrue(second.packageCreatedAt < existing!!) // la UI lo marca «más antiguo»

        store.install(older, second)
        assertEquals(listOf(second.packageId), store.list().map { it.packageId })
        assertEquals(1, root.listFiles()!!.size) // sin restos de carpetas temporales
    }

    @Test
    fun interruptedInstallKeepsPreviousProject() {
        val data = read(demo)
        ProjectStore(root).install(demo, data)
        // Simula una muerte de la app entre apartar la finca anterior e instalar la nueva.
        val target = ProjectStore(root).dirFor(data.project.id)
        assertTrue(target.renameTo(File(root, ".old-crash")))
        assertTrue(File(root, ".staging-crash").mkdirs())

        val recovered = ProjectStore(root)
        assertEquals(data.packageId, recovered.load(data.project.id)?.packageId)
        assertFalse(File(root, ".old-crash").exists())
        assertFalse(File(root, ".staging-crash").exists())
    }

    @Test
    fun productFilesResolveToExtractedFiles() {
        val store = ProjectStore(root)
        val data = read(demo)
        store.install(demo, data)
        val ortho = store.productFile(data.project.id, "ortho-demo")!!
        val model = store.productFile(data.project.id, "model-demo")!!
        assertEquals("ortho.mbtiles", ortho.name)
        assertEquals("model.glb", model.name)
        // Mismo contenido que dentro del paquete (el lector ya verificó su SHA-256).
        val zip = ZipFile(demo)
        assertTrue(zip.getInputStream(zip.getEntry("products/ortho-demo/ortho.mbtiles")).readBytes().contentEquals(ortho.readBytes()))
        zip.close()
        assertNull(store.productFile(data.project.id, "no-existe"))
        assertNull(store.productFile("no-existe", "ortho-demo"))
    }

    @Test
    fun mobileModelIsPreferredWhateverItsPosition() {
        val model = "products/m/model.glb"
        val mobile = "products/m/model_mobile.glb"
        assertEquals(mobile, fileToOpen(listOf(mobile, model)))
        assertEquals(mobile, fileToOpen(listOf(model, mobile)))
        assertEquals(model, fileToOpen(listOf(model)))
        assertEquals("products/o/ortho.mbtiles", fileToOpen(listOf("products/o/ortho.mbtiles")))
        assertNull(fileToOpen(emptyList()))
    }

    @Test
    fun spaceToInstallCountsThePackageCopyAndItsExtractedProducts() {
        val store = ProjectStore(root)
        val products = ZipFile(demo).use { zip ->
            zip.entries().asSequence().filter { it.name.startsWith("products/") }.sumOf { it.size }
        }
        assertTrue(products > 0)
        assertEquals(demo.length() + products, store.spaceToInstall(demo))

        // Coincide con lo que la instalación deja realmente en disco.
        val data = read(demo)
        store.install(demo, data)
        val installed = store.dirFor(data.project.id).walkTopDown().filter { it.isFile }.sumOf { it.length() }
        assertEquals(installed, store.spaceToInstall(demo))
    }

    @Test
    fun modelIsChosenByRoleWhateverTheOrderAndName() {
        val big = "products/m/a.glb"
        val small = "products/m/b.glb"
        val roles = mapOf(big to "model", small to "model_mobile")
        // El rol manda, aunque el modelo móvil vaya segundo y no se llame model_mobile.glb.
        assertEquals(small, fileToOpen(listOf(big, small), roles))
        assertEquals(small, fileToOpen(listOf(small, big), roles))
        // Sin modelo móvil: el del motor, y si tampoco, el de compatibilidad.
        assertEquals(big, fileToOpen(listOf(big), mapOf(big to "model")))
        assertEquals("products/m/c.glb", fileToOpen(listOf("products/m/x.bin", "products/m/c.glb"), mapOf("products/m/c.glb" to "model_compat")))
        // Roles que no son de modelo (informe, teselas): el primero de la lista.
        assertEquals("products/r/report.pdf", fileToOpen(listOf("products/r/report.pdf"), mapOf("products/r/report.pdf" to "document")))
    }

    @Test
    fun glxFixtureInstallsAndOpensTheMobileModelAndItsReport() {
        val glx = File(System.getProperty("geolux.glxFixtures")!!, "finca-demo-v1.glx")
        val store = ProjectStore(root)
        val data = read(glx)
        store.install(glx, data)
        val model = data.products.single { it.kind == "model3d" }
        assertEquals("model_mobile.glb", store.productFile(data.project.id, model.id)!!.name)
        assertEquals("report.pdf", store.productFile(data.project.id, data.products.single { it.kind == "report" }.id)!!.name)
        // Se reabre desde el almacén (otra instancia, sin volver a verificar hashes) con los mismos roles.
        val reopened = ProjectStore(root).load(data.project.id)!!
        assertEquals(model.fileRoles, reopened.products.single { it.kind == "model3d" }.fileRoles)
    }

    @Test
    fun failedReplacementKeepsThePreviousProjectAndLeavesNoTemporaryFolders() {
        val store = ProjectStore(root)
        val first = read(demo)
        store.install(demo, first)
        val other = otherPackage("11111111-2222-4333-8444-555555555555", "2026-11-01T12:00:00+00:00")
        val second = read(other)
        other.delete() // El paquete desaparece a mitad de la instalación: falla la copia.
        assertTrue(runCatching { store.install(other, second) }.isFailure)
        assertEquals(first.packageId, store.load(first.project.id)?.packageId)
        assertEquals(first.packageId, ProjectStore(root).load(first.project.id)?.packageId)
        assertEquals(listOf(store.dirFor(first.project.id).name), root.list()!!.toList())
    }

    @Test
    fun orthoTilesAreExtractedOnceIntoXyzFiles() {
        val store = ProjectStore(root)
        val data = read(demo)
        store.install(demo, data)
        var calls = 0
        // Extractor de prueba: el real usa el SQLite de Android (prueba instrumentada).
        val extractor = TileExtractor { mbtiles, into ->
            calls++
            assertTrue(mbtiles.isFile)
            File(into, "16/19988/${xyzRow(16, 33917)}.png").apply { parentFile!!.mkdirs() }.writeBytes(byteArrayOf(1))
        }
        val tiles = store.orthoTiles(data.project.id, "ortho-demo", extractor)!!
        val dir = File(store.dirFor(data.project.id), "tiles/ortho-demo")
        assertEquals("file://${dir.absolutePath}/{z}/{x}/{y}.png", tiles.urlTemplate)
        assertEquals(16, tiles.minZoom)
        assertEquals(16, tiles.maxZoom)
        assertTrue(File(dir, "16/19988/31618.png").isFile)
        // El MBTiles ya extraído se borra; la segunda vez no se vuelve a extraer.
        assertNull(store.productFile(data.project.id, "ortho-demo"))
        store.orthoTiles(data.project.id, "ortho-demo", extractor)
        assertEquals(1, calls)
        // Un modelo 3D no es un ortomosaico.
        assertNull(store.orthoTiles(data.project.id, "model-demo", extractor))
    }

    @Test
    fun xyzRowFlipsTmsRows() {
        assertEquals(31618, xyzRow(16, 33917))
        assertEquals(0, xyzRow(0, 0))
        assertEquals(3, xyzRow(2, 0))
    }

    @Test
    fun unknownProjectIsNull() {
        assertNull(ProjectStore(root).load("no-existe"))
    }

    @Test
    fun installReportsWhatItWritesUpToSpaceToInstall() {
        val store = ProjectStore(root)
        val written = mutableListOf<Long>()
        store.install(demo, read(demo)) { written += it }
        assertEquals(written.sorted(), written)
        assertEquals(store.spaceToInstall(demo), written.last())
    }

    @Test
    fun progressIsReportedEveryPercentNotEveryBlock() {
        val seen = mutableListOf<ImportProgress>()
        val report = throttled(ImportStep.Copy) { seen += it }
        val total = 100L * 1024 * 1024
        var done = 0L
        while (done < total) {
            done = minOf(total, done + 64 * 1024)
            report(done, total)
        }
        // 1600 bloques de 64 KB: como mucho un aviso por cada 1 %, y siempre el final.
        assertTrue(seen.size in 50..101)
        assertEquals(ImportProgress(ImportStep.Copy, total, total), seen.last())
        assertEquals(1f, seen.last().fraction)
        assertNull(ImportProgress(ImportStep.Copy, 10, 0).fraction)
    }

    /** Un potrero de ~50 m al norte de finca-demo, donde no choca con nada. */
    private val newZoneRing = listOf(Position(-70.2, 6.31), Position(-70.1995, 6.31), Position(-70.1995, 6.3105), Position(-70.2, 6.3105))

    @Test
    fun lasEdicionesSeGuardanYSobrevivenAlCerrarLaApp() {
        val store = ProjectStore(root)
        val data = read(demo)
        store.install(demo, data)
        assertFalse(store.hasEdits(data.project.id))
        var edited = FarmEditor.createZone(data, "Potrero nuevo", newZoneRing)
        edited = FarmEditor.createElement(edited, "Tanque 2", "tank", Position(-70.1985, 6.301))
        edited = FarmEditor.deleteLine(edited, data.lines.first().id)
        store.saveEdits(edited)
        assertTrue(store.hasEdits(data.project.id))

        // Otra instancia = la app vuelve a abrirse: lee el paquete y encima los cambios.
        val reopened = ProjectStore(root).load(data.project.id)!!
        assertEquals(edited.zones, reopened.zones)
        assertEquals(edited.elements, reopened.elements)
        assertEquals(edited.lines, reopened.lines)
        assertEquals(data.missions, reopened.missions)
        // El paquete importado no se toca.
        assertTrue(File(store.dirFor(data.project.id), ProjectStore.PACKAGE).readBytes().contentEquals(demo.readBytes()))
    }

    @Test
    fun unGuardadoInterrumpidoDejaLosCambiosAnterioresCompletos() {
        val store = ProjectStore(root)
        val data = read(demo)
        store.install(demo, data)
        val first = FarmEditor.createZone(data, "Primero", newZoneRing)
        store.saveEdits(first)
        // Simula una muerte entre apartar los cambios anteriores y poner los nuevos.
        val dir = store.dirFor(data.project.id)
        assertTrue(File(dir, "layers").renameTo(File(dir, "layers.old")))
        File(dir, "layers.tmp").mkdirs()
        File(dir, "layers.tmp/zones.geojson").writeText("a medias")

        val reopened = ProjectStore(root).load(data.project.id)!!
        assertEquals(first.zones, reopened.zones)
        assertFalse(File(dir, "layers.tmp").exists())
        assertFalse(File(dir, "layers.old").exists())
    }

    @Test
    fun reemplazarLaFincaDescartaLosCambiosDelTelefono() {
        val store = ProjectStore(root)
        val data = read(demo)
        store.install(demo, data)
        store.saveEdits(FarmEditor.createZone(data, "Se perderá", newZoneRing))
        val newer = otherPackage("11111111-2222-4333-8444-555555555555", "2026-11-01T12:00:00+00:00")
        store.install(newer, read(newer))
        assertFalse(store.hasEdits(data.project.id))
        assertEquals(data.zones, ProjectStore(root).load(data.project.id)!!.zones)
    }

    @Test
    fun unaCapaEditadaIlegibleNoImpideAbrirLaFinca() {
        val store = ProjectStore(root)
        val data = read(demo)
        store.install(demo, data)
        store.saveEdits(FarmEditor.createZone(data, "Nuevo", newZoneRing))
        File(store.dirFor(data.project.id), "layers/zones.geojson").writeText("{ roto")
        val reopened = ProjectStore(root).load(data.project.id)!!
        assertEquals(data.zones, reopened.zones) // se ve la del paquete
        assertTrue(File(store.dirFor(data.project.id), "layers/zones.geojson").isFile) // y no se borra nada
    }

    // -- Eliminar una finca (12 nov. 2026) ---------------------------------------------------

    private val glxDir = File(System.getProperty("geolux.glxFixtures")!!)

    /** Dos fincas distintas instaladas: la demo (ortomosaico, modelo 3D, informe) y otra con gestión. */
    private fun twoFarms(store: ProjectStore): Pair<ProjectData, ProjectData> {
        val demoGlx = File(glxDir, "finca-demo-v1.glx")
        val other = File(glxDir, "finca-ida-vuelta-android-v1.glx")
        val a = read(demoGlx).also { store.install(demoGlx, it) }
        val b = read(other).also { store.install(other, it) }
        return a to b
    }

    private fun snapshot(dir: File): Map<String, Long> =
        dir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dir).path to it.length() }

    @Test
    fun deletingAFarmRemovesAllItsFilesAndNothingOfTheOther() {
        val store = ProjectStore(root)
        val (demoFarm, other) = twoFarms(store)
        val otherDir = store.dirFor(other.project.id)
        val otherFiles = snapshot(otherDir)
        // Copias de gestión de cada finca en .respaldos-gestion: solo se van las de la eliminada.
        val backups = File(root, ProjectStore.BACKUPS).apply { mkdirs() }
        File(backups, store.dirFor(demoFarm.project.id).name + "-2026").mkdirs()
        File(backups, otherDir.name + "-2026").mkdirs()
        // Lo que prepara el teléfono también se va (modelo reducido).
        store.viewingModelFile(demoFarm.project.id, "m", 2048).apply { parentFile!!.mkdirs(); writeText("glb") }

        val trash = store.detach(demoFarm.project.id)!!
        assertEquals(listOf(other.project.id), store.list().map { it.project.id })
        store.purge(trash, demoFarm.project.id)

        assertFalse(store.dirFor(demoFarm.project.id).exists())
        assertFalse(trash.exists())
        assertEquals(listOf(otherDir.name + "-2026"), backups.list()!!.toList())
        assertEquals(otherFiles, snapshot(otherDir))
        // Otra instancia (la app cerrada y abierta) no la vuelve a encontrar.
        assertEquals(listOf(other.project.id), ProjectStore(root).list().map { it.project.id })
        assertNull(ProjectStore(root).load(demoFarm.project.id))
    }

    @Test
    fun aFailedDeletionPutsTheFarmBackIntact() {
        val store = ProjectStore(root)
        val (demoFarm, _) = twoFarms(store)
        val dir = store.dirFor(demoFarm.project.id)
        val before = snapshot(dir)
        val trash = store.detach(demoFarm.project.id)!!
        store.restore(trash, demoFarm.project.id) // p. ej. Room no pudo borrar la gestión
        assertEquals(before, snapshot(dir))
        assertEquals(demoFarm.packageId, store.load(demoFarm.project.id)?.packageId)
        assertEquals(2, ProjectStore(root).list().size)
    }

    @Test
    fun aDeletionInterruptedAfterConfirmingIsFinishedOnTheNextStart() {
        val store = ProjectStore(root)
        val (demoFarm, other) = twoFarms(store)
        store.detach(demoFarm.project.id)!! // la app muere antes de borrar los archivos
        val reopened = ProjectStore(root)
        assertEquals(listOf(other.project.id), reopened.list().map { it.project.id })
        assertEquals(listOf(reopened.dirFor(other.project.id).name), root.list()!!.filter { !it.startsWith(".respaldos") })
    }

    @Test
    fun theLastFarmCanBeDeletedAndUnknownFarmsAreNull() {
        val store = ProjectStore(root)
        val data = read(demo).also { store.install(demo, it) }
        store.purge(store.detach(data.project.id)!!, data.project.id)
        assertTrue(store.list().isEmpty())
        assertNull(store.detach(data.project.id))
        assertTrue(ProjectStore(root).list().isEmpty())
    }
}
