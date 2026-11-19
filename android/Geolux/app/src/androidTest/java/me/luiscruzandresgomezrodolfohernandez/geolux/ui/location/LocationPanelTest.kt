package me.luiscruzandresgomezrodolfohernandez.geolux.ui.location

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.luiscruzandresgomezrodolfohernandez.geolux.data.LocationState
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Estados de GPS de 04f (AND-R5) con datos de prueba de finca-demo. */
@RunWith(AndroidJUnit4::class)
class LocationPanelTest {

    @get:Rule
    val compose = createComposeRule()

    private var requested = 0
    private var appSettings = 0
    private var locationSettings = 0

    private fun show(state: LocationState?, blocked: Boolean = false) {
        compose.setContent {
            GeoLuxTheme {
                LocationPanel(
                    state = state,
                    data = FakeProjectRepository.demo,
                    permissionBlocked = blocked,
                    onRequestPermission = { requested++ },
                    onOpenAppSettings = { appSettings++ },
                    onOpenLocationSettings = { locationSettings++ },
                )
            }
        }
    }

    @Test
    fun sinPermisoPideActivar() {
        show(LocationState.NoPermission)
        compose.onNodeWithText("Activa la ubicación").assertIsDisplayed()
        compose.onNodeWithText("Activar").performClick()
        assertEquals(1, requested)
    }

    @Test
    fun permisoNegadoParaSiempreLlevaAAjustes() {
        show(LocationState.NoPermission, blocked = true)
        compose.onNodeWithText("Abrir ajustes").performClick()
        assertEquals(0, requested)
        assertEquals(1, appSettings)
    }

    @Test
    fun ubicacionApagadaLlevaAAjustesDeUbicacion() {
        show(LocationState.Disabled)
        compose.onNodeWithText("La ubicación está apagada").assertIsDisplayed()
        compose.onNodeWithText("Abrir ajustes").performClick()
        assertEquals(1, locationSettings)
    }

    @Test
    fun buscandoSenal() {
        show(LocationState.Searching)
        compose.onNodeWithText("Buscando señal GPS…").assertIsDisplayed()
    }

    @Test
    fun conPosicionMuestraPrecisionYPotrero() {
        show(LocationState.Fix(lat = 6.301, lon = -70.1985, accuracyM = 5f))
        compose.onNodeWithText("Precisión ±5 m").assertIsDisplayed()
        compose.onNodeWithText("Estás en Potrero La Ceiba").assertIsDisplayed()
        compose.onNodeWithText("6.30100, -70.19850").assertIsDisplayed()
    }

    @Test
    fun sinRespuestaNoMuestraNada() {
        show(null)
        compose.onNodeWithTag("gps_searching").assertDoesNotExist()
        compose.onNodeWithTag("gps_no_permission").assertDoesNotExist()
    }
}
