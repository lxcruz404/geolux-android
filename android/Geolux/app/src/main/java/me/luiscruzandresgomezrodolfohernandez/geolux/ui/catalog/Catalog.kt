package me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.StatusChipColors

// Catálogos de Desktop que la UI necesita y el contrato no trae: el color de
// cada tipo de elemento (core/element_types.py, DESIGN.md §7.2) y la familia
// de chip de cada estado (DESIGN.md §3.2). Los textos siempre son los `*_label`
// del contrato; aquí solo se elige el color.

private val ElementTypeColors = mapOf(
    "point_of_interest" to 0xFF5C6BC0,
    "tank" to 0xFF0B7285,
    "trough" to 0xFF1C7ED6,
    "corral" to 0xFF8D6E63,
    "house" to 0xFFD9480F,
    "warehouse" to 0xFF5F3DC4,
    "road" to 0xFFF08C00,
    "infrastructure" to 0xFF495057,
    "stable" to 0xFF6D4C41,
    "shed" to 0xFF5D4037,
    "chute" to 0xFF7048E8,
    "well" to 0xFF0C8599,
    "pond" to 0xFF0C8599,
    "reservoir" to 0xFF1098AD,
    "pump" to 0xFF5C7CFA,
    "mill" to 0xFF845EF7,
    "spring" to 0xFF15AABF,
    "transformer" to 0xFFF59F00,
    "pole" to 0xFF868E96,
    "gate" to 0xFFAE3EC9,
    "dock" to 0xFF1971C2,
    "access_point" to 0xFF37B24D,
    "other" to 0xFF868E96,
)

/** Color del tipo de elemento; un tipo desconocido usa el de «Otro», como Desktop. */
fun elementTypeColor(elementType: String): Color = Color(ElementTypeColors[elementType] ?: ElementTypeColors.getValue("other"))

/** Color `#RRGGBB` del contrato; si no se entiende, [fallback]. */
fun parseHexColor(hex: String, fallback: Color): Color {
    val digits = hex.removePrefix("#")
    if (digits.length != 6) return fallback
    return digits.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: fallback
}

enum class ChipFamily { Green, Amber, Red, Neutral }

/** Estado de potrero (`zones.status`) → familia de chip (§3.2). */
fun zoneStatusFamily(status: String): ChipFamily = when (status) {
    "active" -> ChipFamily.Green
    "resting" -> ChipFamily.Amber
    else -> ChipFamily.Neutral
}

/** Estado del plan de misión (`missions.plan_status`) → familia de chip (§3.2). */
fun planStatusFamily(planStatus: String): ChipFamily = when (planStatus) {
    "valid", "exported", "executed", "completed" -> ChipFamily.Green
    "with_warnings", "processing" -> ChipFamily.Amber
    "invalid" -> ChipFamily.Red
    else -> ChipFamily.Neutral
}

@Composable
@ReadOnlyComposable
fun ChipFamily.colors(): StatusChipColors = when (this) {
    ChipFamily.Green -> GeoLuxTheme.colors.chipGreen
    ChipFamily.Amber -> GeoLuxTheme.colors.chipAmber
    ChipFamily.Red -> GeoLuxTheme.colors.chipRed
    ChipFamily.Neutral -> GeoLuxTheme.colors.chipNeutral
}
