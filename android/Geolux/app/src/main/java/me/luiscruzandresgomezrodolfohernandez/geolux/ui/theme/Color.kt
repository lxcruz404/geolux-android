package me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Colores de docs/android/DESIGN.md §3 (v0.2). La paleta está pendiente de
// aprobación de Luis (06_ANDROID_SPEC §8b): si cambia, solo cambia este archivo.

// §3.1 Roles de Material 3 — claro
internal val LightPrimary = Color(0xFF2D6A4F)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFEAF4EF)
internal val LightOnPrimaryContainer = Color(0xFF173C33)
internal val LightBackground = Color(0xFFF3F6F5)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightSurfaceVariant = Color(0xFFEEF4F1)
internal val LightOnSurface = Color(0xFF1C2A25)
internal val LightOnSurfaceVariant = Color(0xFF5F7169)
internal val LightOutline = Color(0xFFDCE4E1)
internal val LightError = Color(0xFFB3261E)
internal val LightDisabled = Color(0xFF8B9C95)

// §3.1 Roles de Material 3 — oscuro
internal val DarkPrimary = Color(0xFF3E8368)
internal val DarkOnPrimary = Color(0xFFFFFFFF)
internal val DarkPrimaryContainer = Color(0xFF1E3B32)
internal val DarkOnPrimaryContainer = Color(0xFF9FDCC1)
internal val DarkBackground = Color(0xFF101614)
internal val DarkSurface = Color(0xFF182420)
internal val DarkSurfaceVariant = Color(0xFF22302B)
internal val DarkOnSurface = Color(0xFFEAF2EE)
internal val DarkOnSurfaceVariant = Color(0xFFAEC2BA)
internal val DarkOutline = Color(0xFF2A3934)
internal val DarkError = Color(0xFFF28B82)
internal val DarkDisabled = Color(0xFF7C9088)

// §3.2 Chips de estado [Propuesta]: texto / fondo
internal val LightChipGreenContent = Color(0xFF2D6A4F)
internal val LightChipGreenContainer = Color(0xFFEAF4EF)
internal val LightChipAmberContent = Color(0xFF8A5300)
internal val LightChipAmberContainer = Color(0xFFFFF4DF)
internal val LightChipRedContent = Color(0xFFB3261E)
internal val LightChipRedContainer = Color(0xFFFCE8E6)
internal val LightChipNeutralContent = Color(0xFF5F7169)
internal val LightChipNeutralContainer = Color(0xFFEEF4F1)

internal val DarkChipGreenContent = Color(0xFF9FDCC1)
internal val DarkChipGreenContainer = Color(0xFF1E3B32)
internal val DarkChipAmberContent = Color(0xFFF5C46B)
internal val DarkChipAmberContainer = Color(0xFF3A2A0A)
internal val DarkChipRedContent = Color(0xFFF28B82)
internal val DarkChipRedContainer = Color(0xFF3B1A18)
internal val DarkChipNeutralContent = Color(0xFFAEC2BA)
internal val DarkChipNeutralContainer = Color(0xFF22302B)

// §3.3 Colores del mapa [Desktop]
internal val LightMapWorkArea = Color(0xFFC9762B)
internal val DarkMapWorkArea = Color(0xFFE0A458)
internal val LightMapZoneDefault = Color(0xFF2A9D8F)
internal val DarkMapZoneDefault = Color(0xFF4FB8A8)
internal val MapMission = Color(0xFF4C6EF5)
internal val MapMissionWaypointBorder = Color(0xFF364FC7)

/** Par texto / fondo de un chip de estado (DESIGN.md §3.2). */
@Immutable
data class StatusChipColors(
    val content: Color,
    val container: Color,
)

/**
 * Colores de GeoLux que no tienen rol en Material 3: chips de estado,
 * deshabilitado y colores del mapa. Se leen con `GeoLuxTheme.colors`.
 *
 * Familias de chip (§3.2):
 * - [chipGreen]: potrero Activo; misión Válida, Exportada, Ejecutada, Completada.
 * - [chipAmber]: potrero En descanso; misión Con advertencias, Procesando;
 *   etiqueta «Sintético · datos de prueba».
 * - [chipRed]: misión Inválida.
 * - [chipNeutral]: potrero Inactivo; misión Borrador.
 */
@Immutable
data class GeoLuxExtendedColors(
    val chipGreen: StatusChipColors,
    val chipAmber: StatusChipColors,
    val chipRed: StatusChipColors,
    val chipNeutral: StatusChipColors,
    /** Solo para elementos deshabilitados: no da contraste suficiente para texto (§11). */
    val disabled: Color,
    val mapWorkArea: Color,
    /** Potrero sin color propio en `zones.geojson`. */
    val mapZoneDefault: Color,
    val mapMission: Color,
    val mapMissionWaypointBorder: Color,
)

internal val LightExtendedColors = GeoLuxExtendedColors(
    chipGreen = StatusChipColors(LightChipGreenContent, LightChipGreenContainer),
    chipAmber = StatusChipColors(LightChipAmberContent, LightChipAmberContainer),
    chipRed = StatusChipColors(LightChipRedContent, LightChipRedContainer),
    chipNeutral = StatusChipColors(LightChipNeutralContent, LightChipNeutralContainer),
    disabled = LightDisabled,
    mapWorkArea = LightMapWorkArea,
    mapZoneDefault = LightMapZoneDefault,
    mapMission = MapMission,
    mapMissionWaypointBorder = MapMissionWaypointBorder,
)

internal val DarkExtendedColors = GeoLuxExtendedColors(
    chipGreen = StatusChipColors(DarkChipGreenContent, DarkChipGreenContainer),
    chipAmber = StatusChipColors(DarkChipAmberContent, DarkChipAmberContainer),
    chipRed = StatusChipColors(DarkChipRedContent, DarkChipRedContainer),
    chipNeutral = StatusChipColors(DarkChipNeutralContent, DarkChipNeutralContainer),
    disabled = DarkDisabled,
    mapWorkArea = DarkMapWorkArea,
    mapZoneDefault = DarkMapZoneDefault,
    mapMission = MapMission,
    mapMissionWaypointBorder = MapMissionWaypointBorder,
)

internal val LocalGeoLuxColors = staticCompositionLocalOf { LightExtendedColors }
