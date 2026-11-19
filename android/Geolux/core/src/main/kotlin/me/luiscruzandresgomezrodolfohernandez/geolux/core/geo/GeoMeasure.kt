package me.luiscruzandresgomezrodolfohernandez.geolux.core.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position

/**
 * Medidas y reglas geométricas de GeoLux Desktop (`core/geo_measure.py`),
 * con el **mismo algoritmo**: un área calculada en el teléfono es la que
 * calcularía Desktop para los mismos vértices.
 *
 * Proyección local equirectangular: 111 320 m por grado de latitud y, en
 * longitud, eso por el coseno de la latitud media. Área de Gauss (shoelace),
 * perímetro y longitud en ese plano. Precisa a menos del 1 % para una finca
 * (decenas de km²); no es geodesia elipsoidal, igual que en Desktop.
 */
object GeoMeasure {
    private const val METERS_PER_DEGREE_LATITUDE = 111_320.0

    /** Por debajo de esto un «polígono» es una línea o un punto (Desktop: 0,01 m²). */
    private const val MIN_POLYGON_AREA_M2 = 0.01

    /** Distancia a la que un punto «está sobre» otro borde al comprobar solapes (1 mm, como Desktop). */
    const val OVERLAP_TOLERANCE_M = 0.001

    private data class Xy(val x: Double, val y: Double)

    private fun toLocalMeters(points: List<Position>): List<Xy> {
        val meanLat = points.sumOf { it.lat } / points.size
        val metersPerDegreeLon = METERS_PER_DEGREE_LATITUDE * cos(meanLat * PI / 180)
        return points.map { Xy(it.lon * metersPerDegreeLon, it.lat * METERS_PER_DEGREE_LATITUDE) }
    }

    /** Sin el vértice de cierre repetido. */
    private fun openRing(ring: List<Position>): List<Position> =
        if (ring.size >= 2 && ring.first() == ring.last()) ring.dropLast(1) else ring

    /**
     * Área (m²) y perímetro (m) de un anillo en WGS84, abierto o cerrado.
     * @throws GeoValidationException con menos de 3 vértices.
     */
    fun polygonAreaPerimeter(ring: List<Position>): Pair<Double, Double> {
        val points = openRing(ring)
        if (points.size < 3) throw GeoValidationException("Se necesitan al menos 3 vértices para calcular un área.")
        val projected = toLocalMeters(points)
        var shoelace = 0.0
        var perimeter = 0.0
        for (i in projected.indices) {
            val a = projected[i]
            val b = projected[(i + 1) % projected.size]
            shoelace += a.x * b.y - b.x * a.y
            perimeter += hypot(b.x - a.x, b.y - a.y)
        }
        return abs(shoelace) / 2.0 to perimeter
    }

    /**
     * Longitud (m) de una línea abierta; nunca se cierra sola.
     * @throws GeoValidationException con menos de 2 vértices.
     */
    fun polylineLength(points: List<Position>): Double {
        if (points.size < 2) throw GeoValidationException("Se necesitan al menos 2 vértices para medir una distancia.")
        val projected = toLocalMeters(points)
        return projected.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()
    }

    /**
     * Rechaza lo que no es una parcela real: menos de 3 vértices distintos, un
     * borde que cruza a otro (pajarita) o área nula (vértices alineados). Dos
     * bordes que solo se tocan sí se aceptan, como en Desktop.
     */
    fun validateSimplePolygon(ring: List<Position>) {
        val points = openRing(ring)
        // Duplicados seguidos (doble toque) son bordes de largo cero, no un defecto.
        var deduped = points.filterIndexed { i, p -> i == 0 || p != points[i - 1] }
        if (deduped.size > 1 && deduped.first() == deduped.last()) deduped = deduped.dropLast(1)
        if (deduped.toSet().size < 3) throw GeoValidationException("Se necesitan al menos 3 vértices distintos para formar un polígono.")
        val n = deduped.size
        for (i in 0 until n) {
            val a1 = deduped[i]
            val a2 = deduped[(i + 1) % n]
            for (j in i + 1 until n) {
                if ((j + 1) % n == i || (i + 1) % n == j) continue // bordes vecinos comparten vértice
                if (segmentsCross(a1, a2, deduped[j], deduped[(j + 1) % n])) {
                    throw GeoValidationException("El polígono se cruza consigo mismo. Corrige los vértices para que los bordes no se crucen.")
                }
            }
        }
        if (polygonAreaPerimeter(deduped).first < MIN_POLYGON_AREA_M2) {
            throw GeoValidationException("El polígono no tiene área: todos sus vértices están alineados.")
        }
    }

