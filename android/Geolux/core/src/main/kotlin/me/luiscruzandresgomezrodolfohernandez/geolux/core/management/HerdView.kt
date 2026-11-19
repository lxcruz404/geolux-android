package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/*
 * Vistas del software ganadero (Android), calculadas solo con los datos guardados: filas
 * de la tabla de animales, resumen del hato, lotes, registros por tipo y alertas
 * sanitarias. Nada se estima ni se inventa: un dato que falta se muestra como «—».
 * Mismas reglas que Desktop (`core/models/animal.py`: clases productivas y su sugerencia).
 */

/** Clases productivas de Desktop (`PRODUCTION_CLASSES`). */
object ProductionClass {
    val labels = linkedMapOf(
        "calf_male" to "Ternero", "calf_female" to "Ternera", "steer" to "Novillo", "heifer" to "Novilla",
        "cow" to "Vaca", "bull" to "Toro", "ox" to "Buey",
    )
    private val sexOf = mapOf("calf_male" to "male", "steer" to "male", "bull" to "male", "ox" to "male",
        "calf_female" to "female", "heifer" to "female", "cow" to "female")

    /** Sugerencia (se muestra como tal), igual que `suggested_class` de Desktop. */
    fun suggested(sex: String, ageDays: Long?, hasCalved: Boolean): String {
        if (sex !in setOf("male", "female") || ageDays == null) return ""
        if (ageDays < 365) return if (sex == "female") "calf_female" else "calf_male"
        return if (sex == "female") (if (hasCalved) "cow" else "heifer") else "steer"
    }

    fun fitsSex(cls: String, sex: String) = sexOf[cls]?.let { it == sex } ?: true
}

/** Una fila de la tabla de animales: todo lo que se puede consultar, ya resuelto a texto. */
data class AnimalRow(
    val id: String,
    val tag: String,
    val name: String,
    val sex: String,
    val breed: String,
    /** Clase productiva registrada, o la sugerida si no hay ([classSuggested]). */
    val productionClass: String,
    val classSuggested: Boolean,
    val ageDays: Long?,
    val birthDate: String,
    val weightKg: Double?,
    val weightDate: String,
    val lotId: String?,
    val lotLabel: String,
    val zoneId: String?,
    val zoneName: String,
    val status: String,
    /** Cómo entró (compra o nacimiento) y la procedencia anotada. */
    val entryKind: String,
    val origin: String,
    val category: String,
) {
    val classLabel: String get() = ProductionClass.labels[productionClass]?.let { if (classSuggested) "$it (sugerida)" else it } ?: "—"
    val sexLabel: String get() = Livestock.sexLabels[sex] ?: sex
    val statusLabel: String get() = Livestock.statusLabels[status] ?: status
    val ageLabel: String get() = HerdView.ageText(ageDays)
    val weightLabel: String get() = weightKg?.let { "${HerdView.kg(it)} kg" } ?: "—"
    val originLabel: String get() = listOf(Livestock.eventLabels[entryKind]?.removePrefix("Alta por ")?.replaceFirstChar { it.uppercase() }.orEmpty(), origin)
        .filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "—" }
}

/** Columnas de la tabla, en orden; [wide] = visibles por defecto solo en pantallas anchas. */
enum class AnimalColumn(val label: String, val width: Int, val wide: Boolean = false) {
    Tag("Identificación", 120), Name("Nombre", 120, true), Sex("Sexo", 90), Breed("Raza", 110, true),
    Class("Categoría", 140), Age("Edad", 90), Birth("Nacimiento", 110, true), Weight("Peso", 90), Lot("Lote", 170),
    Zone("Potrero", 130, true), Status("Estado", 100), Origin("Procedencia", 150, true);

    fun text(r: AnimalRow): String = when (this) {
        Tag -> r.tag
        Name -> r.name.ifBlank { "—" }
        Sex -> r.sexLabel
        Breed -> r.breed.ifBlank { "—" }
        Class -> r.classLabel
        Age -> r.ageLabel
        Birth -> r.birthDate.ifBlank { "—" }
        Weight -> r.weightLabel
        Lot -> r.lotLabel
        Zone -> r.zoneName.ifBlank { "—" }
        Status -> r.statusLabel
        Origin -> r.originLabel
    }

