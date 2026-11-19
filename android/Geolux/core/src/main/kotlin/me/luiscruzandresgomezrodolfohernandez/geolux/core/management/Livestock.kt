package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull

/*
 * Ganadería en el teléfono, con las reglas de Desktop (`src/geolux/core/models/animal.py`
 * y `services/animal_service.py`): la verdad son los eventos (altas, movimientos, bajas,
 * pesajes, sanidad, partos, anulaciones); el estado del animal (activo, vendido, muerto y su
 * lote) se deduce de ellos. Los eventos nunca se editan ni se borran: se anulan.
 * Viajan en el `.glx` como las filas de SQLite de Desktop (management/animals.json…).
 */

data class LivestockLot(
    val id: String,
    val projectId: String,
    val zoneId: String?,
    val category: String,
    val count: Int,
    val lotDate: String = "",
    val observations: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val mode: String = "aggregate",
)

data class Animal(
    val id: String,
    val projectId: String,
    val tag: String,
    val name: String = "",
    val category: String,
    val sex: String = "unknown",
    val breed: String = "",
    val birthDate: String = "",
    val notes: String = "",
    val status: String = "active",
    val lotId: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val origin: String = "",
    val sireId: String? = null,
    val damId: String? = null,
    val birthDateEstimated: Int = 0,
    val productionClass: String = "",
    val photoPath: String = "",
)

data class AnimalEvent(
    val id: String,
    val projectId: String,
    val animalId: String,
    val eventType: String,
    val eventDate: String,
    val lotId: String? = null,
    val voidsEventId: String? = null,
    val detailsJson: String = "{}",
    val note: String = "",
    val createdAt: String = "",
    val sequence: Int,
)

data class AnimalState(val status: String, val lotId: String?, val entry: AnimalEvent?, val exit: AnimalEvent?, val lastDate: String)

/** Un punto de la curva de peso y la ganancia diaria respecto al pesaje anterior (null en el primero). */
data class WeightPoint(val date: String, val kg: Double, val dailyGainKg: Double?)

object Livestock {
    const val BIRTH = "birth"
    const val PURCHASE = "purchase"
    const val MOVE = "move"
    const val SALE = "sale"
    const val DEATH = "death"
    const val VOID = "void"
    const val WEIGHING = "weighing"
    const val HEALTH = "health"
    const val CALVING = "calving"
    val ENTRY_TYPES = setOf(BIRTH, PURCHASE)
    val EXIT_TYPES = setOf(SALE, DEATH)
    val RECORD_TYPES = setOf(WEIGHING, HEALTH, CALVING)
    const val MAX_WEIGHT_KG = 2000.0

    val eventLabels = linkedMapOf(
        BIRTH to "Alta por nacimiento", PURCHASE to "Alta por compra", MOVE to "Movimiento de lote", SALE to "Baja por venta",
        DEATH to "Baja por muerte", VOID to "Anulación", WEIGHING to "Pesaje", HEALTH to "Registro sanitario", CALVING to "Parto",
    )
    val healthKinds = linkedMapOf(
        "vaccination" to "Vacunación", "deworming" to "Desparasitación", "treatment" to "Tratamiento",
        "illness" to "Enfermedad o síntoma", "exam" to "Revisión veterinaria", "other" to "Otro",
    )
    val healthRoutes = linkedMapOf(
        "" to "Sin especificar", "subcutaneous" to "Subcutánea", "intramuscular" to "Intramuscular", "intravenous" to "Intravenosa",
        "oral" to "Oral", "topical" to "Tópica (pour-on, baño)", "intramammary" to "Intramamaria", "other" to "Otra",
    )
    val statusLabels = linkedMapOf("active" to "Activo", "sold" to "Vendido", "dead" to "Muerto", "not_registered" to "Sin alta válida")
    val sexLabels = linkedMapOf("female" to "Hembra", "male" to "Macho", "unknown" to "Sin especificar")
    val categories = linkedMapOf(
        "bovine" to "Bovino", "sheep" to "Ovino", "goat" to "Caprino", "swine" to "Porcino", "equine" to "Equino",
        "poultry" to "Avícola", "other" to "Otro",
    )

    class InconsistentHistory(message: String) : IllegalStateException(message)

