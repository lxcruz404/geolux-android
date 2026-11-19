package me.luiscruzandresgomezrodolfohernandez.geolux.ui.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Project
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.data.LocationSource
import me.luiscruzandresgomezrodolfohernandez.geolux.data.LocationState
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.ChipFamily
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.elementTypeColor
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.StatusChip
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.BaseMap
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.BaseMapPreference
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.FarmMap
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.FarmMapController
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.LayersSheetContent
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.MapLayerGroup
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.rememberIsOnline
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxShape

/** Una «finca» sin datos, para dibujar solo el mapa base. */
internal fun emptyProjectData() = ProjectData(
    packageId = "",
    packageCreatedAt = "",
    project = Project("", "", "", "", null, null, null, "", ""),
    workArea = null,
    zones = emptyList(),
    elements = emptyList(),
    lines = emptyList(),
    missions = emptyList(),
    missionPaths = emptyList(),
    products = emptyList(),
)

/**
 * Mapa sin ninguna finca abierta (misión, req. 3.4): el mapa base (Satélite Esri
 * o estándar) y la posición GPS, para mirar una zona antes de tener datos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreMapScreen(locationSource: LocationSource, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val empty = remember { emptyProjectData() }
    var baseMap by remember { mutableStateOf(BaseMapPreference.load(context).takeIf { it != BaseMap.None } ?: BaseMap.DEFAULT) }
    var showLayers by rememberSaveable { mutableStateOf(false) }
    val controller = remember { FarmMapController() }
    val locations = remember(locationSource) { locationSource.locations() }
    val location: LocationState? by locations.collectAsStateWithLifecycle(initialValue = null)
    val online = rememberIsOnline()

    Box(modifier.fillMaxSize()) {
        FarmMap(
            data = empty,
            baseMap = baseMap,
            location = location,
            selected = null,
            background = MaterialTheme.colorScheme.surfaceVariant,
            elementColor = { type -> String.format("#%06X", elementTypeColor(type).toArgb() and 0xFFFFFF) },
            onSelect = {},
            controller = controller,
            modifier = Modifier.fillMaxSize().testTag("mapa_explorar"),
        )
        if (!online) {
            StatusChip(
                text = stringResource(R.string.map_offline_basemap),
                family = ChipFamily.Amber,
                modifier = Modifier.align(Alignment.TopCenter).padding(GeoLuxDimens.screenMargin),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = GeoLuxDimens.screenMargin, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.cardSpacing),
        ) {
            ExploreFab(R.drawable.ic_layers, stringResource(R.string.fab_layers), "fab_capas") { showLayers = true }
            ExploreFab(R.drawable.ic_my_location, stringResource(R.string.fab_my_location), "fab_mi_ubicacion") {
                (location as? LocationState.Fix)?.let { controller.center(Position(lon = it.lon, lat = it.lat), zoom = 17.0) }
            }
        }
    }
    if (showLayers) {
        ModalBottomSheet(
            onDismissRequest = { showLayers = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = GeoLuxShape.bottomSheet,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            LayersSheetContent(
                data = empty,
                orthos = emptyList(),
                visible = MapLayerGroup.entries.toSet(),
                onToggle = { _, _ -> },
                baseMap = baseMap,
                onBaseMap = { baseMap = it; BaseMapPreference.save(context, it) },
                showFarmLayers = false,
            )
        }
    }
}

@Composable
private fun ExploreFab(icon: Int, description: String, tag: String, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(GeoLuxDimens.mapFloatingButton).testTag(tag),
    ) {
        Icon(painterResource(icon), contentDescription = description)
    }
}
