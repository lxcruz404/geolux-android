package me.luiscruzandresgomezrodolfohernandez.geolux.ui.importing

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Issue
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportDecision
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportPreview
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportProgress
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ImportStep
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.ChipFamily
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.colors
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.GeoLuxCard
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.ListRow
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.MetricRow
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.PrimaryButton
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.StatusChip
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatDate
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatFileSize
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxShape
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme

/** Acciones del flujo de importación que la hoja le pide a quien la muestra. */
data class ImportActions(
    val onImport: () -> Unit = {},
    val onRequestReplace: () -> Unit = {},
    val onConfirmReplace: () -> Unit = {},
    val onDismissReplaceConfirmation: () -> Unit = {},
    val onOpenFinca: (projectId: String) -> Unit = {},
    val onPickAnother: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

/**
 * Hoja inferior de importación (DESIGN.md §8: «Hoja inferior… importación»).
 * No se muestra en [ImportUiState.Idle]. Mientras se importa no se puede cerrar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(state: ImportUiState, actions: ImportActions, progress: ImportProgress? = null) {
    if (state == ImportUiState.Idle) return
    val currentState by rememberUpdatedState(state)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || currentState !is ImportUiState.Importing },
    )
    ModalBottomSheet(
        onDismissRequest = actions.onCancel,
        sheetState = sheetState,
        shape = GeoLuxShape.bottomSheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ImportContent(state, actions, Modifier.navigationBarsPadding(), progress)
    }
    if (state is ImportUiState.AskReplace && state.confirming) {
        AlertDialog(
            onDismissRequest = actions.onDismissReplaceConfirmation,
            shape = GeoLuxShape.dialog,
            title = { Text(stringResource(R.string.import_replace_confirm_title)) },
            text = { Text(stringResource(R.string.import_replace_confirm_body, state.preview.projectName)) },
            confirmButton = {
                TextButton(onClick = actions.onConfirmReplace, modifier = Modifier.testTag("confirm_replace")) {
                    Text(stringResource(R.string.action_replace))
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissReplaceConfirmation) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Contenido de la hoja según el estado; sin hoja alrededor para poder probarlo y previsualizarlo. */
@Composable
fun ImportContent(
    state: ImportUiState,
    actions: ImportActions,
    modifier: Modifier = Modifier,
    /** Avance en bytes del paso en curso, si el repositorio lo informa. */
    progress: ImportProgress? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GeoLuxDimens.screenMargin)
            .padding(bottom = GeoLuxDimens.screenMargin),
        verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.cardSpacing),
    ) {
        when (state) {
            ImportUiState.Idle -> Unit
            ImportUiState.Checking -> {
                SheetTitle(stringResource(R.string.import_checking_title))
                // Sin avance todavía: «abrir» y «comprobar» a la vez, como antes.
                if (progress?.step == ImportStep.Verify) ProgressSteps(done = 1, active = 1, progress)
                else ProgressSteps(done = 0, active = if (progress == null) 2 else 1, progress)
            }
            is ImportUiState.Preview -> PreviewContent(state.preview, actions)
            is ImportUiState.AlreadyImported -> AlreadyImportedContent(state, actions)
            is ImportUiState.AskReplace -> ReplaceContent(state.preview, actions)
            is ImportUiState.Importing -> {
                SheetTitle(stringResource(R.string.import_importing_title))
                FileCard(state.preview.fileName, state.preview.sizeBytes)
                ProgressSteps(done = 2, active = 1, progress?.takeIf { it.step == ImportStep.Install })
            }
            is ImportUiState.Done -> {
                SheetTitle(stringResource(R.string.import_done_sheet_title))
                ProgressSteps(done = 3, active = 0)
                Notice(
                    family = ChipFamily.Green,
                    icon = R.drawable.ic_check_circle,
                    title = stringResource(R.string.import_done_title),
                    body = stringResource(R.string.import_done_body, state.projectName),
                )
                PrimaryButton(stringResource(R.string.action_open_finca), { actions.onOpenFinca(state.projectId) })
            }
            is ImportUiState.Failed -> FailedContent(state, actions)
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = GeoLuxDimens.spacingUnit))
}