    /** Sin dato para esta columna: va siempre al final, se ordene como se ordene. */
    fun missing(r: AnimalRow): Boolean = when (this) {
        Age -> r.ageDays == null
        Weight -> r.weightKg == null
        Birth -> r.birthDate.isEmpty()
        else -> false
    }

    /** Clave de orden (números como números). */
    fun compare(a: AnimalRow, b: AnimalRow): Int = when (this) {
        Age -> compareValues(a.ageDays ?: Long.MAX_VALUE, b.ageDays ?: Long.MAX_VALUE)
        Weight -> compareValues(a.weightKg ?: Double.MAX_VALUE, b.weightKg ?: Double.MAX_VALUE)
        Birth -> compareValues(a.birthDate.ifEmpty { "9999" }, b.birthDate.ifEmpty { "9999" })
        Tag -> HerdView.naturalCompare(a.tag, b.tag)
        else -> text(a).compareTo(text(b), ignoreCase = true)
    }
}

/** Filtros de la tabla; null = todos. */
data class HerdFilter(
    val query: String = "",
    val sex: String? = null,
    val breed: String? = null,
    val productionClass: String? = null,
    val lotId: String? = null,
    val zoneId: String? = null,
    val status: String? = "active",
) {
    fun matches(r: AnimalRow): Boolean {
        val q = query.trim()
        return (q.isEmpty() || r.tag.contains(q, true) || r.name.contains(q, true)) &&
            (sex == null || r.sex == sex) && (breed == null || r.breed.equals(breed, true)) &&
            (productionClass == null || r.productionClass == productionClass) &&
            (lotId == null || r.lotId == lotId) && (zoneId == null || r.zoneId == zoneId) &&
            (status == null || r.status == status)
    }

    val active: Int get() = listOf(sex, breed, productionClass, lotId, zoneId).count { it != null } + (if (status != "active") 1 else 0)
}

data class LotRow(
    val id: String,
    val label: String,
    val category: String,
    val mode: String,
    val zoneId: String?,
    val zoneName: String,
    /** Animales activos (lote individual) o la cantidad registrada (lote agregado). */
    val count: Int,
    /** Composición por clase: «Vaca 12 · Ternero 4». */
    val composition: String,
    val averageWeightKg: Double?,
    val weighedAnimals: Int,
    val observations: String,
    val lotDate: String,
) {
    val modeLabel: String get() = if (mode == "individual") "Animales identificados" else "Cantidad (agregado)"
}

/** Un registro del hato para las listas de Movimientos, Nacimientos, Compras, Pesajes… */
data class HerdRecord(val event: AnimalEvent, val animalTag: String, val title: String, val detail: String, val voided: Boolean)

enum class RecordKind(val label: String) {
    All("Todo"), Moves("Movimientos"), Births("Nacimientos"), Purchases("Compras"), Exits("Ventas y bajas"),
    Weighings("Pesajes"), Health("Sanidad"), Vaccinations("Vacunaciones"), Treatments("Tratamientos"), Reproduction("Reproducción");

    fun accepts(e: AnimalEvent): Boolean = when (this) {
        All -> e.eventType != Livestock.VOID
        Moves -> e.eventType == Livestock.MOVE
        Births -> e.eventType == Livestock.BIRTH
        Purchases -> e.eventType == Livestock.PURCHASE
        Exits -> e.eventType in Livestock.EXIT_TYPES
        Weighings -> e.eventType == Livestock.WEIGHING
        Health -> e.eventType == Livestock.HEALTH
        Vaccinations -> e.eventType == Livestock.HEALTH && Livestock.detailText(e, "tipo") == "vaccination"
        Treatments -> e.eventType == Livestock.HEALTH && Livestock.detailText(e, "tipo") in setOf("treatment", "deworming")
        Reproduction -> e.eventType == Livestock.CALVING
    }
}

/** Alerta sanitaria: una próxima fecha registrada por la persona que vence pronto o ya venció. */
data class HealthAlert(val animalId: String, val tag: String, val kind: String, val product: String, val dueDate: String, val overdue: Boolean)

