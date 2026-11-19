package me.luiscruzandresgomezrodolfohernandez.geolux.ui.projects

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.heightIn
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.summary
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.EmptyMessage
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.FarmThumbnail
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.GeoLuxCard
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.PrimaryButton
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatDate
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatMeasureArea
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.RecyclerList

/** Pantallas 01 «Mis fincas» y 01b «Mis fincas vacío» (DESIGN.md §9). */
@Composable
fun ProjectsScreen(
    state: ProjectsUiState,
    onImport: () -> Unit,
    onOpen: (projectId: String) -> Unit,
    modifier: Modifier = Modifier,
    /** «Explorar el mapa»: el mapa base sin abrir ninguna finca. */
    onExploreMap: (() -> Unit)? = null,
    /** «Eliminar proyecto» confirmado (nombre escrito): id y nombre de la finca. */
    onDelete: ((projectId: String, name: String) -> Unit)? = null,
) {
    when (state) {
        ProjectsUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        ProjectsUiState.Empty -> CenteredMessage(
            title = stringResource(R.string.projects_empty_title),
            body = stringResource(R.string.projects_empty_body),
            action = stringResource(R.string.action_import),
            onAction = onImport,
            modifier = modifier,
            onExploreMap = onExploreMap,
        )
        ProjectsUiState.Error -> CenteredMessage(
            title = stringResource(R.string.projects_error_title),
            body = stringResource(R.string.projects_error_body),
            action = stringResource(R.string.action_import),
            onAction = onImport,
            modifier = modifier,
        )
        is ProjectsUiState.Content -> RecyclerList(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(GeoLuxDimens.screenMargin),
            verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.cardSpacing),
        ) {
            item { PrimaryButton(text = stringResource(R.string.action_import), onClick = onImport) }
            if (onExploreMap != null) item { ExploreMapButton(onExploreMap) }
            item {
                Text(
                    text = stringResource(R.string.projects_saved, state.projects.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = GeoLuxDimens.cardSpacing),
                )
            }
            items(state.projects, key = { it.summary.id }) { card ->
                ProjectCard(card = card, onOpen = { onOpen(card.summary.id) },
                    onDelete = onDelete?.let { delete -> { delete(card.summary.id, card.summary.name) } })
            }
        }
    }
}

/** Tarjeta de finca (§8): miniatura, nombre, ubicación, área, potreros, misiones, fecha y «Abrir finca». */
@Composable
private fun ProjectCard(card: ProjectCardUi, onOpen: () -> Unit, onDelete: (() -> Unit)? = null) {
    val summary = card.summary
    var menuOpen by remember { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    if (confirming && onDelete != null) {
        DeleteProjectDialog(name = summary.name, onConfirm = { confirming = false; onDelete() }, onDismiss = { confirming = false })
    }
    GeoLuxCard(contentPadding = false) {
        if (card.data != null) {
            FarmThumbnail(
                data = card.data,
                description = stringResource(R.string.thumbnail_description, summary.name),
                // La tarjeta ya recorta sus esquinas superiores.
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
        }
        Column(Modifier.padding(GeoLuxDimens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(summary.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (onDelete != null) {
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("opciones_finca_${summary.id}")) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.project_options, summary.name))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete_project), color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirming = true },
                            )
                        }
                    }
                }
            }
            Text(
                text = summary.location.ifBlank { stringResource(R.string.not_specified) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(GeoLuxDimens.cardSpacing))
            Row(horizontalArrangement = Arrangement.spacedBy(GeoLuxDimens.cardSpacing)) {
                SmallFact(
                    label = stringResource(R.string.label_area),
                    value = summary.area?.let(::formatMeasureArea) ?: stringResource(R.string.not_specified),
                    modifier = Modifier.weight(1f),
                )
                SmallFact(
                    label = stringResource(R.string.tab_potreros),
                    value = pluralStringResource(R.plurals.zone_count, summary.zoneCount, summary.zoneCount),
                    modifier = Modifier.weight(1f),
                )
                SmallFact(
                    label = stringResource(R.string.label_missions),
                    value = pluralStringResource(R.plurals.mission_count, summary.missionCount, summary.missionCount),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(GeoLuxDimens.cardSpacing))
            Text(
                text = stringResource(R.string.created_on, formatDate(summary.packageCreatedAt)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(GeoLuxDimens.cardSpacing))
            PrimaryButton(text = stringResource(R.string.action_open_finca), onClick = onOpen)
        }
    }
}

/**
 * Confirmación de «Eliminar proyecto»: dice qué se borra, que no se puede deshacer y que
 * conviene exportar antes un .glx; el botón definitivo solo se habilita al escribir el nombre
 * exacto de la finca (un toque accidental nunca elimina nada).
 */
@Composable
internal fun DeleteProjectDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val matches = typed == name
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_project_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.spacingUnit)) {
                Text(stringResource(R.string.delete_project_body, name), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.delete_project_warning), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.delete_project_type_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("nombre_confirmacion"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = matches, modifier = Modifier.testTag("confirmar_eliminar")) {
                Text(stringResource(R.string.delete_project_confirm),
                    color = if (matches) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SmallFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Mensaje centrado con una acción: vacío (01b) y error. */
@Composable
private fun CenteredMessage(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    onExploreMap: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(GeoLuxDimens.screenMargin),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        EmptyMessage(body)
        Spacer(Modifier.height(GeoLuxDimens.cardSpacing))
        PrimaryButton(text = action, onClick = onAction)
        if (onExploreMap != null) {
            Spacer(Modifier.height(GeoLuxDimens.spacingUnit))
            ExploreMapButton(onExploreMap)
        }
    }
}

@Composable
private fun ExploreMapButton(onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = GeoLuxDimens.listRowMinHeight).testTag("explorar_mapa"),
    ) {
        Text(stringResource(R.string.action_explore_base_map))
    }
}

@Preview(name = "01 Claro", showBackground = true, heightDp = 720)
@Preview(name = "01 Oscuro", showBackground = true, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProjectsScreenPreview() {
    val demo = FakeProjectRepository.demo
    GeoLuxTheme {
        ProjectsScreen(
            state = ProjectsUiState.Content(listOf(ProjectCardUi(demo.summary(), demo))),
            onImport = {},
            onOpen = {},
        )
    }
}

@Preview(name = "01b Vacío", showBackground = true, heightDp = 720)
@Composable
private fun ProjectsEmptyPreview() {
    GeoLuxTheme { ProjectsScreen(state = ProjectsUiState.Empty, onImport = {}, onOpen = {}) }
}
