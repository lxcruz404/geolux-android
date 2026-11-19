package me.luiscruzandresgomezrodolfohernandez.geolux.ui.map

import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.geo.Bounds
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.maps.Style

/**
 * Mueve la cámara de [FarmMap] desde fuera del mapa: botones flotantes, fichas y
 * «Ver en el mapa». [FarmMap] le asigna el mapa cuando está listo.
 */
class FarmMapController {
    internal var map: MapLibreMap? = null

    /**
     * Encuadra [bounds]; si es un solo punto, lo centra con zoom 17.
     * [bottomPaddingPx] deja sitio a la ficha abierta abajo.
     */
    fun fit(bounds: Bounds, paddingPx: Int = 120, bottomPaddingPx: Int = paddingPx) {
        val map = map ?: return
        if (bounds.width < 1e-9 || bounds.height < 1e-9) {
            center(Position(bounds.west, bounds.south), zoom = 17.0)
            return
        }
        val box = LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west)
        map.easeCamera(CameraUpdateFactory.newLatLngBounds(box, paddingPx, paddingPx, paddingPx, bottomPaddingPx))
    }

    fun center(position: Position, zoom: Double? = null) {
        val map = map ?: return
        val target = LatLng(position.lat, position.lon)
        map.easeCamera(if (zoom != null) CameraUpdateFactory.newLatLngZoom(target, zoom) else CameraUpdateFactory.newLatLng(target))
    }
}

/**
 * Grupos de capas que se encienden y apagan en la hoja «Capas del mapa» (DESIGN.md §9, 04e).
 * [Ortho] son todos los ortomosaicos (cada uno se puede apagar aparte); [OrthoBounds], sus contornos.
 */
enum class MapLayerGroup { Ortho, OrthoBounds, WorkArea, Zones, Elements, Lines, Missions }

/** Prefijo de la capa ráster de cada ortomosaico: `ortomosaico-<product_id>`. */
internal const val ORTHO_LAYER_PREFIX = "ortomosaico-"

/** Capas fijas de [farmStyleJson] de cada grupo (las de los ortomosaicos dependen de la finca). */
internal fun MapLayerGroup.layerIds(): List<String> = when (this) {
    MapLayerGroup.Ortho -> emptyList()
    MapLayerGroup.OrthoBounds -> listOf("ortomosaico-limites")
    MapLayerGroup.WorkArea -> listOf("area-relleno", "area-borde")
    MapLayerGroup.Zones -> listOf("potreros-relleno", "potreros-borde", "potreros-seleccion")
    MapLayerGroup.Missions -> listOf("misiones-relleno", "misiones-borde", "rutas-mision")
    MapLayerGroup.Lines -> listOf("lineas-contorno") + LINE_STYLES.keys.map { "lineas-$it" } + listOf("lineas-toque", "lineas-seleccion")
    MapLayerGroup.Elements -> listOf("elementos", "elementos-seleccion")
}

/**
 * Muestra u oculta cada grupo y, dentro de [MapLayerGroup.Ortho], cada ortomosaico
 * de [hiddenOrthos] (ids de producto). Una capa oculta tampoco responde al toque.
 */
internal fun applyLayerVisibility(style: Style, visible: Set<MapLayerGroup>, hiddenOrthos: Set<String> = emptySet()) {
    fun value(on: Boolean) = visibility(if (on) Property.VISIBLE else Property.NONE)
    MapLayerGroup.entries.forEach { group ->
        group.layerIds().forEach { id -> style.getLayer(id)?.setProperties(value(group in visible)) }
    }
    style.layers.filter { it.id.startsWith(ORTHO_LAYER_PREFIX) && it.id != "ortomosaico-limites" }.forEach { layer ->
        val productId = layer.id.removePrefix(ORTHO_LAYER_PREFIX)
        layer.setProperties(value(MapLayerGroup.Ortho in visible && productId !in hiddenOrthos))
    }
}