    /** Orden de vida, igual que `event_order` de Desktop: fecha, secuencia, creación, id. */
    val order = compareBy<AnimalEvent>({ it.eventDate }, { it.sequence }, { it.createdAt }, { it.id })

    fun voidedIds(events: List<AnimalEvent>) = events.filter { it.eventType == VOID }.mapNotNull { it.voidsEventId }.toSet()

    fun effectiveEvents(events: List<AnimalEvent>): List<AnimalEvent> {
        val voided = voidedIds(events)
        return events.filter { it.eventType != VOID && it.id !in voided }.sortedWith(order)
    }

    fun deriveState(events: List<AnimalEvent>): AnimalState {
        var status = "not_registered"
        var lot: String? = null
        var entry: AnimalEvent? = null
        var exit: AnimalEvent? = null
        var last = ""
        for (e in effectiveEvents(events)) {
            if (e.eventType in RECORD_TYPES) continue
            val label = eventLabels[e.eventType] ?: e.eventType
            when {
                e.eventType in ENTRY_TYPES -> {
                    if (entry != null) throw InconsistentHistory("El animal tiene dos altas válidas.")
                    entry = e; status = "active"; lot = e.lotId
                }
                entry == null -> throw InconsistentHistory("$label del ${e.eventDate} sin un alta válida anterior.")
                exit != null -> throw InconsistentHistory("$label del ${e.eventDate} después de la baja del animal.")
                e.eventType == MOVE -> lot = e.lotId
                e.eventType in EXIT_TYPES -> {
                    exit = e; status = if (e.eventType == SALE) "sold" else "dead"; lot = null
                }
            }
            last = e.eventDate
        }
        return AnimalState(status, lot, entry, exit, last)
    }

    fun details(e: AnimalEvent): JsonObject = (Json.parseToJsonElement(e.detailsJson) as? JsonObject) ?: JsonObject(emptyMap())

    /** Un dato del detalle de un evento como texto ("" si no está). */
    fun detailText(e: AnimalEvent, key: String): String = (details(e)[key] as? JsonPrimitive)?.content.orEmpty()

    /** Curva de peso con ganancia diaria; sin dos pesajes no hay ganancia (no se inventa). */
    fun weightCurve(events: List<AnimalEvent>): List<WeightPoint> {
        val weighings = effectiveEvents(events).filter { it.eventType == WEIGHING }
            .mapNotNull { e -> (details(e)["peso_kg"] as? JsonPrimitive)?.doubleOrNull?.let { e.eventDate to it } }
        return weighings.mapIndexed { i, (date, kg) ->
            val gain = if (i == 0) null else {
                val (prevDate, prevKg) = weighings[i - 1]
                val days = ChronoUnit.DAYS.between(LocalDate.parse(prevDate), LocalDate.parse(date))
                if (days <= 0) null else BigDecimal(kg - prevKg).divide(BigDecimal(days), 3, RoundingMode.HALF_UP).toDouble()
            }
            WeightPoint(date, kg, gain)
        }
    }
}

/** Operaciones de ganadería sobre [ManagementData], con las validaciones de Desktop. */
class LivestockOps(private val ops: ManagementOps) {
    private fun history(data: ManagementData, animalId: String) = data.animalEvents.filter { it.animalId == animalId }

    fun state(data: ManagementData, animalId: String) = Livestock.deriveState(history(data, animalId))

    fun animal(data: ManagementData, animalId: String) =
        data.animals.firstOrNull { it.id == animalId } ?: throw ValidationException("El animal no existe.")

    private fun checkLot(data: ManagementData, lotId: String?, category: String) {
        val lot = data.livestockLots.firstOrNull { it.id == lotId } ?: throw ValidationException("Elige un lote de este proyecto.")
        if (lot.mode != "individual") {
            throw ValidationException("Ese lote es agregado (solo una cantidad): los animales individuales van en lotes individuales, para no contarlos dos veces.")
        }
        if (lot.category != category) throw ValidationException("El lote es de otra categoría de ganado.")
    }

