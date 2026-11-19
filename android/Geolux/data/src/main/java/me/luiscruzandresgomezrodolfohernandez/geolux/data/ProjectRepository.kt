package me.luiscruzandresgomezrodolfohernandez.geolux.data

import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementData
import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.Flow
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Issue
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectSummary

/**
 * Frontera entre datos (Andrés) y UI (Rodolfo), 06 §4. Solo la cambia Andrés.
 * La UI no lee archivos ni el ZIP: todo pasa por aquí.
 */
interface ProjectRepository {
    /** Fincas importadas en el teléfono; se vuelve a emitir tras cada importación. */
    fun projects(): Flow<List<ProjectSummary>>

    /** Datos completos de una finca importada. */
    suspend fun project(id: String): ProjectData

    /**
     * Valida el paquete elegido y decide qué hacer con él (SPEC §7–8). No importa nada.
     * [onProgress] recibe el avance de la copia y de la comprobación (se llama desde E/S).
     */
    suspend fun inspect(uri: Uri, onProgress: (ImportProgress) -> Unit = {}): ImportPreview

    /**
     * Importa un paquete ya inspeccionado. Con [ImportDecision.ASK_REPLACE]
     * solo reemplaza si [replace] es true (respuesta explícita del usuario).
     * Atómico: o queda el paquete nuevo completo o el anterior intacto.
     */
    suspend fun import(
        preview: ImportPreview.Ready,
        replace: Boolean,
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportResult

    /**
     * Teselas del ortomosaico [productId] para una fuente ráster de MapLibre, o
     * null si la finca no lo trae. La primera llamada puede tardar: extrae las
     * teselas del MBTiles (MapLibre no abre `mbtiles://`, AND-R4).
     */
    suspend fun orthoTiles(projectId: String, productId: String): TileSource?

    /**
     * Archivo local de un producto de la finca, listo para abrir: el `.glb` de un
     * modelo 3D (SceneView), el liviano `model_mobile.glb` si el paquete lo trae,
     * o el PDF de un informe. Para un ortomosaico usa [orthoTiles].
     * Null si la finca no trae ese producto o el archivo no está.
     * Los metadatos (bounds, origen, `originPoint`, unidades) están en `Product`.
     */
    suspend fun productFile(projectId: String, productId: String): File?

    /**
     * Aplica [change] (normalmente una operación de `FarmEditor`) a la finca y lo guarda
     * en el teléfono; devuelve la finca nueva. Si [change] lanza (`EditException`, con
     * el mensaje para el usuario), no se guarda nada.
     */
    suspend fun edit(projectId: String, change: (ProjectData) -> ProjectData): ProjectData

    /** La gestión de la finca (actividades, compras, trabajadores, dinero). */
    suspend fun management(projectId: String): ManagementData =
        ManagementData(projectId)

    /**
     * Aplica [change] (una operación de `ManagementOps`) a la gestión y la guarda; si
     * [change] lanza (`ValidationException`, con el mensaje para el usuario), no se guarda nada.
     */
    suspend fun editManagement(projectId: String, change: (ManagementData) -> ManagementData): ManagementData =
        throw UnsupportedOperationException("Este repositorio no guarda gestión.")

    /**
     * Elimina la finca del teléfono con todo lo suyo: paquete, productos (ortomosaicos,
     * modelos 3D, informes, fotos), teselas, capas editadas y su gestión y ganado en Room.
     * No toca otras fincas. Si algo falla antes de borrar, la finca queda como estaba.
     */
    suspend fun deleteProject(projectId: String) {
        throw UnsupportedOperationException("Este repositorio no elimina fincas.")
    }

    /**
     * Exporta la finca, con lo editado en el teléfono, como paquete `.glx` v1 en
     * [destination] (un archivo elegido con el selector del sistema). Lo abre Desktop.
     */
    suspend fun exportPackage(projectId: String, destination: Uri) {
        throw UnsupportedOperationException("Este repositorio no exporta paquetes.")
    }

    /**
     * El modelo 3D para ver con la mejor calidad que admite el teléfono: el del motor
     * con cada textura reducida solo hasta lo que dibuja el GPU ([maxTextureSize]) y
     * cabe en [budgetBytes] de memoria. Se prepara la primera vez ([onProgress]: texturas
     * hechas / total) y se reutiliza. null si la finca no trae el modelo del motor o no
     * mejora la copia de Desktop (1024 px): entonces vale [productFile].
     */
    suspend fun viewingModel(
        projectId: String,
        productId: String,
        maxTextureSize: Int,
        budgetBytes: Long,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ViewingModel? = null
}

/** Igual que `import_decision` del lector de Python (SPEC §8). */
enum class ImportDecision {
    /** La finca no está en el teléfono. */
    NEW,

    /** Es el mismo paquete ya importado: no se hace nada. */
    ALREADY_IMPORTED,

    /** Otro paquete de la misma finca: preguntar Reemplazar / Cancelar. */
    ASK_REPLACE,
}

sealed interface ImportPreview {
    /** El paquete es válido y se puede importar (o ya está importado). */
    data class Ready(
        val fileName: String,
        val sizeBytes: Long,
        val packageId: String,
        /** Id de la finca (`project.id`): con él se abre tras importar o si ya estaba importada. */
        val projectId: String,
        val packageCreatedAt: String,
        val projectName: String,
        val location: String,
        val zoneCount: Int,
        val elementCount: Int,
        val lineCount: Int,
        val missionCount: Int,
        val products: List<ProductPreview>,
        val warnings: List<Issue>,
        val decision: ImportDecision,
        /** Fecha del paquete ya importado de esa finca, si lo hay. */
        val existingPackageCreatedAt: String?,
        /** La finca ya importada tiene cambios hechos en el teléfono: reemplazarla los pierde. */
        val hasLocalEdits: Boolean = false,
    ) : ImportPreview {
        /** El paquete nuevo es más antiguo que el importado (SPEC §8.3). Desktop exporta las fechas en UTC, así que compararlas como texto basta. */
        val isOlderThanExisting: Boolean
            get() = existingPackageCreatedAt != null && packageCreatedAt < existingPackageCreatedAt
    }

    /** El paquete se rechaza; [error] trae el código y el mensaje de SPEC §7. */
    data class Rejected(val fileName: String, val error: Issue) : ImportPreview

    /** No cabe en el teléfono: hacen falta al menos [neededBytes] libres y hay [availableBytes]. */
    data class NoSpace(val fileName: String, val neededBytes: Long, val availableBytes: Long) : ImportPreview
}

/** Paso lento de la importación, en el orden en que ocurren. */
enum class ImportStep {
    /** Copiar el archivo elegido a la caché de la app. */
    Copy,

    /** Comprobar tamaño y SHA-256 de cada archivo del paquete. */
    Verify,

    /** Guardar el paquete y extraer sus productos. */
    Install,
}

/** Avance de un paso: [doneBytes] de [totalBytes]; [totalBytes] = 0 si no se conoce. */
data class ImportProgress(val step: ImportStep, val doneBytes: Long, val totalBytes: Long) {
    /** Entre 0 y 1, o null si no se conoce el total. */
    val fraction: Float? get() = if (totalBytes > 0) (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

data class ProductPreview(val kind: String, val name: String, val isSynthetic: Boolean)

/** Modelo 3D preparado para ver: su archivo, el tamaño máximo de textura usado y el de la mayor original. */
data class ViewingModel(val file: File, val textureCap: Int, val largestTexture: Int)

sealed interface ImportResult {
    data class Imported(val projectId: String) : ImportResult
    data object AlreadyImported : ImportResult
    data object Cancelled : ImportResult
    data class Failed(val error: Issue) : ImportResult
}
