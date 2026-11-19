package me.luiscruzandresgomezrodolfohernandez.geolux.core.edit

import java.util.UUID
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geo.Catalogs
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geo.GeoMeasure
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geo.GeoValidationException
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.MapElement
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.MapLine
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.WorkArea
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Zone

/** Una edición que GeoLux no acepta; el mensaje es para el usuario (los mismos textos que Desktop). */
class EditException(message: String) : IllegalArgumentException(message)

/**
 * Crear, editar, mover y eliminar los datos del mapa de una finca, con **las
 * reglas de GeoLux Desktop** (`zone_service`, `map_line_service`,
 * `map_element_service`, `work_area_service`): nombre obligatorio, polígono
 * simple, potreros sin solaparse (compartir borde sí), línea de al menos 2
 * vértices, tipos y categorías del catálogo, coordenadas en rango. Área,
 * perímetro y longitud se calculan aquí, nunca se fían de la pantalla.
 *
 * Funciones puras: reciben un [ProjectData] y devuelven el nuevo; quien llama
 * lo guarda. Los anillos se reciben abiertos o cerrados y se guardan cerrados.
 */
object FarmEditor {
    fun newId(): String = UUID.randomUUID().toString()

    // ── Potreros ───────────────────────────────────────────────────────────

    fun createZone(
        data: ProjectData,
        name: String,
        ring: List<Position>,
        description: String = "",
        color: String = "",
        status: String = Catalogs.DEFAULT_ZONE_STATUS,
        livestockCapacity: Double? = null,
        id: String = newId(),
    ): ProjectData {
        val clean = zoneName(name)
        validateZoneStatus(status)
        validateCapacity(livestockCapacity)
        val (area, perimeter) = polygonMeasures(ring)
        rejectOverlaps(data, ring, excludeId = null)
        val zone = Zone(
            id = id, name = clean, description = description.trim(), status = status,
            statusLabel = Catalogs.zoneStatusLabel(status), color = color, polygon = listOf(LayersJson.closed(ring)),
            areaM2 = area, perimeterM = perimeter, livestockCapacity = livestockCapacity,
        )
        return data.copy(zones = data.zones + zone)
    }

    fun updateZoneDetails(
        data: ProjectData,
        zoneId: String,
        name: String,
        description: String,
        color: String,
        status: String,
        livestockCapacity: Double?,
    ): ProjectData {
        val existing = zone(data, zoneId)
        val clean = zoneName(name)
        validateZoneStatus(status)
        validateCapacity(livestockCapacity)
        val updated = existing.copy(
            name = clean, description = description.trim(), color = color, status = status,
            statusLabel = Catalogs.zoneStatusLabel(status), livestockCapacity = livestockCapacity,
        )
        return data.copy(zones = data.zones.map { if (it.id == zoneId) updated else it })
    }

    fun updateZoneGeometry(data: ProjectData, zoneId: String, ring: List<Position>): ProjectData {
        val existing = zone(data, zoneId)
        val (area, perimeter) = polygonMeasures(ring)
        rejectOverlaps(data, ring, excludeId = zoneId)
        val updated = existing.copy(polygon = listOf(LayersJson.closed(ring)), areaM2 = area, perimeterM = perimeter)
        return data.copy(zones = data.zones.map { if (it.id == zoneId) updated else it })
    }

    fun deleteZone(data: ProjectData, zoneId: String): ProjectData {
        zone(data, zoneId)
        return data.copy(zones = data.zones.filterNot { it.id == zoneId })
    }

    // ── Líneas (cercas, linderos, vías…) ────────────────────────────────────

    fun createLine(
        data: ProjectData,
        name: String,
        category: String,
        path: List<Position>,
        description: String = "",
        id: String = newId(),
    ): ProjectData {
        val clean = lineName(name)
        validateLineCategory(category)
        val line = MapLine(
            id = id, category = category, categoryLabel = Catalogs.lineCategoryLabel(category), name = clean,
            description = description.trim(), lengthM = lineLength(path), path = path,
        )
        return data.copy(lines = data.lines + line)
    }

    fun updateLineDetails(data: ProjectData, lineId: String, name: String, category: String, description: String): ProjectData {
        val existing = line(data, lineId)
        val clean = lineName(name)
        validateLineCategory(category)
        val updated = existing.copy(
            name = clean, category = category, categoryLabel = Catalogs.lineCategoryLabel(category), description = description.trim(),
        )
        return data.copy(lines = data.lines.map { if (it.id == lineId) updated else it })
    }

    fun updateLineGeometry(data: ProjectData, lineId: String, path: List<Position>): ProjectData {
        val existing = line(data, lineId)
        val updated = existing.copy(path = path, lengthM = lineLength(path))
        return data.copy(lines = data.lines.map { if (it.id == lineId) updated else it })
    }

    fun deleteLine(data: ProjectData, lineId: String): ProjectData {
        line(data, lineId)
        return data.copy(lines = data.lines.filterNot { it.id == lineId })
    }