data class HerdSummary(
    val total: Int,
    val active: Int,
    val males: Int,
    val females: Int,
    /** Por clase productiva (registrada o sugerida), solo activos. */
    val byClass: Map<String, Int>,
    val withoutClass: Int,
    val sold: Int,
    val dead: Int,
    val aggregateHead: Int,
    val recentMoves: List<HerdRecord>,
    val recentBirths: List<HerdRecord>,
    val alerts: List<HealthAlert>,
    val lots: List<LotRow>,
) {
    fun count(vararg classes: String) = classes.sumOf { byClass[it] ?: 0 }
}

object HerdView {
    fun kg(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, v)

    fun ageText(days: Long?): String {
        if (days == null || days < 0) return "—"
        if (days < 31) return "$days d"
        val months = days / 30.4375
        return if (months < 24) "${months.toInt()} m" else {
            val y = (days / 365.25).toInt()
            val m = ((days - y * 365.25) / 30.4375).toInt()
            if (m == 0) "$y a" else "$y a $m m"
        }
    }

    fun naturalCompare(a: String, b: String): Int {
        val na = a.filter { it.isDigit() }.toLongOrNull()
        val nb = b.filter { it.isDigit() }.toLongOrNull()
        return if (na != null && nb != null && na != nb) na.compareTo(nb) else a.compareTo(b, ignoreCase = true)
    }

    fun lotLabel(data: ManagementData, zones: Map<String, String>, lotId: String?): String {
        val lot = data.livestockLots.firstOrNull { it.id == lotId } ?: return "Sin lote"
        return listOfNotNull("Lote ${Livestock.categories[lot.category] ?: lot.category}", zones[lot.zoneId],
            lot.observations.takeIf { it.isNotBlank() }).joinToString(" · ")
    }

    private fun ageDays(birth: String, today: LocalDate): Long? =
        runCatching { ChronoUnit.DAYS.between(LocalDate.parse(birth), today) }.getOrNull()?.takeIf { it >= 0 }

    fun rows(data: ManagementData, zones: Map<String, String>, today: LocalDate): List<AnimalRow> {
        val byAnimal = data.animalEvents.groupBy { it.animalId }
        val calvedDams = data.animalEvents.let { all ->
            val voided = Livestock.voidedIds(all)
            all.filter { it.eventType == Livestock.CALVING && it.id !in voided }.map { it.animalId }.toSet()
        }
        return data.animals.map { a ->
            val events = byAnimal[a.id].orEmpty()
            val effective = Livestock.effectiveEvents(events)
            val last = Livestock.weightCurve(events).lastOrNull()
            val entry = effective.firstOrNull { it.eventType in Livestock.ENTRY_TYPES }
            val age = ageDays(a.birthDate, today)
            val suggested = if (a.productionClass.isBlank()) ProductionClass.suggested(a.sex, age, a.id in calvedDams) else ""
            val lot = data.livestockLots.firstOrNull { it.id == a.lotId }
            AnimalRow(
                id = a.id, tag = a.tag, name = a.name, sex = a.sex, breed = a.breed,
                productionClass = a.productionClass.ifBlank { suggested }, classSuggested = a.productionClass.isBlank() && suggested.isNotEmpty(),
                ageDays = age, birthDate = a.birthDate, weightKg = last?.kg, weightDate = last?.date.orEmpty(),
                lotId = a.lotId, lotLabel = lotLabel(data, zones, a.lotId), zoneId = lot?.zoneId, zoneName = zones[lot?.zoneId].orEmpty(),
                status = a.status, entryKind = entry?.eventType.orEmpty(), origin = a.origin, category = a.category,
            )
        }
    }

    fun filterAndSort(rows: List<AnimalRow>, filter: HerdFilter, sort: AnimalColumn, descending: Boolean): List<AnimalRow> {
        val cmp = Comparator<AnimalRow> { x, y -> sort.compare(x, y) }.let { if (descending) it.reversed() else it }
        return rows.filter(filter::matches).sortedWith(compareBy<AnimalRow> { sort.missing(it) }.then(cmp)
            .thenComparator { x, y -> naturalCompare(x.tag, y.tag) })
    }

