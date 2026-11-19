package me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeLocationSource
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.GeoLuxApp
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.finca.FincaViewModel
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.importing.ImportViewModel
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.projects.ProjectsViewModel
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navegación con navigation-compose: Mis fincas → finca → pestañas y detalles, y el
 * botón atrás deshaciendo un paso cada vez (detalle → pestaña → Mi finca → Mis fincas).
 */
@RunWith(AndroidJUnit4::class)
class GeoLuxNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val demo = FakeProjectRepository.demo
    private lateinit var nav: NavHostController

    private fun launch() {
        val repository = FakeProjectRepository()
        compose.setContent {
            nav = rememberNavController()
            GeoLuxTheme {
                GeoLuxApp(
                    projectsViewModel = ProjectsViewModel(repository, onDeleted = {}),
                    fincaViewModel = FincaViewModel(repository),
                    importViewModel = ImportViewModel(repository),
                    locationSource = FakeLocationSource(),
                    orthoTiles = { _, _ -> null },
                    productFile = { _, _ -> null },
                    navController = nav,
                )
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Abrir finca").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Los listados son RecyclerView: se desliza el dedo hasta que la fila aparece. */
    private fun swipeUntilShown(text: String) {
        repeat(12) {
            if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            onView(isAssignableFrom(RecyclerView::class.java)).perform(swipeUp())
            compose.waitForIdle()
        }
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun openFinca() {
        swipeUntilShown("Abrir finca")
        compose.onAllNodesWithText("Abrir finca").onFirst().performClick()
        compose.waitUntil(5_000) { nav.currentBackStackEntry?.destination?.hasRoute<FincaRoute>() == true }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Potreros").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab_MiFinca").assertIsSelected()
    }

    @Test
    fun abrirUnaFincaYVolverAMisFincas() {
        launch()
        openFinca()
        assertEquals(demo.project.id, nav.currentBackStackEntry?.toRoute<FincaRoute>()?.projectId)
        back()
        assertTrue(nav.currentBackStackEntry?.destination?.hasRoute<MisFincasRoute>() == true)
        compose.onNodeWithText("Abrir finca").assertExists()
    }

    @Test
    fun atrasDesdeElDetalleVuelveALaListaLuegoAMiFincaYLuegoAMisFincas() {
        launch()
        openFinca()
        val zone = demo.zones.first()
        compose.onNodeWithTag("tab_Potreros").performClick()
        compose.onNodeWithTag("tab_Potreros").assertIsSelected()
        compose.onAllNodesWithText(zone.name).onFirst().performClick()
        compose.onNodeWithText("Detalle de potrero").assertExists()
        // El detalle sigue marcando la pestaña Potreros.
        compose.onNodeWithTag("tab_Potreros").assertIsSelected()

        back() // detalle → lista de potreros
        compose.onNodeWithText("Detalle de potrero").assertDoesNotExist()
        compose.onNodeWithTag("tab_Potreros").assertIsSelected()

        back() // Potreros → Mi finca (inicio de la finca)
        compose.onNodeWithTag("tab_MiFinca").assertIsSelected()

        back() // Mi finca → Mis fincas
        assertTrue(nav.currentBackStackEntry?.destination?.hasRoute<MisFincasRoute>() == true)
    }

    @Test
    fun cambiarDePestanaGuardaElDetalleYTocarLaMismaVuelveASuRaiz() {
        launch()
        openFinca()
        val zone = demo.zones.first()
        compose.onNodeWithTag("tab_Potreros").performClick()
        compose.onAllNodesWithText(zone.name).onFirst().performClick()
        compose.onNodeWithText("Detalle de potrero").assertExists()

        // Otra pestaña y de vuelta: el detalle sigue abierto (restoreState).
        compose.onNodeWithTag("tab_Ganado").performClick()
        compose.onNodeWithTag("tab_Ganado").assertIsSelected()
        compose.onNodeWithText("Detalle de potrero").assertDoesNotExist()
        compose.onNodeWithTag("tab_Potreros").performClick()
        compose.onNodeWithText("Detalle de potrero").assertExists()

        // Tocar Potreros estando en Potreros vuelve a la lista.
        compose.onNodeWithTag("tab_Potreros").performClick()
        compose.onNodeWithText("Detalle de potrero").assertDoesNotExist()
        compose.onNodeWithTag("tab_Potreros").assertIsSelected()
    }

    @Test
    fun verEnElMapaAbreElMapaConElPotreroComoArgumento() {
        launch()
        openFinca()
        val zone = demo.zones.first()
        compose.onNodeWithTag("tab_Potreros").performClick()
        compose.onAllNodesWithText(zone.name).onFirst().performClick()
        swipeUntilShown("Ver en el mapa")
        compose.onNodeWithText("Ver en el mapa").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("tab_Mapa").assertIsSelected()
        val entry = nav.currentBackStackEntry
        assertTrue(entry?.destination?.hasRoute<FincaRoute>() == true)
        // Atrás desde el mapa vuelve a Mi finca, el inicio de la finca.
        back()
        compose.onNodeWithTag("tab_MiFinca").assertIsSelected()
    }
}