/** 02a: archivo, finca, fecha, conteos por capa, productos con su origen; Importar / Cancelar. */
@Composable
private fun PreviewContent(preview: ImportPreview.Ready, actions: ImportActions) {
    SheetTitle(stringResource(R.string.import_preview_title))
    FileCard(preview.fileName, preview.sizeBytes)
    FincaCard(preview)
    Text(stringResource(R.string.import_layers_section), style = MaterialTheme.typography.titleMedium)
    MetricRow(
        stringResource(R.string.tab_potreros) to preview.zoneCount.toString(),
        stringResource(R.string.label_elements) to preview.elementCount.toString(),
    )
    MetricRow(
        stringResource(R.string.label_lines) to preview.lineCount.toString(),
        stringResource(R.string.label_missions) to preview.missionCount.toString(),
    )
    ProductsSection(preview)
    IgnoredContentNotice(preview)
    PrimaryButton(stringResource(R.string.action_import_now), actions.onImport, Modifier.testTag("import_now"))
    SecondaryButton(stringResource(R.string.action_cancel), actions.onCancel)
}

/** 02c: el mismo paquete ya está en el teléfono (SPEC §8.1). */
@Composable
private fun AlreadyImportedContent(state: ImportUiState.AlreadyImported, actions: ImportActions) {
    SheetTitle(stringResource(R.string.import_preview_title))
    FileCard(state.preview.fileName, state.preview.sizeBytes)
    Notice(
        family = ChipFamily.Green,
        icon = R.drawable.ic_check_circle,
        title = stringResource(R.string.import_already_title),
        body = stringResource(
            R.string.import_already_body,
            state.preview.projectName,
            formatDate(state.preview.packageCreatedAt),
        ),
    )
    PrimaryButton(stringResource(R.string.action_open_finca), { actions.onOpenFinca(state.preview.projectId) })
    SecondaryButton(stringResource(R.string.action_close), actions.onCancel)
}

