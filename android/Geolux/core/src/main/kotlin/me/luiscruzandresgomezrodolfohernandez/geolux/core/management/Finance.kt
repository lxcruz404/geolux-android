package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.math.BigDecimal

/** Un movimiento financiero (SPEC §2.6). Nunca se edita: se anula con otro que apunta a él. */
data class FinancialTransaction(
    val id: String,
    val projectId: String,
    val type: String,
    val direction: String,
    val date: String,
    val concept: String,
    val category: String,
    val amount: String,
    val currency: String = Values.DEFAULT_CURRENCY,
    val paymentMethod: String = "",
    val counterparty: String = "",
    val responsible: String = "",
    val reference: String = "",
    val workerId: String? = null,
    val activityId: String? = null,
    val purchaseListId: String? = null,
    val voidsTransactionId: String? = null,
    val notes: String = "",
    val createdAt: String = "",
)

/** Totales, como `FinanceService.summary` de Desktop: textos decimales exactos. */
data class FinanceSummary(
    val currency: String,
    val income: String,
    val expenses: String,
    val balance: String,
    val byCategory: Map<String, String>,
    val workerPayments: String,
    val count: Int,
)

object Finance {
    /** Comprueba tipo, sentido, valor y trabajador de un movimiento nuevo (sin persistencia). */
    fun validate(type: String, direction: String?, amount: String, workerId: String?, categories: Set<String>, category: String): String {
        val allowed = Catalogs.transactionType[type]?.second ?: throw ValidationException("Tipo de movimiento «$type» no válido.")
        val resolved = direction ?: allowed.singleOrNull()
        if (resolved == null || resolved !in allowed) throw ValidationException("Indique si el movimiento es de entrada o de salida.")
        if (category !in categories) throw ValidationException("Categoría «$category» no válida.")
        if (type in Catalogs.workerTransactionTypes && workerId.isNullOrBlank()) {
            throw ValidationException("Un pago o anticipo necesita el trabajador.")
        }
        Values.decimalText(amount, "Valor")
        return resolved
    }

    /** Los movimientos que cuentan: ni anulados ni anulaciones. */
    fun effective(all: List<FinancialTransaction>, voided: Set<String> = all.mapNotNull { it.voidsTransactionId }.toSet()): List<FinancialTransaction> =
        all.filter { it.voidsTransactionId == null && it.id !in voided }

    /** Totales de los movimientos efectivos en [currency]; no convierte monedas. */
    /**
     * [voided]: ids anulados en toda la finca (por defecto, los de [all]). Una anulación lleva la
     * fecha del día en que se hizo, así que para un período pasado hay que pasar las de toda la finca.
     */
    fun summary(all: List<FinancialTransaction>, currency: String = Values.DEFAULT_CURRENCY,
                voided: Set<String> = all.mapNotNull { it.voidsTransactionId }.toSet()): FinanceSummary {
        val code = Values.currencyCode(currency)
        var income = BigDecimal.ZERO
        var expenses = BigDecimal.ZERO
        var workers = BigDecimal.ZERO
        val byCategory = sortedMapOf<String, BigDecimal>()
        val rows = effective(all, voided).filter { it.currency == code }
        for (t in rows) {
            val amount = BigDecimal(t.amount)
            when (t.direction) {
                "in" -> income += amount
                "out" -> {
                    expenses += amount
                    byCategory[t.category] = (byCategory[t.category] ?: BigDecimal.ZERO) + amount
                }
            }
            if (t.type in Catalogs.workerTransactionTypes) workers += amount
        }
        return FinanceSummary(
            currency = code,
            income = Values.canonical(income),
            expenses = Values.canonical(expenses),
            balance = Values.canonical(income - expenses),
            byCategory = byCategory.mapValues { Values.canonical(it.value) },
            workerPayments = Values.canonical(workers),
            count = rows.size,
        )
    }
}
