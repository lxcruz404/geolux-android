package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.time.Instant
import java.time.ZoneId

/** Una tabla del informe; sin filas, se muestra [empty] (nunca se rellena). */
data class ReportTable(val title: String, val headers: List<String>, val rows: List<List<String>>, val empty: String, val note: String = "")

data class ManagementReportContent(val title: String, val subtitle: String, val summary: List<String>, val tables: List<ReportTable>)

/**
 * Contenido del informe de gestión del teléfono, con las mismas secciones y reglas que el
 * de Desktop (`src/geolux/core/management/report.py`): solo datos registrados, anulados
 * fuera de los totales, ganancia diaria solo con un pesaje anterior. La app lo dibuja en PDF.
 */
object ManagementReport {
    private fun inPeriod(day: String?, start: String, end: String) = day != null && day >= start && day <= end

    fun build(data: ManagementData, farmName: String, start: String, end: String, zoneNames: Map<String, String> = emptyMap(),
              generated: Instant = Instant.now(), currency: String = Values.DEFAULT_CURRENCY, ops: ManagementOps = ManagementOps()): ManagementReportContent {
        val categories = Catalogs.financialCategory + data.financialCategories.associate { it.key to it.label }
        val workers = data.workers.associate { it.id to it.fullName }
        val inRange = data.transactions.filter { inPeriod(it.date, start, end) }
        val voided = data.transactions.mapNotNull { it.voidsTransactionId }.toSet() // de toda la finca
        val summary = Finance.summary(inRange, currency, voided)
        val tables = mutableListOf<ReportTable>()
        tables += ReportTable("Gastos por categoría", listOf("Categoría", "Gastos ($currency)"),
            summary.byCategory.map { (k, v) -> listOf(categories[k] ?: k, v) }, "Sin gastos registrados en el período.")
        tables += ReportTable("Movimientos", listOf("Fecha", "Tipo", "Concepto", "Valor", "Estado"),
            inRange.sortedBy { it.date }.map { t ->
                listOf(t.date, Catalogs.transactionType[t.type]?.first.orEmpty(), t.concept,
                    (if (t.direction == "out") "−" else if (t.direction == "in") "+" else "") + "${t.amount} ${t.currency}",
                    if (t.voidsTransactionId != null) "anulación" else if (t.id in voided) "anulado" else "")
            }, "Sin movimientos en el período.")
        val activities = ops.sortForAgenda(ops.liveActivities(data).filter { inPeriod(it.scheduledDate, start, end) })
        val overdue = ops.overdue(data, generated).map { it.id }.toSet()
        tables += ReportTable("Actividades del período", listOf("Fecha", "Actividad", "Estado", "Responsable", "Potrero"),
            activities.map { a ->
                listOf(listOfNotNull(a.scheduledDate, a.scheduledTime).joinToString(" "), a.title,
                    Catalogs.activityStatus[a.status].orEmpty() + if (a.id in overdue) " (vencida)" else "",
                    workers[a.assigneeWorkerId].orEmpty(),
                    data.activityLinks.filter { it.activityId == a.id && it.entity == "zone" }.joinToString(", ") { zoneNames[it.entityId] ?: "" })
            }, "Sin actividades programadas en el período.",
            note = "Pendientes: ${activities.count { it.status in Catalogs.openActivityStatus }} · Completadas: ${activities.count { it.status == "done" }}")
        tables += ReportTable("Compras", listOf("Lista", "Artículo", "Cantidad", "Real", "Estado"),
            data.purchaseLists.filter { it.deletedAt == null }.flatMap { l ->
                ops.items(data, l.id).map { i ->
                    listOf(l.name, i.name, "${i.quantity} ${Catalogs.unit[i.unit]}", i.actualPrice.orEmpty(),
                        Catalogs.purchaseItemStatus[i.status].orEmpty() + if (i.transactionId != null) " · pago registrado" else "")
                }
            }, "Sin listas de compra.")
        tables += ReportTable("Trabajadores", listOf("Identificación", "Nombre", "Estado", "Pagos ($currency)"),
            data.workers.filter { it.deletedAt == null }.map { w ->
                listOf(w.code, w.fullName, Catalogs.workerStatus[w.status].orEmpty(),
                    Finance.summary(inRange.filter { it.workerId == w.id }, currency, voided).workerPayments)
            }, "Sin trabajadores registrados.")
        val weights = mutableListOf<List<String>>()
        val health = mutableListOf<List<String>>()
        for (a in data.animals.sortedBy { it.tag }) {
            val events = data.animalEvents.filter { it.animalId == a.id }
            Livestock.weightCurve(events).filter { inPeriod(it.date, start, end) }.forEach {
                weights += listOf(a.tag, it.date, it.kg.toString(), it.dailyGainKg?.let { g -> "%.3f".format(java.util.Locale.ROOT, g) }.orEmpty())
            }
            Livestock.effectiveEvents(events).filter { it.eventType == Livestock.HEALTH && inPeriod(it.eventDate, start, end) }.forEach { e ->
                health += listOf(a.tag, e.eventDate, Livestock.healthKinds[Livestock.detailText(e, "tipo")].orEmpty(),
                    Livestock.detailText(e, "producto"), Livestock.detailText(e, "proxima_fecha"))
            }
        }
        val byStatus = data.animals.groupingBy { it.status }.eachCount()
        tables += ReportTable("Pesajes", listOf("Animal", "Fecha", "Peso (kg)", "Ganancia (kg/día)"), weights,
            "Sin pesajes en el período. La ganancia diaria solo se calcula con un pesaje anterior.",
            note = "Inventario: " + (byStatus.entries.sortedBy { it.key }.joinToString(" · ") { "${Livestock.statusLabels[it.key]} ${it.value}" }
                .ifEmpty { "sin animales individuales" }))
        tables += ReportTable("Sanidad", listOf("Animal", "Fecha", "Tipo", "Producto", "Próxima fecha"), health, "Sin registros sanitarios en el período.")
        val when_ = generated.atZone(ZoneId.systemDefault()).toLocalDateTime().toString().take(16).replace("T", " ")
        return ManagementReportContent(
            title = "Informe de gestión — $farmName",
            subtitle = "Período: $start a $end · Generado el $when_. Solo datos registrados; los anulados no cuentan.",
            summary = listOf("Ingresos ${summary.income}", "Gastos ${summary.expenses}", "Balance ${summary.balance} $currency",
                "Pagos a trabajadores ${summary.workerPayments}"),
            tables = tables,
        )
    }
}
