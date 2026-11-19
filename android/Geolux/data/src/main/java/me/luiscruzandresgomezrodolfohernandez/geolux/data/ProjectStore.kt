package me.luiscruzandresgomezrodolfohernandez.geolux.data

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import me.luiscruzandresgomezrodolfohernandez.geolux.core.edit.LayersJson
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementCodec
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementPackage
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData

/**
 * Fincas importadas, guardadas solo en archivos (06 §8): una carpeta por finca
 * en [root] con el paquete validado (`package.geolux`) y sus productos
 * extraídos (`products/…`) para que el mapa y el visor 3D los abran directo.
 *
 * Instalar es atómico (SPEC §8): la finca nueva se arma en una carpeta aparte y
 * se cambia por la anterior con renombres. Si la app muere a mitad, [recover]
 * deja la finca anterior intacta en el siguiente arranque.
 *
 * Lo que se edita en el teléfono (área de trabajo, potreros, elementos, líneas)
 * se guarda aparte, en `layers/`, con el formato de SPEC §5 ([saveEdits]); el
 * paquete importado no se modifica nunca.
 *
 * La gestión de la finca (actividades, compras, trabajadores, dinero, ganado) vive en Room
 * (`data/management/ManagementRoomStore`). Aquí solo quedan sus fuentes de lectura: la del
 * paquete ([packageManagement]) y la que guardaban versiones anteriores en `management/`
 * ([legacyManagement]), que se pasa a Room una vez y se conserva renombrada como copia.
 *
 * No es seguro entre hilos: [GeoluxProjectRepository] lo usa con un Mutex.
 */
class ProjectStore(private val root: File) {

    private val cache = LinkedHashMap<String, ProjectData>()
    private var loaded = false

    /** Fincas importadas, ordenadas por nombre. */
    fun list(): List<ProjectData> {
        ensureLoaded()
        return cache.values.sortedBy { it.project.name.lowercase() }
    }

    fun load(projectId: String): ProjectData? {
        ensureLoaded()
        return cache[projectId]
    }

    /** Archivo del producto (`.mbtiles` o `.glb`, ver [fileToOpen]) extraído al instalar, si existe. */
    fun productFile(projectId: String, productId: String): File? {
        val product = load(projectId)?.products?.firstOrNull { it.id == productId } ?: return null
        val path = fileToOpen(product.files, product.fileRoles) ?: return null
        return File(dirFor(projectId), path).takeIf { it.isFile }
    }

    /**
     * Teselas del ortomosaico [productId] como archivos XYZ. Se extraen del MBTiles la
     * primera vez que se piden (también sirve para fincas importadas antes de este
     * cambio): primero a una carpeta temporal y luego se renombra, así una
     * extracción interrumpida no deja teselas a medias. Después se borra el MBTiles
     * extraído; el `package.geolux` se conserva.
     */
    fun orthoTiles(projectId: String, productId: String, extractor: TileExtractor): TileSource? {
        val product = load(projectId)?.products
            ?.firstOrNull { it.id == productId && it.isOrthomosaic } ?: return null
        val base = dirFor(projectId)
        val dir = File(base, "$TILES/$productId")
        if (!dir.isDirectory) {
            // Las teselas por `role` (contrato .glx); sin roles, el primer archivo, como en v1.
            val tilesPath = product.files.firstOrNull { product.fileRoles[it] == TILES_ROLE } ?: product.files.firstOrNull()
            val mbtiles = tilesPath?.let { File(base, it) }?.takeIf { it.isFile } ?: return null
            val tmp = File(base, "$TILES/.tmp-$productId")
            tmp.deleteRecursively()
            tmp.mkdirs()
            extractor.extract(mbtiles, tmp)
            if (!tmp.renameTo(dir)) throw IOException("No se pudieron preparar las teselas.")
            mbtiles.delete()
        }
        val zooms = dir.list().orEmpty().mapNotNull { it.toIntOrNull() }
        if (zooms.isEmpty()) return null
        val extension = dir.walkTopDown().firstOrNull { it.isFile }?.extension ?: return null
        return TileSource("file://${dir.absolutePath}/{z}/{x}/{y}.$extension", zooms.min(), zooms.max())
    }

    /**
     * El modelo 3D del motor (`role: model`, o `model.glb` en paquetes sin roles), sin
     * reducir: de él se prepara la copia para ver en el teléfono. null si no viene.
     */
    fun engineModelFile(projectId: String, productId: String): File? {
        val product = load(projectId)?.products?.firstOrNull { it.id == productId && it.kind == "model3d" } ?: return null
        val path = product.files.firstOrNull { product.fileRoles[it] == "model" }
            ?: product.files.firstOrNull { it.substringAfterLast('/') == "model.glb" }
            ?: return null
        return File(dirFor(projectId), path).takeIf { it.isFile }
    }

