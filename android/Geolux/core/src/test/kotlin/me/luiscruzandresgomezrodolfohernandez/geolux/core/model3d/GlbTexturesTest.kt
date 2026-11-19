package me.luiscruzandresgomezrodolfohernandez.geolux.core.model3d

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Texturas del modelo 3D a la mejor calidad que admite el teléfono (no la copia a 1024 px). */
class GlbTexturesTest {
    private val dir = Files.createTempDirectory("glb").toFile()

    private fun image(w: Int, h: Int, format: String): ByteArray {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h step 7) for (x in 0 until w step 7) img.setRGB(x, y, (x * 31 + y * 17) and 0xFFFFFF)
        return ByteArrayOutputStream().also { ImageIO.write(img, format, it) }.toByteArray()
    }

    private val positions = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN).apply {
        floatArrayOf(0f, 0f, 0f, 100f, 0f, 0f, 0f, 100f, 0f).forEach { putFloat(it) }
    }.array()

    /** GLB: un triángulo y dos texturas (una JPEG grande y una PNG chica), como los del motor. */
    private fun glb(big: ByteArray, small: ByteArray): File {
        val chunks = listOf(positions, big, small)
        var offset = 0
        val views = chunks.map { c ->
            val v = """{"buffer":0,"byteOffset":$offset,"byteLength":${c.size}}"""
            offset += c.size
            while (offset % 4 != 0) offset++
            v
        }
        var json = """{"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],""" +
            """"meshes":[{"primitives":[{"attributes":{"POSITION":0},"material":0}]}],""" +
            """"materials":[{"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}}],""" +
            """"textures":[{"source":0,"sampler":0},{"source":1,"sampler":0}],"samplers":[{"magFilter":9729,"minFilter":9729}],""" +
            """"images":[{"bufferView":1,"mimeType":"image/jpeg"},{"bufferView":2,"mimeType":"image/png"}],""" +
            """"accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[100,100,0]}],""" +
            """"bufferViews":[${views.joinToString(",")}],"buffers":[{"byteLength":$offset}]}"""
        while (json.length % 4 != 0) json += " "
        val bin = ByteBuffer.allocate(offset)
        var at = 0
        chunks.forEach { c -> bin.position(at); bin.put(c); at += c.size; while (at % 4 != 0) at++ }
        val out = ByteBuffer.allocate(12 + 8 + json.length + 8 + offset).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67).putInt(2).putInt(out.capacity())
        out.putInt(json.length).putInt(0x4E4F534A).put(json.toByteArray())
        out.putInt(offset).putInt(0x004E4942).put(bin.array())
        return File(dir, "modelo.glb").apply { writeBytes(out.array()) }
    }

    @Test
    fun leeElTamanoDeCadaTexturaSinDecodificarla() {
        val file = glb(image(3000, 2000, "jpg"), image(500, 400, "png"))
        val images = GlbTextures.images(file)
        assertEquals(listOf(3000 to 2000, 500 to 400), images.map { it.width to it.height })
        assertEquals(listOf("image/jpeg", "image/png"), images.map { it.mimeType })
    }

    @Test
    fun elLimiteEsElMayorQueAdmiteElGpuYCabeEnMemoria() {
        // Las texturas de La Giralda (modelo del motor), medidas el 9 nov. 2026.
        val giralda = buildList {
            repeat(3) { add(24576) }; repeat(5) { add(4096) }; repeat(8) { add(2048) }; repeat(14) { add(1024) }; repeat(3) { add(512) }; add(256)
        }.mapIndexed { i, s -> GlbImage(i, i, "image/jpeg", s, s, 0) }
        // Teléfono con GPU de 8192 y mucha memoria: 8192.
        assertEquals(8192, GlbTextures.chooseCap(giralda, 8192, 8L shl 30))
        // Con 800 MB para texturas: 2048 (el doble que la copia de Desktop).
        assertEquals(2048, GlbTextures.chooseCap(giralda, 16384, 800L shl 20))
        // Nunca por debajo de lo que ya daba Desktop.
        assertEquals(1024, GlbTextures.chooseCap(giralda, 16384, 1))
        // Si las texturas son pequeñas, no se pide más de lo que hay.
        assertEquals(1024, GlbTextures.chooseCap(listOf(GlbImage(0, 0, "", 1000, 600, 0)), 16384, 8L shl 30))
        assertTrue(GlbTextures.memoryAt(giralda, 2048) <= 800L shl 20)
    }

    @Test
    fun reescribeSoloLasTexturasGrandesYConservaLaGeometria() {
        val source = glb(image(3000, 2000, "jpg"), image(500, 400, "png"))
        val target = File(dir, "preparado.glb")
        GlbTextures.rewrite(source, target, transform = { image, bytes ->
            if (maxOf(image.width, image.height) <= 1024) return@rewrite null
            val decoded = ImageIO.read(bytes.inputStream())
            val scaled = BufferedImage(1024, 683, BufferedImage.TYPE_INT_RGB).also { it.graphics.drawImage(decoded, 0, 0, 1024, 683, null) }
            ByteArrayOutputStream().also { ImageIO.write(scaled, "jpg", it) }.toByteArray() to "image/jpeg"
        })
        assertEquals(listOf(1024 to 683, 500 to 400), GlbTextures.images(target).map { it.width to it.height })
        val bytes = target.readBytes()
        val head = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(bytes.size, head.getInt(8)) // longitud declarada = la real
        val jsonLength = head.getInt(12)
        val gltf = Json.parseToJsonElement(String(bytes, 20, jsonLength)).jsonObject
        // Los muestreadores piden mipmaps.
        assertEquals(GlbTextures.MIN_FILTER_MIPMAP, gltf["samplers"]!!.jsonArray[0].jsonObject["minFilter"]!!.jsonPrimitive.int)
        // La geometría (vista 0) se copia byte a byte.
        val view0 = gltf["bufferViews"]!!.jsonArray[0].jsonObject
        val binStart = 20 + jsonLength + 8 + view0["byteOffset"]!!.jsonPrimitive.int
        assertArrayEquals(positions, bytes.copyOfRange(binStart, binStart + 36))
        assertTrue(target.length() < source.length())
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".part") })
    }
}
