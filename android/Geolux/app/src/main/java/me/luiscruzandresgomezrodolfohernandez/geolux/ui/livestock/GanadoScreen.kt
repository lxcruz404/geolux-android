package me.luiscruzandresgomezrodolfohernandez.geolux.ui.livestock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.launch
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.AnimalColumn
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.AnimalRow
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.HerdFilter
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.HerdRecord
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.HerdView
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.Livestock
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.LivestockOps
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.LotRow
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ProductionClass
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.RecordKind
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.ChipFamily
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.EmptyMessage
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.GeoLuxCard
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.ListRow
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.PrimaryButton
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.SectionHeader
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.StatusChip
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.management.ManagementUiState
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.management.ManagementViewModel
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.management.PickerRow
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.management.ReportPdf
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.RecyclerList
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.RecyclerListScope
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.management.FormDialog
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.management.SaveManagement

/** Secciones del software ganadero (pestaña «Ganado», separada de Gestión administrativa). */
enum class GanadoView(val label: String) { Resumen("Resumen"), Animales("Animales"), Lotes("Lotes"), Registros("Registros"), Informes("Informes") }

/** Pestañas de la ficha del animal. */
private enum class FichaTab(val label: String, val kind: RecordKind?) {
    General("Historial", RecordKind.All), Pesajes("Pesajes", RecordKind.Weighings), Sanidad("Sanidad", RecordKind.Health),
    Vacunas("Vacunaciones", RecordKind.Vaccinations), Tratamientos("Tratamientos", RecordKind.Treatments),
    Reproduccion("Reproducción", RecordKind.Reproduction), Movimientos("Movimientos", RecordKind.Moves),
    Nacimientos("Crías", null), Observaciones("Observaciones", null),
}

/**
 * Software ganadero: resumen del hato, tabla de animales con filtros y columnas, ficha
 * completa con historiales, lotes, registros por tipo e informe PDF. Todo se calcula de
 * los registros ([HerdView]); las reglas son las de Desktop ([LivestockOps]).
 * [onCreateActivity]: «Crear actividad» desde la ficha (la actividad es de Gestión).
 */
@Composable
fun GanadoScreen(
    project: ProjectData,
    state: ManagementUiState,
    viewModel: ManagementViewModel,
    onMessage: (String) -> Unit,
    onCreateActivity: ((animalId: String) -> Unit)?,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    var view by rememberSaveable { mutableStateOf(GanadoView.Resumen) }
    var openAnimal by rememberSaveable { mutableStateOf<String?>(null) }
    var openLot by rememberSaveable { mutableStateOf<String?>(null) }
    when (state) {
        ManagementUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        ManagementUiState.Error -> EmptyMessage("No se pudo leer el ganado de esta finca.", modifier.padding(GeoLuxDimens.screenMargin))
        is ManagementUiState.Ready -> {
            val data = state.data
            val scope = rememberCoroutineScope()
            val apply = remember(scope, viewModel, onMessage) { SaveManagement(scope, viewModel, onMessage) }
            val zones = remember(project) { project.zones.associate { it.id to it.name } }
            val rows = remember(data, zones, today) { HerdView.rows(data, zones, today) }
            val animal = data.animals.firstOrNull { it.id == openAnimal }
            Column(modifier.fillMaxSize()) {
                if (animal != null) {
                    AnimalFicha(project, data, rows.first { it.id == animal.id }, zones, viewModel, apply, today,
                        onBack = { openAnimal = null }, onOpenAnimal = { openAnimal = it }, onCreateActivity = onCreateActivity)
                    return@Column
                }
                PrimaryScrollableTabRow(selectedTabIndex = view.ordinal, edgePadding = GeoLuxDimens.screenMargin) {
                    GanadoView.entries.forEach { v ->
                        Tab(selected = v == view, onClick = { view = v; openLot = null }, text = { Text(v.label) }, modifier = Modifier.testTag("ganado_${v.name}"))
                    }
                }
                when (view) {
                    GanadoView.Resumen -> Resumen(data, zones, today, onOpenAnimal = { openAnimal = it },
                        onOpenLot = { openLot = it; view = GanadoView.Lotes })
                    GanadoView.Animales -> AnimalesTabla(project, data, rows, viewModel, apply, today, lotFilter = null) { openAnimal = it }
                    GanadoView.Lotes -> {
                        val lot = HerdView.lots(data, zones, rows).firstOrNull { it.id == openLot }
                        if (lot == null) Lotes(data, zones, rows) { openLot = it }
                        else LoteDetalle(project, data, lot, rows, zones, viewModel, apply, today, onBack = { openLot = null }) { openAnimal = it }
                    }
                    GanadoView.Registros -> Registros(data, zones) { openAnimal = it }
                    GanadoView.Informes -> Informes(project, data, zones, today, onMessage)
                }
            }
        }
    }
}