    /** Copia del modelo con sus texturas a [cap] px, preparada en el teléfono (se reutiliza). */
    fun viewingModelFile(projectId: String, productId: String, cap: Int): File =
        File(dirFor(projectId), "$DERIVED/$productId-texturas-$cap.glb")

    /** Decisión de SPEC §8 para un paquete ya validado, con la fecha del importado si existe. */
    fun decide(incoming: ProjectData): Pair<ImportDecision, String?> {
        val existing = load(incoming.project.id) ?: return ImportDecision.NEW to null
        val decision = if (existing.packageId == incoming.packageId) {
            ImportDecision.ALREADY_IMPORTED
        } else {
            ImportDecision.ASK_REPLACE
        }
        return decision to existing.packageCreatedAt
    }

    /**
     * Bytes que [install] necesita libres: la copia del paquete y sus productos
     * extraídos. La finca anterior sigue en disco hasta el final, así que no
     * descuenta. Las teselas se extraen después, cuando ya no está la copia de la
     * caché, y ocupan menos que esa copia.
     */
    fun spaceToInstall(packageFile: File): Long = ZipFile(packageFile).use { zip ->
        packageFile.length() + zip.entries().asSequence().filter(::isExtracted).sumOf { it.size }
    }

    /**
     * Instala [packageFile] (ya validado como [data]); reemplaza la finca si existía.
     * [onWritten] recibe los bytes escritos hasta ahora, de [spaceToInstall] en total.
     */
    fun install(packageFile: File, data: ProjectData, onWritten: (Long) -> Unit = {}) {
        ensureLoaded()
        root.mkdirs()
        val staging = File(root, STAGING_PREFIX + UUID.randomUUID())
        try {
            staging.mkdirs()
            var written = 0L
            packageFile.inputStream().use { input ->
                File(staging, PACKAGE).outputStream().use { written += copyCounting(input, it) { n -> onWritten(written + n) } }
            }
            extractProducts(packageFile, staging) { n -> onWritten(written + n) }
            val target = dirFor(data.project.id)
            // La gestión hecha en el teléfono está en Room: el repositorio la fusiona con el
            // paquete nuevo después de instalar. Aquí no se toca.
            val old = File(root, OLD_PREFIX + UUID.randomUUID())
            if (target.exists() && !target.renameTo(old)) throw IOException("No se pudo apartar la finca anterior.")
            if (!staging.renameTo(target)) {
                old.renameTo(target)
                throw IOException("No se pudo instalar la finca.")
            }
            old.deleteRecursively()
            cache[data.project.id] = data
        } finally {
            staging.deleteRecursively()
        }
    }

    /** La gestión que trae el paquete instalado de la finca; null si la finca no está. */
    fun packageManagement(projectId: String): ManagementData? {
        ensureLoaded()
        if (projectId !in cache) return null
        // Lo ya instalado se pasa a Room tal cual, aunque una regla nueva sea más estricta.
        return ManagementPackage.read(File(dirFor(projectId), PACKAGE), projectId, validate = false)
    }

    /**
     * La gestión que guardaba en `management/` la versión anterior a Room; null si no hay.
     * Completa un guardado interrumpido de entonces (nunca mezcla dos versiones).
     */
    fun legacyManagement(projectId: String): ManagementData? {
        ensureLoaded()
        if (projectId !in cache) return null
        val base = dirFor(projectId)
        val dir = File(base, MANAGEMENT)
        val old = File(base, MANAGEMENT + OLD_SUFFIX)
        if (!dir.exists() && old.isDirectory) old.renameTo(dir)
        return if (dir.isDirectory) readManagementDir(dir, projectId) else null
    }

    /**
     * Tras pasar `management/` a Room se conserva como copia **fuera** de la carpeta de la
     * finca (`.respaldos-gestion/<finca>-<fecha>`), para que una reinstalación no la borre.
     * Nunca se borra sola.
     */
    fun markLegacyMigrated(projectId: String, stamp: String = java.time.LocalDateTime.now().toString().replace(":", "")) {
        val dir = File(dirFor(projectId), MANAGEMENT)
        if (!dir.isDirectory) return
        val backups = File(root, BACKUPS).apply { mkdirs() }
        if (!dir.renameTo(File(backups, "${dirFor(projectId).name}-$stamp"))) throw IOException("No se pudo guardar la copia de la gestión.")
    }

