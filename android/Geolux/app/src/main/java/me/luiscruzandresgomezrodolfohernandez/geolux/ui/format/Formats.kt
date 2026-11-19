package me.luiscruzandresgomezrodolfohernandez.geolux.ui.format

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Measure

// Formatos de DESIGN.md §10, iguales a los de Desktop (`ui/formatting.py`).
// Decimal con punto, como Desktop: por eso Locale.US.

private val DateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Fecha ISO-8601 del contrato → `dd/mm/aaaa` en hora local. Si no se entiende, la deja tal cual. */
fun formatDate(iso: String, zone: ZoneId = ZoneId.systemDefault()): String = try {
    OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(DateFormat)
} catch (e: DateTimeParseException) {
    iso
}

/** Área en m² → `245.30 ha`; por debajo de 0.01 ha añade los m²: `0.00 ha (85 m²)`. */
fun formatArea(areaM2: Double): String {
    val hectares = areaM2 / 10_000.0
    return if (hectares < 0.01) {
        String.format(Locale.US, "%.2f ha (%.0f m²)", hectares, areaM2)
    } else {
        String.format(Locale.US, "%.2f ha", hectares)
    }
}

/** Longitud en m → `850 m`, o `2.30 km` desde 1 km. */
fun formatLength(lengthM: Double): String = if (lengthM < 1000) {
    String.format(Locale.US, "%.0f m", lengthM)
} else {
    String.format(Locale.US, "%.2f km", lengthM / 1000)
}

/** Área de `project.json` (`{"value","unit"}`). Convierte `ha` y `m2`; otra unidad se muestra tal cual. */
fun formatMeasureArea(measure: Measure): String = when (measure.unit.lowercase(Locale.ROOT)) {
    "ha" -> formatArea(measure.value * 10_000.0)
    "m2", "m²" -> formatArea(measure.value)
    else -> String.format(Locale.US, "%.2f %s", measure.value, measure.unit)
}

/** Tamaño de archivo → `512 B`, `6.1 KB`, `48.6 MB` (base 1024, como Desktop `format_file_size`). */
fun formatFileSize(sizeBytes: Long): String {
    var size = sizeBytes.toDouble()
    for (unit in listOf("B", "KB", "MB")) {
        if (size < 1024) return if (unit == "B") String.format(Locale.US, "%.0f B", size) else String.format(Locale.US, "%.1f %s", size, unit)
        size /= 1024
    }
    return String.format(Locale.US, "%.1f GB", size)
}

/** Número sin decimales si es entero (`40`), con dos si no (`40.50`). */
fun formatCount(value: Double): String =
    if (value % 1.0 == 0.0) String.format(Locale.US, "%.0f", value) else String.format(Locale.US, "%.2f", value)

/**
 * Separa la cifra de la unidad para las tarjetas de dato clave (§8), que
 * muestran la cifra grande y la unidad pequeña: `245.30 ha` → (`245.30`, `ha`).
 * Un texto que no empieza por una cifra («No especificado») no se parte.
 */
fun splitValueUnit(formatted: String): Pair<String, String> {
    val space = formatted.indexOf(' ')
    if (space < 0 || formatted.substring(0, space).toDoubleOrNull() == null) return formatted to ""
    return formatted.substring(0, space) to formatted.substring(space + 1)
}
