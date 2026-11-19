package me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mismos casos que `tests/core/test_b1_geolux_contract.py` en Python (SPEC §9, AND-A4). */
class GeoluxReaderTest {
    private val fixtures = File(System.getProperty("geolux.fixtures"))
    private val expected: JsonObject = Json.parseToJsonElement(File(fixtures, "expected.json").readText()).jsonObject
    private val report = GeoluxReader.read(File(fixtures, "finca-demo.geolux"))
    private val data get() = report.data!!

    @Test
    fun fincaDemoIsValid() {
        assertTrue(report.errors.toString(), report.ok)
        assertEquals(expected["package_id"]!!.jsonPrimitive.content, data.packageId)
        assertEquals(expected["format_version"]!!.jsonPrimitive.content.toInt(), GeoluxReader.READER_VERSION)
        val project = expected["project"]!!.jsonObject
        assertEquals(project["id"]!!.jsonPrimitive.content, data.project.id)
        assertEquals(project["name"]!!.jsonPrimitive.content, data.project.name)
        val center = project["center"]!!.jsonObject
        assertEquals(center["lat"]!!.jsonPrimitive.doubleOrNull!!, data.project.center!!.lat, 0.0)
        assertEquals(center["lon"]!!.jsonPrimitive.doubleOrNull!!, data.project.center!!.lon, 0.0)
    }

    @Test
    fun countsAndIdsMatchExpected() {
        val ids = mapOf(
            "work_area" to listOfNotNull(data.workArea?.id),
            "zones" to data.zones.map { it.id },
            "elements" to data.elements.map { it.id },
            "lines" to data.lines.map { it.id },
            "missions" to data.missions.map { it.id },
            "mission_paths" to data.missionPaths.map { it.id },
        )
        val counts = expected["feature_counts"]!!.jsonObject
        val expectedIds = expected["feature_ids"]!!.jsonObject
        assertEquals(counts.keys, ids.keys)
        for ((layer, list) in ids) {
            assertEquals(layer, counts[layer]!!.jsonPrimitive.content.toInt(), list.size)
            assertEquals(layer, expectedIds[layer]!!.jsonArray.map { it.jsonPrimitive.content }, list)
        }
    }

    @Test
    fun checksMatchExpected() {
        val m = data.missions.single { it.id == "a1b2c3d4-0000-4000-8000-0000000000f1" }
        val el1 = data.elements.single { it.id == "el-demo-1" }
        val actual: Map<String, Any?> = mapOf(
            "zones.zone-demo-1.status" to data.zones.single { it.id == "zone-demo-1" }.status,
            "zones.zone-demo-1.livestock_capacity" to data.zones.single { it.id == "zone-demo-1" }.livestockCapacity,
            "zones.zone-demo-2.livestock_capacity" to data.zones.single { it.id == "zone-demo-2" }.livestockCapacity,
            "elements.el-demo-1.coordinates" to listOf(el1.position.lon, el1.position.lat),
            "elements.el-demo-1.type_label" to el1.typeLabel,
            "lines.line-demo-2.category_label" to data.lines.single { it.id == "line-demo-2" }.categoryLabel,
            "missions.${m.id}.waypoint_count" to m.waypointCount.toDouble(),
            "missions.${m.id}.battery_verdict" to m.batteryVerdict,
            "mission_paths.${m.id}.coordinates_count" to data.missionPaths.single { it.id == m.id }.path.size.toDouble(),
        )
        val checks = expected["checks"]!!.jsonObject
        // Si expected.json añade una comprobación, esta prueba obliga a mapearla aquí.
        assertEquals(checks.keys, actual.keys)
        for ((key, value) in checks) {
            val want: Any? = when {
                value is JsonNull -> null
                value is kotlinx.serialization.json.JsonArray -> value.map { it.jsonPrimitive.doubleOrNull }
                value.jsonPrimitive.isString -> value.jsonPrimitive.content
                else -> value.jsonPrimitive.doubleOrNull
            }
            assertEquals(key, want, actual[key])
        }
    }

    @Test
    fun productsAndWarningsMatchExpected() {
        val products = expected["products"]!!.jsonObject
        assertEquals(products.keys, data.products.map { it.id }.toSet())
        for (p in data.products) {
            assertEquals(products[p.id]!!.jsonObject["kind"]!!.jsonPrimitive.content, p.kind)
            assertEquals(products[p.id]!!.jsonObject["origin"]!!.jsonPrimitive.content, p.origin)
        }
        assertEquals(
            expected["warnings"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(),
            report.warnings.map { it.code }.toSet(),
        )
    }

    @Test
    fun everyInvalidFixtureIsRejectedWithItsCode() {
        val invalid = File(fixtures, "invalid").listFiles { f -> f.name.endsWith(".geolux") }!!.sortedBy { it.name }
        assertEquals(9, invalid.size)
        for (file in invalid) {
            val result = GeoluxReader.read(file)
            assertEquals(file.name, listOf(file.name.removeSuffix(".geolux")), result.errors.map { it.code })
            assertNull(file.name, result.data)
        }
    }
}