    /** Copias de la gestión que guardaba la versión anterior, de la más nueva a la más vieja. */
    fun legacyBackups(projectId: String): List<File> =
        File(root, BACKUPS).listFiles { f -> f.name.startsWith(dirFor(projectId).name + "-") }.orEmpty().sortedByDescending { it.name }

    private fun readManagementDir(dir: File, projectId: String): ManagementData =
        ManagementCodec.decode(projectId, ManagementCodec.DATASETS.mapNotNull { name ->
            File(dir, "$name.json").takeIf { it.isFile }?.let { name to it.readText() }
        }.toMap())

    /**
     * Guarda las capas editables de [data] (finca ya instalada). Atómico: se escriben
     * en una carpeta aparte y se cambian por las anteriores con renombres; si la app
     * muere a mitad, [recover] deja las anteriores o las nuevas completas, nunca mezcla.
     */
    fun saveEdits(data: ProjectData) {
        ensureLoaded()
        val base = dirFor(data.project.id)
        if (!File(base, PACKAGE).isFile) throw IOException("La finca no está instalada.")
        val target = File(base, EDITS)
        val staging = File(base, EDITS + STAGING_SUFFIX)
        val old = File(base, EDITS + OLD_SUFFIX)
        staging.deleteRecursively()
        staging.mkdirs()
        LayersJson.write(data).forEach { (dataset, text) -> File(staging, "$dataset.geojson").writeText(text) }
        old.deleteRecursively()
        if (target.exists() && !target.renameTo(old)) throw IOException("No se pudieron apartar los cambios anteriores.")
        if (!staging.renameTo(target)) {
            old.renameTo(target)
            throw IOException("No se pudieron guardar los cambios.")
        }
        old.deleteRecursively()
        cache[data.project.id] = data
    }

    /**
     * Primer paso de eliminar una finca: su carpeta se aparta con un solo renombre a
     * `.deleting-…` (deja de aparecer en la lista y [recover] la termina de borrar si la app
     * muere después). Devuelve la carpeta apartada, o null si la finca no está. Si el renombre
     * falla no cambia nada.
     */
    fun detach(projectId: String): File? {
        ensureLoaded()
        if (projectId !in cache) return null
        val dir = dirFor(projectId)
        val trash = File(root, DELETING_PREFIX + UUID.randomUUID())
        if (!dir.renameTo(trash)) throw IOException("No se pudo apartar la finca para eliminarla.")
        cache.remove(projectId)
        return trash
    }

    /** Deshace [detach] (cuando borrar la gestión de Room falló): la finca vuelve intacta. */
    fun restore(trash: File, projectId: String) {
        if (!trash.renameTo(dirFor(projectId))) throw IOException("No se pudo devolver la finca a su sitio.")
        GeoluxReader.read(File(dirFor(projectId), PACKAGE), verifyIntegrity = false).data?.let {
            cache[projectId] = withEdits(dirFor(projectId), it)
        }
    }

    /**
     * Último paso: borra la carpeta apartada (paquete, productos —ortomosaicos, modelos 3D,
     * informes, fotos—, teselas, modelos preparados, capas editadas) y las copias de gestión de
     * esa finca en `.respaldos-gestion`. Solo lo de esa finca: todo lo suyo está en su carpeta.
     */
    fun purge(trash: File, projectId: String) {
        legacyBackups(projectId).forEach { it.deleteRecursively() }
        trash.deleteRecursively()
    }

    /** Si la finca tiene cambios hechos en el teléfono (se perderían al reemplazarla). */
    fun hasEdits(projectId: String): Boolean = File(dirFor(projectId), EDITS).isDirectory


    /** Carpeta de la finca: hash del id, para que ningún id del paquete escape de [root]. */
    fun dirFor(projectId: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(projectId.toByteArray())
        return File(root, hash.joinToString("") { "%02x".format(it) })
    }

    private fun extractProducts(packageFile: File, into: File, onWritten: (Long) -> Unit) {
        var written = 0L
        ZipFile(packageFile).use { zip ->
            // Los nombres ya pasaron las comprobaciones de rutas seguras del lector.
            for (entry in zip.entries()) {
                if (!isExtracted(entry)) continue
                val out = File(into, entry.name)
                out.parentFile!!.mkdirs()
                zip.getInputStream(entry).use { input -> out.outputStream().use { written += copyCounting(input, it) { n -> onWritten(written + n) } } }
            }
        }
    }

    private fun isExtracted(entry: ZipEntry) = !entry.isDirectory && entry.name.startsWith("products/")

