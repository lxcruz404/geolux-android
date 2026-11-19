package me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Tipografía de docs/android/DESIGN.md §4: Roboto (fuente del sistema), tamaños
// en sp para respetar el tamaño de letra del sistema. Pesos: 400 texto, 500
// botones y pestañas, 600 títulos y cifras. Los estilos que §4 no nombra quedan
// con el valor de Material 3; el interlineado también es el de Material 3.

private val GeoLuxFontFamily = FontFamily.Default

private val Base = Typography()

val GeoLuxTypography = Typography(
    // Cifra de un dato clave («245.30»)
    displaySmall = Base.displaySmall.copy(
        fontFamily = GeoLuxFontFamily,
        fontSize = 36.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    // Nombre de la finca
    headlineSmall = Base.headlineSmall.copy(
        fontFamily = GeoLuxFontFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    // Barra superior
    titleLarge = Base.titleLarge.copy(
        fontFamily = GeoLuxFontFamily,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    // Título de tarjeta y de sección
    titleMedium = Base.titleMedium.copy(
        fontFamily = GeoLuxFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    // Texto principal
    bodyLarge = Base.bodyLarge.copy(
        fontFamily = GeoLuxFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
    // Texto secundario (mínimo para contenido)
    bodyMedium = Base.bodyMedium.copy(
        fontFamily = GeoLuxFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
    ),
    // Botones y pestañas
    labelLarge = Base.labelLarge.copy(
        fontFamily = GeoLuxFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
    // Solo chips
    labelMedium = Base.labelMedium.copy(
        fontFamily = GeoLuxFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    ),
)
