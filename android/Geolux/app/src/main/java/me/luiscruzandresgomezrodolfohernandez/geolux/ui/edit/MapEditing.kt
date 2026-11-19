package me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.PrimaryButton
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.FeatureKind
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.SketchShape
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens

/** Instrucción de la barra de dibujo, en palabras de la finca. */
fun EditSession.instruction(): String = when {
    featureId != null && target.shape == SketchShape.Point -> "Toca el mapa o arrastra el punto para moverlo."
    featureId != null -> "Arrastra un vértice para moverlo, o el punto medio de un borde para agregar uno. Toca un vértice para quitarlo."
    target == EditTarget.Zone -> "Toca el mapa para marcar los vértices del potrero (mínimo 3). Arrastra un vértice para corregirlo."
    target == EditTarget.WorkArea -> "Toca el mapa para marcar el contorno del área de trabajo (mínimo 3 vértices)."
    target == EditTarget.Line -> "Toca el mapa para trazar la línea (mínimo 2 puntos): cerca, lindero, vía…"
    else -> "Toca el mapa donde está el elemento."
}

/**
 * Barra de dibujo, abajo y sobre el mapa: la instrucción, Deshacer, Quitar
 * vértice (con uno elegido), Cancelar y Terminar/Guardar (cuando ya hay
 * vértices suficientes).
 */
@Composable
fun EditToolbar(
    session: EditSession,
    onUndo: () -> Unit,
    onRemoveVertex: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("barra_edicion"),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(GeoLuxDimens.screenMargin), verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.spacingUnit)) {
            Text(session.instruction(), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onUndo, enabled = session.canUndo && !busy, modifier = Modifier.testTag("deshacer")) { Text("Deshacer") }
                if (session.canRemoveSelected) {
                    OutlinedButton(onClick = onRemoveVertex, enabled = !busy, modifier = Modifier.testTag("quitar_vertice")) { Text("Quitar vértice") }
                }
                TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.testTag("cancelar_edicion")) { Text("Cancelar") }
            }
            PrimaryButton(
                text = if (session.featureId != null || session.target == EditTarget.WorkArea) "Guardar" else "Siguiente",
                onClick = { if (session.canFinish && !busy) onDone() },
                modifier = Modifier.testTag("terminar_edicion"),
            )
        }
    }
}

/** Menú «Agregar al mapa»: lo mismo que crea el módulo de mapas de Desktop. */
@Composable
fun AddMenu(hasWorkArea: Boolean, onPick: (EditTarget) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = GeoLuxDimens.screenMargin).padding(bottom = GeoLuxDimens.screenMargin),
        verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.spacingUnit),
    ) {
        Text("Agregar al mapa", style = MaterialTheme.typography.headlineSmall)
        AddRow("Potrero", "Polígono con nombre, estado, color y capacidad", "agregar_Zone") { onPick(EditTarget.Zone) }
        AddRow("Línea", "Cerca, lindero, cerca eléctrica, vía, canal, río, tubería, sendero…", "agregar_Line") { onPick(EditTarget.Line) }
        AddRow("Elemento", "Punto: tanque, bebedero, casa, corral, pozo, puerta…", "agregar_Element") { onPick(EditTarget.Element) }
        AddRow(
            "Área de trabajo",
            if (hasWorkArea) "La finca ya tiene una: tócala en el mapa para cambiar su forma" else "El contorno de la finca (una por finca)",
            "agregar_WorkArea",
            enabled = !hasWorkArea,
        ) { onPick(EditTarget.WorkArea) }
    }
}

@Composable
private fun AddRow(title: String, subtitle: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = GeoLuxDimens.listRowMinHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(tag),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Botones de la ficha de un objeto editable. */
@Composable
fun FichaEditActions(kind: FeatureKind, onEditData: () -> Unit, onEditShape: () -> Unit, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.spacingUnit)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (kind != FeatureKind.WorkArea) {
                OutlinedButton(onClick = onEditData, modifier = Modifier.weight(1f).testTag("editar_datos")) { Text("Editar datos") }
            }
            OutlinedButton(onClick = onEditShape, modifier = Modifier.weight(1f).testTag("editar_forma")) {
                Text(if (kind == FeatureKind.Element) "Mover" else "Cambiar forma")
            }
        }
        TextButton(onClick = onDelete, modifier = Modifier.testTag("eliminar")) {
            Text("Eliminar", color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Confirmación antes de eliminar: se pierde información. */
@Composable
fun DeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Eliminar ${if (name.startsWith("el ")) name else "«$name»"}?") },
        text = { Text("Se borra de esta finca en el teléfono. Puedes deshacerlo justo después con «Deshacer».") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirmar_eliminar")) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
