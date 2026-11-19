package me.luiscruzandresgomezrodolfohernandez.geolux.ui.finca

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Zone
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.parseHexColor
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.zoneStatusFamily
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.EmptyMessage
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.GeoLuxCard
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.ListRow
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.StatusChip
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatArea
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatCount
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxShape
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.RecyclerList

/** Filtro por estado del contrato (`zones.status`); [status] null = todos. */
enum class ZoneFilter(val status: String?) { All(null), Active("active"), Resting("resting"), Inactive("inactive") }

/** Texto de capacidad de carga: «Capacidad: 40 animales» o «Capacidad: Sin definir» (DESIGN.md §10). */
@Composable
fun capacityText(zone: Zone): String =
    zone.livestockCapacity?.let { stringResource(R.string.capacity_animals, formatCount(it)) }
        ?: stringResource(R.string.capacity_undefined)

/** Pantalla 05 «Potreros» (DESIGN.md §9): resumen, filtros por estado y tarjetas. */
@Composable
fun PotrerosScreen(
    zones: List<Zone>,
    onZoneClick: (zoneId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(ZoneFilter.All) }
    val shown = zones.filter { filter.status == null || it.status == filter.status }

    RecyclerList(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(GeoLuxDimens.screenMargin),
        verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.cardSpacing),
    ) {
        item {
            GeoLuxCard(Modifier.fillMaxWidth()) {
                Text(
                    pluralStringResource(R.plurals.zone_count, zones.size, zones.size),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.zones_area_total, formatArea(zones.sumOf { it.areaM2 })),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(GeoLuxDimens.spacingUnit * 2),
            ) {
                ZoneFilter.entries.forEach { option ->
                    val count = zones.count { option.status == null || it.status == option.status }
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(stringResource(option.label(), count)) },
                        shape = GeoLuxShape.chip,
                        modifier = Modifier.testTag("filter_${option.name}"),
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                EmptyMessage(
                    stringResource(if (zones.isEmpty()) R.string.zones_empty else R.string.zones_filter_empty),
                )
            }
        }
        // onItemClick: el Adapter entrega el potrero de la fila tocada.
        items(shown, key = { it.id }, onItemClick = { zone -> onZoneClick(zone.id) }) { zone ->
            GeoLuxCard {
                ListRow(
                    title = zone.name,
                    subtitle = "${formatArea(zone.areaM2)} · ${capacityText(zone)}",
                    leadingColor = parseHexColor(zone.color, GeoLuxTheme.colors.mapZoneDefault),
                    trailing = { StatusChip(zone.statusLabel, zoneStatusFamily(zone.status)) },
                    showChevron = true,
                )
            }
        }
    }
}

private fun ZoneFilter.label(): Int = when (this) {
    ZoneFilter.All -> R.string.filter_all
    ZoneFilter.Active -> R.string.filter_active
    ZoneFilter.Resting -> R.string.filter_resting
    ZoneFilter.Inactive -> R.string.filter_inactive
}

@Preview(name = "05 Claro", showBackground = true, heightDp = 720)
@Preview(name = "05 Oscuro", showBackground = true, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PotrerosPreview() {
    GeoLuxTheme { PotrerosScreen(zones = FakeProjectRepository.demo.zones, onZoneClick = {}) }
}
