package me.luiscruzandresgomezrodolfohernandez.geolux.ui.importing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Issue
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportDecision
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportPreview
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportProgress
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportStep

/**
 * Flujo de importación con el repositorio de prueba. Dispatchers.Unconfined:
 * el Fake no suspende de verdad, así que cada paso termina antes de la aserción.
 */
class ImportControllerTest {

    private val demo = FakeProjectRepository.demo
    private val demoId = demo.project.id

    private fun controller(startImported: Boolean) =
        FakeProjectRepository(startImported = startImported).let { repo ->
            repo to ImportController(repo, CoroutineScope(Dispatchers.Unconfined))
        }

    @Test
    fun sinEspacioDiceCuantoHaceFaltaYCuantoHay() {
        val (_, c) = controller(startImported = false)
        c.inspect { ImportPreview.NoSpace("finca.geolux", 2L * 1024 * 1024 * 1024, 500L * 1024 * 1024) }
        val failed = c.state.value as ImportUiState.Failed
        assertEquals("finca.geolux", failed.fileName)
        assertEquals("no_space", failed.error.code)
        assertEquals(
            "Se necesitan al menos 2.0 GB libres y el teléfono tiene 500.0 MB. Libera espacio y vuelve a intentarlo.",
            failed.error.message,
        )
    }

    @Test
    fun fincaNuevaSeImportaYSeAbre() {
        val (_, c) = controller(startImported = false)
        c.inspect { FakeProjectRepository.previewOf(demo, ImportDecision.NEW) }
        assertTrue(c.state.value is ImportUiState.Preview)
        c.confirmImport()
        assertEquals(ImportUiState.Done(demoId, demo.project.name), c.state.value)
    }

    @Test
    fun mismoPaqueteYaImportadoNoHaceNadaYSabeQueFincaAbrir() {
        val (_, c) = controller(startImported = true)
        c.inspect { FakeProjectRepository.previewOf(demo, ImportDecision.ALREADY_IMPORTED) }
        val state = c.state.value as ImportUiState.AlreadyImported
        // El id viene del propio paquete (ImportPreview.Ready.projectId).
        assertEquals(demoId, state.preview.projectId)
        // «Importar» no aplica en este estado.
        c.confirmImport()
        assertTrue(c.state.value is ImportUiState.AlreadyImported)
    }

    @Test
    fun reemplazarExigeDosRespuestas() {
        val (_, c) = controller(startImported = true)
        c.inspect {
            FakeProjectRepository.previewOf(demo, ImportDecision.ASK_REPLACE, existingPackageCreatedAt = "2026-10-12T12:00:00+00:00")
        }
        val ask = c.state.value as ImportUiState.AskReplace
        assertTrue(ask.preview.isOlderThanExisting)
        // Sin abrir la confirmación, confirmReplace no hace nada.
        c.confirmReplace()
        assertEquals(ask, c.state.value)
        c.requestReplace()
        assertTrue((c.state.value as ImportUiState.AskReplace).confirming)
        c.confirmReplace()
        assertEquals(ImportUiState.Done(demoId, demo.project.name), c.state.value)
    }

    @Test
    fun cancelarLaConfirmacionVuelveAPreguntar() {
        val (_, c) = controller(startImported = true)
        c.inspect { FakeProjectRepository.previewOf(demo, ImportDecision.ASK_REPLACE, "2026-10-01T12:00:00+00:00") }
        c.requestReplace()
        c.dismissReplaceConfirmation()
        assertEquals(false, (c.state.value as ImportUiState.AskReplace).confirming)
        c.cancel()
        assertEquals(ImportUiState.Idle, c.state.value)
    }

    @Test
    fun paqueteRechazadoMuestraElError() {
        val (_, c) = controller(startImported = false)
        val issue = Issue("hash_mismatch", "El archivo layers/zones.geojson está dañado o fue modificado.")
        c.inspect { ImportPreview.Rejected("finca.geolux", issue) }
        assertEquals(ImportUiState.Failed("finca.geolux", issue), c.state.value)
    }

    @Test
    fun errorAlLeerSeConvierteEnFalloDeLectura() {
        val (_, c) = controller(startImported = false)
        c.inspect { throw java.io.IOException("sin permiso") }
        assertEquals(ImportUiState.Failed(null, ImportController.READ_FAILED), c.state.value)
    }

    @Test
    fun codigosDeError() {
        assertEquals(ImportErrorKind.NotAPackage, importErrorKind("not_a_package"))
        assertEquals(ImportErrorKind.Damaged, importErrorKind("hash_mismatch"))
        assertEquals(ImportErrorKind.Damaged, importErrorKind("unsafe_path"))
        assertEquals(ImportErrorKind.TooNew, importErrorKind("too_new"))
        assertEquals(ImportErrorKind.InstallFailed, importErrorKind("install_failed"))
        assertEquals(ImportErrorKind.NoSpace, importErrorKind("no_space"))
        assertEquals(ImportErrorKind.Other, importErrorKind("codigo_nuevo"))
        assertTrue(isIgnoredContentWarning("undeclared_file"))
        assertEquals(false, isIgnoredContentWarning("synthetic_product"))
    }

    @Test
    fun elAvanceSeVeMientrasSeComprueba_ySeBorraAlTerminar() {
        val (_, c) = controller(startImported = false)
        var during: ImportProgress? = null
        c.inspect { onProgress ->
            onProgress(ImportProgress(ImportStep.Verify, 300, 1000))
            during = c.progress.value
            FakeProjectRepository.previewOf(demo, ImportDecision.NEW)
        }
        assertEquals(ImportProgress(ImportStep.Verify, 300, 1000), during)
        assertTrue(c.state.value is ImportUiState.Preview)
        assertEquals(null, c.progress.value)
    }
}
