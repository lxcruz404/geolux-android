package me.luiscruzandresgomezrodolfohernandez.geolux.ui.management

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementReportContent

/**
 * Dibuja el informe de gestión en PDF A4 con `PdfDocument` (sin dependencias): título,
 * resumen, tablas con encabezado verde GeoLux y «Página n de N» en cada hoja. Las celdas
 * largas se recortan con «…» para que la tabla siga legible en vertical.
 */
object ReportPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 36f
    private const val FOOTER = 30f
    private val green = Color.rgb(0x2E, 0x6B, 0x4F)
    private val ink = Color.rgb(0x1D, 0x2A, 0x22)
    private val muted = Color.rgb(0x6B, 0x7A, 0x71)
    private val headerFill = Color.rgb(0xE6, 0xEF, 0xE9)

    private sealed interface Op { val height: Float }
    private data class TextOp(val text: String, val size: Float, val color: Int, val bold: Boolean = false, val italic: Boolean = false, override val height: Float) : Op
    private data class RowOp(val cells: List<String>, val header: Boolean, override val height: Float = 16f) : Op

    private fun paint(size: Float, color: Int, bold: Boolean = false, italic: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, when {
            bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL
        })
    }

    private fun wrap(text: String, p: Paint, width: Float): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        for (word in text.split(" ")) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(next) > width && line.isNotEmpty()) { lines += line; line = word } else line = next
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    private fun fit(text: String, p: Paint, width: Float): String {
        if (p.measureText(text) <= width) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > width) t = t.dropLast(1)
        return "$t…"
    }

    fun write(content: ManagementReportContent, out: OutputStream) {
        val usable = W - 2 * M
        val ops = mutableListOf<Op>()
        ops += TextOp("GEOLUX", 8f, green, bold = true, height = 14f)
        wrap(content.title, paint(16f, green, bold = true), usable).forEach { ops += TextOp(it, 16f, green, bold = true, height = 22f) }
        wrap(content.subtitle, paint(8f, ink), usable).forEach { ops += TextOp(it, 8f, ink, height = 12f) }
        // El resumen se ajusta al ancho (antes era una sola línea y se cortaba con un hato grande).
        wrap(content.summary.joinToString(" · "), paint(10f, ink, bold = true), usable).forEach {
            ops += TextOp(it, 10f, ink, bold = true, height = 15f)
        }
        val columnsOf = mutableMapOf<RowOp, Int>()
        for (table in content.tables) {
            ops += TextOp(table.title, 12f, green, bold = true, height = 24f)
            if (table.rows.isEmpty()) {
                wrap(table.empty, paint(8.5f, muted, italic = true), usable).forEach { ops += TextOp(it, 8.5f, muted, italic = true, height = 13f) }
            } else {
                (listOf(RowOp(table.headers, true)) + table.rows.map { RowOp(it, false) }).forEach { ops += it; columnsOf[it] = table.headers.size }
            }
            if (table.note.isNotBlank()) ops += TextOp(table.note, 8.5f, ink, height = 14f)
        }
        // Paginación: primero se reparte, luego se dibuja sabiendo el total de páginas.
        val pages = mutableListOf(mutableListOf<Op>())
        var y = M
        for (op in ops) {
            if (y + op.height > H - M - FOOTER) { pages += mutableListOf<Op>(); y = M }
            pages.last() += op; y += op.height
        }
        val doc = PdfDocument()
        pages.forEachIndexed { index, list ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, index + 1).create())
            draw(page.canvas, list, usable)
            page.canvas.drawText("GeoLux", M, H - M + 6, paint(8f, muted))
            val number = "Página ${index + 1} de ${pages.size}"
            val p = paint(8f, muted)
            page.canvas.drawText(number, W - M - p.measureText(number), H - M + 6, p)
            doc.finishPage(page)
        }
        doc.writeTo(out)
        doc.close()
    }

    private fun draw(canvas: Canvas, ops: List<Op>, usable: Float) {
        var y = M
        val line = Paint().apply { color = Color.rgb(0xD5, 0xDD, 0xD8); strokeWidth = 0.6f }
        for (op in ops) {
            when (op) {
                is TextOp -> canvas.drawText(op.text, M, y + op.height - 5, paint(op.size, op.color, op.bold, op.italic))
                is RowOp -> {
                    val width = usable / op.cells.size
                    if (op.header) canvas.drawRect(M, y, M + usable, y + op.height, Paint().apply { color = headerFill })
                    val p = paint(7.5f, ink, bold = op.header)
                    op.cells.forEachIndexed { i, cell -> canvas.drawText(fit(cell, p, width - 6), M + i * width + 3, y + op.height - 5, p) }
                    canvas.drawLine(M, y + op.height, M + usable, y + op.height, line)
                }
            }
            y += op.height
        }
    }
}
