package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Operaciones de gestión de la finca sobre [ManagementData], con las mismas reglas que los
 * servicios de Desktop (`src/geolux/core/management/services.py`). Kotlin puro: cada
 * operación valida y devuelve los datos nuevos; quien llama los guarda (todo o nada).
 *
 * [geoIds]: ids de las entidades del proyecto a las que se puede enlazar (potreros, cercas,
 * elementos, área, animales, lotes), por tipo. Así una actividad nunca enlaza algo de otra finca.
 */
class ManagementOps(
    private val now: () -> Instant = { Instant.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** Id nuevo (las operaciones de ganadería lo comparten). */
    fun newIdPublic(): String = newId()

    fun stamp(): String = now().truncatedTo(ChronoUnit.SECONDS).toString().replace("Z", "+00:00")

    // -- Trabajadores -------------------------------------------------------------------------

    fun createWorker(data: ManagementData, code: String, fullName: String, role: String = "", startDate: String? = null,
                     phone: String = "", notes: String = ""): Pair<ManagementData, Worker> {
        val clean = Values.requiredText(code, "Identificación del trabajador", 40)
        if (data.workers.any { it.deletedAt == null && it.codeKey == Values.codeKey(clean) }) {
            throw ValidationException("Ya hay un trabajador con la identificación «$clean».")
        }
        val t = stamp()
        val worker = Worker(newId(), data.projectId, clean, Values.codeKey(clean), Values.requiredText(fullName, "Nombre completo"),
            role.trim(), Values.isoDate(startDate, "Fecha de ingreso", required = false).orEmpty(), phone.trim(), "active",
            notes.trim(), t, t, null)
        return data.copy(workers = data.workers + worker) to worker
    }

    fun setWorkerStatus(data: ManagementData, workerId: String, status: String): ManagementData {
        if (status !in Catalogs.workerStatus) throw ValidationException("Estado «$status» no válido.")
        return data.copy(workers = data.workers.map { if (it.id == workerId) it.copy(status = status, updatedAt = stamp()) else it })
    }

    fun activeWorkers(data: ManagementData) = data.workers.filter { it.deletedAt == null && it.status == "active" }

    // -- Actividades --------------------------------------------------------------------------

    fun createActivity(
        data: ManagementData, geoIds: Map<String, Set<String>>, title: String, category: String = "other",
        priority: String = "medium", scheduledDate: String? = null, scheduledTime: String? = null,
        timeZone: String? = null, remindMinutesBefore: Int? = null, assigneeWorkerId: String? = null,
        description: String = "", notes: String = "", links: List<Pair<String, String>> = emptyList(),
    ): Pair<ManagementData, Activity> {
        if (!Catalogs.isValidKey(category, Catalogs.activityCategory, custom = true)) throw ValidationException("Categoría «$category» no válida.")
        if (priority !in Catalogs.activityPriority) throw ValidationException("Prioridad «$priority» no válida.")
        val date = Values.isoDate(scheduledDate, "Fecha programada", required = false)
        val time = Values.hhMm(scheduledTime)
        if (time != null && date == null) throw ValidationException("Una hora necesita una fecha.")
        if (remindMinutesBefore != null && date == null) throw ValidationException("Un recordatorio necesita una fecha.")
        if (remindMinutesBefore != null && remindMinutesBefore < 0) throw ValidationException("El recordatorio no puede ser después de la actividad.")
        if (assigneeWorkerId != null && activeWorkers(data).none { it.id == assigneeWorkerId }) {
            throw ValidationException("Solo se asignan actividades a trabajadores activos.")
        }
        val t = stamp()
        val activity = Activity(newId(), data.projectId, Values.requiredText(title, "Título"), category, Values.timeZone(timeZone),
            description.trim(), "pending", priority, date, time, remindMinutesBefore, assigneeWorkerId, null, null, null,
            notes.trim(), t, t, null)
        val newLinks = links.distinct().map { (entity, entityId) -> link(data, geoIds, activity.id, entity, entityId, t) }
        return data.copy(activities = data.activities + activity, activityLinks = data.activityLinks + newLinks) to activity
    }

    private fun link(data: ManagementData, geoIds: Map<String, Set<String>>, activityId: String, entity: String, entityId: String, t: String): ActivityLink {
        val label = Catalogs.linkEntity[entity] ?: throw ValidationException("Tipo de enlace «$entity» no válido.")
        val exists = when (entity) {
            "worker" -> data.workers.any { it.id == entityId && it.deletedAt == null }
            "purchase_list" -> data.purchaseLists.any { it.id == entityId && it.deletedAt == null }
            "purchase_item" -> data.purchaseItems.any { it.id == entityId && it.deletedAt == null }
            // Ganado: los animales y lotes viven en la gestión de la finca, no en el mapa.
            "animal" -> data.animals.any { it.id == entityId }
            "livestock_lot" -> data.livestockLots.any { it.id == entityId }
            else -> entityId in geoIds[entity].orEmpty()
        }
        if (!exists) throw ValidationException("$label no existe en esta finca.")
        return ActivityLink(newId(), data.projectId, activityId, entity, entityId, t)
    }

    fun setActivityStatus(data: ManagementData, activityId: String, status: String): ManagementData {
        if (status !in Catalogs.activityStatus) throw ValidationException("Estado «$status» no válido.")
        val t = stamp()
        return data.copy(activities = data.activities.map {
            if (it.id == activityId) it.copy(status = status, completedAt = if (status == "done") t else null, updatedAt = t) else it
        })
    }

    fun reschedule(data: ManagementData, activityId: String, date: String?, time: String?): ManagementData {
        val d = Values.isoDate(date, "Fecha programada", required = false)
        val h = Values.hhMm(time)
        if (h != null && d == null) throw ValidationException("Una hora necesita una fecha.")
        return data.copy(activities = data.activities.map {
            if (it.id == activityId) it.copy(scheduledDate = d, scheduledTime = h, updatedAt = stamp()) else it
        })
    }

    fun deleteActivity(data: ManagementData, activityId: String): ManagementData {
        val t = stamp()
        return data.copy(activities = data.activities.map { if (it.id == activityId) it.copy(deletedAt = t, updatedAt = t) else it })
    }

    fun liveActivities(data: ManagementData) = data.activities.filter { it.deletedAt == null }

    fun linksOf(data: ManagementData, activityId: String) = data.activityLinks.filter { it.activityId == activityId }

    fun activitiesLinkedTo(data: ManagementData, entity: String, entityId: String): List<Activity> {
        val ids = data.activityLinks.filter { it.entity == entity && it.entityId == entityId }.map { it.activityId }.toSet()
        return sortForAgenda(liveActivities(data).filter { it.id in ids })
    }

    /** Cuándo vence, en su zona horaria (fin del día si no tiene hora). */
    fun dueAt(a: Activity): ZonedDateTime? {
        val date = a.scheduledDate?.let(LocalDate::parse) ?: return null
        val time = a.scheduledTime?.let(LocalTime::parse) ?: LocalTime.MAX
        return ZonedDateTime.of(date, time, ZoneId.of(a.timeZone))
    }

    /** Momento del aviso: hora programada (o las 8:00 si no tiene) menos los minutos pedidos. */
    fun reminderAt(a: Activity): ZonedDateTime? {
        val minutes = a.remindMinutesBefore ?: return null
        val date = a.scheduledDate?.let(LocalDate::parse) ?: return null
        val time = a.scheduledTime?.let(LocalTime::parse) ?: LocalTime.of(8, 0)
        return ZonedDateTime.of(date, time, ZoneId.of(a.timeZone)).minusMinutes(minutes.toLong())
    }

    fun overdue(data: ManagementData, at: Instant = now()) = liveActivities(data).filter {
        it.status in Catalogs.openActivityStatus && dueAt(it)?.toInstant()?.isBefore(at) == true
    }

    fun sortForAgenda(list: List<Activity>) =
        list.sortedWith(compareBy<Activity>({ it.scheduledDate == null }, { it.scheduledDate }, { it.scheduledTime == null }, { it.scheduledTime }, { it.createdAt }))

    fun onDay(data: ManagementData, day: LocalDate) = sortForAgenda(liveActivities(data).filter { it.scheduledDate == day.toString() })

    fun between(data: ManagementData, from: LocalDate, to: LocalDate) = sortForAgenda(liveActivities(data).filter {
        val d = it.scheduledDate ?: return@filter false
        d >= from.toString() && d <= to.toString()
    })

    // -- Compras ------------------------------------------------------------------------------

    fun createList(data: ManagementData, name: String, listType: String = "general", plannedDate: String? = null,
                   description: String = ""): Pair<ManagementData, PurchaseList> {
        if (listType !in Catalogs.purchaseListType) throw ValidationException("Tipo de lista «$listType» no válido.")
        val t = stamp()
        val list = PurchaseList(newId(), data.projectId, Values.requiredText(name, "Nombre de la lista"), listType, description.trim(),
            Values.isoDate(plannedDate, "Fecha prevista", required = false), null, "open", "", t, t, null)
        return data.copy(purchaseLists = data.purchaseLists + list) to list
    }

    fun items(data: ManagementData, listId: String) =
        data.purchaseItems.filter { it.listId == listId && it.deletedAt == null }.sortedBy { it.position }

    fun addItem(data: ManagementData, listId: String, name: String, quantity: String, unit: String = "unit",
                estimatedPrice: String? = null): Pair<ManagementData, PurchaseItem> {
        if (data.purchaseLists.none { it.id == listId && it.deletedAt == null }) throw ValidationException("La lista no existe.")
        if (unit !in Catalogs.unit) throw ValidationException("Unidad «$unit» no válida.")
        val t = stamp()
        val position = (data.purchaseItems.filter { it.listId == listId }.maxOfOrNull { it.position } ?: -1) + 1
        val item = PurchaseItem(newId(), data.projectId, listId, Values.requiredText(name, "Artículo"),
            Values.decimalText(quantity, "Cantidad"), unit, estimatedPrice = Values.optionalDecimalText(estimatedPrice, "Precio estimado"),
            position = position, createdAt = t, updatedAt = t)
        return refreshList(data.copy(purchaseItems = data.purchaseItems + item), listId) to item
    }

    /** Comprado, parcial, pendiente o cancelado. Nunca crea un gasto. */
    fun setItemStatus(data: ManagementData, itemId: String, status: String, actualPrice: String? = null): ManagementData {
        if (status !in Catalogs.purchaseItemStatus) throw ValidationException("Estado «$status» no válido.")
        val item = data.purchaseItems.firstOrNull { it.id == itemId && it.deletedAt == null } ?: throw ValidationException("El artículo no existe.")
        if (item.transactionId != null && status in setOf("pending", "cancelled")) {
            throw ValidationException("El pago ya se registró; anúlelo antes de cambiar el estado.")
        }
        val price = if (actualPrice != null) Values.optionalDecimalText(actualPrice, "Precio real") else item.actualPrice
        val updated = data.copy(purchaseItems = data.purchaseItems.map {
            if (it.id == itemId) it.copy(status = status, actualPrice = price, updatedAt = stamp()) else it
        })
        return refreshList(updated, item.listId)
    }

    private fun refreshList(data: ManagementData, listId: String): ManagementData {
        val list = data.purchaseLists.first { it.id == listId }
        if (list.status == "cancelled") return data
        val items = items(data, listId)
        val status = if (items.isNotEmpty() && items.all { it.status in setOf("bought", "cancelled") }) "done" else "open"
        if (status == list.status) return data
        return data.copy(purchaseLists = data.purchaseLists.map { if (it.id == listId) it.copy(status = status, updatedAt = stamp()) else it })
    }

    fun categories(data: ManagementData): Map<String, String> =
        Catalogs.financialCategory + data.financialCategories.filter { it.archived == 0 }.associate { it.key to it.label }

    /** La persona confirma que pagó estos artículos comprados: un movimiento por la suma, enlazado a cada uno. */
    fun confirmPayment(data: ManagementData, itemIds: List<String>, date: String, category: String, paymentMethod: String = "",
                       counterparty: String = "", currency: String? = null): Pair<ManagementData, FinancialTransaction> {
        if (itemIds.isEmpty()) throw ValidationException("Elija al menos un artículo.")
        val chosen = itemIds.map { id -> data.purchaseItems.firstOrNull { it.id == id && it.deletedAt == null } ?: throw ValidationException("El artículo no existe.") }
        if (chosen.map { it.listId }.toSet().size != 1) throw ValidationException("Los artículos deben ser de una misma lista.")
        for (item in chosen) {
            if (item.status !in setOf("bought", "partial")) throw ValidationException("«${item.name}» no está marcado como comprado.")
            if (item.transactionId != null) throw ValidationException("«${item.name}» ya tiene su pago registrado.")
            if (item.actualPrice == null) throw ValidationException("Falta el valor real de «${item.name}».")
        }
        val list = data.purchaseLists.first { it.id == chosen.first().listId }
        val total = chosen.fold(BigDecimal.ZERO) { acc, item -> acc + BigDecimal(item.actualPrice) }
        val (withTx, tx) = recordTransaction(data, type = "purchase", date = date, concept = "Compra: ${list.name}", category = category,
            amount = Values.canonical(total), paymentMethod = paymentMethod, counterparty = counterparty, currency = currency,
            purchaseListId = list.id, notes = chosen.joinToString(", ") { it.name })
        val ids = itemIds.toSet()
        val t = stamp()
        return withTx.copy(purchaseItems = withTx.purchaseItems.map { if (it.id in ids) it.copy(transactionId = tx.id, updatedAt = t) else it }) to tx
    }

    // -- Dinero -------------------------------------------------------------------------------

    fun recordTransaction(
        data: ManagementData, type: String, date: String, concept: String, category: String, amount: String,
        direction: String? = null, currency: String? = null, paymentMethod: String = "", counterparty: String = "",
        responsible: String = "", reference: String = "", workerId: String? = null, activityId: String? = null,
        purchaseListId: String? = null, notes: String = "",
    ): Pair<ManagementData, FinancialTransaction> {
        val resolved = Finance.validate(type, direction, amount, workerId, categories(data).keys, category)
        if (paymentMethod.isNotEmpty() && paymentMethod !in Catalogs.paymentMethod) throw ValidationException("Método de pago «$paymentMethod» no válido.")
        if (workerId != null && data.workers.none { it.id == workerId }) throw ValidationException("El trabajador no existe.")
        if (activityId != null && data.activities.none { it.id == activityId }) throw ValidationException("La actividad no existe.")
        val tx = FinancialTransaction(newId(), data.projectId, type, resolved, Values.isoDate(date, "Fecha")!!,
            Values.requiredText(concept, "Concepto"), category, Values.decimalText(amount, "Valor"), Values.currencyCode(currency),
            paymentMethod, counterparty.trim(), responsible.trim(), reference.trim(), workerId, activityId, purchaseListId, null,
            notes.trim(), stamp())
        return data.copy(transactions = data.transactions + tx) to tx
    }

    fun voidTransaction(data: ManagementData, transactionId: String, reason: String, today: LocalDate): Pair<ManagementData, FinancialTransaction> {
        val original = data.transactions.firstOrNull { it.id == transactionId } ?: throw ValidationException("El movimiento no existe.")
        if (original.voidsTransactionId != null || data.transactions.any { it.voidsTransactionId == transactionId }) {
            throw ValidationException("Ese movimiento ya está anulado o es una anulación.")
        }
        val voiding = original.copy(id = newId(), voidsTransactionId = original.id, concept = "Anulación: ${original.concept}",
            notes = Values.requiredText(reason, "Motivo de la anulación", 500), date = today.toString(), createdAt = stamp())
        return data.copy(transactions = data.transactions + voiding) to voiding
    }

    // -- Fusión (SPEC §3.1, igual que Desktop exchange.merge) ---------------------------------

    /**
     * Une [incoming] (otro paquete de la misma finca) con [local]: en los registros editables
     * gana el `updatedAt` más reciente (empate: lo local); los enlaces viajan con su
     * actividad; los movimientos se unen por id.
     */
    fun merge(local: ManagementData, incoming: ManagementData): ManagementData {
        if (local.projectId != incoming.projectId) throw ValidationException("Los datos son de otra finca.")
        ManagementValidation.check(incoming)
        fun <T> lww(mine: List<T>, theirs: List<T>, id: (T) -> String, updated: (T) -> String): Pair<List<T>, Set<String>> {
            val byId = LinkedHashMap<String, T>()
            mine.forEach { byId[id(it)] = it }
            val won = mutableSetOf<String>()
            for (t in theirs) {
                val current = byId[id(t)]
                if (current == null || updated(t) > updated(current)) {
                    byId[id(t)] = t
                    won += id(t)
                }
            }
            return byId.values.toList() to won
        }
        val (activities, wonActivities) = lww(local.activities, incoming.activities, { it.id }, { it.updatedAt.ifEmpty { it.deletedAt ?: it.createdAt } })
        val links = local.activityLinks.filter { it.activityId !in wonActivities } +
            incoming.activityLinks.filter { it.activityId in wonActivities }
        val txIds = local.transactions.map { it.id }.toSet()
        return local.copy(
            workers = lww(local.workers, incoming.workers, { it.id }, { it.updatedAt }).first,
            financialCategories = lww(local.financialCategories, incoming.financialCategories, { it.id }, { it.updatedAt }).first,
            activitySeries = lww(local.activitySeries, incoming.activitySeries, { it.id }, { it.updatedAt }).first,
            activities = activities,
            activityLinks = links,
            purchaseLists = lww(local.purchaseLists, incoming.purchaseLists, { it.id }, { it.updatedAt }).first,
            transactions = local.transactions + incoming.transactions.filter { it.id !in txIds },
            purchaseItems = lww(local.purchaseItems, incoming.purchaseItems, { it.id }, { it.updatedAt }).first,
            attachments = lww(local.attachments, incoming.attachments, { it.id }, { it.deletedAt ?: it.createdAt }).first,
            livestockLots = lww(local.livestockLots, incoming.livestockLots, { it.id }, { it.updatedAt }).first,
            animals = lww(local.animals, incoming.animals, { it.id }, { it.updatedAt }).first,
            animalEvents = local.animalEvents + incoming.animalEvents.filter { e -> local.animalEvents.none { it.id == e.id } },
        ).let(::rebuildAnimals).also(ManagementValidation::checkNaturalKeys)
    }

    /**
     * Recalcula el estado y el lote de cada animal a partir de sus eventos (como
     * `AnimalService.rebuild` de Desktop). Una historia imposible tras fusionar se rechaza.
     */
    fun rebuildAnimals(data: ManagementData): ManagementData {
        val animals = data.animals.map { a ->
            val state = try {
                Livestock.deriveState(data.animalEvents.filter { it.animalId == a.id })
            } catch (e: Livestock.InconsistentHistory) {
                throw ValidationException("Animal ${a.tag}: ${e.message}")
            }
            if (state.status == a.status && state.lotId == a.lotId) a else a.copy(status = state.status, lotId = state.lotId)
        }
        // Tamaño de cada lote individual = sus animales activos (caché, como en Desktop).
        val lots = data.livestockLots.map { lot ->
            if (lot.mode != "individual") return@map lot
            val count = animals.count { it.lotId == lot.id && it.status == "active" }
            if (count == lot.count) lot else lot.copy(count = count, updatedAt = stamp())
        }
        return data.copy(animals = animals, livestockLots = lots)
    }
}
