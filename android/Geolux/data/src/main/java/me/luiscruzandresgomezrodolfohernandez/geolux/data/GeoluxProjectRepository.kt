package me.luiscruzandresgomezrodolfohernandez.geolux.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.luiscruzandresgomezrodolfohernandez.geolux.data.management.ManagementDatabase
import me.luiscruzandresgomezrodolfohernandez.geolux.data.management.ManagementRoomStore
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.edit.PackageWriter
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model3d.GlbTextures
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Issue
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectSummary
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.summary

/**
 * [bytes] a escribir más la holgura de la propuesta de contrato §3: el 5 % y al
 * menos 100 MiB, para no dejar el teléfono sin espacio justo al terminar.
 */
internal fun withSpaceMargin(bytes: Long): Long = bytes + maxOf(bytes / 20, 100L * 1024 * 1024)

/**
 * Avisa a [onProgress] cada 1 % del total (y al terminar), no por cada bloque de
 * 64 KB: un paquete de 1 GB serían 16 000 recomposiciones.
 */
internal fun throttled(step: ImportStep, onProgress: (ImportProgress) -> Unit): (done: Long, total: Long) -> Unit {
    var last = -1L
    return { done, total ->
        val every = maxOf(total / 100, 256L * 1024)
        if (last < 0 || done - last >= every || (total > 0 && done >= total)) {
            last = done
            onProgress(ImportProgress(step, done, total))
        }
    }
}

/**
 * Repositorio real (AND-A5): lee el `.geolux` que el usuario elige con el
 * selector de archivos de Android (SAF), lo valida con [GeoluxReader] y lo
 * guarda en `filesDir/projects/` con [ProjectStore].
 *
 * [inspect] copia el archivo a la caché y lo valida sin importar nada; [import]
 * instala esa copia. Así el usuario ve la vista previa (02a) y decide antes.
 */
class GeoluxProjectRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val freeSpace: () -> Long = context.filesDir::getUsableSpace,
) : ProjectRepository {

    private val resolver = context.applicationContext.contentResolver
    private val appVersion = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"
    private val store = ProjectStore(File(context.filesDir, "projects"))
    /** Gestión de la finca en Room; los proyectos y paquetes siguen en [store]. */
    private val management by lazy {
        val db = ManagementDatabase.open(context)
        ManagementRoomStore(db.dao(), store::legacyManagement, store::packageManagement, store::markLegacyMigrated)
    }
    private val stagingDir = File(context.cacheDir, "import")
    private var stagingCleaned = false
    private val mutex = Mutex()

    /** Paquete inspeccionado y aún no importado (como mucho uno), por `package_id`. */
    private val staged = HashMap<String, Pair<File, ProjectData>>()
    private val summaries = MutableStateFlow<List<ProjectSummary>?>(null)

    override fun projects(): Flow<List<ProjectSummary>> = flow {
        if (summaries.value == null) refresh()
        emitAll(summaries.filterNotNull())
    }

    override suspend fun project(id: String): ProjectData = withContext(io) {
        mutex.withLock { store.load(id) } ?: throw NoSuchElementException("Finca no importada: $id")
    }

    override suspend fun inspect(uri: Uri, onProgress: (ImportProgress) -> Unit): ImportPreview = withContext(io) {
        val (fileName, sizeBytes) = nameAndSize(uri)
        val copy = File(stagingDir, "${UUID.randomUUID()}.geolux")
        // Solo hay una importación a la vez. Cancelar en 02a/02c/02d no llama a
        // import(), así que la copia pendiente anterior se borra aquí, antes de medir.
        mutex.withLock { dropStaged() }
        // Copias que dejó una sesión anterior; se borran aquí, fuera del hilo principal.
        if (!stagingCleaned) {
            stagingDir.deleteRecursively()
            stagingCleaned = true
        }

        // Antes de copiar nada: Desktop escribe manifest.json primero, así que se sabe
        // cuánto se va a extraer sin copiar el paquete (propuesta de contrato §2.3 y §3).
        val peek = try {
            resolver.openInputStream(uri)?.use(GeoluxReader::peekManifest)
        } catch (e: Exception) { // Si no se puede abrir, lo dirá la copia de abajo.
            null
        }
        val packageBytes = sizeBytes ?: peek?.declaredBytes
        if (packageBytes != null) {
            val alreadyImported = peek != null && mutex.withLock { store.load(peek.projectId)?.packageId } == peek.packageId
            // Copia en caché + copia instalada + productos extraídos; un paquete ya
            // importado solo se copia para comprobarlo. Sin manifiesto al principio, solo
            // se sabe que debe caber la copia: lo demás se mide después de validarla.
            val toWrite = if (peek == null || alreadyImported) packageBytes else 2 * packageBytes + peek.productBytes
            val needed = withSpaceMargin(toWrite)
            val available = freeSpace()
            if (available < needed) return@withContext ImportPreview.NoSpace(fileName, needed, available)
        }

        try {
            stagingDir.mkdirs()
            val input = resolver.openInputStream(uri) ?: throw IOException("Sin contenido")
            val copied = throttled(ImportStep.Copy, onProgress)
            copied(0, packageBytes ?: 0)
            input.use { inp -> copy.outputStream().use { out -> copyCounting(inp, out) { copied(it, packageBytes ?: 0) } } }
        } catch (e: Exception) { // IOException o SecurityException del proveedor del archivo.
            copy.delete()
            return@withContext ImportPreview.Rejected(fileName, READ_FAILED)
        }

        val report = GeoluxReader.read(copy, onVerified = throttled(ImportStep.Verify, onProgress))
        val data = report.data
        if (data == null) {
            copy.delete()
            return@withContext ImportPreview.Rejected(fileName, report.errors.first())
        }
        val (decision, existingCreatedAt) = mutex.withLock { store.decide(data) }
        val hasLocalEdits = decision == ImportDecision.ASK_REPLACE && mutex.withLock { store.hasEdits(data.project.id) }
        // Un paquete ya importado no se instala: no pide espacio.
        if (decision != ImportDecision.ALREADY_IMPORTED) {
            // Ahora con lo que trae de verdad. La copia ya está en disco: cuenta en lo
            // necesario y en lo disponible, igual que en el primer filtro.
            val copySize = copy.length()
            val needed = withSpaceMargin(copySize + store.spaceToInstall(copy))
            val free = freeSpace()
            if (free < needed - copySize) {
                copy.delete()
                return@withContext ImportPreview.NoSpace(fileName, needed, free + copySize)
            }
        }
        mutex.withLock {
            dropStaged()
            staged[data.packageId] = copy to data
        }
        ImportPreview.Ready(
            fileName = fileName,
            sizeBytes = sizeBytes ?: copy.length(),
            packageId = data.packageId,
            projectId = data.project.id,
            packageCreatedAt = data.packageCreatedAt,
            projectName = data.project.name,
            location = data.project.location,
            zoneCount = data.zones.size,
            elementCount = data.elements.size,
            lineCount = data.lines.size,
            missionCount = data.missions.size,
            products = data.products.map { ProductPreview(it.kind, it.name, it.isSynthetic) },
            warnings = report.warnings,
            decision = decision,
            existingPackageCreatedAt = existingCreatedAt,
            hasLocalEdits = hasLocalEdits,
        )
    }

    override suspend fun import(
        preview: ImportPreview.Ready,
        replace: Boolean,
        onProgress: (ImportProgress) -> Unit,
    ): ImportResult = withContext(io) {
        val (file, data) = mutex.withLock { staged.remove(preview.packageId) }
            ?: return@withContext ImportResult.Failed(READ_FAILED)
        try {
            when {
                preview.decision == ImportDecision.ALREADY_IMPORTED -> ImportResult.AlreadyImported
                preview.decision == ImportDecision.ASK_REPLACE && !replace -> ImportResult.Cancelled
                else -> {
                    val total = store.spaceToInstall(file)
                    val written = throttled(ImportStep.Install, onProgress)
                    written(0, total)
                    mutex.withLock {
                        val id = data.project.id
                        // Antes de reemplazar la carpeta: la gestión que aún estuviera en archivos
                        // pasa a Room y se calcula la fusión con el paquete nuevo. Si algo de esto
                        // falla, la finca anterior queda intacta y la importación se informa.
                        if (store.load(id) != null) management.load(id)
                        val merged = management.prepareReinstall(id, file)
                        store.install(file, data) { written(it, total) }
                        merged?.let { management.save(it) }
                    }
                    refresh()
                    ImportResult.Imported(data.project.id)
                }
            }
        } catch (e: IOException) {
            ImportResult.Failed(INSTALL_FAILED)
        } catch (e: IllegalArgumentException) {
            // La gestión del teléfono no se pudo fusionar con el paquete (p. ej. el mismo código
            // de trabajador creado en los dos equipos): no se instala nada.
            ImportResult.Failed(INSTALL_FAILED)
        } catch (e: IllegalStateException) {
            ImportResult.Failed(INSTALL_FAILED)
        } finally {
            file.delete()
        }
    }

    /**
     * Orden seguro: 1) se aparta la carpeta (un renombre; si falla no cambia nada);
     * 2) se borra la gestión de Room en una transacción (si falla, la carpeta vuelve y la
     * finca queda intacta); 3) se borran los archivos apartados (si la app muere aquí, el
     * siguiente arranque termina de borrarlos: ya no hay datos de la finca en ninguna parte).
     */
    override suspend fun deleteProject(projectId: String) = withContext(io) {
        mutex.withLock {
            val trash = store.detach(projectId) ?: throw NoSuchElementException("Finca no importada: $projectId")
            try {
                management.delete(projectId)
            } catch (e: Exception) {
                store.restore(trash, projectId)
                throw IOException("No se pudo eliminar la gestión de la finca; la finca sigue igual.", e)
            }
            store.purge(trash, projectId)
        }
        refresh()
    }

    override suspend fun orthoTiles(projectId: String, productId: String): TileSource? = withContext(io) {
        // Un MBTiles ilegible no debe cerrar la app: sin teselas, el mapa se ve sin ortomosaico.
        try {
            mutex.withLock { store.orthoTiles(projectId, productId, AndroidMbtilesExtractor) }
        } catch (e: IOException) {
            null
        } catch (e: android.database.SQLException) {
            null
        }
    }

    override suspend fun productFile(projectId: String, productId: String): File? = withContext(io) {
        mutex.withLock { store.productFile(projectId, productId) }
    }

    override suspend fun edit(projectId: String, change: (ProjectData) -> ProjectData): ProjectData = withContext(io) {
        val updated = mutex.withLock {
            val current = store.load(projectId) ?: throw NoSuchElementException("Finca no importada: $projectId")
            change(current).also(store::saveEdits)
        }
        refresh()
        updated
    }

    override suspend fun management(projectId: String): ManagementData = withContext(io) {
        mutex.withLock { management.load(projectId) ?: throw NoSuchElementException("Finca no importada: $projectId") }
    }

    override suspend fun editManagement(projectId: String, change: (ManagementData) -> ManagementData): ManagementData =
        withContext(io) {
            mutex.withLock {
                val current = management.load(projectId) ?: throw NoSuchElementException("Finca no importada: $projectId")
                change(current).also { management.save(it) }
            }
        }

    override suspend fun viewingModel(
        projectId: String,
        productId: String,
        maxTextureSize: Int,
        budgetBytes: Long,
        onProgress: (done: Int, total: Int) -> Unit,
    ): ViewingModel? = withContext(io) {
        val source = mutex.withLock { store.engineModelFile(projectId, productId) } ?: return@withContext null
        val images = GlbTextures.images(source)
        val largest = images.maxOfOrNull { maxOf(it.width, it.height) } ?: 0
        val cap = GlbTextures.chooseCap(images, maxTextureSize, budgetBytes)
        // Sin mejora sobre la copia de Desktop (texturas de 1024 px): se usa esa.
        if (cap <= 1024 && largest > 1024) return@withContext null
        val target = mutex.withLock { store.viewingModelFile(projectId, productId, cap) }
        if (!target.isFile) {
            target.parentFile!!.mkdirs()
            GlbTextures.rewrite(source, target, transform = { image, bytes -> TextureScaler.scale(image, bytes, cap) }, onProgress = onProgress)
        }
        ViewingModel(target, cap, largest)
    }

    override suspend fun exportPackage(projectId: String, destination: Uri) = withContext(io) {
        val (original, data, management) = mutex.withLock {
            val data = store.load(projectId) ?: throw NoSuchElementException("Finca no importada: $projectId")
            Triple(File(store.dirFor(projectId), ProjectStore.PACKAGE), data, management.load(projectId))
        }
        val out = resolver.openOutputStream(destination, "wt") ?: throw java.io.IOException("No se pudo abrir el archivo de destino.")
        out.use { PackageWriter.write(original, data, it, appVersion, management = management) }
        Unit
    }

    private fun dropStaged() {
        staged.values.forEach { it.first.delete() }
        staged.clear()
    }

    private suspend fun refresh() {
        summaries.value = withContext(io) { mutex.withLock { store.list().map { it.summary() } } }
    }

    private fun nameAndSize(uri: Uri): Pair<String, Long?> {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(c::getString)
                val size = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getLong)
                if (name != null) return name to size
            }
        }
        return (uri.lastPathSegment ?: "archivo.geolux") to null
    }

    private companion object {
        // Códigos propios de Android, fuera de SPEC §7: fallos del teléfono, no del paquete.
        val READ_FAILED = Issue("read_failed", "No se pudo leer el archivo. Vuelve a elegirlo.")
        val INSTALL_FAILED = Issue(
            "install_failed",
            "No se pudo guardar la finca en el teléfono. La que tenías sigue igual.",
        )
    }
}