// -- Resumen -------------------------------------------------------------------------------

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier, tag: String) {
    GeoLuxCard(modifier.testTag("stat_$tag").semantics(mergeDescendants = true) {}) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun Resumen(data: ManagementData, zones: Map<String, String>, today: LocalDate, onOpenAnimal: (String) -> Unit, onOpenLot: (String) -> Unit) {
    val s = remember(data, today) { HerdView.summary(data, zones, today) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val stats = listOf(
        Triple("Animales", s.total, "total"), Triple("Activos", s.active, "activos"), Triple("Machos", s.males, "machos"),
        Triple("Hembras", s.females, "hembras"), Triple("Terneros", s.count("calf_male", "calf_female"), "terneros"),
        Triple("Novillas", s.count("heifer"), "novillas"), Triple("Novillos", s.count("steer"), "novillos"),
        Triple("Vacas", s.count("cow"), "vacas"), Triple("Toros", s.count("bull"), "toros"), Triple("Sin categoría", s.withoutClass, "sin_categoria"),
    )
    val perRow = if (wide) 5 else 2
    RecyclerList(Modifier.fillMaxSize().padding(horizontal = GeoLuxDimens.screenMargin), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Spacer(Modifier.height(4.dp)) }
        items(stats.chunked(perRow)) { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEach { (label, value, tag) -> Stat(label, value, Modifier.weight(1f), tag) }
                repeat(perRow - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        item {
            Text("Categorías sin registrar se deducen de sexo, edad y partos (como en Desktop). Vendidos ${s.sold} · muertos ${s.dead}" +
                (if (s.aggregateHead > 0) " · ${s.aggregateHead} cabezas en lotes sin identificar" else "") + ".",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SectionHeader("Alertas sanitarias", trailing = "${s.alerts.size}") }
        if (s.alerts.isEmpty()) item { EmptyMessage("Sin próximas fechas sanitarias en 15 días.") }
        items(s.alerts, key = { "al-${it.animalId}-${it.kind}" }) { a ->
            ListRow(title = "${a.tag} · ${a.kind}", overline = a.dueDate, subtitle = a.product.ifBlank { null },
                trailing = { StatusChip(if (a.overdue) "Vencida" else "Próxima", if (a.overdue) ChipFamily.Red else ChipFamily.Amber) },
                showChevron = true, modifier = Modifier.clickable { onOpenAnimal(a.animalId) })
        }
        item { SectionHeader("Movimientos recientes (30 días)") }
        if (s.recentMoves.isEmpty()) item { EmptyMessage("Sin movimientos recientes.") }
        items(s.recentMoves, key = { "mv-${it.event.id}" }) { r -> RecordRow(r, showTag = true) { onOpenAnimal(r.event.animalId) } }
        item { SectionHeader("Nacimientos recientes (30 días)") }
        if (s.recentBirths.isEmpty()) item { EmptyMessage("Sin nacimientos recientes.") }
        items(s.recentBirths, key = { "nb-${it.event.id}" }) { r -> RecordRow(r, showTag = true) { onOpenAnimal(r.event.animalId) } }
        item { SectionHeader("Resumen por lotes") }
        if (s.lots.isEmpty()) item { EmptyMessage("No hay lotes registrados.") }
        items(s.lots, key = { "lt-${it.id}" }) { l -> LotListRow(l) { onOpenLot(l.id) } }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun RecordRow(r: HerdRecord, showTag: Boolean, onClick: (() -> Unit)? = null) {
    ListRow(
        title = (if (showTag) "${r.animalTag} · " else "") + r.title,
        overline = r.event.eventDate,
        subtitle = (r.detail + if (r.voided) " · anulado" else "").ifBlank { null },
        showChevron = onClick != null,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
    HorizontalDivider()
}

@Composable
private fun LotListRow(l: LotRow, onClick: () -> Unit) {
    ListRow(
        title = l.label,
        overline = "${l.modeLabel} · ${l.count} ${if (l.count == 1) "animal" else "animales"}",
        subtitle = listOfNotNull(l.composition, l.averageWeightKg?.let { "Peso promedio ${HerdView.kg(it)} kg (${l.weighedAnimals} pesados)" })
            .joinToString(" · "),
        showChevron = true,
        modifier = Modifier.clickable(onClick = onClick).testTag("lote_${l.id}"),
    )
    HorizontalDivider()
}

// -- Tabla de animales ---------------------------------------------------------------------

@Composable
private fun FilterMenu(label: String, options: List<Pair<String?, String>>, selected: String?, tag: String, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected != null, onClick = { open = true },
            label = { Text(options.firstOrNull { it.first == selected && selected != null }?.second ?: label, maxLines = 1) },
            modifier = Modifier.testTag("filtro_$tag"))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false }, modifier = Modifier.testTag("opcion_${tag}_${value ?: "todos"}"))
            }
        }
    }
}

@Composable
internal fun AnimalesTabla(
    project: ProjectData,
    data: ManagementData,
    rows: List<AnimalRow>,
    vm: ManagementViewModel,
    apply: SaveManagement,
    today: LocalDate,
    lotFilter: String?,
    onOpen: (String) -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    var query by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf<String?>(null) }
    var breed by rememberSaveable { mutableStateOf<String?>(null) }
    var cls by rememberSaveable { mutableStateOf<String?>(null) }
    var lot by rememberSaveable { mutableStateOf<String?>(null) }
    var zone by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf<String?>("active") }
    var sort by rememberSaveable { mutableStateOf(AnimalColumn.Tag) }
    var descending by rememberSaveable { mutableStateOf(false) }
    var hidden by rememberSaveable(wide) { mutableStateOf<List<String>>(if (wide) emptyList() else AnimalColumn.entries.filter { it.wide }.map { it.name }) }
    var columnsMenu by remember { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val filter = HerdFilter(query, sex, breed, cls, lotFilter ?: lot, zone, status)
    val shown = remember(rows, filter, sort, descending) { HerdView.filterAndSort(rows, filter, sort, descending) }
    val columns = AnimalColumn.entries.filter { it != AnimalColumn.Tag && it.name !in hidden }
    val hScroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = GeoLuxDimens.screenMargin)) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, label = { Text("Buscar por identificación o nombre") }, singleLine = true,
                    modifier = Modifier.weight(1f).testTag("buscar_animal"))
                if (lotFilter == null) {
                    Spacer(Modifier.width(8.dp))
                    PrimaryButton("Nuevo", onClick = { creating = true }, modifier = Modifier.width(96.dp).testTag("nuevo_animal"))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterMenu("Estado", listOf<Pair<String?, String>>(null to "Todos los estados") + Livestock.statusLabels.toList(), status, "estado") { status = it }
                FilterMenu("Sexo", listOf<Pair<String?, String>>(null to "Todos") + Livestock.sexLabels.toList(), sex, "sexo") { sex = it }
                FilterMenu("Categoría", listOf<Pair<String?, String>>(null to "Todas") + ProductionClass.labels.toList(), cls, "categoria") { cls = it }
                FilterMenu("Raza", listOf<Pair<String?, String>>(null to "Todas") + rows.map { it.breed }.filter { it.isNotBlank() }.distinct().sorted().map { it to it }, breed, "raza") { breed = it }
                if (lotFilter == null) {
                    FilterMenu("Lote", listOf<Pair<String?, String>>(null to "Todos") + data.livestockLots.map { it.id to HerdView.lotLabel(data, project.zones.associate { z -> z.id to z.name }, it.id) }, lot, "lote") { lot = it }
                }
                FilterMenu("Potrero", listOf<Pair<String?, String>>(null to "Todos") + project.zones.map { it.id to it.name }, zone, "potrero") { zone = it }
                Box {
                    FilterChip(selected = false, onClick = { columnsMenu = true }, label = { Text("Columnas") }, modifier = Modifier.testTag("columnas"))
                    DropdownMenu(expanded = columnsMenu, onDismissRequest = { columnsMenu = false }) {
                        AnimalColumn.entries.filter { it != AnimalColumn.Tag }.forEach { c ->
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(c.name !in hidden, null); Text(c.label) } },
                                onClick = { hidden = if (c.name in hidden) hidden - c.name else hidden + c.name },
                            )
                        }
                    }
                }
            }
            Text("${shown.size} de ${rows.size} animales · ordenado por ${sort.label}${if (descending) " (descendente)" else ""}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("conteo_animales"))
            Spacer(Modifier.height(4.dp))
        }
        // Encabezado: la identificación queda fija y el resto se desplaza en horizontal junto con las filas.
        val headerBg = MaterialTheme.colorScheme.surfaceVariant
        Row(Modifier.fillMaxWidth().background(headerBg)) {
            HeaderCell(AnimalColumn.Tag, sort, descending) { if (sort == it) descending = !descending else { sort = it; descending = false } }
            Row(Modifier.horizontalScroll(hScroll)) {
                columns.forEach { c -> HeaderCell(c, sort, descending) { if (sort == it) descending = !descending else { sort = it; descending = false } } }
            }
        }
        if (shown.isEmpty()) EmptyMessage("No hay animales con estos filtros.", Modifier.padding(GeoLuxDimens.screenMargin))
        RecyclerList(Modifier.fillMaxSize().testTag("tabla_animales")) {
            items(shown, key = { it.id }) { r -> AnimalTableRow(r, columns, hScroll) { onOpen(r.id) } }
        }
    }
    if (creating) AltaDialog(project, data, vm, apply, today) { creating = false }
}