    private fun orientation(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double) =
        (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)

    private fun orientation(a: Position, b: Position, c: Position) = orientation(a.lon, a.lat, b.lon, b.lat, c.lon, c.lat)

    private fun onSegment(a: Position, b: Position, p: Position) =
        p.lon in min(a.lon, b.lon)..max(a.lon, b.lon) && p.lat in min(a.lat, b.lat)..max(a.lat, b.lat)

    /** Si los segmentos comparten algún punto, incluidos extremos y tramos colineales. */
    fun segmentsIntersect(p1: Position, p2: Position, p3: Position, p4: Position): Boolean {
        val d1 = orientation(p3, p4, p1)
        val d2 = orientation(p3, p4, p2)
        val d3 = orientation(p1, p2, p3)
        val d4 = orientation(p1, p2, p4)
        if ((d1 > 0) != (d2 > 0) && (d3 > 0) != (d4 > 0)) return true
        return (d1 == 0.0 && onSegment(p3, p4, p1)) || (d2 == 0.0 && onSegment(p3, p4, p2)) ||
            (d3 == 0.0 && onSegment(p1, p2, p3)) || (d4 == 0.0 && onSegment(p1, p2, p4))
    }

    /** Solo un cruce propio: tocarse en un extremo o ir por el mismo borde no cuenta. */
    fun segmentsCross(p1: Position, p2: Position, p3: Position, p4: Position): Boolean {
        val d1 = orientation(p3, p4, p1)
        val d2 = orientation(p3, p4, p2)
        val d3 = orientation(p1, p2, p3)
        val d4 = orientation(p1, p2, p4)
        return (d1 > 0) != (d2 > 0) && (d3 > 0) != (d4 > 0)
    }

    /**
     * Punto dentro del polígono (rayo), en grados. Un vértice exacto cuenta como
     * dentro; el resto sigue la convención semiabierta, como Desktop.
     */
    fun pointInPolygon(point: Position, ring: List<Position>): Boolean {
        val points = openRing(ring)
        if (points.size < 3) return false
        if (point in points) return true
        return pointInXy(point.lon, point.lat, points.map { Xy(it.lon, it.lat) })
    }

    private fun pointInXy(x: Double, y: Double, points: List<Xy>): Boolean {
        var inside = false
        val n = points.size
        for (i in 0 until n) {
            val a = points[i]
            val b = points[(i + 1) % n]
            if ((a.y > y) != (b.y > y)) {
                val xIntersect = a.x + (y - a.y) * (b.x - a.x) / (b.y - a.y)
                if (x < xIntersect) inside = !inside
            }
        }
        return inside
    }

    /** (distancia, t) del punto al segmento a-b, con t en [0, 1]. */
    private fun distanceToSegment(p: Xy, a: Xy, b: Xy): Pair<Double, Double> {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val lengthSq = abx * abx + aby * aby
        if (lengthSq == 0.0) return hypot(p.x - a.x, p.y - a.y) to 0.0
        val t = (((p.x - a.x) * abx + (p.y - a.y) * aby) / lengthSq).coerceIn(0.0, 1.0)
        return hypot(p.x - (a.x + t * abx), p.y - (a.y + t * aby)) to t
    }

