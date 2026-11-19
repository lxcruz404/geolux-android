package me.luiscruzandresgomezrodolfohernandez.geolux.data.management

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

/*
 * Gestión de la finca en SQLite con Room (pedido de Luis, 11 nov. 2026): actividades,
 * repeticiones, trabajadores, compras, dinero, adjuntos (solo referencias: los archivos viven
 * en la carpeta de la finca) y ganadería. Cada fila lleva su `projectId`: nada se mezcla
 * entre fincas. Las reglas de negocio no están aquí sino en `ManagementOps` (:core), las
 * mismas que Desktop; Room solo guarda. Los proyectos, sus capas y los `.glx` siguen en
 * archivos (`ProjectStore`), sin duplicarse aquí.
 *
 * Clave primaria (projectId, id): dos fincas nunca chocan. Sin claves foráneas inmediatas:
 * las referencias las cuidan las reglas (como en Desktop) y una referencia a algo que ya no
 * está nunca debe dejar ilegible la gestión de una finca.
 */

@Entity(primaryKeys = ["projectId", "id"], tableName = "workers", indices = [Index("projectId"), Index("projectId", "codeKey")])
data class WorkerEntity(
    val id: String, val projectId: String, val code: String, val codeKey: String, val fullName: String,
    val role: String, val startDate: String, val phone: String, val status: String, val notes: String,
    val createdAt: String, val updatedAt: String, val deletedAt: String?,
)

@Entity(primaryKeys = ["projectId", "id"], tableName = "activity_series", indices = [Index("projectId")])
data class ActivitySeriesEntity(
    val id: String, val projectId: String, val frequency: String, val startDate: String, val templateJson: String,
    @ColumnInfo(name = "repeatInterval") val interval: Int, val weekdays: String, val untilDate: String?, val count: Int?,
    val createdAt: String, val updatedAt: String, val deletedAt: String?,
)

@Entity(
    primaryKeys = ["projectId", "id"],
    tableName = "activities",
    indices = [Index("projectId", "scheduledDate"), Index(value = ["seriesId", "occurrenceDate"])],
)
data class ActivityEntity(
    val id: String, val projectId: String, val title: String, val category: String, val timeZone: String,
    val description: String, val status: String, val priority: String, val scheduledDate: String?, val scheduledTime: String?,
    val remindMinutesBefore: Int?, val assigneeWorkerId: String?, val seriesId: String?, val occurrenceDate: String?,
    val completedAt: String?, val notes: String, val createdAt: String, val updatedAt: String, val deletedAt: String?,
)

@Entity(
    primaryKeys = ["projectId", "id"],
    tableName = "activity_links",
    indices = [Index("activityId"), Index("entity", "entityId"), Index("projectId")],
)
data class ActivityLinkEntity(
    val id: String, val projectId: String, val activityId: String, val entity: String, val entityId: String, val createdAt: String,
)

@Entity(primaryKeys = ["projectId", "id"], tableName = "purchase_lists", indices = [Index("projectId")])
data class PurchaseListEntity(
    val id: String, val projectId: String, val name: String, val listType: String, val description: String,
    val plannedDate: String?, val responsibleWorkerId: String?, val status: String, val notes: String,
    val createdAt: String, val updatedAt: String, val deletedAt: String?,
)

@Entity(
    primaryKeys = ["projectId", "id"],
    tableName = "purchase_items",
    indices = [Index("listId"), Index("projectId")],
)
data class PurchaseItemEntity(
    val id: String, val projectId: String, val listId: String, val name: String, val quantity: String, val unit: String,
    val category: String, val description: String, val estimatedPrice: String?, val actualPrice: String?, val status: String,
    val transactionId: String?, val notes: String, val position: Int, val createdAt: String, val updatedAt: String, val deletedAt: String?,
)

@Entity(primaryKeys = ["projectId", "id"], tableName = "financial_categories", indices = [Index(value = ["projectId", "key"], unique = true)])
data class FinancialCategoryEntity(
    val id: String, val projectId: String, val key: String, val label: String, val archived: Int,
    val createdAt: String, val updatedAt: String,
)

/** Importes como texto decimal exacto, nunca coma flotante (SPEC §1). */
@Entity(primaryKeys = ["projectId", "id"], tableName = "financial_transactions", indices = [Index("projectId", "date"), Index("voidsTransactionId")])
data class FinancialTransactionEntity(
    val id: String, val projectId: String, val type: String, val direction: String, val date: String, val concept: String,
    val category: String, val amount: String, val currency: String, val paymentMethod: String, val counterparty: String,
    val responsible: String, val reference: String, val workerId: String?, val activityId: String?, val purchaseListId: String?,
    val voidsTransactionId: String?, val notes: String, val createdAt: String,
)

