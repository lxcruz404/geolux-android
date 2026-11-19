package me.luiscruzandresgomezrodolfohernandez.geolux.ui.report

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Product
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.ChipFamily
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.StatusChip
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatDate
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.screens.PlaceholderScreen
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.RecyclerList
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.rememberRecyclerListState

/** Al doble del ancho de la pantalla el texto se sigue leyendo al acercar. */
private const val RENDER_SCALE = 2
private const val MAX_RENDER_WIDTH_PX = 2000
private const val MAX_ZOOM = 4f

/**
 * Informe PDF de la finca (producto `report`). Se abre sin conexión con el
 * [android.graphics.pdf.PdfRenderer] del sistema, sin depender de otra app.
 * Dentro de la app: un dedo desplaza, dos dedos acercan.
 */
@Composable
fun ReportScreen(
    report: Product,
    /** Archivo local del producto (`ProjectRepository.productFile`). */
    productFile: suspend (productId: String) -> File?,
    modifier: Modifier = Modifier,
) {
    val state by produceState<ReportState>(ReportState.Loading, report.id) {
        val document = try {
            productFile(report.id)?.let { withContext(Dispatchers.IO) { ReportDocument.open(it) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { // Dañado o con contraseña: se avisa en vez de cerrar la app.
            null
        }
        if (document == null) {
            value = ReportState.Unavailable
            return@produceState
        }
        value = ReportState.Ready(document)
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { document.close() }
        }
    }

    val current = state
    if (current !is ReportState.Ready) {
        PlaceholderScreen(
            title = report.name,
            body = stringResource(if (current == ReportState.Loading) R.string.report_opening else R.string.report_unavailable),
            icon = R.drawable.ic_description,
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(GeoLuxDimens.screenMargin), verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.spacingUnit)) {
                Text(report.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatDate(report.createdAt)} · " +
                        pluralStringResource(R.plurals.page_count, current.document.pageCount, current.document.pageCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (report.isSynthetic) StatusChip(stringResource(R.string.chip_synthetic), ChipFamily.Amber)
            }
        }
        Pages(current.document, Modifier.fillMaxWidth().weight(1f))
    }
}

/** Las páginas en lista, con pellizco para acercar y arrastre para moverse por la página acercada. */
@Composable
private fun Pages(document: ReportDocument, modifier: Modifier) {
    val listState = rememberRecyclerListState()
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(listState) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        // Pase inicial: gana a la lista. Sin acercar y con un dedo, desplaza la lista.
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } < 2 && zoom == 1f) continue
                        zoom = (zoom * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                        val pan = event.calculatePan()
                        val maxX = size.width * (zoom - 1f) / 2
                        val maxY = size.height * (zoom - 1f) / 2
                        val targetY = offsetY + pan.y
                        offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                        offsetY = targetY.coerceIn(-maxY, maxY)
                        // Al llegar al borde de lo acercado, sigue con la página siguiente o anterior.
                        listState.scrollBy((offsetY - targetY) / zoom)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val renderWidth = (constraints.maxWidth * RENDER_SCALE).coerceAtMost(MAX_RENDER_WIDTH_PX)
        RecyclerList(
            state = listState,
            contentPadding = PaddingValues(GeoLuxDimens.spacingUnit * 2),
            verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.spacingUnit * 2),
            modifier = Modifier
                .fillMaxSize()
                .testTag("report_pages")
                // Las páginas van en otras vistas (filas del RecyclerView): el zoom se anuncia aquí.
                .semantics { stateDescription = "Zoom ${(zoom * 100).roundToInt()} %" }
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offsetX
                    translationY = offsetY
                },
        ) {
            items(document.pageCount) { index ->
                Page(document, index, renderWidth, Modifier.fillMaxWidth().aspectRatio(1f / document.pageRatios[index]))
            }
        }
    }
}

/** Una página: se dibuja al entrar en la lista y se libera al salir de ella. */
@Composable
private fun Page(document: ReportDocument, index: Int, renderWidth: Int, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, document, index, renderWidth) {
        value = document.render(index, renderWidth)
    }
    Box(modifier.background(Color.White).testTag("report_page_$index")) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = stringResource(R.string.report_page, index + 1, document.pageCount),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Estado del informe en la pantalla. */
private sealed interface ReportState {
    data object Loading : ReportState
    data class Ready(val document: ReportDocument) : ReportState

    /** Falta el archivo, está dañado o tiene contraseña. */
    data object Unavailable : ReportState
}
