package me.luiscruzandresgomezrodolfohernandez.geolux.ui.report

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * PDF abierto con [PdfRenderer] del sistema. El renderer solo admite una página
 * abierta a la vez y no se puede usar desde dos hilos: todo pasa por [mutex].
 */
internal class ReportDocument private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    /** Alto / ancho de cada página: reserva su sitio en la lista antes de dibujarla. */
    val pageRatios: List<Float>,
) {
    private val mutex = Mutex()
    private var closed = false

    val pageCount: Int get() = pageRatios.size

    /** La página [index] sobre blanco, de [widthPx] de ancho; null si no se pudo dibujar. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed) return@withLock null
            try {
                renderer.openPage(index).use { page ->
                    Bitmap.createBitmap(widthPx, (widthPx * pageRatios[index]).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).apply {
                        eraseColor(Color.WHITE)
                        page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) { // Una página ausente no debe cerrar la app.
                null
            }
        }
    }

    suspend fun close() = mutex.withLock {
        closed = true
        renderer.close()
        descriptor.close()
    }

    companion object {
        /** Abre [file]. Lanza una excepción si está dañado o protegido con contraseña. */
        fun open(file: File): ReportDocument {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(descriptor)
                val ratios = List(renderer.pageCount) { index ->
                    renderer.openPage(index).use { it.height.toFloat() / it.width }
                }
                return ReportDocument(descriptor, renderer, ratios)
            } catch (e: Exception) {
                descriptor.close()
                throw e
            }
        }
    }
}
