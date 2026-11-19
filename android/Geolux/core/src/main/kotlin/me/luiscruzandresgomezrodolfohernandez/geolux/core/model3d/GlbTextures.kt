package me.luiscruzandresgomezrodolfohernandez.geolux.core.model3d

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Una imagen incrustada en un GLB: su vista de búfer, tipo y tamaño en píxeles (leído de la cabecera). */
data class GlbImage(val index: Int, val bufferView: Int, val mimeType: String, val width: Int, val height: Int, val byteLength: Int)

/**
 * Texturas de un modelo 3D (GLB, glTF 2.0 binario) para verlo en el teléfono
 * **con la mejor calidad que el teléfono admite**, en vez de la copia a 1024 px
 * que hace Desktop (`MOBILE_TEXTURE_SIZE`).
 *
 * El modelo del motor (ODM) trae texturas de hasta 24576 px: ningún GPU de
 * teléfono las dibuja (el máximo habitual es 4096–16384) y descomprimidas no
 * caben en memoria. Aquí se elige el mayor tamaño que admite el GPU y cabe en un
 * presupuesto de memoria ([chooseCap]) y se reescribe el GLB con cada textura
 * mayor reducida a ese tamaño ([rewrite]); la geometría se copia byte a byte.
 * Lo mismo que hace el visor de Desktop con `MAX_TEXTURE_SIZE`, más mipmaps.
 *
 * Sin dependencias de Android: el redimensionado lo pone quien llama.
 */
object GlbTextures {
    private const val GLB_MAGIC = 0x46546C67
    private const val CHUNK_JSON = 0x4E4F534A
    private const val CHUNK_BIN = 0x004E4942

    /** glTF: LINEAR_MIPMAP_LINEAR. Con él, Filament genera mipmaps (menos parpadeo al alejarse). */
    const val MIN_FILTER_MIPMAP = 9987

    private val json = Json { ignoreUnknownKeys = true }

    private class Layout(val gltf: JsonObject, val binOffset: Long, val binLength: Long)

    private fun layout(raf: RandomAccessFile): Layout {
        val header = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        raf.seek(0)
        raf.readFully(header.array())
        if (header.getInt(0) != GLB_MAGIC || header.getInt(4) != 2) throw IOException("No es un GLB (glTF 2.0 binario).")
        val jsonLength = header.getInt(12)
        if (header.getInt(16) != CHUNK_JSON) throw IOException("El GLB no empieza por su JSON.")
        val jsonBytes = ByteArray(jsonLength)
        raf.readFully(jsonBytes)
        val binHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        raf.readFully(binHeader.array())
        if (binHeader.getInt(4) != CHUNK_BIN) throw IOException("El GLB no trae su bloque binario.")
        val gltf = json.parseToJsonElement(jsonBytes.toString(Charsets.UTF_8)).jsonObject
        return Layout(gltf, 12L + 8 + jsonLength + 8, binHeader.getInt(0).toLong() and 0xFFFFFFFFL)
    }

    private fun JsonObject.array(key: String): List<JsonObject> = (this[key] as? JsonArray)?.map { it.jsonObject }.orEmpty()

    /** Las imágenes incrustadas del GLB con su tamaño real (cabecera JPEG o PNG; sin decodificarlas). */
    fun images(file: File): List<GlbImage> = RandomAccessFile(file, "r").use { raf ->
        val layout = layout(raf)
        val views = layout.gltf.array("bufferViews")
        layout.gltf.array("images").mapIndexedNotNull { index, image ->
            val viewIndex = image["bufferView"]?.jsonPrimitive?.intOrNull ?: return@mapIndexedNotNull null
            val view = views[viewIndex]
            val offset = view["byteOffset"]?.jsonPrimitive?.intOrNull ?: 0
            val length = view["byteLength"]!!.jsonPrimitive.int
            val head = ByteArray(minOf(length, 256 * 1024))
            raf.seek(layout.binOffset + offset)
            raf.readFully(head)
            val (w, h) = imageSize(head) ?: return@mapIndexedNotNull null
            GlbImage(index, viewIndex, image["mimeType"]?.jsonPrimitive?.content ?: "", w, h, length)
        }
    }

    /**
     * Tamaño máximo de textura (potencia de dos) para ver el modelo: el mayor que
     * admite el GPU ([maxTextureSize]) y con el que todas las texturas, ya reducidas,
     * caben en [budgetBytes] (RGBA8 y mipmaps, +⅓). Nunca menos de 1024 (lo de Desktop).
     */
    fun chooseCap(images: List<GlbImage>, maxTextureSize: Int, budgetBytes: Long): Int {
        val gpuMax = Integer.highestOneBit(maxTextureSize.coerceAtLeast(1024))
        val largest = images.maxOfOrNull { maxOf(it.width, it.height) } ?: return gpuMax
        // Nada que ganar por encima de la potencia de dos que ya contiene la textura mayor.
        val needed = Integer.highestOneBit(largest).let { if (it < largest) it * 2 else it }
        var cap = minOf(gpuMax, needed.coerceAtLeast(1024))
        while (cap > 1024 && memoryAt(images, cap) > budgetBytes) cap /= 2
        return cap
    }

