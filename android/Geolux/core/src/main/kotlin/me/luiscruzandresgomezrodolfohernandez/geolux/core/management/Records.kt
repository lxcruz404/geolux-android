package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

/*
 * Registros de gestión de la finca (contrato `geolux.management/1`, SPEC §2). Los campos
 * son los de Desktop (`src/geolux/core/management/models.py`) en camelCase; en el `.glx` y
 * en el almacenamiento del teléfono van en snake_case (ver [ManagementCodec]).
 */

data class Worker(
    val id: String,
    val projectId: String,
    val code: String,
    val codeKey: String,
    val fullName: String,
    val role: String = "",
    val startDate: String = "",
    val phone: String = "",
    val status: String = "active",
    val notes: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val deletedAt: String? = null,
)

data class ActivitySeries(
    val id: String,
    val projectId: String,
    val frequency: String,
    val startDate: String,
    val templateJson: String,
    val interval: Int = 1,
    val weekdays: String = "",
    val untilDate: String? = null,
    val count: Int? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val deletedAt: String? = null,
)

data class Activity(
    val id: String,
    val projectId: String,
    val title: String,
    val category: String,
    val timeZone: String,
    val description: String = "",
    val status: String = "pending",
    val priority: String = "medium",
    val scheduledDate: String? = null,
    val scheduledTime: String? = null,
    val remindMinutesBefore: Int? = null,
    val assigneeWorkerId: String? = null,
    val seriesId: String? = null,
    val occurrenceDate: String? = null,
    val completedAt: String? = null,
    val notes: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val deletedAt: String? = null,
)

data class ActivityLink(
    val id: String,
    val projectId: String,
    val activityId: String,
    val entity: String,
    val entityId: String,
    val createdAt: String = "",
)

data class PurchaseList(
    val id: String,
    val projectId: String,
    val name: String,
    val listType: String,
    val description: String = "",
    val plannedDate: String? = null,
    val responsibleWorkerId: String? = null,
    val status: String = "open",
    val notes: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val deletedAt: String? = null,
)

data class PurchaseItem(
    val id: String,
    val projectId: String,
    val listId: String,
    val name: String,
    val quantity: String,
    val unit: String,
    val category: String = "",
    val description: String = "",
    val estimatedPrice: String? = null,
    val actualPrice: String? = null,
    val status: String = "pending",
    val transactionId: String? = null,
    val notes: String = "",
    val position: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val deletedAt: String? = null,
)

data class FinancialCategory(
    val id: String,
    val projectId: String,
    val key: String,
    val label: String,
    val archived: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

data class Attachment(
    val id: String,
    val projectId: String,
    val ownerEntity: String,
    val ownerId: String,
    val path: String,
    val mediaType: String,
    val sizeBytes: Long,
    val sha256: String,
    val originalName: String = "",
    val createdAt: String = "",
    val deletedAt: String? = null,
)

/** Toda la gestión de una finca, como viaja en `management/<conjunto>.json`. */
data class ManagementData(
    val projectId: String,
    val workers: List<Worker> = emptyList(),
    val financialCategories: List<FinancialCategory> = emptyList(),
    val activitySeries: List<ActivitySeries> = emptyList(),
    val activities: List<Activity> = emptyList(),
    val activityLinks: List<ActivityLink> = emptyList(),
    val purchaseLists: List<PurchaseList> = emptyList(),
    val transactions: List<FinancialTransaction> = emptyList(),
    val purchaseItems: List<PurchaseItem> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val livestockLots: List<LivestockLot> = emptyList(),
    val animals: List<Animal> = emptyList(),
    val animalEvents: List<AnimalEvent> = emptyList(),
) {
    val isEmpty: Boolean
        get() = workers.isEmpty() && financialCategories.isEmpty() && activitySeries.isEmpty() && activities.isEmpty() &&
            activityLinks.isEmpty() && purchaseLists.isEmpty() && transactions.isEmpty() && purchaseItems.isEmpty() &&
            attachments.isEmpty() && livestockLots.isEmpty() && animals.isEmpty() && animalEvents.isEmpty()
}
