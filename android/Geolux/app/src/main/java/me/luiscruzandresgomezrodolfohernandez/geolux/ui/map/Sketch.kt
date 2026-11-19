package me.luiscruzandresgomezrodolfohernandez.geolux.ui.map

import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** Forma de lo que se dibuja: un punto (elemento), una línea (cerca, vía…) o un polígono (potrero, área de trabajo). */
enum class SketchShape { Point, Line, Polygon }

/**
 * Geometría que el usuario está dibujando o editando, todavía sin guardar.
 * [points] va en orden; un polígono se guarda abierto (el cierre lo pone el
 * almacén). [color] es `#RRGGBB`.
 */
data class Sketch(val shape: SketchShape, val points: List<Position>, val color: String = "#2D6A4F") {
    /** Si con estos vértices ya se puede guardar. */
    val isComplete: Boolean
        get() = when (shape) {
            SketchShape.Point -> points.size == 1
            SketchShape.Line -> points.size >= 2
            SketchShape.Polygon -> points.size >= 3
        }
}

/** GeoJSON del boceto para la fuente `boceto`: el contorno o la línea y, aparte, sus vértices. */
fun sketchGeoJson(sketch: Sketch?): String {
    if (sketch == null || sketch.points.isEmpty()) return emptyCollection()
    val pts = sketch.points.map { Point.fromLngLat(it.lon, it.lat) }
    val features = mutableListOf<Feature>()
    when {
        sketch.shape == SketchShape.Polygon && pts.size >= 3 ->
            features += Feature.fromGeometry(Polygon.fromLngLats(listOf(pts + pts.first()))).apply { addStringProperty("parte", "area") }
        sketch.shape != SketchShape.Point && pts.size >= 2 ->
            features += Feature.fromGeometry(LineString.fromLngLats(pts)).apply { addStringProperty("parte", "linea") }
    }
    pts.forEachIndexed { i, p ->
        features += Feature.fromGeometry(p).apply {
            addStringProperty("parte", "vertice")
            addNumberProperty("orden", i)
        }
    }
    return FeatureCollection.fromFeatures(features).toJson()
}
