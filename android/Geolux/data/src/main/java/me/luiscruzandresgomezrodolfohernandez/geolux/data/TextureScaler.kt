package me.luiscruzandresgomezrodolfohernandez.geolux.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.ByteArrayOutputStream
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model3d.GlbImage

/**
 * Reduce una textura del modelo 3D a [cap] px por su lado mayor, en el teléfono.
 *
 * Las del motor llegan a 24576 px (600 MB descomprimida): no se decodifica entera.
 * Se lee por franjas con [BitmapRegionDecoder], ya submuestreadas por el decodificador
 * JPEG (inSampleSize), y cada franja se dibuja con filtro bilineal en la textura final.
 * Memoria: la textura final más una franja de unos 32 MB.
 */
internal object TextureScaler {
    /** Píxeles por franja decodificada (~32 MB en ARGB). */
    private const val STRIP_PIXELS = 8L * 1024 * 1024

    /** Bytes nuevos y tipo MIME, o null si ya cabe en [cap] o no se puede leer (viaja igual). */
    fun scale(image: GlbImage, bytes: ByteArray, cap: Int): Pair<ByteArray, String>? {
        val longest = maxOf(image.width, image.height)
        if (longest <= cap) return null
        val ratio = cap.toDouble() / longest
        val tw = (image.width * ratio).toInt().coerceAtLeast(1)
        val th = (image.height * ratio).toInt().coerceAtLeast(1)
        // Mayor potencia de dos que deja la franja decodificada aún por encima del tamaño final.
        var sample = 1
        while (image.width / (sample * 2) >= tw && image.height / (sample * 2) >= th) sample *= 2

        @Suppress("DEPRECATION") // newInstance(byte[], …) sin el booleano es de la API 31; minSdk 28.
        val decoder = BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false) ?: return null
        val alpha = image.mimeType == "image/png"
        val target = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(target)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rowsPerStrip = (STRIP_PIXELS / (image.width / sample).coerceAtLeast(1) * sample).toInt().coerceAtLeast(sample)
            var top = 0
            while (top < image.height) {
                val bottom = minOf(image.height, top + rowsPerStrip)
                val strip = decoder.decodeRegion(Rect(0, top, image.width, bottom), options) ?: return null
                canvas.drawBitmap(strip, null, RectF(0f, (top * ratio).toFloat(), tw.toFloat(), (bottom * ratio).toFloat()), paint)
                strip.recycle()
                top = bottom
            }
            val out = ByteArrayOutputStream()
            return if (alpha && target.hasAlpha()) {
                target.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray() to "image/png"
            } else {
                target.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.toByteArray() to "image/jpeg"
            }
        } finally {
            target.recycle()
            decoder.recycle()
        }
    }
}
