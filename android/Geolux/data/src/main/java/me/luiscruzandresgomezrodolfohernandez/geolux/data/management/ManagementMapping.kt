package me.luiscruzandresgomezrodolfohernandez.geolux.data.management

import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.Activity
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ActivityLink
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ActivitySeries
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.Animal
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.AnimalEvent
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.Attachment
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.FinancialCategory
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.FinancialTransaction
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.LivestockLot
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.PurchaseItem
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.PurchaseList
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.Worker

/** Registros del contrato ↔ filas de Room, campo a campo (sin perder ninguno). */
object ManagementMapping {
    fun toRows(d: ManagementData) = ManagementRows(
        workers = d.workers.map { WorkerEntity(it.id, it.projectId, it.code, it.codeKey, it.fullName, it.role, it.startDate, it.phone, it.status, it.notes, it.createdAt, it.updatedAt, it.deletedAt) },
        categories = d.financialCategories.map { FinancialCategoryEntity(it.id, it.projectId, it.key, it.label, it.archived, it.createdAt, it.updatedAt) },
        series = d.activitySeries.map { ActivitySeriesEntity(it.id, it.projectId, it.frequency, it.startDate, it.templateJson, it.interval, it.weekdays, it.untilDate, it.count, it.createdAt, it.updatedAt, it.deletedAt) },
        activities = d.activities.map {
            ActivityEntity(it.id, it.projectId, it.title, it.category, it.timeZone, it.description, it.status, it.priority, it.scheduledDate,
                it.scheduledTime, it.remindMinutesBefore, it.assigneeWorkerId, it.seriesId, it.occurrenceDate, it.completedAt, it.notes,
                it.createdAt, it.updatedAt, it.deletedAt)
        },
        links = d.activityLinks.map { ActivityLinkEntity(it.id, it.projectId, it.activityId, it.entity, it.entityId, it.createdAt) },
        lists = d.purchaseLists.map { PurchaseListEntity(it.id, it.projectId, it.name, it.listType, it.description, it.plannedDate, it.responsibleWorkerId, it.status, it.notes, it.createdAt, it.updatedAt, it.deletedAt) },
        transactions = d.transactions.map {
            FinancialTransactionEntity(it.id, it.projectId, it.type, it.direction, it.date, it.concept, it.category, it.amount, it.currency,
                it.paymentMethod, it.counterparty, it.responsible, it.reference, it.workerId, it.activityId, it.purchaseListId,
                it.voidsTransactionId, it.notes, it.createdAt)
        },
        items = d.purchaseItems.map {
            PurchaseItemEntity(it.id, it.projectId, it.listId, it.name, it.quantity, it.unit, it.category, it.description, it.estimatedPrice,
                it.actualPrice, it.status, it.transactionId, it.notes, it.position, it.createdAt, it.updatedAt, it.deletedAt)
        },
        attachments = d.attachments.map { AttachmentEntity(it.id, it.projectId, it.ownerEntity, it.ownerId, it.path, it.mediaType, it.sizeBytes, it.sha256, it.originalName, it.createdAt, it.deletedAt) },
        lots = d.livestockLots.map { LivestockLotEntity(it.id, it.projectId, it.zoneId, it.category, it.count, it.lotDate, it.observations, it.createdAt, it.updatedAt, it.mode) },
        animals = d.animals.map {
            AnimalEntity(it.id, it.projectId, it.tag, it.name, it.category, it.sex, it.breed, it.birthDate, it.notes, it.status, it.lotId,
                it.createdAt, it.updatedAt, it.origin, it.sireId, it.damId, it.birthDateEstimated, it.productionClass, it.photoPath)
        },
        events = d.animalEvents.map { AnimalEventEntity(it.id, it.projectId, it.animalId, it.eventType, it.eventDate, it.lotId, it.voidsEventId, it.detailsJson, it.note, it.createdAt, it.sequence) },
    )

    /** Mismo orden que guarda el contrato (orden de inserción no importa a las reglas). */
    fun toData(projectId: String, r: ManagementRows) = ManagementData(
        projectId = projectId,
        workers = r.workers.map { Worker(it.id, it.projectId, it.code, it.codeKey, it.fullName, it.role, it.startDate, it.phone, it.status, it.notes, it.createdAt, it.updatedAt, it.deletedAt) },
        financialCategories = r.categories.map { FinancialCategory(it.id, it.projectId, it.key, it.label, it.archived, it.createdAt, it.updatedAt) },
        activitySeries = r.series.map { ActivitySeries(it.id, it.projectId, it.frequency, it.startDate, it.templateJson, it.interval, it.weekdays, it.untilDate, it.count, it.createdAt, it.updatedAt, it.deletedAt) },
        activities = r.activities.map {
            Activity(it.id, it.projectId, it.title, it.category, it.timeZone, it.description, it.status, it.priority, it.scheduledDate,
                it.scheduledTime, it.remindMinutesBefore, it.assigneeWorkerId, it.seriesId, it.occurrenceDate, it.completedAt, it.notes,
                it.createdAt, it.updatedAt, it.deletedAt)
        },
        activityLinks = r.links.map { ActivityLink(it.id, it.projectId, it.activityId, it.entity, it.entityId, it.createdAt) },
        purchaseLists = r.lists.map { PurchaseList(it.id, it.projectId, it.name, it.listType, it.description, it.plannedDate, it.responsibleWorkerId, it.status, it.notes, it.createdAt, it.updatedAt, it.deletedAt) },
        transactions = r.transactions.map {
            FinancialTransaction(it.id, it.projectId, it.type, it.direction, it.date, it.concept, it.category, it.amount, it.currency,
                it.paymentMethod, it.counterparty, it.responsible, it.reference, it.workerId, it.activityId, it.purchaseListId,
                it.voidsTransactionId, it.notes, it.createdAt)
        },
        purchaseItems = r.items.map {
            PurchaseItem(it.id, it.projectId, it.listId, it.name, it.quantity, it.unit, it.category, it.description, it.estimatedPrice,
                it.actualPrice, it.status, it.transactionId, it.notes, it.position, it.createdAt, it.updatedAt, it.deletedAt)
        },
        attachments = r.attachments.map { Attachment(it.id, it.projectId, it.ownerEntity, it.ownerId, it.path, it.mediaType, it.sizeBytes, it.sha256, it.originalName, it.createdAt, it.deletedAt) },
        livestockLots = r.lots.map { LivestockLot(it.id, it.projectId, it.zoneId, it.category, it.count, it.lotDate, it.observations, it.createdAt, it.updatedAt, it.mode) },
        animals = r.animals.map {
            Animal(it.id, it.projectId, it.tag, it.name, it.category, it.sex, it.breed, it.birthDate, it.notes, it.status, it.lotId,
                it.createdAt, it.updatedAt, it.origin, it.sireId, it.damId, it.birthDateEstimated, it.productionClass, it.photoPath)
        },
        animalEvents = r.events.map { AnimalEvent(it.id, it.projectId, it.animalId, it.eventType, it.eventDate, it.lotId, it.voidsEventId, it.detailsJson, it.note, it.createdAt, it.sequence) },
    )
}