    private fun properCrossingT(a: Xy, b: Xy, c: Xy, d: Xy): Double? {
        val d1 = orientation(c.x, c.y, d.x, d.y, a.x, a.y)
        val d2 = orientation(c.x, c.y, d.x, d.y, b.x, b.y)
        val d3 = orientation(a.x, a.y, b.x, b.y, c.x, c.y)
        val d4 = orientation(a.x, a.y, b.x, b.y, d.x, d.y)
        if (!((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))) return null
        if (!((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return null
        return d1 / (d1 - d2)
    }

    /** Cada borde de [ring] cortado donde toca el borde de [other] (ver Desktop `_boundary_pieces`). */
    private fun boundaryPieces(ring: List<Xy>, other: List<Xy>, tolerance: Double): List<Pair<Xy, Xy>> {
        val pieces = mutableListOf<Pair<Xy, Xy>>()
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            val cuts = sortedSetOf(0.0, 1.0)
            for (vertex in other) {
                val (dist, t) = distanceToSegment(vertex, a, b)
                if (dist <= tolerance && t > 0.0 && t < 1.0) cuts += t
            }
            for (j in other.indices) properCrossingT(a, b, other[j], other[(j + 1) % other.size])?.let { cuts += it }
            cuts.toList().zipWithNext { t0, t1 ->
                val p = Xy(a.x + (b.x - a.x) * t0, a.y + (b.y - a.y) * t0)
                val q = Xy(a.x + (b.x - a.x) * t1, a.y + (b.y - a.y) * t1)
                if (hypot(q.x - p.x, q.y - p.y) > tolerance) pieces += p to q
            }
        }
        return pieces
    }

    private fun pieceOnBoundary(p: Xy, q: Xy, other: List<Xy>, tolerance: Double): Boolean =
        other.indices.any { j ->
            val c = other[j]
            val d = other[(j + 1) % other.size]
            distanceToSegment(p, c, d).first <= tolerance && distanceToSegment(q, c, d).first <= tolerance
        }

    /**
     * Si dos polígonos simples comparten área. Compartir un borde, parte de él o
     * un vértice **no** es solape (así se tocan dos potreros vecinos); meterse
     * en el otro, contenerlo, cruzarse o ser idénticos sí. Mismo método que
     * Desktop (`polygons_overlap`, V0.22).
     */
    fun polygonsOverlap(ringA: List<Position>, ringB: List<Position>, toleranceM: Double = OVERLAP_TOLERANCE_M): Boolean {
        val a = openRing(ringA)
        val b = openRing(ringB)
        if (a.size < 3 || b.size < 3) return false
        val projected = toLocalMeters(a + b)
        val pa = projected.subList(0, a.size)
        val pb = projected.subList(a.size, projected.size)
        if (pa.minOf { it.x } > pb.maxOf { it.x } + toleranceM || pb.minOf { it.x } > pa.maxOf { it.x } + toleranceM ||
            pa.minOf { it.y } > pb.maxOf { it.y } + toleranceM || pb.minOf { it.y } > pa.maxOf { it.y } + toleranceM
        ) {
            return false
        }
        var aAllOnB = true
        for ((p, q) in boundaryPieces(pa, pb, toleranceM)) {
            if (pieceOnBoundary(p, q, pb, toleranceM)) continue
            aAllOnB = false
            if (pointInXy((p.x + q.x) / 2, (p.y + q.y) / 2, pb)) return true
        }
        var bAllOnA = true
        for ((p, q) in boundaryPieces(pb, pa, toleranceM)) {
            if (pieceOnBoundary(p, q, pa, toleranceM)) continue
            bAllOnA = false
            if (pointInXy((p.x + q.x) / 2, (p.y + q.y) / 2, pa)) return true
        }
        // Ningún borde entra en el otro: o no se tocan por dentro, o son el mismo contorno.
        return aAllOnB && bAllOnA
    }
}

/** Una geometría que GeoLux no acepta; el mensaje es para el usuario (igual que Desktop). */
class GeoValidationException(message: String) : IllegalArgumentException(message)
