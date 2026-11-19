package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * `management/<conjunto>.json` del `.glx` (SPEC §3): `{"schema": "geolux.management/1",
 * "records": [...]}` con los nombres de campo de Desktop (snake_case). El mismo formato
 * guarda la gestión en el teléfono, así que lo que se importa se exporta igual.
 */
object ManagementCodec {
    const val SCHEMA = "geolux.management/1"
    const val KIND = "management"

    /** Conjuntos en el orden de Desktop (`exchange.DATASETS`). */
    val DATASETS = listOf(
        "workers", "financial_categories", "activity_series", "activities", "activity_links",
        "purchase_lists", "transactions", "purchase_items", "attachments",
        "livestock_lots", "animals", "animal_events",
    )

    fun pathOf(dataset: String) = "management/$dataset.json"

    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    class CodecException(message: String) : IllegalArgumentException(message)

    // -- Lectura ------------------------------------------------------------------------------

    private class R(val o: JsonObject, val where: String) {
        private fun p(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }
        fun s(k: String): String = p(k)?.takeIf { it.isString }?.content ?: throw CodecException("$where: falta $k.")
        fun sOr(k: String, default: String = ""): String = p(k)?.takeIf { it.isString }?.content ?: default
        fun sOpt(k: String): String? = p(k)?.takeIf { it.isString }?.content
        fun iOpt(k: String): Int? = p(k)?.intOrNull
        fun i(k: String, default: Int): Int = p(k)?.intOrNull ?: default
        fun l(k: String): Long = p(k)?.longOrNull ?: throw CodecException("$where: falta $k.")
    }

    fun records(text: String, dataset: String): List<JsonObject> {
        val doc = Json.parseToJsonElement(text) as? JsonObject ?: throw CodecException("${pathOf(dataset)} no es un objeto.")
        val schema = (doc["schema"] as? JsonPrimitive)?.content
        if (schema != SCHEMA) throw CodecException("${pathOf(dataset)}: esquema $schema desconocido.")
        return (doc["records"] as? JsonArray ?: JsonArray(emptyList())).map {
            it as? JsonObject ?: throw CodecException("${pathOf(dataset)}: un registro no es un objeto.")
        }
    }