    fun lots(data: ManagementData, zones: Map<String, String>, rows: List<AnimalRow>): List<LotRow> = data.livestockLots
        .sortedWith(compareBy({ it.mode != "individual" }, { it.category }, { it.lotDate }))
        .map { lot ->
            val members = rows.filter { it.lotId == lot.id && it.status == "active" }
            val weights = members.mapNotNull { it.weightKg }
            val composition = members.groupingBy { ProductionClass.labels[it.productionClass] ?: "Sin categoría" }.eachCount()
                .entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key} ${it.value}" }
            LotRow(
                lot.id, lotLabel(data, zones, lot.id), lot.category, lot.mode, lot.zoneId, zones[lot.zoneId].orEmpty(),
                if (lot.mode == "individual") members.size else lot.count,
                if (lot.mode == "individual") composition.ifEmpty { "Sin animales activos" } else "${Livestock.categories[lot.category]} (sin identificar)",
                if (weights.isEmpty()) null else weights.average(), weights.size, lot.observations, lot.lotDate,
            )
        }

    fun records(data: ManagementData, zones: Map<String, String>, kind: RecordKind, animalId: String? = null): List<HerdRecord> {
        val tags = data.animals.associate { it.id to it.tag }
        val voided = Livestock.voidedIds(data.animalEvents)
        return data.animalEvents
            .filter { (animalId == null || it.animalId == animalId) && kind.accepts(it) }
            .sortedWith(Livestock.order).reversed()
            .map { e -> HerdRecord(e, tags[e.animalId].orEmpty(), Livestock.eventLabels[e.eventType] ?: e.eventType, describe(data, zones, e), e.id in voided) }
    }

    fun describe(data: ManagementData, zones: Map<String, String>, e: AnimalEvent): String {
        fun s(k: String) = Livestock.detailText(e, k)
        return when (e.eventType) {
            Livestock.WEIGHING -> listOf((s("peso_kg").toDoubleOrNull()?.let { kg(it) } ?: s("peso_kg")) + " kg", s("metodo")).filter { it.isNotBlank() }.joinToString(" · ")
            Livestock.HEALTH -> listOf(Livestock.healthKinds[s("tipo")].orEmpty(), s("producto"), s("dosis"),
                Livestock.healthRoutes[s("via")].takeIf { s("via").isNotEmpty() }.orEmpty(), s("responsable"),
                s("proxima_fecha").takeIf { it.isNotEmpty() }?.let { "próxima $it" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
            Livestock.MOVE, Livestock.PURCHASE, Livestock.BIRTH -> lotLabel(data, zones, e.lotId)
            Livestock.CALVING -> data.animals.firstOrNull { it.id == s("cria_id") }?.let { "Cría ${it.tag}" } ?: "Parto"
            else -> e.note
        }.let { if (e.note.isNotBlank() && e.eventType != Livestock.VOID && it != e.note) "$it · ${e.note}" else it }
    }

    /** Próximas fechas sanitarias registradas: la última de cada tipo por animal activo, si vence en [days] días o ya venció. */
    fun alerts(data: ManagementData, today: LocalDate, days: Long = 15): List<HealthAlert> {
        val active = data.animals.filter { it.status == "active" }.associateBy { it.id }
        val limit = today.plusDays(days).toString()
        return Livestock.effectiveEvents(data.animalEvents.filter { it.animalId in active })
            .filter { it.eventType == Livestock.HEALTH }
            .groupBy { it.animalId to Livestock.detailText(it, "tipo") }
            .mapNotNull { (_, list) ->
                val last = list.last()
                val due = Livestock.detailText(last, "proxima_fecha")
                if (due.isEmpty() || due > limit) null
                else HealthAlert(last.animalId, active.getValue(last.animalId).tag, Livestock.healthKinds[Livestock.detailText(last, "tipo")].orEmpty(),
                    Livestock.detailText(last, "producto"), due, due < today.toString())
            }.sortedBy { it.dueDate }
    }

    fun summary(data: ManagementData, zones: Map<String, String>, today: LocalDate, recentDays: Long = 30): HerdSummary {
        val rows = rows(data, zones, today)
        val active = rows.filter { it.status == "active" }
        val since = today.minusDays(recentDays).toString()
        val moves = records(data, zones, RecordKind.Moves).filter { !it.voided && it.event.eventDate >= since }
        val births = records(data, zones, RecordKind.Births).filter { !it.voided && it.event.eventDate >= since }
        return HerdSummary(
            total = rows.size, active = active.size,
            males = active.count { it.sex == "male" }, females = active.count { it.sex == "female" },
            byClass = active.filter { it.productionClass.isNotEmpty() }.groupingBy { it.productionClass }.eachCount(),
            withoutClass = active.count { it.productionClass.isEmpty() },
            sold = rows.count { it.status == "sold" }, dead = rows.count { it.status == "dead" },
            aggregateHead = data.livestockLots.filter { it.mode != "individual" }.sumOf { it.count },
            recentMoves = moves.take(8), recentBirths = births.take(8), alerts = alerts(data, today), lots = lots(data, zones, rows),
        )
    }

    /** Informe ganadero (PDF): resumen, inventario de activos, lotes y alertas, con los mismos datos de la pantalla. */
    fun report(data: ManagementData, farmName: String, zones: Map<String, String>, today: LocalDate): ManagementReportContent {
        val s = summary(data, zones, today)
        val rows = filterAndSort(rows(data, zones, today), HerdFilter(), AnimalColumn.Tag, false)
        return ManagementReportContent(
            title = "Informe ganadero — $farmName",
            subtitle = "Inventario al $today. Datos registrados en GeoLux; las categorías marcadas «sugerida» se deducen de sexo, edad y partos.",
            summary = listOf(
                "Animales activos: ${s.active} (machos ${s.males}, hembras ${s.females}); vendidos ${s.sold}, muertos ${s.dead}.",
                "Terneros ${s.count("calf_male", "calf_female")} · Novillas ${s.count("heifer")} · Novillos ${s.count("steer")} · Vacas ${s.count("cow")} · Toros ${s.count("bull")} · Bueyes ${s.count("ox")} · Sin categoría ${s.withoutClass}.",
                "Cabezas en lotes agregados (sin identificar): ${s.aggregateHead}.",
            ),
            tables = listOf(
                ReportTable("Inventario de animales activos", listOf("Identificación", "Sexo", "Raza", "Categoría", "Edad", "Peso", "Lote", "Potrero"),
                    rows.map { r -> listOf(r.tag, r.sexLabel, r.breed.ifBlank { "—" }, r.classLabel, r.ageLabel, r.weightLabel, r.lotLabel, r.zoneName.ifBlank { "—" }) },
                    "No hay animales activos."),
                ReportTable("Lotes", listOf("Lote", "Tipo", "Animales", "Composición", "Peso promedio"),
                    s.lots.map { l -> listOf(l.label, l.modeLabel, l.count.toString(), l.composition, l.averageWeightKg?.let { "${kg(it)} kg (${l.weighedAnimals})" } ?: "—") },
                    "No hay lotes."),
                ReportTable("Alertas sanitarias (próximos 15 días y vencidas)", listOf("Animal", "Tipo", "Producto", "Fecha", "Estado"),
                    s.alerts.map { a -> listOf(a.tag, a.kind, a.product.ifBlank { "—" }, a.dueDate, if (a.overdue) "Vencida" else "Próxima") },
                    "Sin alertas."),
            ),
        )
    }

    /** Edad legible para la ficha (con años, meses y días). */
    fun ageLong(birth: String, today: LocalDate): String = runCatching {
        val p = Period.between(LocalDate.parse(birth), today)
        listOfNotNull(p.years.takeIf { it > 0 }?.let { "$it ${if (it == 1) "año" else "años"}" },
            p.months.takeIf { it > 0 }?.let { "$it ${if (it == 1) "mes" else "meses"}" },
            p.days.takeIf { p.years == 0 && it > 0 }?.let { "$it d" }).joinToString(" ").ifEmpty { "Recién nacido" }
    }.getOrDefault("—")
}