    /** Bytes de GPU de las texturas reducidas a [cap]: RGBA8 con mipmaps (+⅓). */
    fun memoryAt(images: List<GlbImage>, cap: Int): Long = images.sumOf { image ->
        val scale = minOf(1.0, cap.toDouble() / maxOf(image.width, image.height))
        val w = (image.width * scale).toLong().coerceAtLeast(1)
        val h = (image.height * scale).toLong().coerceAtLeast(1)
        w * h * 4 * 4 / 3
    }

    /**
     * Escribe en [target] una copia de [source] donde cada imagen mayor de lo que
     * hace falta pasa por [transform] (que devuelve los bytes nuevos y su tipo MIME,
     * o null para dejarla igual), y los muestreadores piden mipmaps. Todo lo demás
     * (geometría, Draco, materiales, CESIUM_RTC) se copia tal cual. Atómico: se
     * escribe en un `.part` y se renombra.
     */
    fun rewrite(
        source: File,
        target: File,
        transform: (GlbImage, ByteArray) -> Pair<ByteArray, String>?,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        val partial = File(target.parentFile, target.name + ".part")
        RandomAccessFile(source, "r").use { raf ->
            val layout = layout(raf)
            val images = images(source).associateBy { it.bufferView }
            val gltf = layout.gltf
            val views = gltf.array("bufferViews").toMutableList()
            val imageObjects = gltf.array("images").toMutableList()
            partial.outputStream().buffered(1 shl 20).use { out ->
                // El bloque binario se escribe después del JSON, cuya longitud aún no se sabe:
                // primero a un archivo temporal de datos.
                val bin = File(target.parentFile, target.name + ".bin.part")
                var written = 0L
                bin.outputStream().buffered(1 shl 20).use { binOut ->
                    views.forEachIndexed { index, view ->
                        val offset = view["byteOffset"]?.jsonPrimitive?.intOrNull ?: 0
                        val length = view["byteLength"]!!.jsonPrimitive.int
                        val data = ByteArray(length)
                        raf.seek(layout.binOffset + offset)
                        raf.readFully(data)
                        var bytes = data
                        images[index]?.let { image ->
                            transform(image, data)?.let { (newBytes, mime) ->
                                bytes = newBytes
                                imageObjects[image.index] = JsonObject(imageObjects[image.index] + ("mimeType" to JsonPrimitive(mime)))
                            }
                            onProgress(images.keys.indexOf(index) + 1, images.size)
                        }
                        while (written % 4 != 0L) { binOut.write(0); written++ }
                        views[index] = JsonObject(view + mapOf("byteOffset" to JsonPrimitive(written), "byteLength" to JsonPrimitive(bytes.size)))
                        binOut.write(bytes)
                        written += bytes.size
                    }
                    while (written % 4 != 0L) { binOut.write(0); written++ }
                }
                val buffers = gltf.array("buffers").mapIndexed { i, b -> if (i == 0) JsonObject(b + ("byteLength" to JsonPrimitive(written))) else b }
                val samplers = gltf.array("samplers").map { JsonObject(it + ("minFilter" to JsonPrimitive(MIN_FILTER_MIPMAP))) }
                val newGltf = JsonObject(
                    gltf + mapOf<String, JsonElement>(
                        "bufferViews" to JsonArray(views),
                        "images" to JsonArray(imageObjects),
                        "buffers" to JsonArray(buffers),
                    ) + (if (samplers.isNotEmpty()) mapOf("samplers" to JsonArray(samplers)) else emptyMap()),
                )
                var jsonBytes = newGltf.toString().toByteArray(Charsets.UTF_8)
                if (jsonBytes.size % 4 != 0) jsonBytes += ByteArray(4 - jsonBytes.size % 4) { ' '.code.toByte() }
                val total = 12L + 8 + jsonBytes.size + 8 + written
                if (total > Int.MAX_VALUE) throw IOException("El modelo reducido sigue siendo demasiado grande.")
                val head = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(GLB_MAGIC).putInt(2).putInt(total.toInt()).putInt(jsonBytes.size).putInt(CHUNK_JSON)
                out.write(head.array())
                out.write(jsonBytes)
                out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(written.toInt()).putInt(CHUNK_BIN).array())
                bin.inputStream().use { it.copyTo(out) }
                bin.delete()
            }
        }
        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) throw IOException("No se pudo guardar el modelo preparado.")
    }

    /** (ancho, alto) de un JPEG o PNG leyendo solo su cabecera; null si no se reconoce. */
    fun imageSize(bytes: ByteArray): Pair<Int, Int>? {
        fun u16(i: Int) = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
        fun u32(i: Int) = (u16(i) shl 16) or u16(i + 2)
        if (bytes.size >= 24 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) return u32(16) to u32(20)
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
        var i = 2
        while (i + 9 < bytes.size) {
            if (bytes[i] != 0xFF.toByte()) return null
            val marker = bytes[i + 1].toInt() and 0xFF
            if (marker == 0xD8 || marker in 0xD0..0xD7 || marker == 0x01) { i += 2; continue }
            val length = u16(i + 2)
            // SOF0–SOF15 salvo DHT (C4), JPG (C8) y DAC (CC): alto y ancho.
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) return u16(i + 7) to u16(i + 5)
            i += 2 + length
        }
        return null
    }
}
