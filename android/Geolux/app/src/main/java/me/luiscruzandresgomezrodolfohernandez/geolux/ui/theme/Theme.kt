package me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

// DESIGN.md §3.1 no define secondary, tertiary, contenedores de superficie ni
// roles inversos. Se derivan de la paleta verde para que no se cuele el morado
// por defecto de Material 3 (p. ej. el indicador de la barra inferior usa
// secondaryContainer y la barra usa surfaceContainer). Los contenedores de
// error salen de los chips rojos de §3.2.

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = DarkOnPrimaryContainer,
    secondary = LightPrimary,
    onSecondary = LightOnPrimary,
    secondaryContainer = LightPrimaryContainer,
    onSecondaryContainer = LightOnPrimaryContainer,
    tertiary = LightPrimary,
    onTertiary = LightOnPrimary,
    tertiaryContainer = LightPrimaryContainer,
    onTertiaryContainer = LightOnPrimaryContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightPrimary,
    inverseSurface = DarkSurface,
    inverseOnSurface = DarkOnSurface,
    error = LightError,
    onError = LightOnPrimary,
    errorContainer = LightChipRedContainer,
    onErrorContainer = LightChipRedContent,
    outline = LightOutline,
    outlineVariant = LightOutline,
    surfaceBright = LightSurface,
    surfaceDim = LightBackground,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerHighest = LightSurfaceVariant,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = LightPrimary,
    // La barra inferior pinta con `secondary` la etiqueta de la pestaña activa:
    // DarkPrimary sobre la superficie oscura da 3.5:1, por debajo de 4.5:1 (§11).
    secondary = DarkOnPrimaryContainer,
    onSecondary = DarkPrimaryContainer,
    secondaryContainer = DarkPrimaryContainer,
    onSecondaryContainer = DarkOnPrimaryContainer,
    tertiary = DarkPrimary,
    onTertiary = DarkOnPrimary,
    tertiaryContainer = DarkPrimaryContainer,
    onTertiaryContainer = DarkOnPrimaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkPrimary,
    inverseSurface = LightSurface,
    inverseOnSurface = LightOnSurface,
    error = DarkError,
    onError = DarkChipRedContainer,
    errorContainer = DarkChipRedContainer,
    onErrorContainer = DarkChipRedContent,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    surfaceBright = DarkSurfaceVariant,
    surfaceDim = DarkBackground,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = DarkSurfaceVariant,
)

/**
 * Tema de GeoLux Android (DESIGN.md §3–5). Sigue el modo claro u oscuro del
 * sistema (§11). Sin color dinámico: la app usa siempre la paleta de GeoLux.
 */
@Composable
fun GeoLuxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    CompositionLocalProvider(LocalGeoLuxColors provides extendedColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = GeoLuxTypography,
            shapes = GeoLuxShapes,
            content = content,
        )
    }
}

/** Acceso a los colores propios de GeoLux: `GeoLuxTheme.colors.chipAmber`. */
object GeoLuxTheme {
    val colors: GeoLuxExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGeoLuxColors.current
}