    private fun ensureLoaded() {
        if (loaded) return
        recover()
        root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty().forEach { dir ->
            val report = GeoluxReader.read(File(dir, PACKAGE), verifyIntegrity = false)
            report.data?.let { cache[it.project.id] = withEdits(dir, it) }
        }
        loaded = true
    }

    /** La finca del paquete con las capas editadas en el teléfono encima, si las hay. */
    private fun withEdits(dir: File, data: ProjectData): ProjectData {
        val edits = File(dir, EDITS)
        val old = File(dir, EDITS + OLD_SUFFIX)
        // Murió entre apartar los cambios anteriores y poner los nuevos: vuelven los anteriores.
        if (!edits.exists() && old.isDirectory) old.renameTo(edits)
        File(dir, EDITS + STAGING_SUFFIX).deleteRecursively()
        old.deleteRecursively()
        if (!edits.isDirectory) return data
        val texts = LayersJson.EDITABLE.mapNotNull { ds -> File(edits, "$ds.geojson").takeIf { it.isFile }?.let { ds to it.readText() } }.toMap()
        // Una capa editada ilegible no debe dejar la finca sin abrir: se muestra la del
        // paquete y los archivos se conservan tal cual (no se borra nada).
        return runCatching { GeoluxReader.readEditedLayers(texts, data) }.getOrDefault(data)
    }

    /** Limpia lo que dejó una instalación interrumpida, sin perder nunca la finca anterior. */
    private fun recover() {
        root.listFiles().orEmpty().forEach { dir ->
            when {
                dir.name.startsWith(STAGING_PREFIX) -> dir.deleteRecursively()
                // Una eliminación confirmada que no terminó de borrar los archivos.
                dir.name.startsWith(DELETING_PREFIX) -> dir.deleteRecursively()
                dir.name.startsWith(OLD_PREFIX) -> {
                    val id = GeoluxReader.read(File(dir, PACKAGE), verifyIntegrity = false).data?.project?.id
                    // Murió entre apartar la anterior e instalar la nueva: se restaura.
                    if (id != null && !dirFor(id).exists()) dir.renameTo(dirFor(id)) else dir.deleteRecursively()
                }
            }
        }
    }

    companion object {
        const val PACKAGE = "package.geolux"
        /** Capas editadas en el teléfono, dentro de la carpeta de la finca. */
        const val EDITS = "layers"
        /** Gestión de la finca editada en el teléfono (management/<conjunto>.json). */
        const val MANAGEMENT = "management"
        /** Copias de `management/` tras pasarla a Room, fuera de las fincas (nunca se borran solas). */
        const val BACKUPS = ".respaldos-gestion"
        /** Lo que el teléfono prepara a partir de los productos (modelo 3D para ver). */
        private const val DERIVED = "derived"
        private const val STAGING_SUFFIX = ".tmp"
        private const val OLD_SUFFIX = ".old"
        private const val TILES = "tiles"
        private const val TILES_ROLE = "tiles"
        private const val STAGING_PREFIX = ".staging-"
        private const val OLD_PREFIX = ".old-"
        private const val DELETING_PREFIX = ".deleting-"
    }
}

/** Papeles de un modelo 3D, del más liviano al más pesado (propuesta de contrato §2.6). */
private val MODEL_ROLES = listOf("model_mobile", "model", "model_compat")

/**
 * Archivo que se abre de un producto. Un modelo 3D puede traer `model_mobile.glb`
 * junto a un `model.glb` del motor varias veces más pesado (151 MB cerraban la app):
 * 1. Por el `role` del manifiesto: `model_mobile`, si no `model`, si no `model_compat`.
 * 2. Paquetes sin `role`: un archivo llamado `model_mobile.glb`.
 * 3. Si no, el primero de la lista (regla de los paquetes v1 antiguos).
 */
internal fun fileToOpen(files: List<String>, roles: Map<String, String> = emptyMap()): String? =
    MODEL_ROLES.firstNotNullOfOrNull { role -> files.firstOrNull { roles[it] == role } }
        ?: files.firstOrNull { it.substringAfterLast('/') == "model_mobile.glb" }
        ?: files.firstOrNull()

/** Copia [input] en [output] avisando con los bytes copiados hasta ahora; devuelve el total. */
internal fun copyCounting(input: InputStream, output: OutputStream, onCopied: (Long) -> Unit): Long {
    val buffer = ByteArray(64 * 1024)
    var copied = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        output.write(buffer, 0, read)
        copied += read
        onCopied(copied)
    }
    return copied
}
