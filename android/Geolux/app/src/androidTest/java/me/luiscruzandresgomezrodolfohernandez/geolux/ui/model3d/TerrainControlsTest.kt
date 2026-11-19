package me.luiscruzandresgomezrodolfohernandez.geolux.ui.model3d

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Botonera del visor 3D: cada botón mueve la cámara y Recentrar la devuelve al inicio. */
@RunWith(AndroidJUnit4::class)
class TerrainControlsTest {
    @get:Rule
    val compose = createComposeRule()

    private val view = TerrainView()

    private fun show() = compose.setContent { GeoLuxTheme { TerrainControls(view) } }

    @Test
    fun todosLosBotonesSeVenYSeLeenConTalkBack() {
        show()
        listOf(
            "Avanzar", "Retroceder", "Mover a la izquierda", "Mover a la derecha",
            "Girar a la izquierda", "Girar a la derecha", "Acercar", "Alejar", "Volver a la vista inicial",
        ).forEach { compose.onNodeWithContentDescription(it).assertIsDisplayed().assertHasClickAction() }
    }

    @Test
    fun lasFlechasMuevenPorElPisoYRecentrarVuelve() {
        show()
        compose.onNodeWithTag("3d_avanzar").performClick()
        compose.waitForIdle()
        assertTrue(view.targetZ < 0f) // ↑ avanza hacia donde se mira (−z en la vista inicial)
        compose.onNodeWithTag("3d_derecha").performClick()
        compose.waitForIdle()
        assertTrue(view.targetX > 0f)

        compose.onNodeWithTag("3d_girar_izquierda").performClick()
        compose.onNodeWithTag("3d_acercar").performClick()
        compose.waitForIdle()
        assertTrue(view.yaw != TerrainView.HOME_YAW)
        assertTrue(view.distance < TerrainView.HOME_DISTANCE)

        compose.onNodeWithTag("3d_recentrar").performClick()
        compose.waitForIdle()
        assertEquals(0f, view.targetX)
        assertEquals(0f, view.targetZ)
        assertEquals(TerrainView.HOME_YAW, view.yaw)
        assertEquals(TerrainView.HOME_DISTANCE, view.distance)
    }

    @Test
    fun unToqueEsUnPasoYMantenerPulsadoRepite() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("3d_girar_derecha").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        val oneStep = TerrainView().apply { rotate(-(Math.PI / 12).toFloat()) }.yaw
        assertEquals(oneStep, view.yaw, 1e-4f)

        view.reset()
        compose.onNodeWithTag("3d_alejar").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000) // 400 ms de espera y luego cada 80 ms
        compose.onNodeWithTag("3d_alejar").performTouchInput { up() }
        assertTrue(view.distance > TerrainView.HOME_DISTANCE * 1.25f * 1.25f)
    }

    @Test
    fun talkBackActivaElBotonUnaVez() {
        show()
        compose.onNodeWithTag("3d_retroceder").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue(view.targetZ > 0f)
    }
}
