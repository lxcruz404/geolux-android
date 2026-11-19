package me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit

import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.FeatureKind
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.Sketch
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.SketchShape

/** Lo que se dibuja o edita en el mapa (las entidades del módulo de mapas de Desktop). */
enum class EditTarget(val shape: SketchShape, val kind: FeatureKind) {
    Zone(SketchShape.Polygon, FeatureKind.Zone),
    Line(SketchShape.Line, FeatureKind.Line),
    Element(SketchShape.Point, FeatureKind.Element),
    WorkArea(SketchShape.Polygon, FeatureKind.WorkArea),
    ;

    companion object {
        fun of(kind: FeatureKind): EditTarget? = entries.firstOrNull { it.kind == kind }
    }
}

/**
 * Una geometría en edición, todavía sin guardar: un objeto nuevo ([featureId]
 * null) o la forma de uno existente. Cada cambio guarda el estado anterior en
 * [history] para «Deshacer». Inmutable: cada operación devuelve otra sesión.
 */
data class EditSession(
    val target: EditTarget,
    val featureId: String?,
    val points: List<Position>,
    val history: List<List<Position>> = emptyList(),
    /** Vértice elegido (tocado) para quitarlo. */
    val selectedVertex: Int? = null,
) {
    val sketch: Sketch get() = Sketch(target.shape, points)
    val canFinish: Boolean get() = sketch.isComplete
    val canUndo: Boolean get() = history.isNotEmpty()

    /** Un polígono necesita 3 vértices y una línea 2: no se puede quitar uno por debajo de eso. */
    val canRemoveSelected: Boolean
        get() = selectedVertex != null && points.size > when (target.shape) {
            SketchShape.Polygon -> 3
            SketchShape.Line -> 2
            SketchShape.Point -> 1
        }

    private fun change(newPoints: List<Position>, selected: Int? = null) =
        copy(points = newPoints, history = history + listOf(points), selectedVertex = selected)

    /** Toque en el mapa: un punto se coloca ahí; una línea o un polígono suman un vértice al final. */
    fun tap(p: Position): EditSession = when (target.shape) {
        SketchShape.Point -> change(listOf(p))
        else -> change(points + p)
    }

    /** Antes de arrastrar un vértice: un solo «Deshacer» devuelve el arrastre entero. */
    fun beginDrag(): EditSession = copy(history = history + listOf(points))

    /** Durante el arrastre (sin historial por cada movimiento). */
    fun moveVertex(index: Int, p: Position): EditSession =
        if (index !in points.indices) this else copy(points = points.toMutableList().also { it[index] = p }, selectedVertex = index)

    /** Arrastrar el punto medio de un borde crea un vértice nuevo detrás de [afterIndex]. */
    fun insertVertex(afterIndex: Int, p: Position): EditSession =
        change(points.toMutableList().also { it.add(afterIndex + 1, p) }, selected = afterIndex + 1)

    fun select(index: Int?): EditSession = copy(selectedVertex = index?.takeIf { it in points.indices })

    fun removeSelected(): EditSession {
        val i = selectedVertex ?: return this
        if (!canRemoveSelected) return this
        return change(points.filterIndexed { j, _ -> j != i })
    }

    fun undo(): EditSession = if (history.isEmpty()) this else copy(points = history.last(), history = history.dropLast(1), selectedVertex = null)

    /** Puntos medios de cada borde (con el de cierre en un polígono), para insertar vértices. */
    fun midpoints(): List<Pair<Int, Position>> {
        if (target.shape == SketchShape.Point || points.size < 2) return emptyList()
        val pairs = points.indices.zipWithNext().toMutableList()
        if (target.shape == SketchShape.Polygon && points.size >= 3) pairs += points.lastIndex to 0
        return pairs.map { (a, b) -> a to Position((points[a].lon + points[b].lon) / 2, (points[a].lat + points[b].lat) / 2) }
    }

    companion object {
        fun new(target: EditTarget) = EditSession(target, null, emptyList())

        /** Editar la forma de un objeto existente: sus vértices actuales (los anillos, sin el de cierre). */
        fun reshape(data: ProjectData, kind: FeatureKind, id: String): EditSession? {
            fun open(ring: List<Position>) = if (ring.size >= 2 && ring.first() == ring.last()) ring.dropLast(1) else ring
            return when (kind) {
                FeatureKind.Zone -> data.zones.firstOrNull { it.id == id }?.let { EditSession(EditTarget.Zone, id, open(it.polygon.first())) }
                FeatureKind.Line -> data.lines.firstOrNull { it.id == id }?.let { EditSession(EditTarget.Line, id, it.path) }
                FeatureKind.Element -> data.elements.firstOrNull { it.id == id }?.let { EditSession(EditTarget.Element, id, listOf(it.position)) }
                FeatureKind.WorkArea -> data.workArea?.takeIf { it.id == id }?.let { EditSession(EditTarget.WorkArea, id, open(it.polygon.first())) }
                FeatureKind.Mission -> null // las misiones son del planificador de Desktop
            }
        }
    }
}
