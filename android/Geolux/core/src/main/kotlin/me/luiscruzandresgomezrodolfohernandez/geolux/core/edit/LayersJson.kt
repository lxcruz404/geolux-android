package me.luiscruzandresgomezrodolfohernandez.geolux.core.edit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Ring

/**
 * Las capas que se editan en el teléfono, escritas **en el formato de SPEC §5**
 * (las mismas propiedades que exporta Desktop, `id` en el feature y en
 * `properties.id`, etiquetas `*_label` en español, WGS84 `[lon, lat]` sin
 * redondear). Así una finca editada en Android se lee con el mismo lector que
 * un paquete de Desktop, y el `.glx` que exporta Android lo abre Desktop.
 */
object LayersJson {
    /** Capas editables, en el orden del contrato. Las misiones son de Desktop (solo lectura). */
    val EDITABLE = listOf("work_area", "zones", "elements", "lines")

    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    /** dataset → texto GeoJSON (FeatureCollection) de cada capa editable. */
    fun write(data: ProjectData): Map<String, String> = mapOf(
        "work_area" to collection(listOfNotNull(data.workArea?.let { area ->
            feature(area.id, polygon(area.polygon)) {
                put("area_m2", area.areaM2)
                put("perimeter_m", area.perimeterM)
            }
        })),
        "zones" to collection(data.zones.map { z ->
            feature(z.id, polygon(z.polygon)) {
                put("name", z.name)
                put("description", z.description)
                put("status", z.status)
                put("status_label", z.statusLabel)
                put("color", z.color)
                put("area_m2", z.areaM2)
                put("perimeter_m", z.perimeterM)
                put("livestock_capacity", z.livestockCapacity?.let(::JsonPrimitive) ?: JsonNull)
            }
        }),
        "elements" to collection(data.elements.map { e ->
            feature(e.id, point(e.position)) {
                put("element_type", e.elementType)
                put("type_label", e.typeLabel)
                put("name", e.name)
                put("description", e.description)
            }
        }),
        "lines" to collection(data.lines.map { l ->
            feature(l.id, lineString(l.path)) {
                put("category", l.category)
                put("category_label", l.categoryLabel)
                put("name", l.name)
                put("description", l.description)
                put("length_m", l.lengthM)
            }
        }),
    ).mapValues { (_, value) -> json.encodeToString(JsonElement.serializer(), value) + "\n" }

    private fun collection(features: List<JsonObject>) = buildJsonObject {
        put("type", "FeatureCollection")
        put("features", JsonArray(features))
    }

    private fun feature(id: String, geometry: JsonObject, props: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = buildJsonObject {
        put("type", "Feature")
        put("id", id)
        put("geometry", geometry)
        put("properties", buildJsonObject {
            put("id", id)
            props()
        })
    }

    private fun coords(p: Position) = buildJsonArray {
        add(JsonPrimitive(p.lon))
        add(JsonPrimitive(p.lat))
    }

    private fun point(p: Position) = buildJsonObject {
        put("type", "Point")
        put("coordinates", coords(p))
    }

    private fun lineString(path: List<Position>) = buildJsonObject {
        put("type", "LineString")
        put("coordinates", JsonArray(path.map(::coords)))
    }

    /** Anillos cerrados (GeoJSON exige repetir el primer vértice al final). */
    private fun polygon(rings: List<Ring>) = buildJsonObject {
        put("type", "Polygon")
        put("coordinates", JsonArray(rings.map { ring -> JsonArray(closed(ring).map(::coords)) }))
    }

    fun closed(ring: Ring): Ring = if (ring.isNotEmpty() && ring.first() != ring.last()) ring + ring.first() else ring
}
