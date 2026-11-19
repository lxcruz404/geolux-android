package me.luiscruzandresgomezrodolfohernandez.geolux.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.MapElement
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.MapLine
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Mission
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Zone
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.planStatusFamily
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.zoneStatusFamily
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.PrimaryButton
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.StatusChip
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatArea
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatCount
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatDate
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatLength
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.geo.isInside
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens

/**
 * Ficha del objeto tocado en el mapa (DESIGN.md §9, 04b–04d), abajo y sobre el
 * mapa. Solo datos del contrato v1 (SPEC §5): nada inventado.
 */
@Composable
fun MapFicha(
    data: ProjectData,
    selection: MapSelection,
    onClose: () -> Unit,
    onOpenZone: (String) -> Unit,
    /** «Ver ortomosaico» de una misión; null si no se puede mostrar. */
    onShowOrtho: ((Mission) -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Botones de edición (Editar, Cambiar forma, Eliminar) cuando el objeto se puede editar. */
    actions: (@Composable () -> Unit)? = null,
    /** «Crear actividad» con el potrero ya enlazado (pestaña Gestión); null la oculta. */
    onCreateActivity: ((String) -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("ficha_mapa"),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(GeoLuxDimens.screenMargin),
            verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.cardSpacing),
        ) {
            when (selection.kind) {
                FeatureKind.Zone -> data.zones.firstOrNull { it.id == selection.id }?.let { ZoneFicha(it, onOpenZone, onCreateActivity) }
                FeatureKind.Element -> data.elements.firstOrNull { it.id == selection.id }?.let { ElementFicha(it, data) }
                FeatureKind.Line -> data.lines.firstOrNull { it.id == selection.id }?.let { LineFicha(it) }
                FeatureKind.Mission -> data.missions.firstOrNull { it.id == selection.id }?.let { mission ->
                    // Solo si la finca trae un ortomosaico de esta misión (`mission_id`, SPEC §6).
                    val hasOrtho = data.products.any { it.isOrthomosaic && it.missionId == mission.id }
                    MissionFicha(mission, onShowOrtho?.takeIf { hasOrtho }?.let { show -> { show(mission) } })
                }
                FeatureKind.WorkArea -> data.workArea?.let { area ->
                    Header(stringResource(R.string.layer_work_area)) {}
                    Fields(
                        stringResource(R.string.label_area) to formatArea(area.areaM2),
                        stringResource(R.string.label_perimeter) to formatLength(area.perimeterM),
                    )
                }
            }
            actions?.invoke()
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

@Composable
private fun Header(title: String, chip: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.size(GeoLuxDimens.spacingUnit))
        chip()
    }
}

/** Datos en dos columnas: etiqueta pequeña y valor. */
@Composable
private fun Fields(vararg fields: Pair<String, String>) {
    fields.toList().chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEach { (label, value) ->
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Description(text: String) {
    if (text.isBlank()) return
    Column {
        Text(stringResource(R.string.label_description), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ZoneFicha(zone: Zone, onOpenZone: (String) -> Unit, onCreateActivity: ((String) -> Unit)? = null) {
    Header(zone.name) { StatusChip(zone.statusLabel, zoneStatusFamily(zone.status)) }
    Fields(
        stringResource(R.string.label_area) to formatArea(zone.areaM2),
        stringResource(R.string.label_perimeter) to formatLength(zone.perimeterM),
        stringResource(R.string.label_capacity) to
            (zone.livestockCapacity?.let { stringResource(R.string.animals, formatCount(it)) } ?: stringResource(R.string.undefined)),
    )
    Description(zone.description)
    PrimaryButton(stringResource(R.string.map_zone_detail), onClick = { onOpenZone(zone.id) })
    if (onCreateActivity != null) {
        PrimaryButton("Crear actividad", onClick = { onCreateActivity(zone.id) }, modifier = Modifier.testTag("crear_actividad_potrero"))
    }
}

@Composable
private fun ElementFicha(element: MapElement, data: ProjectData) {
    val title = element.name.ifBlank { element.typeLabel }
    Header(title) { if (title != element.typeLabel) StatusChip(element.typeLabel, chipFamilyNeutral) }
    val zone = data.zones.firstOrNull { element.position.isInside(it.polygon) }
    Fields(
        stringResource(R.string.map_in_zone) to (zone?.name ?: stringResource(R.string.map_outside_zones)),
        stringResource(R.string.map_coordinates) to formatCoordinates(element.position.lat, element.position.lon),
    )
    Description(element.description)
}

@Composable
private fun LineFicha(line: MapLine) {
    val title = line.name.ifBlank { line.categoryLabel }
    Header(title) { if (title != line.categoryLabel) StatusChip(line.categoryLabel, chipFamilyNeutral) }
    Fields(stringResource(R.string.map_length) to formatLength(line.lengthM))
    Description(line.description)
}

@Composable
private fun MissionFicha(mission: Mission, onShowOrtho: (() -> Unit)?) {
    Header(mission.name) { StatusChip(mission.planStatusLabel, planStatusFamily(mission.planStatus)) }
    Fields(
        stringResource(R.string.map_planned_at) to formatDate(mission.plannedAt),
        stringResource(R.string.map_altitude) to formatNumber(mission.altitudeM, "m", 0),
        stringResource(R.string.map_gsd) to formatNumber(mission.gsdCmPx, "cm/px", 2),
        stringResource(R.string.map_speed) to formatNumber(mission.speedMps, "m/s", 2),
        stringResource(R.string.map_front_overlap) to formatNumber(mission.frontOverlapPercent, "%", 0),
        stringResource(R.string.map_side_overlap) to formatNumber(mission.sideOverlapPercent, "%", 0),
        stringResource(R.string.map_photos) to mission.photoCount.toString(),
        stringResource(R.string.map_waypoints) to mission.waypointCount.toString(),
        stringResource(R.string.map_distance) to formatLength(mission.totalDistanceM),
        stringResource(R.string.map_estimated_time) to formatMinutes(mission.estimatedTimeS),
        stringResource(R.string.map_battery) to batteryLabel(mission.batteryVerdict),
    )
    if (onShowOrtho != null) {
        PrimaryButton(stringResource(R.string.map_show_ortho), onShowOrtho, Modifier.testTag("ver_ortomosaico"))
    }
}

private val chipFamilyNeutral = me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.ChipFamily.Neutral

/** `6.30105, -70.19895` (lat, lon) con 5 decimales: ~1 m. */
fun formatCoordinates(lat: Double, lon: Double): String = String.format(Locale.US, "%.5f, %.5f", lat, lon)

/** Número con su unidad separada por espacio (DESIGN.md §10): `80 m`, `2.10 cm/px`, `75 %`. */
fun formatNumber(value: Double, unit: String, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f %s", value, unit)

/** Segundos → `18 min` (redondeado; mínimo 1 min si hay vuelo). */
fun formatMinutes(seconds: Double): String = "${if (seconds <= 0) 0 else maxOf(1, Math.round(seconds / 60.0))} min"

/** Etiquetas de Desktop (`core/flight/battery.py`); el contrato solo trae la clave. */
@Composable
fun batteryLabel(verdict: String?): String = when (verdict) {
    "viable" -> stringResource(R.string.battery_viable)
    "at_limit" -> stringResource(R.string.battery_at_limit)
    "not_viable" -> stringResource(R.string.battery_not_viable)
    null -> stringResource(R.string.not_specified)
    else -> verdict
}