/** Solo la referencia al archivo (ruta relativa a la carpeta de la finca y su SHA-256). */
@Entity(primaryKeys = ["projectId", "id"], tableName = "attachments", indices = [Index("ownerEntity", "ownerId"), Index("projectId")])
data class AttachmentEntity(
    val id: String, val projectId: String, val ownerEntity: String, val ownerId: String, val path: String,
    val mediaType: String, val sizeBytes: Long, val sha256: String, val originalName: String, val createdAt: String, val deletedAt: String?,
)

@Entity(primaryKeys = ["projectId", "id"], tableName = "livestock_lots", indices = [Index("projectId")])
data class LivestockLotEntity(
    val id: String, val projectId: String, val zoneId: String?, val category: String, val count: Int, val lotDate: String,
    val observations: String, val createdAt: String, val updatedAt: String, val mode: String,
)

@Entity(primaryKeys = ["projectId", "id"], tableName = "animals", indices = [Index("projectId"), Index("projectId", "tag")])
data class AnimalEntity(
    val id: String, val projectId: String, val tag: String, val name: String, val category: String, val sex: String,
    val breed: String, val birthDate: String, val notes: String, val status: String, val lotId: String?, val createdAt: String,
    val updatedAt: String, val origin: String, val sireId: String?, val damId: String?, val birthDateEstimated: Int,
    val productionClass: String, val photoPath: String,
)

@Entity(
    primaryKeys = ["projectId", "id"],
    tableName = "animal_events",
    indices = [Index("animalId"), Index("projectId")],
)
data class AnimalEventEntity(
    val id: String, val projectId: String, val animalId: String, val eventType: String, val eventDate: String,
    val lotId: String?, val voidsEventId: String?, val detailsJson: String, val note: String, val createdAt: String, val sequence: Int,
)

/** De dónde salió la gestión de cada finca y cuándo se pasó a Room (auditoría de la migración). */
@Entity(tableName = "management_state")
data class ManagementStateEntity(@PrimaryKey val projectId: String, val source: String, val migratedAt: String)

@Dao
interface ManagementDao {
    @Query("SELECT * FROM management_state WHERE projectId = :projectId") suspend fun state(projectId: String): ManagementStateEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setState(state: ManagementStateEntity)

    @Query("SELECT * FROM workers WHERE projectId = :p ORDER BY rowid") suspend fun workers(p: String): List<WorkerEntity>
    @Query("SELECT * FROM financial_categories WHERE projectId = :p ORDER BY rowid") suspend fun categories(p: String): List<FinancialCategoryEntity>
    @Query("SELECT * FROM activity_series WHERE projectId = :p ORDER BY rowid") suspend fun series(p: String): List<ActivitySeriesEntity>
    @Query("SELECT * FROM activities WHERE projectId = :p ORDER BY rowid") suspend fun activities(p: String): List<ActivityEntity>
    @Query("SELECT * FROM activity_links WHERE projectId = :p ORDER BY rowid") suspend fun links(p: String): List<ActivityLinkEntity>
    @Query("SELECT * FROM purchase_lists WHERE projectId = :p ORDER BY rowid") suspend fun lists(p: String): List<PurchaseListEntity>
    @Query("SELECT * FROM financial_transactions WHERE projectId = :p ORDER BY rowid") suspend fun transactions(p: String): List<FinancialTransactionEntity>
    @Query("SELECT * FROM purchase_items WHERE projectId = :p ORDER BY rowid") suspend fun items(p: String): List<PurchaseItemEntity>
    @Query("SELECT * FROM attachments WHERE projectId = :p ORDER BY rowid") suspend fun attachments(p: String): List<AttachmentEntity>
    @Query("SELECT * FROM livestock_lots WHERE projectId = :p ORDER BY rowid") suspend fun lots(p: String): List<LivestockLotEntity>
    @Query("SELECT * FROM animals WHERE projectId = :p ORDER BY rowid") suspend fun animals(p: String): List<AnimalEntity>
    @Query("SELECT * FROM animal_events WHERE projectId = :p ORDER BY rowid") suspend fun events(p: String): List<AnimalEventEntity>

    @Insert suspend fun insertWorkers(rows: List<WorkerEntity>)
    @Insert suspend fun insertCategories(rows: List<FinancialCategoryEntity>)
    @Insert suspend fun insertSeries(rows: List<ActivitySeriesEntity>)
    @Insert suspend fun insertActivities(rows: List<ActivityEntity>)
    @Insert suspend fun insertLinks(rows: List<ActivityLinkEntity>)
    @Insert suspend fun insertLists(rows: List<PurchaseListEntity>)
    @Insert suspend fun insertTransactions(rows: List<FinancialTransactionEntity>)
    @Insert suspend fun insertItems(rows: List<PurchaseItemEntity>)
    @Insert suspend fun insertAttachments(rows: List<AttachmentEntity>)
    @Insert suspend fun insertLots(rows: List<LivestockLotEntity>)
    @Insert suspend fun insertAnimals(rows: List<AnimalEntity>)
    @Insert suspend fun insertEvents(rows: List<AnimalEventEntity>)

