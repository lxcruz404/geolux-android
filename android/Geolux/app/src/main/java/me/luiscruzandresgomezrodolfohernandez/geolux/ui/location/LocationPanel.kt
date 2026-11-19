package me.luiscruzandresgomezrodolfohernandez.geolux.ui.location

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.data.LocationState
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.ChipFamily
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.colors
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxShape
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme

/**
 * Estado del GPS sobre el mapa (DESIGN.md §9, 04f). Los tres estados del
 * diseño más el de ubicación apagada:
 * - Sin permiso: aviso ámbar «Activa la ubicación…» + «Activar» (o «Abrir
 *   ajustes» si el usuario negó el permiso para siempre).
 * - Ubicación apagada: aviso ámbar + «Abrir ajustes».
 * - Buscando: «Buscando señal GPS…».
 * - Con posición: «Precisión ±N m», coordenadas y en qué potrero estás.
 * Con [state] null (aún no hay respuesta) no muestra nada.
 */
@Composable
fun LocationPanel(
    state: LocationState?,
    data: ProjectData,
    permissionBlocked: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        null -> Unit
        LocationState.NoPermission -> LocationNotice(
            title = stringResource(R.string.gps_no_permission_title),
            body = stringResource(
                if (permissionBlocked) R.string.gps_no_permission_blocked_body else R.string.gps_no_permission_body,
            ),
            action = stringResource(if (permissionBlocked) R.string.action_open_settings else R.string.action_activate),
            onAction = if (permissionBlocked) onOpenAppSettings else onRequestPermission,
            modifier = modifier.testTag("gps_no_permission"),
        )
        LocationState.Disabled -> LocationNotice(
            title = stringResource(R.string.gps_disabled_title),
            body = stringResource(R.string.gps_disabled_body),
            action = stringResource(R.string.action_open_settings),
            onAction = onOpenLocationSettings,
            modifier = modifier.testTag("gps_disabled"),
        )
        LocationState.Searching -> StatusPill(modifier.testTag("gps_searching")) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(GeoLuxDimens.spacingUnit * 2))
            Text(stringResource(R.string.gps_searching), style = MaterialTheme.typography.bodyLarge)
        }
        is LocationState.Fix -> FixCard(state, data, modifier.testTag("gps_fix"))
    }
}

@Composable
private fun LocationNotice(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChipFamily.Amber.colors()
    Surface(
        color = colors.container,
        contentColor = colors.content,
        shape = GeoLuxShape.card,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(GeoLuxDimens.cardPadding), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null)
            Spacer(Modifier.width(GeoLuxDimens.cardSpacing))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = GeoLuxDimens.minTouchTarget)) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = colors.content)
            }
        }
    }
}

@Composable
private fun StatusPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = GeoLuxDimens.cardPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

@Composable
private fun FixCard(fix: LocationState.Fix, data: ProjectData, modifier: Modifier = Modifier) {
    val place = when (val p = placeOf(fix, data)) {
        is PlaceInFinca.InZone -> stringResource(R.string.gps_in_zone, p.zoneName)
        PlaceInFinca.InWorkArea -> stringResource(R.string.gps_in_work_area)
        PlaceInFinca.Outside -> stringResource(R.string.gps_outside)
    }
    Surface(
        shape = GeoLuxShape.card,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(GeoLuxDimens.cardPadding), verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.spacingUnit)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(10.dp)) {}
                Spacer(Modifier.width(GeoLuxDimens.spacingUnit * 2))
                Text(
                    stringResource(R.string.gps_accuracy, formatAccuracy(fix.accuracyM)),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(place, style = MaterialTheme.typography.bodyLarge)
            Text(
                formatCoordinates(fix.lat, fix.lon),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Previews con datos de prueba (FakeLocationSource / finca-demo) ---

@Composable
private fun PanelPreview(state: LocationState, blocked: Boolean = false) {
    GeoLuxTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            LocationPanel(state, FakeProjectRepository.demo, blocked, {}, {}, {}, Modifier.padding(16.dp))
        }
    }
}

@Preview(name = "04f Sin permiso")
@Composable
private fun PreviewNoPermission() = PanelPreview(LocationState.NoPermission)

@Preview(name = "04f Apagada", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewDisabled() = PanelPreview(LocationState.Disabled)

@Preview(name = "04f Buscando")
@Composable
private fun PreviewSearching() = PanelPreview(LocationState.Searching)

@Preview(name = "04f Con posición")
@Composable
private fun PreviewFix() = PanelPreview(LocationState.Fix(6.301, -70.1985, 5f))
