package me.luiscruzandresgomezrodolfohernandez.geolux.data.fake

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** El Fake debe seguir siendo el fixture del contrato: si el fixture cambia, esto falla. */
class FakeDataTest {
    private val expected: JsonObject = Json.parseToJsonElement(
        File(System.getProperty("geolux.fixtures"), "expected.json").readText(),
    ).jsonObject

    private fun ids(layer: String) =
        expected["feature_ids"]!!.jsonObject[layer]!!.jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun packageAndProjectMatch() {
        assertEquals(expected["package_id"]!!.jsonPrimitive.content, FINCA_DEMO.packageId)
        val project = expected["project"]!!.jsonObject
        assertEquals(project["id"]!!.jsonPrimitive.content, FINCA_DEMO.project.id)
        assertEquals(project["name"]!!.jsonPrimitive.content, FINCA_DEMO.project.name)
    }

    @Test
    fun layerIdsMatch() {
        assertEquals(ids("work_area"), listOfNotNull(FINCA_DEMO.workArea?.id))
        assertEquals(ids("zones"), FINCA_DEMO.zones.map { it.id })
        assertEquals(ids("elements"), FINCA_DEMO.elements.map { it.id })
        assertEquals(ids("lines"), FINCA_DEMO.lines.map { it.id })
        assertEquals(ids("missions"), FINCA_DEMO.missions.map { it.id })
        assertEquals(ids("mission_paths"), FINCA_DEMO.missionPaths.map { it.id })
    }

    @Test
    fun keyValuesMatch() {
        val zone1 = FINCA_DEMO.zones.first { it.id == "zone-demo-1" }
        assertEquals("active", zone1.status)
        assertEquals(40.0, zone1.livestockCapacity)
        assertEquals(null, FINCA_DEMO.zones.first { it.id == "zone-demo-2" }.livestockCapacity)
        val el1 = FINCA_DEMO.elements.first { it.id == "el-demo-1" }
        assertEquals(-70.19895, el1.position.lon, 0.0)
        assertEquals(6.30105, el1.position.lat, 0.0)
        assertEquals("Tanque", el1.typeLabel)
        assertEquals("Tubería", FINCA_DEMO.lines.first { it.id == "line-demo-2" }.categoryLabel)
        assertEquals(4, FINCA_DEMO.missions.single().waypointCount)
        assertEquals("viable", FINCA_DEMO.missions.single().batteryVerdict)
        assertEquals(4, FINCA_DEMO.missionPaths.single().path.size)
    }

    @Test
    fun productsMatch() {
        val products = expected["products"]!!.jsonObject
        assertEquals(products.keys, FINCA_DEMO.products.map { it.id }.toSet())
        for (p in FINCA_DEMO.products) {
            val e = products[p.id]!!.jsonObject
            assertEquals(e["kind"]!!.jsonPrimitive.content, p.kind)
            assertEquals(e["origin"]!!.jsonPrimitive.content, p.origin)
        }
    }
}
