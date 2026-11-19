package me.luiscruzandresgomezrodolfohernandez.geolux.ui.management

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.LocalDate
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementOps
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.livestock.GanadoScreen
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Formularios de Gestión y Ganado: si la validación falla, el diálogo sigue abierto,
 * muestra el error y conserva lo escrito; si guarda, se cierra y avisa.
 */
@RunWith(AndroidJUnit4::class)
class FormDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private val demo = FakeProjectRepository.demo
    private val today = LocalDate.parse("2026-11-11")
    private var n = 0
    private val messages = mutableListOf<String>()

    private fun viewModel(): ManagementViewModel {
        val ops = ManagementOps(now = { Instant.parse("2026-11-11T12:00:00Z") }, newId = { "f-${++n}" })
        return ManagementViewModel(FakeProjectRepository(), ops).also { it.open(demo.project.id) }
    }

    private fun waitFor(tag: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitGone(tag: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }

    @Test
    fun nuevaListaSinNombreNoSeCierra() {
        val vm = viewModel()
        compose.setContent {
            GeoLuxTheme {
                val state by vm.state.collectAsState()
                GestionScreen(demo, state, vm, onMessage = { messages += it }, today = today)
            }
        }
        compose.onNodeWithTag("gestion_Compras").performClick()
        compose.onNodeWithText("Nueva lista").performClick()
        compose.onNodeWithTag("lista_nombre").performTextInput("   ")
        compose.onNodeWithTag("guardar").performClick()
        waitFor("error_formulario")
        compose.onNodeWithTag("lista_nombre").assertIsDisplayed()
        assertEquals(emptyList<String>(), messages)

        compose.onNodeWithTag("lista_nombre").performTextInput("Insumos")
        compose.onNodeWithTag("guardar").performClick()
        waitGone("lista_nombre")
        assertEquals(listOf("Lista creada."), messages)
    }

    @Test
    fun nuevoAnimalSinIdentificacionConservaLoEscrito() {
        val vm = viewModel()
        compose.setContent {
            GeoLuxTheme {
                val state by vm.state.collectAsState()
                GanadoScreen(demo, state, vm, onMessage = { messages += it }, onCreateActivity = {}, today = today)
            }
        }
        waitFor("ganado_Animales")
        compose.onNodeWithTag("ganado_Animales").performClick()
        compose.onNodeWithTag("nuevo_animal").performClick()
        compose.onNodeWithTag("alta_nombre").performTextInput("Lola")
        compose.onNodeWithTag("guardar").performClick()
        waitFor("error_formulario")
        compose.onNodeWithTag("error_formulario").assertTextContains("identificación", substring = true)
        compose.onNodeWithTag("alta_nombre").assertTextContains("Lola")
        assertEquals(emptyList<String>(), messages)

        compose.onNodeWithTag("alta_tag").performTextInput("2001")
        compose.onNodeWithTag("guardar").performClick()
        waitGone("alta_tag")
        assertEquals(listOf("Animal registrado."), messages)
        compose.onNodeWithTag("animal_2001").assertExists()
    }
}
