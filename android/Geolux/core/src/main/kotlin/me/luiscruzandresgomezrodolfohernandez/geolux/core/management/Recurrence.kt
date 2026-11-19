package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.time.LocalDate
import java.time.YearMonth

/**
 * Fechas de una actividad repetida (SPEC §2.3), con las mismas reglas que
 * `src/geolux/core/management/recurrence.py` de Desktop:
 *
 * - diaria: cada [interval] días desde [start];
 * - semanal: los [weekdays] (1 = lunes … 7 = domingo) de cada [interval]-ésima semana contada
 *   desde la semana de [start]; sin días, el de [start];
 * - mensual: el día de [start] cada [interval] meses; en un mes más corto, su último día.
 *
 * [until] (incluido) o [count] (ocurrencias desde [start]) terminan la serie.
 */
data class RecurrenceRule(
    val frequency: String,
    val start: LocalDate,
    val interval: Int = 1,
    val weekdays: List<Int> = emptyList(),
    val until: LocalDate? = null,
    val count: Int? = null,
) {
    fun validate() {
        if (frequency !in Catalogs.recurrenceFrequency) throw ValidationException("Frecuencia «$frequency» no válida.")
        if (interval < 1) throw ValidationException("El intervalo de la repetición debe ser 1 o más.")
        if (weekdays.any { it !in 1..7 }) throw ValidationException("Los días de la semana van de 1 (lunes) a 7 (domingo).")
        if (weekdays.isNotEmpty() && frequency != "weekly") {
            throw ValidationException("Los días de la semana solo se usan en la repetición semanal.")
        }
        if (until != null && until < start) throw ValidationException("La repetición termina antes de empezar.")
        if (count != null && count < 1) throw ValidationException("El número de repeticiones debe ser 1 o más.")
    }

    private fun all(): Sequence<LocalDate> = when (frequency) {
        "daily" -> generateSequence(start) { it.plusDays(interval.toLong()) }
        "weekly" -> {
            val days = weekdays.toSortedSet().ifEmpty { sortedSetOf(start.dayOfWeek.value) }
            val firstMonday = start.minusDays((start.dayOfWeek.value - 1).toLong())
            generateSequence(firstMonday) { it.plusWeeks(interval.toLong()) }
                .flatMap { monday -> days.asSequence().map { monday.plusDays((it - 1).toLong()) } }
                .filter { it >= start }
        }
        else -> generateSequence(0) { it + 1 }.map { n ->
            val month = YearMonth.from(start).plusMonths(n.toLong() * interval)
            month.atDay(minOf(start.dayOfMonth, month.lengthOfMonth()))
        }
    }

    /** Las fechas dentro de [from, to], ambos incluidos. */
    fun occurrences(from: LocalDate, to: LocalDate): List<LocalDate> {
        validate()
        val found = mutableListOf<LocalDate>()
        for ((index, day) in all().withIndex()) {
            if (count != null && index + 1 > count) break
            if (until != null && day > until) break
            if (day > to) break
            if (day >= from) found += day
        }
        return found
    }

    companion object {
        /** Hasta dónde se generan actividades concretas. */
        const val HORIZON_DAYS = 60L
    }
}