    @Query("DELETE FROM activity_links WHERE projectId = :p") suspend fun deleteLinks(p: String)
    @Query("DELETE FROM purchase_items WHERE projectId = :p") suspend fun deleteItems(p: String)
    @Query("DELETE FROM animal_events WHERE projectId = :p") suspend fun deleteEvents(p: String)
    @Query("DELETE FROM activities WHERE projectId = :p") suspend fun deleteActivities(p: String)
    @Query("DELETE FROM activity_series WHERE projectId = :p") suspend fun deleteSeries(p: String)
    @Query("DELETE FROM purchase_lists WHERE projectId = :p") suspend fun deleteLists(p: String)
    @Query("DELETE FROM financial_transactions WHERE projectId = :p") suspend fun deleteTransactions(p: String)
    @Query("DELETE FROM financial_categories WHERE projectId = :p") suspend fun deleteCategories(p: String)
    @Query("DELETE FROM attachments WHERE projectId = :p") suspend fun deleteAttachments(p: String)
    @Query("DELETE FROM animals WHERE projectId = :p") suspend fun deleteAnimals(p: String)
    @Query("DELETE FROM livestock_lots WHERE projectId = :p") suspend fun deleteLots(p: String)
    @Query("DELETE FROM workers WHERE projectId = :p") suspend fun deleteWorkers(p: String)

    /**
     * Sustituye la gestión de una finca entera en una sola transacción: o queda la nueva
     * completa o la anterior intacta (nunca una mezcla). Hijos antes que padres al borrar.
     */
    @Query("DELETE FROM management_state WHERE projectId = :p") suspend fun deleteState(p: String)

    /**
     * Borra toda la gestión y el ganado de una finca (y su marca de migración) en una sola
     * transacción: o se borra todo o nada. Solo filas de [projectId]; las demás fincas no se tocan.
     */
    @Transaction
    suspend fun deleteProject(projectId: String) {
        deleteLinks(projectId); deleteItems(projectId); deleteEvents(projectId); deleteActivities(projectId)
        deleteSeries(projectId); deleteLists(projectId); deleteTransactions(projectId); deleteCategories(projectId)
        deleteAttachments(projectId); deleteAnimals(projectId); deleteLots(projectId); deleteWorkers(projectId)
        deleteState(projectId)
    }

    @Transaction
    suspend fun replaceProject(projectId: String, rows: ManagementRows) {
        deleteLinks(projectId); deleteItems(projectId); deleteEvents(projectId); deleteActivities(projectId)
        deleteSeries(projectId); deleteLists(projectId); deleteTransactions(projectId); deleteCategories(projectId)
        deleteAttachments(projectId); deleteAnimals(projectId); deleteLots(projectId); deleteWorkers(projectId)
        insertWorkers(rows.workers); insertCategories(rows.categories); insertSeries(rows.series)
        insertActivities(rows.activities); insertLinks(rows.links); insertLists(rows.lists)
        insertTransactions(rows.transactions); insertItems(rows.items); insertAttachments(rows.attachments)
        insertLots(rows.lots); insertAnimals(rows.animals); insertEvents(rows.events)
    }
}

/** Todas las filas de una finca, ya convertidas (ver ManagementMapping). */
data class ManagementRows(
    val workers: List<WorkerEntity>, val categories: List<FinancialCategoryEntity>, val series: List<ActivitySeriesEntity>,
    val activities: List<ActivityEntity>, val links: List<ActivityLinkEntity>, val lists: List<PurchaseListEntity>,
    val transactions: List<FinancialTransactionEntity>, val items: List<PurchaseItemEntity>, val attachments: List<AttachmentEntity>,
    val lots: List<LivestockLotEntity>, val animals: List<AnimalEntity>, val events: List<AnimalEventEntity>,
)

@Database(
    entities = [
        WorkerEntity::class, ActivitySeriesEntity::class, ActivityEntity::class, ActivityLinkEntity::class,
        PurchaseListEntity::class, PurchaseItemEntity::class, FinancialCategoryEntity::class,
        FinancialTransactionEntity::class, AttachmentEntity::class, LivestockLotEntity::class, AnimalEntity::class,
        AnimalEventEntity::class, ManagementStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ManagementDatabase : RoomDatabase() {
    abstract fun dao(): ManagementDao

    companion object {
        const val NAME = "gestion.db"

        /**
         * Una sola base para todas las fincas (filas separadas por `projectId`). Las migraciones
         * futuras se añaden aquí con `addMigrations`; nunca `fallbackToDestructiveMigration`:
         * perder la gestión de una finca no es aceptable.
         */
        fun open(context: Context): ManagementDatabase =
            Room.databaseBuilder(context.applicationContext, ManagementDatabase::class.java, NAME).build()

        fun inMemory(context: Context): ManagementDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, ManagementDatabase::class.java).build()
    }
}
