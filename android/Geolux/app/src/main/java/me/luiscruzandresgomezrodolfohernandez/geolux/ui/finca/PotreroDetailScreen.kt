package me.luiscruzandresgomezrodolfohernandez.geolux.ui.finca

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Zone
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.elementTypeColor
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.zoneStatusFamily
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.EmptyMessage
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.FarmThumbnail
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.GeoLuxCard
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.ListRow
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.MetricCard
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.MetricRow
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.PrimaryButton
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.SectionHeader
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.StatusChip
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatArea
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatCount
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatLength
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.geo.isInside
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.geo.touches
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxShape
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.RecyclerList

/**
 * Pantalla 06 «Detalle de potrero» (DESIGN.md §9): datos del potrero,
 * descripción, elementos dentro, líneas del potrero y «Ver en el mapa».
 * Qué elementos y líneas le pertenecen se calcula con su geometría.
 */
@Composable
fun PotreroDetailScreen(
    data: ProjectData,
    zone: Zone,
    onShowOnMap: () -> Unit,
    modifier: Modifier = Modifier,
    /** «Crear actividad» con este potrero ya enlazado; null la oculta. */
    onCreateActivity: (() -> Unit)? = null,
) {
    val elements = remember(data, zone) { data.elements.filter { it.position.isInside(zone.polygon) } }
    val lines = remember(data, zone) { data.lines.filter { it.path.touches(zone.polygon) } }
    val notSpecified = stringResource(R.string.not_specified)

    RecyclerList(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(GeoLuxDimens.screenMargin),
        verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.cardSpacing),
    ) {
        item {
            FarmThumbnail(
                data = data,
                highlightZoneId = zone.id,
                description = stringResource(R.string.thumbnail_zone_description, zone.name),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(GeoLuxShape.card),
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(zone.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(GeoLuxDimens.spacingUnit * 2))
                StatusChip(zone.statusLabel, zoneStatusFamily(zone.status))
            }
        }
        item {
            MetricRow(
                stringResource(R.string.label_area) to formatArea(zone.areaM2),
                stringResource(R.string.label_perimeter) to formatLength(zone.perimeterM),
            )
        }
        item {
            MetricCard(
                label = stringResource(R.string.label_capacity),
                value = zone.livestockCapacity?.let { stringResource(R.string.animals, formatCount(it)) }
                    ?: stringResource(R.string.undefined),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            GeoLuxCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.label_description), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(GeoLuxDimens.spacingUnit * 2))
                Text(
                    text = zone.description.ifBlank { notSpecified },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (zone.description.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
        item {
            GeoLuxCard(Modifier.fillMaxWidth()) {
                SectionHeader(
                    title = stringResource(R.string.section_zone_elements),
                    trailing = elements.size.toString(),
                )
                if (elements.isEmpty()) EmptyMessage(stringResource(R.string.zone_elements_empty))
                elements.forEach { element ->
                    ListRow(
                        overline = element.typeLabel,
                        title = element.name.ifBlank { notSpecified },
                        leadingColor = elementTypeColor(element.elementType),
                    )
                }
            }
        }
        item {
            GeoLuxCard(Modifier.fillMaxWidth()) {
                SectionHeader(
                    title = stringResource(R.string.section_zone_lines),
                    trailing = lines.size.toString(),
                )
                if (lines.isEmpty()) EmptyMessage(stringResource(R.string.zone_lines_empty))
                lines.forEach { line ->
                    ListRow(
                        overline = line.categoryLabel,
                        title = line.name.ifBlank { notSpecified },
                        trailing = {
                            Text(
                                formatLength(line.lengthM),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
        item {
            PrimaryButton(
                text = stringResource(R.string.action_show_on_map),
                onClick = onShowOnMap,
                modifier = Modifier.padding(top = GeoLuxDimens.cardSpacing),
            )
        }
        if (onCreateActivity != null) {
            item {
                PrimaryButton(
                    text = "Crear actividad en este potrero",
                    onClick = onCreateActivity,
                    modifier = Modifier.padding(top = GeoLuxDimens.cardSpacing),
                )
            }
        }
    }
}

@Preview(name = "06 Claro", showBackground = true, heightDp = 1100)
@Preview(name = "06 Oscuro", showBackground = true, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PotreroDetailPreview() {
    val demo = FakeProjectRepository.demo
    GeoLuxTheme { PotreroDetailScreen(data = demo, zone = demo.zones.first(), onShowOnMap = {}) }
}
