package me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit

import me.luiscruzandresgomezrodolfohernandez.geolux.core.edit.EditException
import me.luiscruzandresgomezrodolfohernandez.geolux.core.edit.FarmEditor
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.FeatureKind

/**
 * Lo que el usuario hace en el mapa, como operaciones de [FarmEditor] sobre la
 * finca. Funciones de `(ProjectData) -> ProjectData` para `FincaViewModel.edit`:
 * si la edición no cumple las reglas de Desktop lanzan [EditException] y no se
 * guarda nada.
 */
object EditActions {
    /** Guardar un objeto recién dibujado con sus datos del formulario. */
    fun createNew(target: EditTarget, points: List<Position>, fields: FeatureFields): (ProjectData) -> ProjectData = { data ->
        when (target) {
            EditTarget.Zone -> FarmEditor.createZone(
                data, fields.name, points, fields.description, fields.color, fields.status, capacity(fields),
            )
            EditTarget.Line -> FarmEditor.createLine(data, fields.name, fields.type, points, fields.description)
            EditTarget.Element -> FarmEditor.createElement(
                data, fields.name, fields.type, points.singleOrNull() ?: throw EditException("Toca el mapa para colocar el elemento."), fields.description,
            )
            EditTarget.WorkArea -> FarmEditor.saveWorkArea(data, points)
        }
    }

    /** «Editar datos» de un objeto existente (nunca toca su forma). */
    fun updateDetails(target: EditTarget, id: String, fields: FeatureFields): (ProjectData) -> ProjectData = { data ->
        when (target) {
            EditTarget.Zone -> FarmEditor.updateZoneDetails(data, id, fields.name, fields.description, fields.color, fields.status, capacity(fields))
            EditTarget.Line -> FarmEditor.updateLineDetails(data, id, fields.name, fields.type, fields.description)
            EditTarget.Element -> FarmEditor.updateElement(data, id, fields.name, fields.type, fields.description)
            EditTarget.WorkArea -> data
        }
    }

    /** Guardar la forma editada (vértices movidos, agregados o quitados; el punto movido). */
    fun saveShape(session: EditSession): (ProjectData) -> ProjectData = { data ->
        val id = session.featureId ?: throw EditException("No hay nada que guardar.")
        when (session.target) {
            EditTarget.Zone -> FarmEditor.updateZoneGeometry(data, id, session.points)
            EditTarget.Line -> FarmEditor.updateLineGeometry(data, id, session.points)
            EditTarget.Element -> FarmEditor.moveElement(data, id, session.points.single())
            EditTarget.WorkArea -> FarmEditor.saveWorkArea(data, session.points)
        }
    }

    fun delete(kind: FeatureKind, id: String): (ProjectData) -> ProjectData = { data ->
        when (kind) {
            FeatureKind.Zone -> FarmEditor.deleteZone(data, id)
            FeatureKind.Line -> FarmEditor.deleteLine(data, id)
            FeatureKind.Element -> FarmEditor.deleteElement(data, id)
            FeatureKind.WorkArea -> FarmEditor.deleteWorkArea(data)
            FeatureKind.Mission -> throw EditException("Las misiones se planifican en GeoLux Desktop.")
        }
    }

    /** «Deshacer» tras guardar: vuelve a dejar las capas editables como estaban. */
    fun restore(previous: ProjectData): (ProjectData) -> ProjectData = { data ->
        data.copy(workArea = previous.workArea, zones = previous.zones, elements = previous.elements, lines = previous.lines)
    }

    private fun capacity(fields: FeatureFields): Double? {
        if (!fields.capacityIsValid) throw EditException("La capacidad de animales debe ser un número.")
        return fields.capacityValue()
    }

    /** Nombre con el que se pregunta antes de eliminar. */
    fun displayName(data: ProjectData, kind: FeatureKind, id: String): String = when (kind) {
        FeatureKind.Zone -> data.zones.firstOrNull { it.id == id }?.name
        FeatureKind.Line -> data.lines.firstOrNull { it.id == id }?.let { it.name.ifBlank { it.categoryLabel } }
        FeatureKind.Element -> data.elements.firstOrNull { it.id == id }?.let { it.name.ifBlank { it.typeLabel } }
        FeatureKind.WorkArea -> "el área de trabajo"
        FeatureKind.Mission -> data.missions.firstOrNull { it.id == id }?.name
    } ?: ""
}