    /** Arma [ManagementData] a partir de los textos de cada conjunto (los que falten, vacíos). */
    fun decode(projectId: String, texts: Map<String, String>): ManagementData {
        fun rows(name: String) = texts[name]?.let { records(it, name) }.orEmpty().map { R(it, pathOf(name)) }.onEach {
            if (it.s("project_id") != projectId) throw CodecException("${it.where}: el registro ${it.sOr("id")} es de otro proyecto.")
        }
        return ManagementData(
            projectId = projectId,
            workers = rows("workers").map {
                Worker(it.s("id"), it.s("project_id"), it.s("code"), it.s("code_key"), it.s("full_name"), it.sOr("role"),
                    it.sOr("start_date"), it.sOr("phone"), it.sOr("status", "active"), it.sOr("notes"), it.sOr("created_at"),
                    it.sOr("updated_at"), it.sOpt("deleted_at"))
            },
            financialCategories = rows("financial_categories").map {
                FinancialCategory(it.s("id"), it.s("project_id"), it.s("key"), it.s("label"), it.i("archived", 0),
                    it.sOr("created_at"), it.sOr("updated_at"))
            },
            activitySeries = rows("activity_series").map {
                ActivitySeries(it.s("id"), it.s("project_id"), it.s("frequency"), it.s("start_date"), it.s("template_json"),
                    it.i("interval", 1), it.sOr("weekdays"), it.sOpt("until_date"), it.iOpt("count"), it.sOr("created_at"),
                    it.sOr("updated_at"), it.sOpt("deleted_at"))
            },
            activities = rows("activities").map {
                Activity(it.s("id"), it.s("project_id"), it.s("title"), it.s("category"), it.s("time_zone"),
                    it.sOr("description"), it.sOr("status", "pending"), it.sOr("priority", "medium"), it.sOpt("scheduled_date"),
                    it.sOpt("scheduled_time"), it.iOpt("remind_minutes_before"), it.sOpt("assignee_worker_id"),
                    it.sOpt("series_id"), it.sOpt("occurrence_date"), it.sOpt("completed_at"), it.sOr("notes"),
                    it.sOr("created_at"), it.sOr("updated_at"), it.sOpt("deleted_at"))
            },
            activityLinks = rows("activity_links").map {
                ActivityLink(it.s("id"), it.s("project_id"), it.s("activity_id"), it.s("entity"), it.s("entity_id"), it.sOr("created_at"))
            },
            purchaseLists = rows("purchase_lists").map {
                PurchaseList(it.s("id"), it.s("project_id"), it.s("name"), it.s("list_type"), it.sOr("description"),
                    it.sOpt("planned_date"), it.sOpt("responsible_worker_id"), it.sOr("status", "open"), it.sOr("notes"),
                    it.sOr("created_at"), it.sOr("updated_at"), it.sOpt("deleted_at"))
            },
            transactions = rows("transactions").map {
                FinancialTransaction(it.s("id"), it.s("project_id"), it.s("type"), it.s("direction"), it.s("date"), it.s("concept"),
                    it.s("category"), it.s("amount"), it.sOr("currency", Values.DEFAULT_CURRENCY), it.sOr("payment_method"),
                    it.sOr("counterparty"), it.sOr("responsible"), it.sOr("reference"), it.sOpt("worker_id"), it.sOpt("activity_id"),
                    it.sOpt("purchase_list_id"), it.sOpt("voids_transaction_id"), it.sOr("notes"), it.sOr("created_at"))
            },
            purchaseItems = rows("purchase_items").map {
                PurchaseItem(it.s("id"), it.s("project_id"), it.s("list_id"), it.s("name"), it.s("quantity"), it.s("unit"),
                    it.sOr("category"), it.sOr("description"), it.sOpt("estimated_price"), it.sOpt("actual_price"),
                    it.sOr("status", "pending"), it.sOpt("transaction_id"), it.sOr("notes"), it.i("position", 0),
                    it.sOr("created_at"), it.sOr("updated_at"), it.sOpt("deleted_at"))
            },
            attachments = rows("attachments").map {
                Attachment(it.s("id"), it.s("project_id"), it.s("owner_entity"), it.s("owner_id"), it.s("path"), it.s("media_type"),
                    it.l("size_bytes"), it.s("sha256"), it.sOr("original_name"), it.sOr("created_at"), it.sOpt("deleted_at"))
            },
            livestockLots = rows("livestock_lots").map {
                LivestockLot(it.s("id"), it.s("project_id"), it.sOpt("zone_id"), it.s("category"), it.i("count", 0), it.sOr("lot_date"),
                    it.sOr("observations"), it.sOr("created_at"), it.sOr("updated_at"), it.sOr("mode", "aggregate"))
            },
            animals = rows("animals").map {
                Animal(it.s("id"), it.s("project_id"), it.s("tag"), it.sOr("name"), it.s("category"), it.sOr("sex", "unknown"),
                    it.sOr("breed"), it.sOr("birth_date"), it.sOr("notes"), it.sOr("status", "active"), it.sOpt("lot_id"),
                    it.sOr("created_at"), it.sOr("updated_at"), it.sOr("origin"), it.sOpt("sire_id"), it.sOpt("dam_id"),
                    it.i("birth_date_estimated", 0), it.sOr("production_class"), it.sOr("photo_path"))
            },
            animalEvents = rows("animal_events").map {
                AnimalEvent(it.s("id"), it.s("project_id"), it.s("animal_id"), it.s("event_type"), it.s("event_date"), it.sOpt("lot_id"),
                    it.sOpt("voids_event_id"), it.sOr("details_json", "{}"), it.sOr("note"), it.sOr("created_at"), it.i("sequence", 0))
            },
        )
    }

    // -- Escritura ----------------------------------------------------------------------------

    private fun obj(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
        for ((k, v) in pairs) put(k, when (v) {
            null -> JsonNull
            is String -> JsonPrimitive(v)
            is Number -> JsonPrimitive(v)
            is JsonElement -> v
            else -> JsonPrimitive(v.toString())
        })
    }

