package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** Un valor que la persona escribió y GeoLux no puede aceptar, con el motivo. */
class ValidationException(message: String) : IllegalArgumentException(message)

/**
 * Reglas de valores del contrato de gestión (SPEC §1), iguales a
 * `src/geolux/core/management/values.py` de Desktop: dinero y cantidades como decimal
 * exacto en texto, fechas `AAAA-MM-DD`, horas `HH:MM`, zonas IANA y códigos visibles.
 */
object Values {
    const val DEFAULT_TIME_ZONE = "America/Bogota"
    const val DEFAULT_CURRENCY = "COP"
    private val currencyRegex = Regex("^[A-Z]{3}$")

    /**
     * Texto decimal canónico, como Desktop: "125000.50" → "125000.5", "0.10" → "0.1".
     * No acepta coma decimal ni `Double` (escondería redondeos).
     */
    fun decimalText(value: String?, what: String, positive: Boolean = true, allowZero: Boolean = false): String {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) throw ValidationException("$what: falta el valor.")
        val number = try {
            BigDecimal(text)
        } catch (e: NumberFormatException) {
            throw ValidationException("$what: «$text» no es un número (use punto decimal).")
        }
        if (positive && (number.signum() < 0 || (number.signum() == 0 && !allowZero))) {
            throw ValidationException("$what: debe ser mayor que cero.")
        }
        return canonical(number)
    }

    fun canonical(number: BigDecimal): String {
        if (number.signum() == 0) return "0"
        val plain = number.stripTrailingZeros().toPlainString()
        return if ('.' in plain) plain.trimEnd('0').trimEnd('.') else plain
    }

    fun optionalDecimalText(value: String?, what: String): String? =
        if (value.isNullOrBlank()) null else decimalText(value, what, allowZero = true)

    fun currencyCode(value: String?): String {
        val code = (value ?: DEFAULT_CURRENCY).trim().uppercase()
        if (!currencyRegex.matches(code)) throw ValidationException("Moneda «$value» no válida (código de tres letras, p. ej. COP).")
        return code
    }

    fun isoDate(value: String?, what: String, required: Boolean = true): String? {
        if (value.isNullOrBlank()) {
            if (required) throw ValidationException("$what: falta la fecha.")
            return null
        }
        return try {
            LocalDate.parse(value.trim()).toString()
        } catch (e: DateTimeParseException) {
            throw ValidationException("$what: «$value» no es una fecha AAAA-MM-DD.")
        }
    }

    fun hhMm(value: String?, what: String = "Hora"): String? {
        if (value.isNullOrBlank()) return null
        val parsed = try {
            LocalTime.parse(value.trim())
        } catch (e: DateTimeParseException) {
            throw ValidationException("$what: «$value» no es una hora HH:MM.")
        }
        if (parsed.second != 0 || parsed.nano != 0) throw ValidationException("$what: use horas y minutos (HH:MM).")
        return "%02d:%02d".format(parsed.hour, parsed.minute)
    }

    fun timeZone(value: String?): String {
        val name = (value ?: DEFAULT_TIME_ZONE).trim()
        try {
            ZoneId.of(name)
        } catch (e: Exception) {
            throw ValidationException("Zona horaria «$name» desconocida.")
        }
        return name
    }

    /** Cómo se comparan dos códigos visibles: sin mayúsculas ni espacios de los extremos ("2068-38"). */
    fun codeKey(code: String): String = code.trim().lowercase()

    fun requiredText(value: String?, what: String, limit: Int = 200): String {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) throw ValidationException("$what: es obligatorio.")
        if (text.length > limit) throw ValidationException("$what: máximo $limit caracteres.")
        return text
    }
}
