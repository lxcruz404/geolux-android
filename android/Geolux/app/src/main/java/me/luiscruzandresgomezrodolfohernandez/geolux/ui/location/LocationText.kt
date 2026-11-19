package me.luiscruzandresgomezrodolfohernandez.geolux.ui.location

import java.util.Locale
import kotlin.math.roundToInt
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.data.LocationState
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.geo.isInside

/** Precisión del GPS en metros enteros: «±5 m» (DESIGN.md §9, 04f). Nunca menos de 1 m. */
fun formatAccuracy(accuracyM: Float): String = "±${accuracyM.roundToInt().coerceAtLeast(1)} m"

/** Latitud y longitud con 5 decimales (≈ 1 m) y punto decimal, como el resto de la app. */
fun formatCoordinates(lat: Double, lon: Double): String = String.format(Locale.US, "%.5f, %.5f", lat, lon)

/** Dónde cae la posición respecto a la finca. */
sealed interface PlaceInFinca {
    data class InZone(val zoneName: String) : PlaceInFinca
    data object InWorkArea : PlaceInFinca
    data object Outside : PlaceInFinca
}

/** Potrero que contiene la posición; si ninguno, si está dentro del área de trabajo o fuera. */
fun placeOf(fix: LocationState.Fix, data: ProjectData): PlaceInFinca {
    val position = Position(lon = fix.lon, lat = fix.lat)
    data.zones.firstOrNull { position.isInside(it.polygon) }?.let { return PlaceInFinca.InZone(it.name) }
    if (data.workArea?.let { position.isInside(it.polygon) } == true) return PlaceInFinca.InWorkArea
    return PlaceInFinca.Outside
}
