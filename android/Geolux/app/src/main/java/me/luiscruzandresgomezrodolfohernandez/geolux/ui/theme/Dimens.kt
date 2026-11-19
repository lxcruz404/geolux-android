package me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme

import androidx.compose.ui.unit.dp

// Espaciado, tamaños táctiles y elevación de docs/android/DESIGN.md §5–6 y §8.
// Todo en múltiplos de 4 dp.

object GeoLuxDimens {
    val spacingUnit = 4.dp

    val screenMargin = 16.dp
    val cardPadding = 16.dp
    val cardSpacing = 12.dp
    val sectionSpacing = 24.dp

    /** Objetivo táctil mínimo: 48 × 48 dp. */
    val minTouchTarget = 48.dp
    /** Botones flotantes del mapa: Capas, Mi ubicación, Ver toda la finca. */
    val mapFloatingButton = 48.dp

    /** Material Symbols Rounded (§6). */
    val icon = 24.dp

    /** Filas de lista (§8). */
    val listRowMinHeight = 56.dp
    val listRowMaxHeight = 72.dp

    /**
     * Las tarjetas son planas con borde `outline` (§5). El grosor no está en
     * DESIGN.md; 1 dp es el de Material 3 para OutlinedCard.
     */
    val cardBorder = 1.dp
    val cardElevation = 0.dp
}
