package me.luiscruzandresgomezrodolfohernandez.geolux.data.management

import java.io.File
import java.time.Instant
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementOps
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementPackage

/**
 * La gestión de cada finca en Room. Primera vez que se pide una finca:
 * 1. si una versión anterior la guardó en `management/` (archivos), esa se pasa a Room tal cual
 *    y la carpeta se conserva como copia fuera de la finca;
 * 2. si no, se toma la del paquete importado.
 * La finca solo se marca como migrada después de comprobar que lo leído de Room es lo que se
 * escribió; si no coincide, Room queda vacío para esa finca y los archivos intactos.
 * Desde entonces Room es la única fuente; proyectos, capas y paquetes siguen en archivos.
 */
class ManagementRoomStore(
    private val dao: ManagementDao,
    /** La gestión que guardaba la versión anterior (archivos), o null. */
    private val legacy: (String) -> ManagementData?,
    /** La gestión del paquete instalado (sin revalidar), o null si la finca no está. */
    private val fromPackage: (String) -> ManagementData?,
    private val markMigrated: (String) -> Unit,
    private val now: () -> Instant = { Instant.now() },
) {
    /** Gestión de la finca, o null si la finca no está importada. */
    suspend fun load(projectId: String): ManagementData? {
        if (dao.state(projectId) != null) return read(projectId)
        val old = legacy(projectId)
        val initial = old ?: fromPackage(projectId) ?: return null
        dao.replaceProject(projectId, ManagementMapping.toRows(initial))
        val stored = read(projectId)
        if (!sameContent(stored, initial)) {
            dao.replaceProject(projectId, ManagementMapping.toRows(ManagementData(projectId)))
            error("La gestión pasada a Room no coincide con la original; se conservan los archivos.")
        }
        dao.setState(ManagementStateEntity(projectId, if (old != null) "archivos" else "paquete", now().toString()))
        if (old != null) markMigrated(projectId)
        return stored
    }

    private suspend fun read(projectId: String): ManagementData = ManagementMapping.toData(projectId, ManagementRows(
        dao.workers(projectId), dao.categories(projectId), dao.series(projectId), dao.activities(projectId), dao.links(projectId),
        dao.lists(projectId), dao.transactions(projectId), dao.items(projectId), dao.attachments(projectId), dao.lots(projectId),
        dao.animals(projectId), dao.events(projectId),
    ))

    /**
     * Guarda la gestión entera de la finca en una transacción (todo o nada). Cada operación ya
     * validó lo que cambió (`ManagementOps`); aquí no se revalida lo guardado antes.
     */
    suspend fun save(data: ManagementData) {
        dao.replaceProject(data.projectId, ManagementMapping.toRows(data))
    }

    /** Borra la gestión de la finca (al eliminar la finca del teléfono). Todo o nada. */
    suspend fun delete(projectId: String) = dao.deleteProject(projectId)

    /**
     * Antes de reinstalar la finca con [packageFile]: la gestión del teléfono fusionada con la del
     * paquete nuevo (gana lo más reciente; `ManagementOps.merge`), o null si la finca aún no está
     * en Room. Se calcula **antes** de tocar los archivos: si la fusión no es posible, la
     * reinstalación no se hace y todo queda como estaba.
     */
    suspend fun prepareReinstall(projectId: String, packageFile: File): ManagementData? {
        if (dao.state(projectId) == null) return null
        return ManagementOps().merge(ManagementPackage.read(packageFile, projectId), read(projectId))
    }

    companion object {
        /** Igual contenido, sin importar el orden de las filas. */
        fun sameContent(a: ManagementData, b: ManagementData): Boolean =
            a.projectId == b.projectId && listOf(
                a.workers to b.workers, a.financialCategories to b.financialCategories, a.activitySeries to b.activitySeries,
                a.activities to b.activities, a.activityLinks to b.activityLinks, a.purchaseLists to b.purchaseLists,
                a.transactions to b.transactions, a.purchaseItems to b.purchaseItems, a.attachments to b.attachments,
                a.livestockLots to b.livestockLots, a.animals to b.animals, a.animalEvents to b.animalEvents,
            ).all { (x, y) -> x.toSet() == y.toSet() && x.size == y.size }
    }
}
