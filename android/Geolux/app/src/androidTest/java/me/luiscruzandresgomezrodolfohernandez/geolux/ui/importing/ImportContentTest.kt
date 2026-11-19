package me.luiscruzandresgomezrodolfohernandez.geolux.ui.importing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Issue
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportDecision
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ProductPreview
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportProgress
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportStep

/** Pantallas 02a–02e con datos de prueba de finca-demo (AND-R7, prueba instrumentada). */
@RunWith(AndroidJUnit4::class)
class ImportContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val demo = FakeProjectRepository.demo
    private val preview = FakeProjectRepository.previewOf(demo, ImportDecision.NEW)

    private fun show(state: ImportUiState, actions: ImportActions = ImportActions()) {
        compose.setContent { GeoLuxTheme { ImportContent(state, actions) } }
    }

    @Test
    fun vistaPreviaMuestraFincaConteosYProductosSinteticos() {
        var imported = false
        show(ImportUiState.Preview(preview), ImportActions(onImport = { imported = true }))
        compose.onNodeWithText("finca-demo.geolux").assertIsDisplayed()
        compose.onNodeWithText(demo.project.name).assertIsDisplayed()
        compose.onNodeWithText("Fecha de creación: 09/10/2026").assertIsDisplayed()
        // Los dos productos de finca-demo son sintéticos: los dos llevan la etiqueta.
        assertEquals(2, compose.onAllNodesWithTextCount("Sintético · datos de prueba"))
        compose.onNodeWithTag("import_now").performScrollTo().performClick()
        assertTrue(imported)
    }

    @Test
    fun reemplazarMarcaMasAntiguoYPideConfirmacion() {
        var requested = false
        val older = preview.copy(decision = ImportDecision.ASK_REPLACE, existingPackageCreatedAt = "2026-10-12T12:00:00+00:00")
        show(ImportUiState.AskReplace(older), ImportActions(onRequestReplace = { requested = true }))
        compose.onNodeWithText("¿Reemplazar ${demo.project.name}?").assertIsDisplayed()
        compose.onNodeWithText("12/10/2026").assertIsDisplayed()
        compose.onNodeWithText("Más antiguo").assertIsDisplayed()
        compose.onNodeWithText("Se pedirá confirmación antes de reemplazar.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("replace").performScrollTo().performClick()
        assertTrue(requested)
    }

    @Test
    fun yaImportadoPermiteAbrirLaFinca() {
        var opened: String? = null
        show(ImportUiState.AlreadyImported(preview), ImportActions(onOpenFinca = { opened = it }))
        compose.onNodeWithText("Este paquete ya está importado").assertIsDisplayed()
        compose.onNodeWithText("Abrir finca").performClick()
        assertEquals(demo.project.id, opened)
    }

    @Test
    fun errorDeIntegridadSinCodigosNiRutas() {
        show(ImportUiState.Failed("finca-demo.geolux", Issue("hash_mismatch", "El archivo layers/zones.geojson está dañado o fue modificado.")))
        compose.onNodeWithText("El archivo está dañado o fue modificado").assertIsDisplayed()
        // DESIGN.md §10: ni el código ni la ruta interna del paquete.
        assertEquals(0, compose.onAllNodesWithTextCount("hash_mismatch", substring = true))
        assertEquals(0, compose.onAllNodesWithTextCount("layers/zones.geojson", substring = true))
        compose.onNodeWithText("Elegir otro archivo").assertIsDisplayed()
    }

    @Test
    fun versionNuevaPideActualizar() {
        show(ImportUiState.Failed("f.geolux", Issue("too_new", "El paquete se creó con una versión más nueva de GeoLux (formato 2); actualiza la aplicación para abrirlo.")))
        compose.onNodeWithText("Se creó con una versión más nueva de GeoLux").assertIsDisplayed()
        compose.onNodeWithText("formato 2", substring = true).assertIsDisplayed()
    }

    @Test
    fun sinEspacioExplicaCuantoHaceFaltaYPermiteElegirOtro() {
        val message = "Se necesitan al menos 2.0 GB libres y el teléfono tiene 500.0 MB. Libera espacio y vuelve a intentarlo."
        show(ImportUiState.Failed("f.geolux", Issue("no_space", message)))
        compose.onNodeWithText("No hay espacio suficiente en el teléfono").assertIsDisplayed()
        compose.onNodeWithText(message).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTextCount("no_space", substring = true))
        compose.onNodeWithText("Elegir otro archivo").assertIsDisplayed()
    }

    @Test
    fun informeSeRotulaComoInformeYUnTipoDesconocidoComoProducto() {
        val products = listOf(
            ProductPreview("report", "Informe GeoLux", isSynthetic = false),
            ProductPreview("dsm", "Modelo de superficie", isSynthetic = false),
        )
        show(ImportUiState.Preview(preview.copy(products = products)))
        // ListRow muestra el rótulo en mayúsculas.
        compose.onNodeWithText("INFORME").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("PRODUCTO").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun importadaOfreceAbrirFinca() {
        show(ImportUiState.Done(demo.project.id, demo.project.name))
        compose.onNodeWithText("Finca importada").assertIsDisplayed()
        compose.onNodeWithText("Abrir finca").performScrollTo().assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(
        text: String,
        substring: Boolean = false,
    ): Int = onAllNodes(androidx.compose.ui.test.hasText(text, substring = substring)).fetchSemanticsNodes().size

    @Test
    fun comprobandoMuestraElAvanceEnMegabytes() {
        compose.setContent {
            GeoLuxTheme {
                ImportContent(ImportUiState.Checking, ImportActions(), progress = ImportProgress(ImportStep.Copy, 300L * 1024 * 1024, 1024L * 1024 * 1024))
            }
        }
        compose.onNodeWithTag("import_progress").assertIsDisplayed()
        compose.onNodeWithText("300.0 MB de 1.0 GB").assertIsDisplayed()
    }

    @Test
    fun importandoMuestraElAvanceDeLaInstalacion() {
        compose.setContent {
            GeoLuxTheme {
                ImportContent(ImportUiState.Importing(preview), ImportActions(), progress = ImportProgress(ImportStep.Install, 512L * 1024, 1024L * 1024))
            }
        }
        compose.onNodeWithText("512.0 KB de 1.0 MB").assertIsDisplayed()
    }
}
