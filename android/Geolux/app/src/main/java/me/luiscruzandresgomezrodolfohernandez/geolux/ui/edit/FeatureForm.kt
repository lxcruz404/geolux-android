package me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geo.CatalogEntry
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geo.Catalogs
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.parseHexColor
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.PrimaryButton
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.ZONE_FALLBACK_COLOR
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens

/** Paleta de potreros de Desktop (`zone_form_dialog._COLOR_CHOICES`); "" = color del tema. */
val ZONE_COLORS = listOf(
    "" to "Color del tema",
    "#2A9D8F" to "Verde",
    "#1C7ED6" to "Azul",
    "#F08C00" to "Naranja",
    "#5F3DC4" to "Morado",
    "#E5484D" to "Rojo",
    "#8D6E63" to "Marrón",
)

/** Los datos que se escriben en el formulario (texto tal cual; las reglas las aplica FarmEditor). */
data class FeatureFields(
    val name: String = "",
    val description: String = "",
    /** Categoría de línea o tipo de elemento. */
    val type: String = "",
    val status: String = Catalogs.DEFAULT_ZONE_STATUS,
    val color: String = "",
    val capacity: String = "",
) {
    companion object {
        fun forNew(target: EditTarget) = when (target) {
            EditTarget.Line -> FeatureFields(type = Catalogs.DEFAULT_LINE_CATEGORY)
            EditTarget.Element -> FeatureFields(type = Catalogs.DEFAULT_ELEMENT_TYPE)
            else -> FeatureFields()
        }

        /** Los datos actuales de un objeto existente, para «Editar datos». */
        fun of(data: ProjectData, target: EditTarget, id: String): FeatureFields? = when (target) {
            EditTarget.Zone -> data.zones.firstOrNull { it.id == id }?.let {
                FeatureFields(it.name, it.description, status = it.status, color = it.color, capacity = it.livestockCapacity?.let(::plain) ?: "")
            }
            EditTarget.Line -> data.lines.firstOrNull { it.id == id }?.let { FeatureFields(it.name, it.description, type = it.category) }
            EditTarget.Element -> data.elements.firstOrNull { it.id == id }?.let { FeatureFields(it.name, it.description, type = it.elementType) }
            EditTarget.WorkArea -> null
        }

        private fun plain(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }

    /** Capacidad escrita → número; vacío = sin definir. Coma o punto decimal. */
    fun capacityValue(): Double? = capacity.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val capacityIsValid: Boolean get() = capacity.isBlank() || capacityValue() != null
}

/**
 * Formulario de propiedades (hoja inferior) de un potrero, una línea o un
 * elemento: los mismos campos que los diálogos de Desktop. [error] es el
 * mensaje de la última vez que se intentó guardar (reglas de Desktop).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeatureForm(
    target: EditTarget,
    initial: FeatureFields,
    isNew: Boolean,
    error: String?,
    onSave: (FeatureFields) -> Unit,
    onCancel: () -> Unit,
) {
    var fields by remember(target, isNew) { mutableStateOf(initial) }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GeoLuxDimens.screenMargin)
            .padding(bottom = GeoLuxDimens.screenMargin),
        verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.cardSpacing),
    ) {
        Text(
            when (target) {
                EditTarget.Zone -> if (isNew) "Nuevo potrero" else "Datos del potrero"
                EditTarget.Line -> if (isNew) "Nueva línea" else "Datos de la línea"
                EditTarget.Element -> if (isNew) "Nuevo elemento" else "Datos del elemento"
                EditTarget.WorkArea -> "Área de trabajo"
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        when (target) {
            EditTarget.Line -> CatalogField("Tipo de línea", Catalogs.lineCategories, fields.type, "campo_tipo") { fields = fields.copy(type = it) }
            EditTarget.Element -> CatalogField("Tipo de elemento", Catalogs.elementTypes, fields.type, "campo_tipo") { fields = fields.copy(type = it) }
            else -> Unit
        }
        OutlinedTextField(
            value = fields.name, onValueChange = { fields = fields.copy(name = it) },
            label = { Text("Nombre") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("campo_nombre"),
        )
        OutlinedTextField(
            value = fields.description, onValueChange = { fields = fields.copy(description = it) },
            label = { Text("Descripción (opcional)") }, minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("campo_descripcion"),
        )
        if (target == EditTarget.Zone) {
            Text("Estado", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Catalogs.zoneStatuses.forEach { s ->
                    FilterChip(
                        selected = fields.status == s.key, onClick = { fields = fields.copy(status = s.key) },
                        label = { Text(s.label) }, modifier = Modifier.testTag("estado_${s.key}"),
                    )
                }
            }
            Text("Color", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZONE_COLORS.forEach { (hex, label) ->
                    val selected = fields.color == hex
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(parseHexColor(hex.ifEmpty { ZONE_FALLBACK_COLOR }, Color.Gray), CircleShape)
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.White, CircleShape)
                            .clickable { fields = fields.copy(color = hex) }
                            .semantics { contentDescription = label; this.selected = selected }
                            .testTag("color_${hex.ifEmpty { "tema" }}"),
                    )
                }
            }
            OutlinedTextField(
                value = fields.capacity, onValueChange = { fields = fields.copy(capacity = it) },
                label = { Text("Capacidad de animales (opcional)") }, singleLine = true,
                isError = !fields.capacityIsValid,
                supportingText = { if (!fields.capacityIsValid) Text("Escribe un número, por ejemplo 25.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("campo_capacidad"),
            )
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("error_formulario"))
        }
        PrimaryButton(
            text = "Guardar",
            onClick = { if (fields.capacityIsValid) onSave(fields) },
            modifier = Modifier.testTag("guardar_formulario"),
        )
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogField(label: String, entries: List<CatalogEntry>, selected: String, tag: String, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = entries.firstOrNull { it.key == selected }?.label ?: selected,
            onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag(tag),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label) },
                    onClick = { onSelect(entry.key); expanded = false },
                    modifier = Modifier.testTag("opcion_${entry.key}"),
                )
            }
        }
    }
}

/** Formulario abierto: para un objeto nuevo ([featureId] null, con sus [points] dibujados) o existente. */
data class FormRequest(
    val target: EditTarget,
    val featureId: String?,
    val points: List<me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position>?,
    val initial: FeatureFields,
)