    fun encodeRecords(data: ManagementData): Map<String, List<JsonObject>> = mapOf(
        "workers" to data.workers.map {
            obj("id" to it.id, "project_id" to it.projectId, "code" to it.code, "code_key" to it.codeKey, "full_name" to it.fullName,
                "role" to it.role, "start_date" to it.startDate, "phone" to it.phone, "status" to it.status, "notes" to it.notes,
                "created_at" to it.createdAt, "updated_at" to it.updatedAt, "deleted_at" to it.deletedAt)
        },
        "financial_categories" to data.financialCategories.map {
            obj("id" to it.id, "project_id" to it.projectId, "key" to it.key, "label" to it.label, "archived" to it.archived,
                "created_at" to it.createdAt, "updated_at" to it.updatedAt)
        },
        "activity_series" to data.activitySeries.map {
            obj("id" to it.id, "project_id" to it.projectId, "frequency" to it.frequency, "start_date" to it.startDate,
                "template_json" to it.templateJson, "interval" to it.interval, "weekdays" to it.weekdays, "until_date" to it.untilDate,
                "count" to it.count, "created_at" to it.createdAt, "updated_at" to it.updatedAt, "deleted_at" to it.deletedAt)
        },
        "activities" to data.activities.map {
            obj("id" to it.id, "project_id" to it.projectId, "title" to it.title, "category" to it.category,
                "time_zone" to it.timeZone, "description" to it.description, "status" to it.status, "priority" to it.priority,
                "scheduled_date" to it.scheduledDate, "scheduled_time" to it.scheduledTime,
                "remind_minutes_before" to it.remindMinutesBefore, "assignee_worker_id" to it.assigneeWorkerId,
                "series_id" to it.seriesId, "occurrence_date" to it.occurrenceDate, "completed_at" to it.completedAt,
                "notes" to it.notes, "created_at" to it.createdAt, "updated_at" to it.updatedAt, "deleted_at" to it.deletedAt)
        },
        "activity_links" to data.activityLinks.map {
            obj("id" to it.id, "project_id" to it.projectId, "activity_id" to it.activityId, "entity" to it.entity,
                "entity_id" to it.entityId, "created_at" to it.createdAt)
        },
        "purchase_lists" to data.purchaseLists.map {
            obj("id" to it.id, "project_id" to it.projectId, "name" to it.name, "list_type" to it.listType,
                "description" to it.description, "planned_date" to it.plannedDate, "responsible_worker_id" to it.responsibleWorkerId,
                "status" to it.status, "notes" to it.notes, "created_at" to it.createdAt, "updated_at" to it.updatedAt,
                "deleted_at" to it.deletedAt)
        },
        "transactions" to data.transactions.map {
            obj("id" to it.id, "project_id" to it.projectId, "type" to it.type, "direction" to it.direction, "date" to it.date,
                "concept" to it.concept, "category" to it.category, "amount" to it.amount, "currency" to it.currency,
                "payment_method" to it.paymentMethod, "counterparty" to it.counterparty, "responsible" to it.responsible,
                "reference" to it.reference, "worker_id" to it.workerId, "activity_id" to it.activityId,
                "purchase_list_id" to it.purchaseListId, "voids_transaction_id" to it.voidsTransactionId, "notes" to it.notes,
                "created_at" to it.createdAt)
        },
        "purchase_items" to data.purchaseItems.map {
            obj("id" to it.id, "project_id" to it.projectId, "list_id" to it.listId, "name" to it.name, "quantity" to it.quantity,
                "unit" to it.unit, "category" to it.category, "description" to it.description,
                "estimated_price" to it.estimatedPrice, "actual_price" to it.actualPrice, "status" to it.status,
                "transaction_id" to it.transactionId, "notes" to it.notes, "position" to it.position,
                "created_at" to it.createdAt, "updated_at" to it.updatedAt, "deleted_at" to it.deletedAt)
        },
        "attachments" to data.attachments.map {
            obj("id" to it.id, "project_id" to it.projectId, "owner_entity" to it.ownerEntity, "owner_id" to it.ownerId,
                "path" to it.path, "media_type" to it.mediaType, "size_bytes" to it.sizeBytes, "sha256" to it.sha256,
                "original_name" to it.originalName, "created_at" to it.createdAt, "deleted_at" to it.deletedAt)
        },
        // Ganadería: las columnas de SQLite de Desktop, en su orden.
        "livestock_lots" to data.livestockLots.map {
            obj("id" to it.id, "project_id" to it.projectId, "zone_id" to it.zoneId, "category" to it.category, "count" to it.count,
                "lot_date" to it.lotDate, "observations" to it.observations, "created_at" to it.createdAt,
                "updated_at" to it.updatedAt, "mode" to it.mode)
        },
        "animals" to data.animals.map {
            obj("id" to it.id, "project_id" to it.projectId, "tag" to it.tag, "name" to it.name, "category" to it.category,
                "sex" to it.sex, "breed" to it.breed, "birth_date" to it.birthDate, "notes" to it.notes, "status" to it.status,
                "lot_id" to it.lotId, "created_at" to it.createdAt, "updated_at" to it.updatedAt, "origin" to it.origin,
                "sire_id" to it.sireId, "dam_id" to it.damId, "birth_date_estimated" to it.birthDateEstimated,
                "production_class" to it.productionClass, "photo_path" to it.photoPath)
        },
        "animal_events" to data.animalEvents.map {
            obj("id" to it.id, "project_id" to it.projectId, "animal_id" to it.animalId, "event_type" to it.eventType,
                "event_date" to it.eventDate, "lot_id" to it.lotId, "voids_event_id" to it.voidsEventId,
                "details_json" to it.detailsJson, "note" to it.note, "created_at" to it.createdAt, "sequence" to it.sequence)
        },
    )

    /** dataset → texto del documento; los conjuntos vacíos no se escriben, como en Desktop. */
    fun encode(data: ManagementData): Map<String, String> = encodeRecords(data).filterValues { it.isNotEmpty() }.mapValues { (_, rows) ->
        json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("schema", JsonPrimitive(SCHEMA))
            put("records", JsonArray(rows))
        }) + "\n"
    }
}
