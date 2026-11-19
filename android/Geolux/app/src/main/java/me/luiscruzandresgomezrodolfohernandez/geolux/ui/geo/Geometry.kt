package me.luiscruzandresgomezrodolfohernandez.geolux.ui.geo

import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Ring

// Cálculos sobre las geometrías del contrato para la UI: qué elementos y
// líneas caen en un potrero (pantalla 06) y el encuadre de la miniatura.
// Coordenadas WGS84 [lon, lat]; a escala de finca basta tratarlas como planas.

/** Punto dentro de un polígono (primer anillo exterior, el resto huecos), por cruce de rayos. */
fun Position.isInside(polygon: List<Ring>): Boolean {
    val exterior = polygon.firstOrNull() ?: return false
    return isInsideRing(exterior) && polygon.drop(1).none { isInsideRing(it) }
}

private fun Position.isInsideRing(ring: Ring): Boolean {
    var inside = false
    var j = ring.lastIndex
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[j]
        if ((a.lat > lat) != (b.lat > lat) &&
            lon < (b.lon - a.lon) * (lat - a.lat) / (b.lat - a.lat) + a.lon
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

/** Una línea pertenece a un potrero si algún vértice está dentro o si cruza su borde. */
fun List<Position>.touches(polygon: List<Ring>): Boolean {
    if (any { it.isInside(polygon) }) return true
    val edges = polygon.flatMap { ring -> ring.zipWithNext() }
    return zipWithNext().any { (p, q) -> edges.any { (a, b) -> segmentsIntersect(p, q, a, b) } }
}

private fun segmentsIntersect(p: Position, q: Position, a: Position, b: Position): Boolean {
    val d1 = cross(a, b, p)
    val d2 = cross(a, b, q)
    val d3 = cross(p, q, a)
    val d4 = cross(p, q, b)
    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true
    return (d1 == 0.0 && onSegment(a, b, p)) || (d2 == 0.0 && onSegment(a, b, q)) ||
        (d3 == 0.0 && onSegment(p, q, a)) || (d4 == 0.0 && onSegment(p, q, b))
}

private fun cross(o: Position, a: Position, b: Position) =
    (a.lon - o.lon) * (b.lat - o.lat) - (a.lat - o.lat) * (b.lon - o.lon)

private fun onSegment(a: Position, b: Position, p: Position) =
    p.lon in minOf(a.lon, b.lon)..maxOf(a.lon, b.lon) && p.lat in minOf(a.lat, b.lat)..maxOf(a.lat, b.lat)

/** Rectángulo [oeste, sur, este, norte] que contiene todas las posiciones. */
data class Bounds(val west: Double, val south: Double, val east: Double, val north: Double) {
    val width get() = east - west
    val height get() = north - south
}

fun boundsOf(positions: Iterable<Position>): Bounds? {
    val list = positions.toList()
    if (list.isEmpty()) return null
    return Bounds(
        west = list.minOf { it.lon },
        south = list.minOf { it.lat },
        east = list.maxOf { it.lon },
        north = list.maxOf { it.lat },
    )
}
