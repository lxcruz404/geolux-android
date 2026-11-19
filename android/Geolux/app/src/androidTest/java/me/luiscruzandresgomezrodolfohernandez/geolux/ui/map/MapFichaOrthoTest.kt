package me.luiscruzandresgomezrodolfohernandez.geolux.ui.map

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Mission
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** «Ver ortomosaico» en la ficha de misión (04d) con finca-demo. */
@RunWith(AndroidJUnit4::class)
class MapFichaOrthoTest {

    @get:Rule
    val compose = createComposeRule()

    private val demo = FakeProjectRepository.demo
    private val mission = demo.missions.first()

    @Test
    fun misionConOrtomosaicoOfreceVerlo() {
        var shown: Mission? = null
        compose.setContent {
            GeoLuxTheme {
                MapFicha(demo, MapSelection(FeatureKind.Mission, mission.id), onClose = {}, onOpenZone = {}, onShowOrtho = { shown = it })
            }
        }
        compose.onNodeWithTag("ver_ortomosaico").performScrollTo().performClick()
        assertEquals(mission, shown)
    }

    @Test
    fun sinOrtomosaicoDeEsaMisionNoHayBoton() {
        val sinOrtho = demo.copy(products = demo.products.filter { it.kind != "orthomosaic" })
        compose.setContent {
            GeoLuxTheme {
                MapFicha(sinOrtho, MapSelection(FeatureKind.Mission, mission.id), onClose = {}, onOpenZone = {}, onShowOrtho = {})
            }
        }
        compose.onNodeWithTag("ver_ortomosaico").assertDoesNotExist()
    }

    @Test
    fun sinAccionNoHayBoton() {
        compose.setContent {
            GeoLuxTheme { MapFicha(demo, MapSelection(FeatureKind.Mission, mission.id), onClose = {}, onOpenZone = {}) }
        }
        compose.onNodeWithTag("ver_ortomosaico").assertDoesNotExist()
    }
}
