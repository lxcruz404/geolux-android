package me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ortomosaico original y recortado con los paquetes que escribe GeoLux Desktop
 * (`tests/core/fixtures/glx-v1/finca-recortado-*.glx`, contrato unificado del
 * 11 nov. 2026). Los mismos bytes los comprueba Desktop en
 * `tests/core/test_glx_crop_fixtures.py`, con el mismo `finca-recortado.expected.json`.
 *
 * - `finca-recortado-v1.glx`: contrato unificado (`variant`, `source_product_id`, `derived_from`, roles).
 * - `finca-recortado-kind-crop.glx`: rama de Desktop del 10 nov. (`kind: orthomosaic_crop`, solo `derived_from`).
 * - `finca-recortado-variant.glx`: rama de Andrés del 10 nov. (`variant` y `source_product_id`).
 */
class OrthoCropFixtureTest {
    private val dir = File(System.getProperty("geolux.glxFixtures")!!)
    private val expected: JsonObject =
        Json.parseToJsonElement(File(dir, "finca-recortado.expected.json").readText()).jsonObject

    private fun check(name: String) {
        val report = GeoluxReader.read(File(dir, name))
        assertTrue("$name: ${report.errors}", report.ok)
        val products = report.data!!.products
        val orthos = products.filter { it.isOrthomosaic }
        val want = expected.getValue(name).jsonObject
        val originals = want.getValue("originals").jsonArray.map { it.jsonPrimitive.content }.sorted()
        val cuts = want.getValue("cuts").jsonObject.mapValues { it.value.jsonPrimitive.content }

        assertEquals(name, originals, orthos.filterNot { it.isCropped }.map { it.id }.sorted())
        assertEquals(name, cuts.keys, orthos.filter { it.isCropped }.map { it.id }.toSet())
        for ((cutId, originalId) in cuts) {
            val cut = orthos.single { it.id == cutId }
            assertEquals(name, originalId, cut.originalProductId)
            // El recortado tiene sus propias teselas, distintas de las del original.
            val original = orthos.single { it.id == originalId }
            assertFalse(name, cut.files.first() == original.files.first())
        }
        for (original in orthos.filterNot { it.isCropped }) assertNull(name, original.originalProductId)
        // El primer ortomosaico (el que centra el mapa) es un original.
        assertFalse(name, orthos.first().isCropped)
    }

    @Test
    fun contratoUnificado() {
        check("finca-recortado-v1.glx")
        val cut = GeoluxReader.read(File(dir, "finca-recortado-v1.glx")).data!!.products.single { it.isCropped }
        assertEquals("orthomosaic", cut.kind)
        assertEquals("cropped", cut.variant)
        assertEquals(listOf(cut.sourceProductId), cut.derivedFrom)
        assertEquals("tiles", cut.fileRoles[cut.files.first()])
    }

    @Test
    fun ramaDelDiezDeNoviembreConKindOrthomosaicCrop() {
        check("finca-recortado-kind-crop.glx")
        val cut = GeoluxReader.read(File(dir, "finca-recortado-kind-crop.glx")).data!!.products.single { it.isCropped }
        assertEquals("orthomosaic_crop", cut.kind)
        assertNull(cut.sourceProductId)
    }

    @Test
    fun ramaDeAndresConVariant() {
        check("finca-recortado-variant.glx")
        val cut = GeoluxReader.read(File(dir, "finca-recortado-variant.glx")).data!!.products.single { it.isCropped }
        assertTrue(cut.derivedFrom.isEmpty())
    }
}
