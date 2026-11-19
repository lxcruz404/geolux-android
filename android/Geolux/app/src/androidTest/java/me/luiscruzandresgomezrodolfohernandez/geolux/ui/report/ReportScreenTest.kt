package me.luiscruzandresgomezrodolfohernandez.geolux.ui.report

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Product
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.finca.MiFincaScreen
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Informes PDF: entrada en Mi finca y visor con un PDF real creado en la prueba. */
@RunWith(AndroidJUnit4::class)
class ReportScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val report = report(origin = "real")

    private fun report(origin: String) = Product(
        id = "informe-1",
        kind = "report",
        missionId = null,
        name = "Informe GeoLux",
        createdAt = "2026-10-29T12:00:00+00:00",
        origin = origin,
        crs = "EPSG:4326",
        bounds = listOf(0.0, 0.0, 1.0, 1.0),
        files = listOf("products/informe-1/report.pdf"),
    )

    private fun pdf(pages: Int): File {
        val document = PdfDocument()
        repeat(pages) { index ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
            page.canvas.drawText("Página ${index + 1}", 40f, 60f, Paint().apply { color = Color.BLACK })
            document.finishPage(page)
        }
        val file = File(context.cacheDir, "informe-prueba.pdf")
        file.outputStream().use(document::writeTo)
        document.close()
        return file
    }

    private fun show(file: File?, product: Product = report) =
        compose.setContent { GeoLuxTheme { ReportScreen(product, productFile = { file }) } }

    private fun waitForText(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForPage(description: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty()
    }

    /** Los listados son RecyclerView: se desplazan con el dedo hasta que aparece [target]. */
    private fun scrollUntil(listTag: String, target: SemanticsMatcher) {
        repeat(20) {
            if (compose.onAllNodes(target).fetchSemanticsNodes().isNotEmpty()) return
            compose.onNodeWithTag(listTag).performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        throw AssertionError("No apareció ${target.description} al desplazar $listTag")
    }

    private fun zoomText() = compose.onNodeWithTag("report_pages").fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    @Test
    fun abreElPdfYDibujaSusPaginas() {
        show(pdf(pages = 3))
        waitForPage("Página 1 de 3")
        compose.onNodeWithContentDescription("Página 1 de 3").assertIsDisplayed()
        compose.onNodeWithText("Informe GeoLux").assertIsDisplayed()
        compose.onNodeWithText("29/10/2026 · 3 páginas").assertIsDisplayed()
    }

    @Test
    fun llegaALaUltimaPaginaAlDesplazar() {
        show(pdf(pages = 8))
        waitForPage("Página 1 de 8")
        scrollUntil("report_pages", hasTestTag("report_page_7"))
        waitForPage("Página 8 de 8")
        compose.onNodeWithContentDescription("Página 8 de 8").assertIsDisplayed()
    }

    @Test
    fun pellizcarAcercaLaPagina() {
        show(pdf(pages = 2))
        waitForPage("Página 1 de 2")
        // El zoom es una transformación de la lista entera; su nivel se anuncia en la semántica.
        assertEquals("Zoom 100 %", zoomText())
        compose.onNodeWithTag("report_pages").performTouchInput {
            pinch(center + Offset(-40f, 0f), center + Offset(-200f, 0f), center + Offset(40f, 0f), center + Offset(200f, 0f))
        }
        compose.waitForIdle()
        val zoomed = zoomText().removePrefix("Zoom ").removeSuffix(" %").toInt()
        assertTrue("acercada $zoomed %", zoomed > 100)

        // Y al alejar del todo vuelve a su sitio.
        compose.onNodeWithTag("report_pages").performTouchInput {
            pinch(center + Offset(-300f, 0f), center + Offset(-10f, 0f), center + Offset(300f, 0f), center + Offset(10f, 0f))
        }
        compose.waitForIdle()
        assertEquals("Zoom 100 %", zoomText())
    }

    @Test
    fun sinArchivoAvisa() {
        show(null)
        waitForText("No se pudo abrir este informe")
    }

    @Test
    fun archivoDanadoAvisaSinCerrarLaApp() {
        val broken = File(context.cacheDir, "informe-danado.pdf").apply { writeText("esto no es un PDF") }
        show(broken)
        waitForText("No se pudo abrir este informe")
    }

    @Test
    fun informeSinteticoSeMuestraMarcado() {
        show(pdf(pages = 1), report(origin = "synthetic"))
        waitForText("Sintético · datos de prueba")
    }

    @Test
    fun miFincaListaLosInformesYLosAbre() {
        val demo = FakeProjectRepository.demo
        var opened: String? = null
        compose.setContent {
            GeoLuxTheme {
                MiFincaScreen(demo.copy(products = demo.products + report), onShowZones = {}, onExploreMap = {}, onOpenReport = { opened = it })
            }
        }
        scrollUntil("mi_finca", hasText("Informes"))
        scrollUntil("mi_finca", hasText("1 informe"))
        compose.onNodeWithText("1 informe").assertIsDisplayed()
        scrollUntil("mi_finca", hasText("Informe GeoLux"))
        compose.onNodeWithText("Informe GeoLux").performClick()
        assertEquals(report.id, opened)
    }

    @Test
    fun miFincaSinInformesNoMuestraLaSeccion() {
        compose.setContent {
            GeoLuxTheme { MiFincaScreen(FakeProjectRepository.demo, onShowZones = {}, onExploreMap = {}, onOpenReport = {}) }
        }
        scrollUntil("mi_finca", hasText("Explorar", substring = true))
        assertEquals(0, compose.onAllNodes(hasText("Informes")).fetchSemanticsNodes().size)
    }
}
