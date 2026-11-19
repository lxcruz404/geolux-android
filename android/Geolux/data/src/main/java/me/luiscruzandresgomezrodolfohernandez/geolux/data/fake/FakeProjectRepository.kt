package me.luiscruzandresgomezrodolfohernandez.geolux.data.fake

import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Issue
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectSummary
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.summary
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportDecision
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportPreview
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportProgress
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportResult
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ProductPreview
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.data.TileSource

/**
 * Repositorio de mentira con la finca del fixture `finca-demo`, para que la UI
 * avance sin el lector real (06 §4). Se sustituye en AND-A8.
 *
 * [startImported] = false muestra el estado vacío. Si [nextInspect] no es
 * null, fija lo que devuelve [inspect], para ver cada pantalla de importación
 * (02a–02e); si es null, se comporta como el lector real con finca-demo.
 */
class FakeProjectRepository(
    startImported: Boolean = true,
    var nextInspect: ((Uri) -> ImportPreview)? = null,
) : ProjectRepository {

    private val imported = MutableStateFlow(if (startImported) listOf(FINCA_DEMO) else emptyList())

    override fun projects(): Flow<List<ProjectSummary>> = imported.map { list -> list.map { it.summary() } }

    override suspend fun project(id: String): ProjectData =
        imported.value.firstOrNull { it.project.id == id } ?: error("Finca no importada: $id")

    override suspend fun inspect(uri: Uri, onProgress: (ImportProgress) -> Unit): ImportPreview = nextInspect?.invoke(uri)
        ?: previewOf(FINCA_DEMO, if (imported.value.isEmpty()) ImportDecision.NEW else ImportDecision.ALREADY_IMPORTED)

    override suspend fun import(preview: ImportPreview.Ready, replace: Boolean, onProgress: (ImportProgress) -> Unit): ImportResult = when {
        preview.decision == ImportDecision.ALREADY_IMPORTED -> ImportResult.AlreadyImported
        preview.decision == ImportDecision.ASK_REPLACE && !replace -> ImportResult.Cancelled
        else -> {
            imported.value = listOf(FINCA_DEMO)
            ImportResult.Imported(FINCA_DEMO.project.id)
        }
    }

    override suspend fun deleteProject(projectId: String) {
        if (imported.value.none { it.project.id == projectId }) throw NoSuchElementException("Finca no importada: $projectId")
        imported.value = imported.value.filter { it.project.id != projectId }
    }

    override suspend fun orthoTiles(projectId: String, productId: String): TileSource? = null

    /**
     * El Fake no trae archivos de productos. Para ver el ortomosaico o el GLB de
     * finca-demo, usa el repositorio real e importa `finca-demo.geolux` (AND-A8).
     */
    override suspend fun productFile(projectId: String, productId: String): File? = null

    /** Ediciones en memoria, para previews y pruebas de la interfaz. */
    private val managementData = mutableMapOf<String, ManagementData>()

    override suspend fun management(projectId: String): ManagementData =
        managementData[projectId] ?: ManagementData(project(projectId).project.id)

    override suspend fun editManagement(projectId: String, change: (ManagementData) -> ManagementData): ManagementData =
        change(management(projectId)).also { managementData[projectId] = it }

    override suspend fun edit(projectId: String, change: (ProjectData) -> ProjectData): ProjectData {
        val current = project(projectId)
        val updated = change(current)
        imported.value = imported.value.map { if (it.project.id == projectId) updated else it }
        return updated
    }

    companion object {
        /** Vista previa de [data] como la daría el lector real. */
        fun previewOf(
            data: ProjectData,
            decision: ImportDecision,
            existingPackageCreatedAt: String? = null,
        ) = ImportPreview.Ready(
            fileName = "finca-demo.geolux",
            sizeBytes = 6196,
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
            warnings = if (data.products.any { it.isSynthetic }) {
                listOf(Issue("synthetic_product", "El paquete incluye productos sintéticos de prueba."))
            } else {
                emptyList()
            },
            decision = decision,
            existingPackageCreatedAt = existingPackageCreatedAt,
        )

        /** Datos de la finca de ejemplo, para previews de Compose. */
        val demo: ProjectData get() = FINCA_DEMO
    }
}
