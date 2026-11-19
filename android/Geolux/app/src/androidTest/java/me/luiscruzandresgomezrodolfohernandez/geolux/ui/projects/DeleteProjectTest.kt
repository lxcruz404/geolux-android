package me.luiscruzandresgomezrodolfohernandez.geolux.ui.projects

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.summary
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «Eliminar proyecto» en Mis fincas: menú de tres puntos de la tarjeta, aviso de que no se
 * puede deshacer y de exportar antes un .glx, y botón definitivo habilitado solo con el nombre
 * exacto de la finca. Cancelar o un nombre distinto no eliminan nada.
 */
@RunWith(AndroidJUnit4::class)
class DeleteProjectTest {
    @get:Rule
    val compose = createComposeRule()
    private val demo = FakeProjectRepository.demo
    private val name = demo.project.name

    private fun show(onDelete: (String, String) -> Unit) {
        compose.setContent {
            GeoLuxTheme {
                ProjectsScreen(
                    state = ProjectsUiState.Content(listOf(ProjectCardUi(demo.summary(), demo))),
                    onImport = {}, onOpen = {}, onDelete = onDelete,
                )
            }
        }
    }

    @Test
    fun soloElNombreExactoHabilitaLaEliminacion() {
        val deleted = mutableListOf<String>()
        show { id, _ -> deleted += id }
        compose.onNodeWithTag("opciones_finca_${demo.project.id}").performClick()
        compose.onNodeWithText("Eliminar proyecto").performClick()
        compose.onNodeWithText("¿Eliminar $name?").assertExists()
        compose.onNodeWithText("Esta acción no se puede deshacer. Antes, exporta un archivo .glx de la finca como respaldo.").assertExists()
        compose.onNodeWithTag("confirmar_eliminar").assertIsNotEnabled()
        // Un nombre parecido no basta (mayúsculas, espacios, incompleto).
        for (wrong in listOf(name.lowercase(), "$name ", name.dropLast(1))) {
            compose.onNodeWithTag("nombre_confirmacion").performTextClearance()
            compose.onNodeWithTag("nombre_confirmacion").performTextInput(wrong)
            compose.onNodeWithTag("confirmar_eliminar").assertIsNotEnabled()
        }
        compose.onNodeWithTag("nombre_confirmacion").performTextClearance()
        compose.onNodeWithTag("nombre_confirmacion").performTextInput(name)
        compose.onNodeWithTag("confirmar_eliminar").assertIsEnabled().performClick()
        assertEquals(listOf(demo.project.id), deleted)
    }

    @Test
    fun cancelarNoEliminaNada() {
        val deleted = mutableListOf<String>()
        show { id, _ -> deleted += id }
        compose.onNodeWithTag("opciones_finca_${demo.project.id}").performClick()
        compose.onNodeWithText("Eliminar proyecto").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("¿Eliminar $name?").assertDoesNotExist()
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun elViewModelEliminaYLaListaQuedaVacia() = runBlocking {
        val repository = FakeProjectRepository(startImported = true)
        var forgotten: String? = null
        val viewModel = ProjectsViewModel(repository, onDeleted = { forgotten = it })
        var result: String? = "pendiente"
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        viewModel.delete(demo.project.id, name) { result = it; done.complete(Unit) }
        done.await()
        assertEquals(null, result)
        assertEquals(demo.project.id, forgotten)
        assertTrue(repository.projects().first().isEmpty())
    }
}