    private fun addEvent(data: ManagementData, animal: Animal, type: String, date: String, lotId: String? = null,
                         voids: String? = null, details: JsonObject = JsonObject(emptyMap()), note: String = ""): Pair<ManagementData, AnimalEvent> {
        val sequence = (history(data, animal.id).maxOfOrNull { it.sequence } ?: 0) + 1
        val event = AnimalEvent(ops.newIdPublic(), data.projectId, animal.id, type, date, lotId, voids, details.toString(), note.trim(), ops.stamp(), sequence)
        val updated = data.copy(animalEvents = data.animalEvents + event)
        val state = try {
            Livestock.deriveState(history(updated, animal.id))
        } catch (e: Livestock.InconsistentHistory) {
            throw ValidationException(e.message ?: "Historia del animal no válida.")
        }
        // Caché del estado, como `_write_cache` de Desktop.
        val animals = updated.animals.map {
            if (it.id == animal.id) it.copy(status = state.status, lotId = state.lotId, updatedAt = ops.stamp()) else it
        }
        // Y la cantidad de los lotes individuales, como `_set_lot_count`: viaja ya correcta y
        // Desktop no tiene que recontarla (si no, cada importación volvería a cambiarla).
        val lots = updated.livestockLots.map { lot ->
            val active = animals.count { it.lotId == lot.id && it.status == "active" }
            if (lot.mode == "individual" && lot.count != active) lot.copy(count = active, updatedAt = ops.stamp()) else lot
        }
        return updated.copy(animals = animals, livestockLots = lots) to event
    }

    fun createIndividualLot(data: ManagementData, category: String, zoneId: String?, observations: String = ""): Pair<ManagementData, LivestockLot> {
        if (category !in Livestock.categories) throw ValidationException("Selecciona una categoría de ganado válida.")
        val t = ops.stamp()
        val lot = LivestockLot(ops.newIdPublic(), data.projectId, zoneId, category, 0, "", observations.trim(), t, t, "individual")
        return data.copy(livestockLots = data.livestockLots + lot) to lot
    }

    fun registerEntry(data: ManagementData, tag: String, category: String, kind: String, eventDate: String, lotId: String,
                      name: String = "", sex: String = "unknown", breed: String = "", birthDate: String = ""): Pair<ManagementData, Animal> {
        if (kind !in Livestock.ENTRY_TYPES) throw ValidationException("Elige cómo entra el animal a la finca: nació en ella (alta por nacimiento) o llegó por compra o ingreso.")
        val clean = tag.trim()
        if (clean.isEmpty()) throw ValidationException("La identificación (chapeta) es obligatoria.")
        if (category !in Livestock.categories) throw ValidationException("Selecciona una categoría de ganado válida.")
        if (sex !in Livestock.sexLabels) throw ValidationException("Sexo no válido.")
        val date = Values.isoDate(eventDate, "La fecha del alta")!!
        val birth = Values.isoDate(birthDate.ifBlank { if (kind == Livestock.BIRTH) date else "" }, "La fecha de nacimiento", required = false).orEmpty()
        if (birth.isNotEmpty() && birth > date) throw ValidationException("La fecha de nacimiento es posterior a la del alta.")
        checkLot(data, lotId, category)
        if (data.animals.any { it.tag.equals(clean, ignoreCase = true) }) throw ValidationException("Ya existe un animal con la identificación «$clean» en esta finca.")
        val t = ops.stamp()
        val animal = Animal(ops.newIdPublic(), data.projectId, clean, name.trim(), category, sex, breed.trim(), birth, "", "active", lotId, t, t)
        val (withEvent, _) = addEvent(data.copy(animals = data.animals + animal), animal, kind, date, lotId = lotId)
        return withEvent to withEvent.animals.first { it.id == animal.id }
    }

    private fun checkAfter(date: String?, state: AnimalState): String {
        val when_ = Values.isoDate(date, "La fecha")!!
        if (state.lastDate.isNotEmpty() && when_ < state.lastDate) {
            throw ValidationException("La fecha es anterior al último registro del animal (${state.lastDate}). Si un registro anterior está mal, corrígelo.")
        }
        return when_
    }

    private fun checkRecordDate(date: String?, state: AnimalState, label: String): String {
        val when_ = Values.isoDate(date, label)!!
        val entry = state.entry ?: throw ValidationException("El animal no tiene un alta válida.")
        if (when_ < entry.eventDate) throw ValidationException("$label es anterior al alta del animal (${entry.eventDate}).")
        state.exit?.let { if (when_ > it.eventDate) throw ValidationException("$label es posterior a la baja del animal (${it.eventDate}).") }
        return when_
    }