@Composable
private fun HeaderCell(c: AnimalColumn, sort: AnimalColumn, descending: Boolean, onSort: (AnimalColumn) -> Unit) {
    Text(
        c.label + if (c == sort) (if (descending) " ↓" else " ↑") else "",
        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.width(c.width.dp).clickable { onSort(c) }.padding(horizontal = 8.dp, vertical = 10.dp).testTag("col_${c.name}"),
    )
}

@Composable
private fun AnimalTableRow(r: AnimalRow, columns: List<AnimalColumn>, hScroll: ScrollState, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("animal_${r.tag}"), verticalAlignment = Alignment.CenterVertically) {
        Text(r.tag, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (r.status == "active") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(AnimalColumn.Tag.width.dp).padding(horizontal = 8.dp, vertical = 12.dp))
        Row(Modifier.horizontalScroll(hScroll)) {
            columns.forEach { c ->
                Text(c.text(r), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (c == AnimalColumn.Class && r.classSuggested) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(c.width.dp).padding(horizontal = 8.dp, vertical = 12.dp))
            }
        }
    }
    HorizontalDivider()
}

// -- Ficha del animal ----------------------------------------------------------------------

@Composable
private fun Field(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AnimalFicha(
    project: ProjectData,
    data: ManagementData,
    row: AnimalRow,
    zones: Map<String, String>,
    vm: ManagementViewModel,
    apply: SaveManagement,
    today: LocalDate,
    onBack: () -> Unit,
    onOpenAnimal: (String) -> Unit,
    onCreateActivity: ((String) -> Unit)?,
) {
    val animal = data.animals.first { it.id == row.id }
    var tab by rememberSaveable(row.id) { mutableStateOf(FichaTab.General) }
    var dialog by rememberSaveable(row.id) { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(onBack = onBack)
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val events = data.animalEvents.filter { it.animalId == row.id }
    val curve = Livestock.weightCurve(events)
    RecyclerList(Modifier.fillMaxSize().padding(horizontal = GeoLuxDimens.screenMargin).testTag("ficha_animal")) {
        item {
            TextButton(onClick = onBack) { Text("← Ganado") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Las fotos de los animales aún no viajan en el .glx: marcador con la identificación.
                Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Text(row.tag.take(4), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.tag + if (row.name.isNotBlank()) " · ${row.name}" else "", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(row.statusLabel, if (row.status == "active") ChipFamily.Green else ChipFamily.Neutral)
                        StatusChip(row.classLabel, ChipFamily.Neutral)
                    }
                    if (animal.photoPath.isNotBlank()) {
                        Text("La fotografía está en Desktop (aún no viaja al teléfono).", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val fields = listOf(
                "Identificación" to row.tag, "Sexo" to row.sexLabel, "Raza" to row.breed, "Categoría" to row.classLabel,
                "Edad" to if (row.birthDate.isBlank()) "—" else HerdView.ageLong(row.birthDate, today), "Nacimiento" to row.birthDate +
                    if (animal.birthDateEstimated == 1) " (estimada)" else "",
                "Peso actual" to (row.weightKg?.let { "${HerdView.kg(it)} kg (${row.weightDate})" } ?: "—"), "Lote" to row.lotLabel,
                "Potrero" to row.zoneName, "Estado" to row.statusLabel, "Procedencia" to row.originLabel, "Especie" to (Livestock.categories[row.category] ?: row.category),
            )
            val perRow = if (wide) 4 else 2
            fields.chunked(perRow).forEach { chunk ->
                Row(Modifier.fillMaxWidth()) {
                    chunk.forEach { (l, v) -> Field(l, v, Modifier.weight(1f)) }
                    repeat(perRow - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row.status == "active") {
                    OutlinedButton(onClick = { dialog = "pesaje" }, modifier = Modifier.testTag("accion_pesaje")) { Text("Pesaje") }
                    OutlinedButton(onClick = { dialog = "sanidad" }, modifier = Modifier.testTag("accion_sanidad")) { Text("Sanidad") }
                    OutlinedButton(onClick = { dialog = "mover" }) { Text("Mover") }
                    OutlinedButton(onClick = { dialog = "baja" }) { Text("Baja") }
                }
                if (onCreateActivity != null) {
                    OutlinedButton(onClick = { onCreateActivity(row.id) }, modifier = Modifier.testTag("accion_actividad")) { Text("Crear actividad") }
                }
            }
            Spacer(Modifier.height(8.dp))
            PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 0.dp) {
                FichaTab.entries.forEach { t -> Tab(selected = t == tab, onClick = { tab = t }, text = { Text(t.label) }, modifier = Modifier.testTag("ficha_${t.name}")) }
            }
        }
        fichaContent(tab, data, zones, row, curve, onOpenAnimal)
        item { Spacer(Modifier.height(16.dp)) }
    }
    LivestockDialogs(project, data, row.id, dialog, vm, apply, today) { dialog = null }
}

private fun RecyclerListScope.fichaContent(
    tab: FichaTab, data: ManagementData, zones: Map<String, String>, row: AnimalRow,
    curve: List<me.luiscruzandresgomezrodolfohernandez.geolux.core.management.WeightPoint>, onOpenAnimal: (String) -> Unit,
) {
    when (tab) {
        FichaTab.Pesajes -> {
            if (curve.isEmpty()) item { EmptyMessage("Sin pesajes registrados.") }
            items(curve.reversed(), key = { "w-${it.date}-${it.kg}" }) { p ->
                ListRow(title = "${HerdView.kg(p.kg)} kg", overline = p.date,
                    subtitle = p.dailyGainKg?.let { "Ganancia diaria $it kg/día respecto al pesaje anterior" })
                HorizontalDivider()
            }
        }
        FichaTab.Nacimientos -> {
            val offspring = data.animals.filter { it.damId == row.id || it.sireId == row.id }.sortedBy { it.birthDate }
            if (offspring.isEmpty()) item { EmptyMessage("Sin crías registradas.") }
            items(offspring, key = { "o-${it.id}" }) { c ->
                ListRow(title = c.tag + if (c.name.isNotBlank()) " · ${c.name}" else "", overline = c.birthDate.ifBlank { "Sin fecha" },
                    subtitle = "${Livestock.sexLabels[c.sex]} · ${Livestock.statusLabels[c.status]}", showChevron = true,
                    modifier = Modifier.clickable { onOpenAnimal(c.id) })
                HorizontalDivider()
            }
        }
        FichaTab.Observaciones -> {
            val animal = data.animals.first { it.id == row.id }
            val notes = data.animalEvents.filter { it.animalId == row.id && it.note.isNotBlank() }.sortedWith(Livestock.order).reversed()
            item {
                Text(animal.notes.ifBlank { "Sin observaciones generales." }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
            }
            items(notes, key = { "n-${it.id}" }) { e ->
                ListRow(title = e.note, overline = "${e.eventDate} · ${Livestock.eventLabels[e.eventType]}")
                HorizontalDivider()
            }
        }
        else -> {
            val records = HerdView.records(data, zones, tab.kind!!, row.id)
            if (records.isEmpty()) item { EmptyMessage("Sin registros de este tipo.") }
            items(records, key = { "r-${it.event.id}" }) { r -> RecordRow(r, showTag = false) }
        }
    }
}

// -- Lotes ---------------------------------------------------------------------------------

@Composable
private fun Lotes(data: ManagementData, zones: Map<String, String>, rows: List<AnimalRow>, onOpen: (String) -> Unit) {
    val lots = remember(data, rows) { HerdView.lots(data, zones, rows) }
    RecyclerList(Modifier.fillMaxSize().padding(horizontal = GeoLuxDimens.screenMargin)) {
        item {
            Text("${lots.size} lotes · ${lots.sumOf { it.count }} animales en total", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (lots.isEmpty()) item { EmptyMessage("No hay lotes. Se crean desde Desktop o al registrar el primer animal.") }
        items(lots, key = { it.id }) { l -> LotListRow(l) { onOpen(l.id) } }
    }
}

@Composable
private fun LoteDetalle(
    project: ProjectData, data: ManagementData, lot: LotRow, rows: List<AnimalRow>, zones: Map<String, String>,
    vm: ManagementViewModel, apply: SaveManagement, today: LocalDate,
    onBack: () -> Unit, onOpenAnimal: (String) -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    var showHistory by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = GeoLuxDimens.screenMargin)) {
            TextButton(onClick = onBack) { Text("← Lotes") }
            Text(lot.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("lote_titulo"))
            Row(Modifier.fillMaxWidth()) {
                Field("Tipo", lot.modeLabel, Modifier.weight(1f))
                Field("Animales", lot.count.toString(), Modifier.weight(1f))
                Field("Peso promedio", lot.averageWeightKg?.let { "${HerdView.kg(it)} kg" } ?: "—", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                Field("Potrero", lot.zoneName, Modifier.weight(1f))
                Field("Composición", lot.composition, Modifier.weight(2f))
            }
            Row {
                FilterChip(selected = !showHistory, onClick = { showHistory = false }, label = { Text("Animales") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = showHistory, onClick = { showHistory = true }, label = { Text("Historial del lote") }, modifier = Modifier.testTag("lote_historial"))
            }
        }
        if (showHistory) {
            // Ingresos y traslados a este lote (los eventos de alta y movimiento llevan el lote).
            val history = remember(data, lot.id) { HerdView.records(data, zones, RecordKind.All).filter { it.event.lotId == lot.id } }
            RecyclerList(Modifier.fillMaxSize().padding(horizontal = GeoLuxDimens.screenMargin)) {
                if (history.isEmpty()) item { EmptyMessage("Sin ingresos ni traslados a este lote.") }
                items(history, key = { it.event.id }) { r -> RecordRow(r, showTag = true) { onOpenAnimal(r.event.animalId) } }
            }
        } else {
            AnimalesTabla(project, data, rows, vm, apply, today, lotFilter = lot.id, onOpen = onOpenAnimal)
        }
    }
}

// -- Registros e informes ------------------------------------------------------------------

@Composable
private fun Registros(data: ManagementData, zones: Map<String, String>, onOpenAnimal: (String) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(RecordKind.All) }
    val records = remember(data, kind) { HerdView.records(data, zones, kind) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = GeoLuxDimens.screenMargin, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RecordKind.entries.forEach { k ->
                FilterChip(selected = k == kind, onClick = { kind = k }, label = { Text(k.label) }, modifier = Modifier.testTag("registro_${k.name}"))
            }
        }
        Text("${records.size} registros · del más reciente al más antiguo", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = GeoLuxDimens.screenMargin))
        RecyclerList(Modifier.fillMaxSize().padding(horizontal = GeoLuxDimens.screenMargin)) {
            if (records.isEmpty()) item { EmptyMessage("Sin registros de este tipo.") }
            items(records, key = { it.event.id }) { r -> RecordRow(r, showTag = true) { onOpenAnimal(r.event.animalId) } }
        }
    }
}

@Composable
private fun Informes(project: ProjectData, data: ManagementData, zones: Map<String, String>, today: LocalDate, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) scope.launch {
            val message = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val content = HerdView.report(data, project.project.name, zones, today)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { ReportPdf.write(content, it) }
                        ?: throw java.io.IOException("No se pudo abrir el archivo.")
                }
                "Informe ganadero guardado."
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo crear el informe: ${e.message}"
            }
            onMessage(message)
        }
    }
    val s = remember(data, today) { HerdView.summary(data, zones, today) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(GeoLuxDimens.screenMargin), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Informe ganadero")
        Text("PDF con el resumen del hato, el inventario de animales activos (identificación, sexo, raza, categoría, edad, peso, lote y potrero), " +
            "los lotes y las alertas sanitarias. Usa solo lo registrado.", style = MaterialTheme.typography.bodyMedium)
        Text("Hoy: ${s.active} activos, ${s.lots.size} lotes, ${s.alerts.size} alertas sanitarias.", style = MaterialTheme.typography.bodyMedium)
        PrimaryButton("Crear informe ganadero (PDF)", onClick = { launcher.launch("${project.project.name} ganado $today.pdf") },
            modifier = Modifier.testTag("informe_ganadero"))
        Text("El informe de gestión del mes (dinero, actividades, compras) está en la pestaña Gestión.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// -- Formularios ---------------------------------------------------------------------------

@Composable
private fun AltaDialog(project: ProjectData, data: ManagementData, vm: ManagementViewModel,
                       apply: SaveManagement, today: LocalDate, close: () -> Unit) {
    val herd = LivestockOps(vm.ops)
    val zones = project.zones.associate { it.id to it.name }
    val lots = data.livestockLots.filter { it.mode == "individual" }
    var tag by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf("unknown") }
    var breed by rememberSaveable { mutableStateOf("") }
    var birth by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(Livestock.PURCHASE) }
    var date by rememberSaveable { mutableStateOf(today.toString()) }
    var lot by rememberSaveable { mutableStateOf(lots.firstOrNull()?.id) }
    FormDialog("Nuevo animal", close, {
        apply.submit({ d ->
            var base = d
            val lotId = lot ?: herd.createIndividualLot(base, "bovine", null, "Creado en el teléfono").also { base = it.first }.second.id
            herd.registerEntry(base, tag, "bovine", kind, date, lotId, name = name, sex = sex, breed = breed, birthDate = birth).first
        }, "Animal registrado.")
    }) {
        OutlinedTextField(tag, { tag = it }, label = { Text("Identificación (p. ej. 2068-38)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("alta_tag"))
        OutlinedTextField(name, { name = it }, label = { Text("Nombre (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("alta_nombre"))
        PickerRow("Sexo", Livestock.sexLabels.toList(), sex) { sex = it }
        OutlinedTextField(breed, { breed = it }, label = { Text("Raza (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(birth, { birth = it }, label = { Text("Nacimiento (AAAA-MM-DD, opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        PickerRow("Alta", listOf(Livestock.PURCHASE to "Compra o ingreso", Livestock.BIRTH to "Nacimiento en la finca"), kind) { kind = it }
        OutlinedTextField(date, { date = it }, label = { Text("Fecha del alta (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        PickerRow("Lote", listOf<Pair<String?, String>>(null to "Lote nuevo") + lots.map { it.id to HerdView.lotLabel(data, zones, it.id) }, lot) { lot = it }
    }
}

@Composable
private fun LivestockDialogs(project: ProjectData, data: ManagementData, animalId: String, dialog: String?, vm: ManagementViewModel,
                             apply: SaveManagement, today: LocalDate, close: () -> Unit) {
    val herd = LivestockOps(vm.ops)
    val open = data.animals.firstOrNull { it.id == animalId } ?: return
    val zones = project.zones.associate { it.id to it.name }
    when (dialog) {
        "pesaje" -> {
            var kg by rememberSaveable { mutableStateOf("") }
            var date by rememberSaveable { mutableStateOf(today.toString()) }
            FormDialog("Pesaje de ${open.tag}", close, { apply.submit({ herd.recordWeighing(it, open.id, date, kg) }, "Pesaje registrado.") }) {
                OutlinedTextField(kg, { kg = it }, label = { Text("Peso (kg)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("peso_kg"))
                OutlinedTextField(date, { date = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        "sanidad" -> {
            var kind by rememberSaveable { mutableStateOf("vaccination") }
            var product by rememberSaveable { mutableStateOf("") }
            var dose by rememberSaveable { mutableStateOf("") }
            var route by rememberSaveable { mutableStateOf("") }
            var who by rememberSaveable { mutableStateOf("") }
            var date by rememberSaveable { mutableStateOf(today.toString()) }
            var next by rememberSaveable { mutableStateOf("") }
            FormDialog("Registro sanitario de ${open.tag}", close, {
                apply.submit({ herd.recordHealth(it, open.id, date, kind, product, dose, route, responsible = who, nextDate = next) }, "Registro sanitario guardado.")
            }) {
                Text("GeoLux guarda lo que registras; no sugiere productos ni dosis.", style = MaterialTheme.typography.bodySmall)
                PickerRow("Tipo", Livestock.healthKinds.toList(), kind) { kind = it }
                OutlinedTextField(product, { product = it }, label = { Text("Producto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dose, { dose = it }, label = { Text("Dosis (como la indicó el responsable)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PickerRow("Vía", Livestock.healthRoutes.toList(), route) { route = it }
                OutlinedTextField(who, { who = it }, label = { Text("Responsable (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(next, { next = it }, label = { Text("Próxima fecha (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        "mover" -> {
            val lots = data.livestockLots.filter { it.mode == "individual" && it.category == open.category && it.id != open.lotId }
            var lot by rememberSaveable { mutableStateOf(lots.firstOrNull()?.id) }
            var date by rememberSaveable { mutableStateOf(today.toString()) }
            FormDialog("Mover ${open.tag}", close, {
                apply.submit({ herd.move(it, open.id, lot ?: throw IllegalArgumentException("No hay otro lote individual de esta categoría."), date) }, "Animal movido.")
            }) {
                PickerRow("Lote destino", lots.map { it.id to HerdView.lotLabel(data, zones, it.id) }, lot) { lot = it }
                OutlinedTextField(date, { date = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        "baja" -> {
            var kind by rememberSaveable { mutableStateOf(Livestock.SALE) }
            var date by rememberSaveable { mutableStateOf(today.toString()) }
            FormDialog("Baja de ${open.tag}", close, { apply.submit({ herd.registerExit(it, open.id, kind, date) }, "Baja registrada.") }) {
                Text("El historial del animal se conserva.", style = MaterialTheme.typography.bodySmall)
                PickerRow("Motivo", listOf(Livestock.SALE to "Venta", Livestock.DEATH to "Muerte"), kind) { kind = it }
                OutlinedTextField(date, { date = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
