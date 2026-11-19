package me.luiscruzandresgomezrodolfohernandez.geolux.ui.map

import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Product
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatArea
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatDate
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme

/** Un ortomosaico de la finca en la hoja de capas: si sus teselas están listas para dibujarse. */
data class OrthoAvailability(val product: Product, val ready: Boolean)

/**
 * Hoja «Capas del mapa» (DESIGN.md §9, 04e): el mapa base (Satélite Esri,
 * Estándar OpenStreetMap o ninguno), cada ortomosaico de la finca por separado,
 * sus límites y un interruptor por capa con su resumen. Un ortomosaico que no
 * se pudo preparar queda desactivado.
 */
@Composable
fun LayersSheetContent(
    data: ProjectData,
    orthos: List<OrthoAvailability>,
    visible: Set<MapLayerGroup>,
    onToggle: (MapLayerGroup, Boolean) -> Unit,
    baseMap: BaseMap = BaseMap.None,
    onBaseMap: (BaseMap) -> Unit = {},
    hiddenOrthos: Set<String> = emptySet(),
    onToggleOrtho: (String, Boolean) -> Unit = { _, _ -> },
    /** false en el mapa sin finca: solo el mapa base. */
    showFarmLayers: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GeoLuxDimens.screenMargin)
            .padding(bottom = GeoLuxDimens.screenMargin),
        verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.spacingUnit),
    ) {
        Text(stringResource(R.string.layers_title), style = MaterialTheme.typography.headlineSmall)

        SectionTitle(stringResource(R.string.layers_base_map))
        BaseMapRow(BaseMap.Satellite, stringResource(R.string.base_map_satellite), stringResource(R.string.base_map_satellite_note), baseMap, onBaseMap)
        BaseMapRow(BaseMap.Standard, stringResource(R.string.base_map_standard), stringResource(R.string.base_map_standard_note), baseMap, onBaseMap)
        BaseMapRow(BaseMap.None, stringResource(R.string.base_map_none), stringResource(R.string.base_map_none_note), baseMap, onBaseMap)

        if (!showFarmLayers) return@Column
        SectionTitle(stringResource(R.string.layers_farm))
        if (orthos.isEmpty()) {
            LayerRow(MapLayerGroup.Ortho, stringResource(R.string.product_orthomosaic), stringResource(R.string.layers_no_ortho), false, visible, onToggle)
        } else {
            LayerRow(
                MapLayerGroup.Ortho, stringResource(R.string.layers_orthos),
                pluralStringResource(R.plurals.ortho_count, orthos.size, orthos.size), orthos.any { it.ready }, visible, onToggle,
            )
            orthos.forEach { o ->
                SwitchRow(
                    title = o.product.name,
                    subtitle = listOfNotNull(
                        // Original y recortado (Desktop, fase F): se dice cuál es cuál.
                        when {
                            o.product.isCropped -> stringResource(R.string.layers_ortho_cropped)
                            o.product.variant == "full" || orthos.any { it.product.isCropped } -> stringResource(R.string.layers_ortho_full)
                            else -> null
                        },
                        when {
                            !o.ready -> stringResource(R.string.map_ortho_unavailable)
                            o.product.isSynthetic -> stringResource(R.string.layers_ortho_synthetic, formatDate(o.product.createdAt))
                            else -> formatDate(o.product.createdAt)
                        },
                    ).joinToString(" · "),
                    checked = o.ready && MapLayerGroup.Ortho in visible && o.product.id !in hiddenOrthos,
                    enabled = o.ready && MapLayerGroup.Ortho in visible,
                    tag = "orto_${o.product.id}",
                    onChange = { onToggleOrtho(o.product.id, it) },
                    indent = true,
                )
            }
            LayerRow(MapLayerGroup.OrthoBounds, stringResource(R.string.layers_ortho_bounds), stringResource(R.string.layers_ortho_bounds_note), true, visible, onToggle)
        }
        LayerRow(
            MapLayerGroup.WorkArea, stringResource(R.string.layer_work_area),
            data.workArea?.let { formatArea(it.areaM2) } ?: stringResource(R.string.not_specified), true, visible, onToggle,
        )
        LayerRow(
            MapLayerGroup.Zones, stringResource(R.string.tab_potreros),
            pluralStringResource(R.plurals.zone_count, data.zones.size, data.zones.size), true, visible, onToggle,
        )
        LayerRow(
            MapLayerGroup.Elements, stringResource(R.string.label_elements),
            pluralStringResource(R.plurals.element_count, data.elements.size, data.elements.size), true, visible, onToggle,
        )
        LayerRow(
            MapLayerGroup.Lines, stringResource(R.string.label_lines),
            pluralStringResource(R.plurals.line_count, data.lines.size, data.lines.size), true, visible, onToggle,
        )
        LayerRow(
            MapLayerGroup.Missions, stringResource(R.string.label_missions),
            pluralStringResource(R.plurals.mission_count, data.missions.size, data.missions.size), true, visible, onToggle,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun BaseMapRow(option: BaseMap, title: String, subtitle: String, current: BaseMap, onSelect: (BaseMap) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = GeoLuxDimens.listRowMinHeight)
            .selectable(selected = option == current, role = Role.RadioButton, onClick = { onSelect(option) })
            .testTag("base_${option.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = option == current, onClick = null)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
    indent: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = GeoLuxDimens.listRowMinHeight)
            .padding(start = if (indent) 16.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun LayerRow(
    group: MapLayerGroup,
    title: String,
    subtitle: String,
    enabled: Boolean,
    visible: Set<MapLayerGroup>,
    onToggle: (MapLayerGroup, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = GeoLuxDimens.listRowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = enabled && group in visible,
            onCheckedChange = { onToggle(group, it) },
            enabled = enabled,
            modifier = Modifier.testTag("capa_${group.name}"),
        )
    }
}

@Preview(name = "04e Capas", heightDp = 640)
@Composable
private fun LayersSheetPreview() {
    GeoLuxTheme {
        Surface {
            val demo = FakeProjectRepository.demo
            LayersSheetContent(
                demo, demo.products.filter { it.isOrthomosaic }.map { OrthoAvailability(it, true) },
                visible = MapLayerGroup.entries.toSet(), onToggle = { _, _ -> }, baseMap = BaseMap.Satellite,
            )
        }
    }
}