    fun move(data: ManagementData, animalId: String, lotId: String, date: String): ManagementData {
        val a = animal(data, animalId)
        val s = state(data, animalId)
        if (s.status != "active") throw ValidationException("Solo se puede mover un animal activo.")
        if (lotId == s.lotId) throw ValidationException("El animal ya está en ese lote.")
        checkLot(data, lotId, a.category)
        return addEvent(data, a, Livestock.MOVE, checkAfter(date, s), lotId = lotId).first
    }

    fun recordWeighing(data: ManagementData, animalId: String, date: String, weightKg: String, method: String = ""): ManagementData {
        val kg = weightKg.trim().toDoubleOrNull() ?: throw ValidationException("El peso debe ser un número en kilogramos.")
        if (kg <= 0 || kg > Livestock.MAX_WEIGHT_KG) throw ValidationException("El peso debe estar entre 0 y ${Livestock.MAX_WEIGHT_KG.toInt()} kg.")
        val a = animal(data, animalId)
        val when_ = checkRecordDate(date, state(data, animalId), "La fecha del pesaje")
        val rounded = BigDecimal(kg).setScale(1, RoundingMode.HALF_UP).toDouble()
        return addEvent(data, a, Livestock.WEIGHING, when_, details = buildJsonObject {
            put("peso_kg", JsonPrimitive(rounded)); put("metodo", JsonPrimitive(method.trim()))
        }).first
    }

    /** Lo que la persona registra; GeoLux no sugiere productos, dosis ni fechas. */
    fun recordHealth(data: ManagementData, animalId: String, date: String, kind: String, product: String = "", dose: String = "",
                     route: String = "", responsible: String = "", nextDate: String = ""): ManagementData {
        if (kind !in Livestock.healthKinds) throw ValidationException("Elige el tipo de registro sanitario.")
        if (route !in Livestock.healthRoutes) throw ValidationException("Vía de administración no válida.")
        if (kind in setOf("vaccination", "deworming", "treatment") && product.isBlank()) throw ValidationException("Indica el producto aplicado.")
        val a = animal(data, animalId)
        val when_ = checkRecordDate(date, state(data, animalId), "La fecha del registro sanitario")
        val next = Values.isoDate(nextDate, "La próxima fecha", required = false)
        if (next != null && next <= when_) throw ValidationException("La próxima fecha debe ser posterior al registro.")
        return addEvent(data, a, Livestock.HEALTH, when_, details = buildJsonObject {
            put("tipo", JsonPrimitive(kind)); put("producto", JsonPrimitive(product.trim())); put("principio_activo", JsonPrimitive(""))
            put("dosis", JsonPrimitive(dose.trim())); put("via", JsonPrimitive(route)); put("duracion", JsonPrimitive(""))
            put("responsable", JsonPrimitive(responsible.trim()))
            if (next != null) put("proxima_fecha", JsonPrimitive(next))
        }).first
    }

    fun registerExit(data: ManagementData, animalId: String, kind: String, date: String): ManagementData {
        if (kind !in Livestock.EXIT_TYPES) throw ValidationException("La baja debe ser por venta o por muerte.")
        val s = state(data, animalId)
        if (s.status != "active") throw ValidationException("El animal no está activo: ya tiene una baja.")
        return addEvent(data, animal(data, animalId), kind, checkAfter(date, s)).first
    }

    fun voidEvent(data: ManagementData, eventId: String, reason: String, today: LocalDate): ManagementData {
        if (reason.isBlank()) throw ValidationException("Indica el motivo de la anulación.")
        val e = data.animalEvents.firstOrNull { it.id == eventId } ?: throw ValidationException("El registro no existe.")
        return try {
            addEvent(data, animal(data, e.animalId), Livestock.VOID, today.toString(), voids = e.id, note = reason).first
        } catch (x: ValidationException) {
            throw ValidationException("No se puede anular «${Livestock.eventLabels[e.eventType]}» del ${e.eventDate}: ${x.message} Anula primero los registros posteriores.")
        }
    }

    /** Animales activos por lote (tamaño de cada lote individual). */
    fun activeCount(data: ManagementData, lotId: String) = data.animals.count { it.lotId == lotId && it.status == "active" }
}