/** 02d: otro paquete de la misma finca; fechas de los dos y «Más antiguo» (SPEC §8.2–3). */
@Composable
private fun ReplaceContent(preview: ImportPreview.Ready, actions: ImportActions) {
    SheetTitle(stringResource(R.string.import_replace_title, preview.projectName))
    Text(
        stringResource(R.string.import_replace_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(GeoLuxDimens.cardSpacing)) {
        DateCard(
            label = stringResource(R.string.import_on_phone),
            date = preview.existingPackageCreatedAt?.let(::formatDate) ?: stringResource(R.string.not_specified),
            modifier = Modifier.weight(1f),
        )
        DateCard(
            label = stringResource(R.string.import_new_file),
            date = formatDate(preview.packageCreatedAt),
            chip = if (preview.isOlderThanExisting) stringResource(R.string.chip_older) else null,
            modifier = Modifier.weight(1f),
        )
    }
    if (preview.isOlderThanExisting) {
        Notice(
            family = ChipFamily.Amber,
            icon = R.drawable.ic_warning,
            title = stringResource(R.string.import_older_title),
            body = stringResource(R.string.import_older_body),
        )
    }
    Text(
        stringResource(R.string.import_replace_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PrimaryButton(stringResource(R.string.action_replace), actions.onRequestReplace, Modifier.testTag("replace"))
    SecondaryButton(stringResource(R.string.action_cancel), actions.onCancel)
}

/** 02e: mensaje claro según el caso, sin códigos (DESIGN.md §10), y «Elegir otro archivo». */
@Composable
private fun FailedContent(state: ImportUiState.Failed, actions: ImportActions) {
    SheetTitle(stringResource(R.string.import_failed_sheet_title))
    state.fileName?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    val kind = importErrorKind(state.error.code)
    val (title, body) = when (kind) {
        ImportErrorKind.NotAPackage -> stringResource(R.string.import_error_not_package_title) to
            stringResource(R.string.import_error_not_package_body)
        ImportErrorKind.Damaged -> stringResource(R.string.import_error_damaged_title) to
            stringResource(R.string.import_error_damaged_body)
        // Estos mensajes ya están en lenguaje claro (SPEC §7 y GeoluxProjectRepository).
        ImportErrorKind.TooNew -> stringResource(R.string.import_error_too_new_title) to state.error.message
        ImportErrorKind.ReadFailed -> stringResource(R.string.import_error_read_title) to state.error.message
        ImportErrorKind.InstallFailed -> stringResource(R.string.import_error_install_title) to state.error.message
        ImportErrorKind.NoSpace -> stringResource(R.string.import_error_no_space_title) to state.error.message
        ImportErrorKind.Other -> stringResource(R.string.import_error_other_title) to
            stringResource(R.string.import_error_other_body)
    }
    Notice(
        family = if (kind == ImportErrorKind.TooNew) ChipFamily.Amber else ChipFamily.Red,
        icon = if (kind == ImportErrorKind.TooNew) R.drawable.ic_warning else R.drawable.ic_error,
        title = title,
        body = body,
    )
    PrimaryButton(stringResource(R.string.action_pick_another), actions.onPickAnother)
    SecondaryButton(stringResource(R.string.action_cancel), actions.onCancel)
}

@Composable
private fun FileCard(fileName: String, sizeBytes: Long) {
    GeoLuxCard(Modifier.fillMaxWidth()) {
        Text(fileName, style = MaterialTheme.typography.titleMedium)
        Text(formatFileSize(sizeBytes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FincaCard(preview: ImportPreview.Ready) {
    GeoLuxCard(Modifier.fillMaxWidth()) {
        Text(preview.projectName, style = MaterialTheme.typography.headlineSmall)
        Text(
            preview.location.ifBlank { stringResource(R.string.not_specified) },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(GeoLuxDimens.spacingUnit * 2))
        Text(
            stringResource(R.string.import_created_on, formatDate(preview.packageCreatedAt)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Productos con su origen: los sintéticos llevan siempre la etiqueta ámbar (DESIGN.md §2.5). */
@Composable
private fun ProductsSection(preview: ImportPreview.Ready) {
    Text(stringResource(R.string.import_products_section), style = MaterialTheme.typography.titleMedium)
    GeoLuxCard(Modifier.fillMaxWidth()) {
        if (preview.products.isEmpty()) {
            Text(
                stringResource(R.string.import_no_products),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        preview.products.forEach { product ->
            ListRow(
                overline = stringResource(
                    when (product.kind) {
                        "orthomosaic", "orthomosaic_crop" -> R.string.product_orthomosaic
                        "model3d" -> R.string.product_model3d
                        "report" -> R.string.product_report
                        else -> R.string.product_other
                    },
                ),
                title = product.name,
                trailing = if (product.isSynthetic) {
                    { StatusChip(stringResource(R.string.chip_synthetic), ChipFamily.Amber) }
                } else {
                    null
                },
            )
        }
    }
}

/** Advertencias de SPEC §7 que no son el producto sintético: se importa igual, y se informa. */
@Composable
private fun IgnoredContentNotice(preview: ImportPreview.Ready) {
    val ignored = preview.warnings.count { isIgnoredContentWarning(it.code) }
    if (ignored == 0) return
    Notice(
        family = ChipFamily.Amber,
        icon = R.drawable.ic_warning,
        title = stringResource(R.string.import_ignored_title),
        body = pluralStringResource(R.plurals.import_ignored_body, ignored, ignored),
    )
}

@Composable
private fun DateCard(label: String, date: String, modifier: Modifier = Modifier, chip: String? = null) {
    GeoLuxCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(date, style = MaterialTheme.typography.titleMedium)
        if (chip != null) {
            Spacer(Modifier.height(GeoLuxDimens.spacingUnit * 2))
            StatusChip(chip, ChipFamily.Amber)
        }
    }
}

/** Aviso (§8): tarjeta verde, ámbar o roja con ícono, título y texto. */
@Composable
private fun Notice(family: ChipFamily, @DrawableRes icon: Int, title: String, body: String) {
    val colors = family.colors()
    Surface(
        color = colors.container,
        contentColor = colors.content,
        shape = GeoLuxShape.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(GeoLuxDimens.cardPadding)) {
            Icon(painterResource(icon), contentDescription = null)
            Spacer(Modifier.width(GeoLuxDimens.cardSpacing))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(GeoLuxDimens.spacingUnit))
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = GeoLuxShape.button,
        border = BorderStroke(GeoLuxDimens.cardBorder, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = GeoLuxDimens.minTouchTarget),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Progreso lineal con pasos nombrados (§8). Los pasos 1 y 2 ocurren dentro de
 * `inspect` y el 3 dentro de `import`; el repositorio no informa avance más
 * fino, así que la barra es indeterminada mientras hay un paso activo.
 */
@Composable
private fun ProgressSteps(done: Int, active: Int, progress: ImportProgress? = null) {
    val labels = listOf(
        R.string.import_step_open,
        R.string.import_step_check,
        R.string.import_step_prepare,
    )
    GeoLuxCard(Modifier.fillMaxWidth()) {
        val fraction = progress?.fraction
        if (active > 0 && fraction != null) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().testTag("import_progress"))
            Spacer(Modifier.height(GeoLuxDimens.spacingUnit))
            Text(
                stringResource(R.string.import_progress_bytes, formatFileSize(progress.doneBytes), formatFileSize(progress.totalBytes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (active > 0) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(GeoLuxDimens.cardSpacing))
        labels.forEachIndexed { index, label ->
            val status = when {
                index < done -> StepStatus.Done
                index < done + active -> StepStatus.Active
                else -> StepStatus.Pending
            }
            StepRow(stringResource(label), status)
        }
    }
}

private enum class StepStatus { Done, Active, Pending }

@Composable
private fun StepRow(label: String, status: StepStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = GeoLuxDimens.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (status) {
                StepStatus.Done -> Icon(
                    painterResource(R.drawable.ic_check_circle),
                    contentDescription = stringResource(R.string.step_done),
                    tint = MaterialTheme.colorScheme.primary,
                )
                StepStatus.Active -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                StepStatus.Pending -> Surface(
                    shape = CircleShape,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(20.dp),
                ) {}
            }
        }
        Spacer(Modifier.width(GeoLuxDimens.cardSpacing))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (status == StepStatus.Pending) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// --- Previews con datos de prueba de finca-demo (FakeProjectRepository) ---

private val demoPreview = FakeProjectRepository.previewOf(FakeProjectRepository.demo, ImportDecision.NEW)

@Composable
private fun PreviewSurface(state: ImportUiState) {
    GeoLuxTheme { Surface { ImportContent(state, ImportActions()) } }
}

@Preview(name = "02a Vista previa", heightDp = 1100)
@Preview(name = "02a Oscuro", heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewNew() = PreviewSurface(ImportUiState.Preview(demoPreview))

@Preview(name = "02b Importando")
@Composable
private fun PreviewImporting() = PreviewSurface(ImportUiState.Importing(demoPreview))

@Preview(name = "02b Importada")
@Composable
private fun PreviewDone() = PreviewSurface(ImportUiState.Done("id", demoPreview.projectName))

@Preview(name = "02c Ya importado")
@Composable
private fun PreviewAlready() = PreviewSurface(ImportUiState.AlreadyImported(demoPreview))

@Preview(name = "02d Reemplazar", heightDp = 800)
@Composable
private fun PreviewReplace() = PreviewSurface(
    ImportUiState.AskReplace(demoPreview.copy(decision = ImportDecision.ASK_REPLACE, existingPackageCreatedAt = "2026-10-12T12:00:00+00:00")),
)

@Preview(name = "02e Error")
@Composable
private fun PreviewError() = PreviewSurface(
    ImportUiState.Failed("finca-demo.geolux", Issue("hash_mismatch", "El archivo layers/zones.geojson está dañado o fue modificado.")),
)
