package me.luiscruzandresgomezrodolfohernandez.geolux.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fichas del mapa 04b–04d (AND-R3, AND-R8) con datos de finca-demo. */
@RunWith(AndroidJUnit4::class)
class MapFichaTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private var closed = 0

    private fun show(kind: FeatureKind, id: String) = compose.setContent {
        GeoLuxTheme {
            MapFicha(FakeProjectRepository.demo, MapSelection(kind, id), onClose = { closed++ }, onOpenZone = { opened += it })
        }
    }

    @Test
    fun potreroMuestraSusDatosYAbreElDetalle() {
        show(FeatureKind.Zone, "zone-demo-2")
        compose.onNodeWithText("Potrero El Bajo").assertIsDisplayed()
        compose.onNodeWithText("En descanso").assertIsDisplayed()
        compose.onNodeWithText("7.35 ha").assertIsDisplayed()
        compose.onNodeWithText("Sin definir").assertIsDisplayed() // capacidad null en el fixture
        compose.onNodeWithText("Ver detalle del potrero").performScrollTo().performClick()
        assertEquals(listOf("zone-demo-2"), opened)
    }

    @Test
    fun elementoDiceSusCoordenadasYSeCierra() {
        show(FeatureKind.Element, "el-demo-1")
        compose.onNodeWithText("Tanque principal").assertIsDisplayed()
        compose.onNodeWithText("Tanque").assertIsDisplayed()
        compose.onNodeWithText("6.30105, -70.19895").assertIsDisplayed()
        compose.onNodeWithText("Cerrar").performScrollTo().performClick()
        assertEquals(1, closed)
    }

    @Test
    fun misionMuestraElPlanConLasEtiquetasDeDesktop() {
        show(FeatureKind.Mission, "a1b2c3d4-0000-4000-8000-0000000000f1")
        compose.onNodeWithText("Vuelo Potrero La Ceiba").assertIsDisplayed()
        compose.onNodeWithText("Válida").assertIsDisplayed()
        compose.onNodeWithText("60 m").assertIsDisplayed()
        compose.onNodeWithText("1.64 cm/px").assertIsDisplayed()
        compose.onNodeWithText("Viable").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun lineaMuestraCategoriaYLongitud() {
        show(FeatureKind.Line, "line-demo-2")
        compose.onNodeWithText("Tubería").assertIsDisplayed()
        compose.onNodeWithText("106 m").assertIsDisplayed()
    }
}