    // ── Elementos (puntos) ──────────────────────────────────────────────────

    fun createElement(
        data: ProjectData,
        name: String,
        elementType: String,
        position: Position,
        description: String = "",
        id: String = newId(),
    ): ProjectData {
        validateElementType(elementType)
        val clean = elementName(name)
        validatePosition(position)
        val element = MapElement(
            id = id, elementType = elementType, typeLabel = Catalogs.elementTypeLabel(elementType), name = clean,
            description = description.trim(), position = position,
        )
        return data.copy(elements = data.elements + element)
    }

    fun updateElement(data: ProjectData, elementId: String, name: String, elementType: String, description: String): ProjectData {
        val existing = element(data, elementId)
        validateElementType(elementType)
        val clean = elementName(name)
        val updated = existing.copy(
            name = clean, elementType = elementType, typeLabel = Catalogs.elementTypeLabel(elementType), description = description.trim(),
        )
        return data.copy(elements = data.elements.map { if (it.id == elementId) updated else it })
    }

    fun moveElement(data: ProjectData, elementId: String, position: Position): ProjectData {
        val existing = element(data, elementId)
        validatePosition(position)
        return data.copy(elements = data.elements.map { if (it.id == elementId) existing.copy(position = position) else it })
    }

    fun deleteElement(data: ProjectData, elementId: String): ProjectData {
        element(data, elementId)
        return data.copy(elements = data.elements.filterNot { it.id == elementId })
    }

    // ── Área de trabajo (una por finca) ─────────────────────────────────────

    /** Crea el área de trabajo o reemplaza su forma; conserva el id si ya existía. */
    fun saveWorkArea(data: ProjectData, ring: List<Position>): ProjectData {
        val (area, perimeter) = polygonMeasures(ring)
        val id = data.workArea?.id ?: newId()
        return data.copy(workArea = WorkArea(id, listOf(LayersJson.closed(ring)), area, perimeter))
    }

    fun deleteWorkArea(data: ProjectData): ProjectData {
        if (data.workArea == null) throw EditException("La finca no tiene área de trabajo.")
        return data.copy(workArea = null)
    }

    // ── Reglas ──────────────────────────────────────────────────────────────

    private fun zone(data: ProjectData, id: String) = data.zones.firstOrNull { it.id == id } ?: throw EditException("La zona no existe.")
    private fun line(data: ProjectData, id: String) = data.lines.firstOrNull { it.id == id } ?: throw EditException("La línea no existe.")
    private fun element(data: ProjectData, id: String) = data.elements.firstOrNull { it.id == id } ?: throw EditException("El elemento no existe.")

    private fun zoneName(name: String) = name.trim().ifEmpty { throw EditException("El nombre de la zona es obligatorio.") }
    private fun lineName(name: String) = name.trim().ifEmpty { throw EditException("El nombre de la línea es obligatorio.") }
    private fun elementName(name: String) = name.trim().ifEmpty { throw EditException("El nombre del elemento es obligatorio.") }

    private fun validateZoneStatus(status: String) {
        if (Catalogs.zoneStatuses.none { it.key == status }) throw EditException("Selecciona un estado de zona válido.")
    }

    private fun validateLineCategory(category: String) {
        if (Catalogs.lineCategories.none { it.key == category }) throw EditException("Selecciona un tipo de línea válido.")
    }

    private fun validateElementType(type: String) {
        if (Catalogs.elementTypes.none { it.key == type }) throw EditException("Selecciona un tipo de elemento válido.")
    }

    private fun validateCapacity(capacity: Double?) {
        if (capacity != null && capacity < 0) throw EditException("La capacidad ganadera no puede ser negativa.")
    }

    private fun validatePosition(p: Position) {
        if (p.lat !in -90.0..90.0) throw EditException("La latitud debe estar entre -90 y 90.")
        if (p.lon !in -180.0..180.0) throw EditException("La longitud debe estar entre -180 y 180.")
    }

    private fun polygonMeasures(ring: List<Position>): Pair<Double, Double> = try {
        GeoMeasure.validateSimplePolygon(ring)
        GeoMeasure.polygonAreaPerimeter(ring)
    } catch (e: GeoValidationException) {
        throw EditException(e.message ?: "")
    }

    private fun lineLength(path: List<Position>): Double = try {
        GeoMeasure.polylineLength(path)
    } catch (e: GeoValidationException) {
        throw EditException(e.message ?: "")
    }

    private fun rejectOverlaps(data: ProjectData, ring: List<Position>, excludeId: String?) {
        val overlapping = data.zones.filter { it.id != excludeId && GeoMeasure.polygonsOverlap(ring, it.polygon.firstOrNull().orEmpty()) }
        if (overlapping.isNotEmpty()) {
            val names = overlapping.joinToString(", ") { "\"${it.name}\"" }
            throw EditException(
                "El potrero se superpone con $names. Dos potreros pueden compartir un límite o un vértice, pero no área. " +
                    "Ajusta los vértices para que solo toquen el borde vecino.",
            )
        }
    }
}
