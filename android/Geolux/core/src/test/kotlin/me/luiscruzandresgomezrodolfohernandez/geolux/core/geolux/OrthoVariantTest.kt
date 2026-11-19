package me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ortomosaico original y recortado (propuesta del 9 nov. 2026): `variant` y
 * `source_product_id` son opcionales en el meta.json del producto. Los lee el
 * lector y un paquete sin ellos sigue igual.
 */
class OrthoVariantTest {
    private val demo = File(System.getProperty("geolux.fixtures")!!, "finca-demo.geolux")

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** finca-demo con [extra] añadido al meta.json del ortomosaico, y el manifiesto al día. */
    private fun withOrthoMeta(extra: String): File {
        val metaPath = "products/ortho-demo/meta.json"
        val out = Files.createTempFile("variante", ".geolux").toFile()
        ZipFile(demo).use { zip ->
            val meta = zip.getInputStream(zip.getEntry(metaPath)).readBytes().toString(Charsets.UTF_8)
                .replaceFirst("{", "{$extra,").toByteArray()
            var manifest = zip.getInputStream(zip.getEntry("manifest.json")).readBytes().toString(Charsets.UTF_8)
            val old = zip.getInputStream(zip.getEntry(metaPath)).readBytes()
            manifest = manifest.replace(sha256(old), sha256(meta))
                .replace("\"size_bytes\": ${old.size},\n      \"sha256\": \"${sha256(meta)}\"", "\"size_bytes\": ${meta.size},\n      \"sha256\": \"${sha256(meta)}\"")
            ZipOutputStream(out.outputStream()).use { zos ->
                for (entry in zip.entries()) {
                    val bytes = when (entry.name) {
                        metaPath -> meta
                        "manifest.json" -> manifest.toByteArray()
                        else -> zip.getInputStream(entry).readBytes()
                    }
                    zos.putNextEntry(ZipEntry(entry.name))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }
        return out
    }

    @Test
    fun unRecortadoDiceDeQueOriginalSale() {
        val report = GeoluxReader.read(withOrthoMeta("\"variant\": \"cropped\", \"source_product_id\": \"orto-original\""))
        assertTrue(report.errors.toString(), report.errors.isEmpty())
        val ortho = report.data!!.products.single { it.kind == "orthomosaic" }
        assertEquals("cropped", ortho.variant)
        assertEquals("orto-original", ortho.sourceProductId)
        assertTrue(ortho.isCropped)
    }

    @Test
    fun unPaqueteSinLosCamposNuevosSigueIgual() {
        val ortho = GeoluxReader.read(demo).data!!.products.single { it.kind == "orthomosaic" }
        assertNull(ortho.variant)
        assertNull(ortho.sourceProductId)
        assertFalse(ortho.isCropped)
    }
}
