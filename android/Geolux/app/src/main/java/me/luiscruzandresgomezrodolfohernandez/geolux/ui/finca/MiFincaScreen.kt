package me.luiscruzandresgomezrodolfohernandez.geolux.ui.finca

import androidx.compose.ui.platform.testTag
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.totalArea
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.ChipFamily
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.elementTypeColor
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.planStatusFamily
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.EmptyMessage
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.FarmThumbnail
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.GeoLuxCard
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.ListRow
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.MetricRow
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.PrimaryButton
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.SectionHeader
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.StatusChip
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatDate
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatLength
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatMeasureArea
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxShape
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.RecyclerList
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.RecyclerListState
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.rememberRecyclerListState

/** Instalaciones de un tipo: etiqueta del contrato, color del catálogo y cantidad. */
private data class ElementTypeCount(val elementType: String, val label: String, val count: Int)

private fun ProjectData.elementTypeCounts(): List<ElementTypeCount> =
    elements.groupBy { it.elementType }
        .map { (type, list) -> ElementTypeCount(type, list.first().typeLabel, list.size) }
        .sortedWith(compareByDescending<ElementTypeCount> { it.count }.thenBy { it.label })

/**
 * Pantalla 03 «Mi finca» (DESIGN.md §9): miniatura, área, perímetro, potreros,
 * instalaciones por tipo, misiones e informes.
 */
@Composable
fun MiFincaScreen(
    data: ProjectData,
    onShowZones: () -> Unit,
    onExploreMap: () -> Unit,
    onOpenReport: (productId: String) -> Unit,
    modifier: Modifier = Modifier,
    listState: RecyclerListState = rememberRecyclerListState(),
    /** «Exportar paquete .glx»: la finca con lo editado en el teléfono, para Desktop. */
    onExport: (() -> Unit)? = null,
) {
    val notSpecified = stringResource(R.string.not_specified)
    // «Área total» es la de project.json; si falta, la del área de trabajo (como Mis fincas).
    val area = data.totalArea()?.let(::formatMeasureArea) ?: notSpecified
    val perimeter = data.workArea?.let { formatLength(it.perimeterM) } ?: notSpecified
    val typeCounts = data.elementTypeCounts()
    val reports = data.products.filter { it.kind == "report" }

    RecyclerList(
        state = listState,
        modifier = modifier.fillMaxSize().testTag("mi_finca"),
        contentPadding = PaddingValues(GeoLuxDimens.screenMargin),
        verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.cardSpacing),
    ) {
        item {
            FarmThumbnail(
                data = data,
                description = stringResource(R.string.thumbnail_description, data.project.name),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(GeoLuxShape.card),
            )
        }
        item {
            Column {
                Text(data.project.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = data.project.location.ifBlank { notSpecified },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            MetricRow(
                stringResource(R.string.label_total_area) to area,
                stringResource(R.string.label_work_area_perimeter) to perimeter,
            )
        }
        item {
            GeoLuxCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.tab_potreros),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            pluralStringResource(R.plurals.zone_count, data.zones.size, data.zones.size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    OutlinedButton(onClick = onShowZones, shape = GeoLuxShape.button) {
                        Text(stringResource(R.string.action_show_zones))
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.section_facilities),
                trailing = pluralStringResource(R.plurals.type_count, typeCounts.size, typeCounts.size),
                modifier = Modifier.padding(top = GeoLuxDimens.cardSpacing),
            )
        }
        item {
            GeoLuxCard {
                if (typeCounts.isEmpty()) {
                    EmptyMessage(stringResource(R.string.facilities_empty))
                }
                typeCounts.forEach { type ->
                    ListRow(
                        title = type.label,
                        leadingColor = elementTypeColor(type.elementType),
                        trailing = { Text(type.count.toString(), style = MaterialTheme.typography.titleMedium) },
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.label_missions),
                trailing = pluralStringResource(R.plurals.mission_count, data.missions.size, data.missions.size),
                modifier = Modifier.padding(top = GeoLuxDimens.cardSpacing),
            )
        }
        item {
            GeoLuxCard {
                if (data.missions.isEmpty()) {
                    EmptyMessage(stringResource(R.string.missions_empty))
                }
                data.missions.forEach { mission ->
                    ListRow(
                        title = mission.name,
                        subtitle = formatDate(mission.plannedAt),
                        trailing = { StatusChip(mission.planStatusLabel, planStatusFamily(mission.planStatus)) },
                    )
                }
            }
        }

        // Los informes son opcionales: un paquete sin ellos no muestra la sección.
        if (reports.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.section_reports),
                    trailing = pluralStringResource(R.plurals.report_count, reports.size, reports.size),
                    modifier = Modifier.padding(top = GeoLuxDimens.cardSpacing),
                )
            }
            item {
                GeoLuxCard {
                    reports.forEach { report ->
                        ListRow(
                            title = report.name,
                            subtitle = formatDate(report.createdAt),
                            trailing = if (report.isSynthetic) {
                                { StatusChip(stringResource(R.string.chip_synthetic), ChipFamily.Amber) }
                            } else {
                                null
                            },
                            showChevron = true,
                            modifier = Modifier.clickable { onOpenReport(report.id) },
                        )
                    }
                }
            }
        }
        item {
            PrimaryButton(
                text = stringResource(R.string.action_explore_map),
                onClick = onExploreMap,
                modifier = Modifier.padding(top = GeoLuxDimens.cardSpacing),
            )
        }
        if (onExport != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.spacingUnit)) {
                    OutlinedButton(
                        onClick = onExport,
                        shape = GeoLuxShape.button,
                        modifier = Modifier.fillMaxWidth().testTag("exportar_glx"),
                    ) {
                        Text(stringResource(R.string.action_export_glx))
                    }
                    Text(
                        stringResource(R.string.export_glx_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(name = "03 Claro", showBackground = true, heightDp = 1100)
@Preview(name = "03 Oscuro", showBackground = true, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MiFincaPreview() {
    GeoLuxTheme { MiFincaScreen(data = FakeProjectRepository.demo, onShowZones = {}, onExploreMap = {}, onOpenReport = {}) }
}
