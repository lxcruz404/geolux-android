package me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Formas de docs/android/DESIGN.md §5: 8 dp chips; 12 dp tarjetas y botones;
// 16 dp diálogos; 28 dp borde superior de hojas inferiores.

val GeoLuxShapes = Shapes(
    small = RoundedCornerShape(8.dp), // chips (Material 3 usa `small` en chips)
    medium = RoundedCornerShape(12.dp), // tarjetas (Material 3 usa `medium` en Card)
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Formas con nombre para los componentes cuyo valor por defecto en Material 3
 * no coincide con §5: los botones son píldora y los diálogos usan
 * `extraLarge` (28 dp). Las pantallas pasan estas formas de forma explícita.
 */
object GeoLuxShape {
    val chip = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(12.dp)
    val button = RoundedCornerShape(12.dp)
    val dialog = RoundedCornerShape(16.dp)
    val bottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}
