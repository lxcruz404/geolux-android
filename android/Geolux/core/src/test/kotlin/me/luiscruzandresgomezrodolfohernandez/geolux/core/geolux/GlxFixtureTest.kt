package me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux

import java.io.File
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `finca-demo-v1.glx` del exportador de Desktop (`tests/core/fixtures/glx-v1`,
 * main c04b710) con el lector Android, comparado con su `expected.json`. Mismo
 * resultado que `tests/core/test_glx_v1_fixture.py` y que la comprobación de
 * `docs/proposals/glx-v1-contract-update.md` §7.
 */
class GlxFixtureTest {
    private val dir = File(System.getProperty("geolux.glxFixtures"))
    private val glx = File(dir, "finca-demo-v1.glx")
    private val expected: JsonObject = Json.parseToJsonElement(File(dir, "expected.json").readText()).jsonObject
    private val report = GeoluxReader.read(glx)
    private val data get() = report.data!!

    private fun manifest(): JsonObject =
        ZipFile(glx).use { zip -> Json.parseToJsonElement(zip.getInputStream(zip.getEntry("manifest.json")).readBytes().decodeToString()).jsonObject }

    @Test
    fun seLeeSinErroresConLosAvisosSinteticosPrevistos() {
        assertTrue(report.errors.toString(), report.ok)
        assertEquals(expected["package_id"]!!.jsonPrimitive.content, data.packageId)
        assertEquals(expected["project_id"]!!.jsonPrimitive.content, data.project.id)
        assertEquals(expected["format_version"]!!.jsonPrimitive.content.toInt(), GeoluxReader.READER_VERSION)
        // Cinco productos sintéticos: cinco avisos, que no son errores ni otra cosa.
        assertEquals(List(5) { "synthetic_product" }, report.warnings.map { it.code })
    }

    @Test
    fun productosYTiposComoExpected() {
        val products = expected["products"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
        assertEquals(products, data.products.associate { it.id to it.kind })
    }

    @Test
    fun cadaEntradaDelManifiestoTieneElTipoRolYCompresionDeExpected() {
        val entries = expected["entries"]!!.jsonObject
        val declared = manifest()["contents"]!!.jsonArray.associate { it.jsonObject["path"]!!.jsonPrimitive.content to it.jsonObject }
        assertEquals(entries.keys, declared.keys)
        ZipFile(glx).use { zip ->
            for ((path, want) in entries) {
                val got = declared.getValue(path)
                for (field in listOf("kind", "role", "compression")) {
                    val wanted = want.jsonObject[field]!!.let { if (it is JsonNull) null else it.jsonPrimitive.content }
                    assertEquals("$path $field", wanted, got[field]?.let { if (it is JsonNull) null else it.jsonPrimitive.content })
                }
                // El método del ZIP coincide con lo declarado (stored = sin comprimir).
                val method = if (zip.getEntry(path).method == java.util.zip.ZipEntry.STORED) "stored" else "deflate"
                assertEquals(path, got["compression"]!!.jsonPrimitive.content, method)
            }
        }
    }

    @Test
    fun losRolesLleganAlProductoYElModeloMovilEsElDeExpected() {
        val model = data.products.single { it.kind == "model3d" }
        val mobile = expected["mobile_model"]!!.jsonPrimitive.content
        assertEquals("model_mobile", model.fileRoles[mobile])
        assertEquals("model", model.fileRoles[model.files.single { it != mobile }])
        assertEquals("document", data.products.single { it.kind == "report" }.fileRoles.values.single())
    }

    @Test
    fun lasReferenciasDeFotosYTablasExistenEnElPaquete() {
        ZipFile(glx).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            val index = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("photos/index.json")).readBytes().decodeToString()).jsonObject
            val photos = index["photos"]!!.jsonArray
            assertEquals(expected["photos"]!!.jsonPrimitive.content.toInt(), photos.size)
            for (photo in photos) {
                val files = photo.jsonObject["files"]!!.jsonObject.values.map { it.jsonPrimitive.content }
                assertTrue(files.isNotEmpty())
                files.forEach { assertTrue("falta $it", it in names) }
            }
            assertEquals(expected["tables"]!!.jsonPrimitive.content.toInt(), names.count { it.startsWith("tables/") })
        }
    }

    @Test
    fun peekManifestLeeLosTamanosSinCopiarElPaquete() {
        val peek = glx.inputStream().use(GeoluxReader::peekManifest)
        assertNotNull(peek)
        peek!!
        assertEquals(data.packageId, peek.packageId)
        assertEquals(data.project.id, peek.projectId)
        val contents = manifest()["contents"]!!.jsonArray.map { it.jsonObject }
        val sizes = contents.associate { it["path"]!!.jsonPrimitive.content to it["size_bytes"]!!.jsonPrimitive.longOrNull!! }
        assertEquals(sizes.values.sum(), peek.declaredBytes)
        assertEquals(sizes.filterKeys { it.startsWith("products/") }.values.sum(), peek.productBytes)
        // Coincide con totals.bytes, que Desktop declara para esto.
        assertEquals(manifest()["totals"]!!.jsonObject["bytes"]!!.jsonPrimitive.longOrNull, peek.declaredBytes)
    }

    @Test
    fun peekManifestDevuelveNullSiNoPuede() {
        assertNull(GeoluxReader.peekManifest("esto no es un zip".byteInputStream()))
        val legacy = File(System.getProperty("geolux.fixtures"), "finca-demo.geolux")
        // El fixture v1 antiguo: si su manifiesto no va primero, no hay estimación previa y no pasa nada.
        val first = ZipFile(legacy).use { it.entries().nextElement().name }
        val peek = legacy.inputStream().use(GeoluxReader::peekManifest)
        if (first == "manifest.json") assertNotNull(peek) else assertNull(peek)
    }

    @Test
    fun laComprobacionAvisaDeSuAvanceHastaElTotalDeclarado() {
        val seen = mutableListOf<Pair<Long, Long>>()
        assertTrue(GeoluxReader.read(glx) { done, total -> seen += done to total }.ok)
        val total = glx.inputStream().use(GeoluxReader::peekManifest)!!.declaredBytes
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it.second == total })
        assertEquals(seen.map { it.first }.sorted(), seen.map { it.first })
        assertEquals(total, seen.last().first)
    }
}
