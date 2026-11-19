package me.luiscruzandresgomezrodolfohernandez.geolux.ui.model3d

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens

/** Paso de cada pulsación; manteniendo pulsado se repite. */
private const val ROTATE_STEP = (PI / 12).toFloat() // 15°
private const val ZOOM_STEP = 1.25f

/** Avance por pulsación, proporcional a la distancia: cerca, pasos cortos. */
private fun moveStep(view: TerrainView) = view.distance * 0.08f

/**
 * Botonera del visor 3D: flechas para moverse por el piso (↑ avanza hacia donde se
 * mira), girar, acercar y, en el centro de las flechas, volver a la vista inicial.
 * Cambia [view], que SceneView lee en cada fotograma.
 */
@Composable
internal fun TerrainControls(view: TerrainView, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (compact) {
        // Pantalla completa: solo lo que no cubren los gestos (girar, acercar, ajustar y
        // volver a la vista inicial), en una columna estrecha de botones pequeños. Mover se
        // hace con dos dedos.
        Column(modifier) {
            Row {
                ControlButton(R.drawable.ic_rotate_left, stringResource(R.string.model3d_rotate_left), "3d_girar_izquierda", small = true) { view.rotate(ROTATE_STEP) }
                ControlButton(R.drawable.ic_rotate_right, stringResource(R.string.model3d_rotate_right), "3d_girar_derecha", small = true) { view.rotate(-ROTATE_STEP) }
            }
            Row {
                ControlButton(R.drawable.ic_remove, stringResource(R.string.model3d_zoom_out), "3d_alejar", small = true) { view.zoom(1 / ZOOM_STEP) }
                ControlButton(R.drawable.ic_add, stringResource(R.string.model3d_zoom_in), "3d_acercar", small = true) { view.zoom(ZOOM_STEP) }
            }
            Row {
                ControlButton(R.drawable.ic_fit_screen, stringResource(R.string.model3d_fit), "3d_ajustar", repeat = false, small = true) { view.fit() }
                ControlButton(R.drawable.ic_zoom_out_map, stringResource(R.string.model3d_recenter), "3d_recentrar", repeat = false, small = true) { view.reset() }
            }
        }
        return
    }
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Column {
            ControlButton(R.drawable.ic_fit_screen, stringResource(R.string.model3d_fit), "3d_ajustar", repeat = false) { view.fit() }
            Row {
                ControlButton(R.drawable.ic_rotate_left, stringResource(R.string.model3d_rotate_left), "3d_girar_izquierda") { view.rotate(ROTATE_STEP) }
                ControlButton(R.drawable.ic_rotate_right, stringResource(R.string.model3d_rotate_right), "3d_girar_derecha") { view.rotate(-ROTATE_STEP) }
            }
            Row {
                ControlButton(R.drawable.ic_remove, stringResource(R.string.model3d_zoom_out), "3d_alejar") { view.zoom(1 / ZOOM_STEP) }
                ControlButton(R.drawable.ic_add, stringResource(R.string.model3d_zoom_in), "3d_acercar") { view.zoom(ZOOM_STEP) }
            }
        }
        Spacer(Modifier.size(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ControlButton(R.drawable.ic_arrow_upward, stringResource(R.string.model3d_forward), "3d_avanzar") {
                view.move(right = 0f, forward = moveStep(view))
            }
            Row {
                ControlButton(R.drawable.ic_arrow_upward, stringResource(R.string.model3d_left), "3d_izquierda", iconRotation = -90f) {
                    view.move(right = -moveStep(view), forward = 0f)
                }
                ControlButton(R.drawable.ic_zoom_out_map, stringResource(R.string.model3d_recenter), "3d_recentrar", repeat = false) {
                    view.reset()
                }
                ControlButton(R.drawable.ic_arrow_upward, stringResource(R.string.model3d_right), "3d_derecha", iconRotation = 90f) {
                    view.move(right = moveStep(view), forward = 0f)
                }
            }
            ControlButton(R.drawable.ic_arrow_upward, stringResource(R.string.model3d_back), "3d_retroceder", iconRotation = 180f) {
                view.move(right = 0f, forward = -moveStep(view))
            }
        }
    }
}

/**
 * Botón redondo de 48 dp (más 2 dp de separación). Al tocarlo actúa una vez; si se mantiene pulsado, repite
 * hasta soltarlo ([repeat]). Para TalkBack es un botón normal.
 */
@Composable
private fun ControlButton(
    @DrawableRes icon: Int,
    description: String,
    tag: String,
    iconRotation: Float = 0f,
    repeat: Boolean = true,
    small: Boolean = false,
    onPress: () -> Unit,
) {
    val action by rememberUpdatedState(onPress)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(2.dp)
            .size(if (small) SMALL_BUTTON else GeoLuxDimens.mapFloatingButton)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (small) 0.88f else 1f), RoundedCornerShape(50))
            .testTag(tag)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick { action(); true }
            }
            .pointerInput(repeat) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown()
                        action()
                        val repeating = if (repeat) {
                            launch {
                                delay(REPEAT_AFTER_MS)
                                while (true) {
                                    action()
                                    delay(REPEAT_EVERY_MS)
                                }
                            }
                        } else {
                            null
                        }
                        waitForUpOrCancellation()
                        repeating?.cancel()
                    }
                }
            },
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.rotate(iconRotation),
        )
    }
}

/** Botón de la pantalla completa: 44 dp, aún cómodo de tocar y casi sin tapar el modelo. */
private val SMALL_BUTTON = 44.dp

private const val REPEAT_AFTER_MS = 400L
private const val REPEAT_EVERY_MS = 80L
