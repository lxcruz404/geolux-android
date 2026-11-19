package me.luiscruzandresgomezrodolfohernandez.geolux.ui.management

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.Activity
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.Catalogs
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.Finance
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.ChipFamily
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.EmptyMessage
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.ListRow
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.MetricCard
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.PrimaryButton
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.SectionHeader
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.StatusChip
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.RecyclerList

/** Secciones de Gestión administrativa. El ganado tiene su propia pestaña (software ganadero). */
enum class GestionSection(val label: String) { Actividades("Actividades"), Compras("Compras"), Trabajadores("Trabajadores"), Dinero("Dinero") }

/** Filtros de la agenda. */
enum class AgendaFilter(val label: String) { Hoy("Hoy"), Semana("Semana"), Mes("Mes"), Pendientes("Pendientes"), Vencidas("Vencidas"), Completadas("Completadas") }

/**
 * Gestión de la finca en el teléfono: listas compactas, formularios cortos y las mismas
 * reglas que Desktop. [prefillZoneId]: potrero ya elegido («Crear actividad» desde un potrero).
 */
@Composable
fun GestionScreen(
    project: ProjectData,
    state: ManagementUiState,
    viewModel: ManagementViewModel,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    prefillZoneId: String? = null,
    onPrefillConsumed: () -> Unit = {},
    today: LocalDate = LocalDate.now(),
    /** «Crear actividad» desde la ficha de un animal: la actividad queda vinculada a él. */
    prefillAnimalId: String? = null,
) {
    var section by rememberSaveable { mutableStateOf(GestionSection.Actividades) }
    // Viene de «Crear actividad» en un potrero (mapa o ficha): abre Actividades.
    androidx.compose.runtime.LaunchedEffect(prefillZoneId, prefillAnimalId) { if (prefillZoneId != null || prefillAnimalId != null) section = GestionSection.Actividades }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Android 13+: el permiso de avisos se pide una vez, cuando ya hay recordatorios.
    val notifyPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    var askedNotify by rememberSaveable { mutableStateOf(false) }
    val wantsReminders = (state as? ManagementUiState.Ready)?.data?.activities?.any { it.remindMinutesBefore != null && it.deletedAt == null } == true
    androidx.compose.runtime.LaunchedEffect(wantsReminders) {
        if (wantsReminders && !askedNotify && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            !me.luiscruzandresgomezrodolfohernandez.geolux.reminders.ReminderScheduler.canNotify(context)
        ) {
            askedNotify = true
            notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val reportScope = rememberCoroutineScope()
    // Informe del mes en curso: la persona elige dónde guardarlo (selector del sistema).
    val reportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val ready = state as? ManagementUiState.Ready
        if (uri != null && ready != null) reportScope.launch {
            val message = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val content = me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementReport.build(
                        ready.data, project.project.name, today.withDayOfMonth(1).toString(), today.toString(),
                        zoneNames = project.zones.associate { it.id to it.name }, ops = viewModel.ops)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { ReportPdf.write(content, it) }
                        ?: throw java.io.IOException("No se pudo abrir el archivo.")
                }
                "Informe PDF guardado."
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo crear el informe: ${e.message}"
            }
            onMessage(message)
        }
    }
    Column(modifier.fillMaxSize()) {
        TextButton(
            onClick = { reportLauncher.launch("${project.project.name} gestión ${today.withDayOfMonth(1)} a $today.pdf") },
            modifier = Modifier.padding(horizontal = GeoLuxDimens.screenMargin),
        ) { Text("Informe PDF del mes") }
        PrimaryScrollableTabRow(selectedTabIndex = section.ordinal, edgePadding = GeoLuxDimens.screenMargin) {
            GestionSection.entries.forEach { s ->
                Tab(selected = s == section, onClick = { section = s }, text = { Text(s.label) }, modifier = Modifier.testTag("gestion_${s.name}"))
            }
        }
        when (state) {
            ManagementUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            ManagementUiState.Error -> EmptyMessage("No se pudo leer la gestión de esta finca.", Modifier.padding(GeoLuxDimens.screenMargin))
            is ManagementUiState.Ready -> {
                val scope = rememberCoroutineScope()
                val apply = remember(scope, viewModel, onMessage) { SaveManagement(scope, viewModel, onMessage) }
                when (section) {
                    GestionSection.Actividades -> ActividadesSection(project, state.data, viewModel, apply, today,
                        prefillZoneId, onPrefillConsumed, prefillAnimalId)
                    GestionSection.Compras -> ComprasSection(state.data, viewModel, apply, today)
                    GestionSection.Trabajadores -> TrabajadoresSection(state.data, viewModel, apply)
                    GestionSection.Dinero -> DineroSection(state.data, viewModel, apply, today)
                }
            }
        }
    }
}

private fun priorityFamily(p: String) = when (p) {
    "urgent" -> ChipFamily.Red
    "high" -> ChipFamily.Amber
    else -> ChipFamily.Neutral
}

/** Nombres legibles de los enlaces de una actividad («Potrero 03», «Cerca norte»…). */
internal fun linkLabels(project: ProjectData, data: ManagementData, activityId: String): List<String> =
    data.activityLinks.filter { it.activityId == activityId }.map { link ->
        val name = when (link.entity) {
            "zone" -> project.zones.firstOrNull { it.id == link.entityId }?.name
            "map_line" -> project.lines.firstOrNull { it.id == link.entityId }?.name
            "map_element" -> project.elements.firstOrNull { it.id == link.entityId }?.name
            "worker" -> data.workers.firstOrNull { it.id == link.entityId }?.fullName
            "purchase_list" -> data.purchaseLists.firstOrNull { it.id == link.entityId }?.name
            "purchase_item" -> data.purchaseItems.firstOrNull { it.id == link.entityId }?.name
            "animal" -> data.animals.firstOrNull { it.id == link.entityId }?.tag
            "livestock_lot" -> data.livestockLots.firstOrNull { it.id == link.entityId }?.let {
                me.luiscruzandresgomezrodolfohernandez.geolux.core.management.HerdView.lotLabel(data, project.zones.associate { z -> z.id to z.name }, it.id)
            }
            else -> null
        }
        "${Catalogs.linkEntity[link.entity]}: ${name ?: link.entityId.take(8)}"
    }

@Composable
private fun ActividadesSection(
    project: ProjectData,
    data: ManagementData,
    vm: ManagementViewModel,
    apply: SaveManagement,
    today: LocalDate,
    prefillZoneId: String?,
    onPrefillConsumed: () -> Unit,
    prefillAnimalId: String? = null,
) {
    var filter by rememberSaveable { mutableStateOf(AgendaFilter.Pendientes) }
    var creating by rememberSaveable { mutableStateOf(prefillZoneId != null || prefillAnimalId != null) }
    androidx.compose.runtime.LaunchedEffect(prefillZoneId, prefillAnimalId) { if (prefillZoneId != null || prefillAnimalId != null) creating = true }
    val ops = vm.ops
    val shown = when (filter) {
        AgendaFilter.Hoy -> ops.onDay(data, today)
        AgendaFilter.Semana -> ops.between(data, today.minusDays((today.dayOfWeek.value - 1).toLong()),
            today.minusDays((today.dayOfWeek.value - 1).toLong()).plusDays(6))
        AgendaFilter.Mes -> ops.between(data, today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))
        AgendaFilter.Pendientes -> ops.sortForAgenda(ops.liveActivities(data).filter { it.status in Catalogs.openActivityStatus })
        AgendaFilter.Vencidas -> ops.sortForAgenda(ops.overdue(data, Instant.now()))
        AgendaFilter.Completadas -> ops.liveActivities(data).filter { it.status == "done" }.sortedByDescending { it.completedAt }
    }
    RecyclerList(Modifier.fillMaxSize().padding(horizontal = GeoLuxDimens.screenMargin)) {
        item {
            // Los filtros se desplazan en horizontal: en un teléfono angosto no se aplastan.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AgendaFilter.entries.forEach { f ->
                    FilterChip(selected = f == filter, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
            PrimaryButton("Nueva actividad", onClick = { creating = true }, modifier = Modifier.testTag("nueva_actividad"))
            Spacer(Modifier.height(8.dp))
        }
        if (shown.isEmpty()) item { EmptyMessage("No hay actividades en esta vista.") }
        items(shown, key = { it.id }) { a -> ActivityRow(project, data, a, apply, vm) }
    }
    if (creating) {
        // Se cierra (onDismiss) solo si se guardó; con error, el formulario sigue abierto.
        ActivityForm(project, data, vm, prefillZoneId, prefillAnimalId, onDismiss = { creating = false; onPrefillConsumed() }) { change ->
            apply.submit(change, "Actividad guardada.")
        }
    }
}

@Composable
private fun ActivityRow(project: ProjectData, data: ManagementData, a: Activity, apply: SaveManagement, vm: ManagementViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }
    val when_ = listOfNotNull(a.scheduledDate, a.scheduledTime).joinToString(" ").ifEmpty { "Sin fecha" } +
        (a.remindMinutesBefore?.let { " · aviso $it min antes" } ?: "")
    val who = data.workers.firstOrNull { it.id == a.assigneeWorkerId }?.fullName
    ListRow(
        title = a.title,
        overline = "${Catalogs.activityCategory[a.category] ?: a.category.removePrefix(Catalogs.CUSTOM_PREFIX)} · $when_",
        subtitle = (listOfNotNull(who) + linkLabels(project, data, a.id)).joinToString(" · ").ifEmpty { null },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(Catalogs.activityPriority.getValue(a.priority), priorityFamily(a.priority))
                Checkbox(
                    checked = a.status == "done",
                    onCheckedChange = { done ->
                        apply({ vm.ops.setActivityStatus(it, a.id, if (done) "done" else "pending") },
                            if (done) "Actividad completada." else "Actividad pendiente otra vez.")
                    },
                    modifier = Modifier.testTag("completar_${a.id}"),
                )
            }
        },
        modifier = Modifier.clickable { confirmDelete = true },
    )
    HorizontalDivider()
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(a.title) },
            text = { Text("Estado: ${Catalogs.activityStatus[a.status]}. ¿Quieres quitar esta actividad? Se conserva en el historial que viaja a Desktop.") },
            confirmButton = { TextButton(onClick = { apply({ vm.ops.deleteActivity(it, a.id) }, "Actividad quitada."); confirmDelete = false }) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
internal fun <T> PickerRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) =
    Picker(label, options, selected, onSelect)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Picker(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second.orEmpty(), onValueChange = {}, readOnly = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false }) }
        }
    }
}

/**
 * Guarda un cambio de la gestión (Room, vía [ManagementViewModel.edit]).
 * Llamarlo como `apply(cambio, "Hecho.")` es para acciones sueltas: el resultado va al
 * snackbar. [submit] es para formularios: devuelve el error (null = guardado) para que
 * el diálogo siga abierto, con lo escrito, si algo no es válido.
 */
internal class SaveManagement(
    private val scope: CoroutineScope,
    private val viewModel: ManagementViewModel,
    private val onMessage: (String) -> Unit,
) {
    operator fun invoke(change: (ManagementData) -> ManagementData, done: String) {
        scope.launch { onMessage(viewModel.edit(change) ?: done) }
    }

    suspend fun submit(change: (ManagementData) -> ManagementData, done: String): String? =
        viewModel.edit(change).also { error -> if (error == null) onMessage(done) }
}

/**
 * Diálogo de formulario de Gestión y Ganado. [onSave] valida y guarda, y devuelve el error
 * (null = guardado): solo entonces se cierra. Si hay error se muestra aquí mismo y lo
 * escrito se conserva; «Guardar» se desactiva mientras guarda.
 */
@Composable
internal fun FormDialog(title: String, onDismiss: () -> Unit, onSave: suspend () -> String?, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error_formulario")) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        saving = true
                        error = onSave()
                        saving = false
                        if (error == null) onDismiss()
                    }
                },
                enabled = !saving,
                modifier = Modifier.testTag("guardar"),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } },
    )
}

@Composable
private fun ActivityForm(project: ProjectData, data: ManagementData, vm: ManagementViewModel, prefillZoneId: String?,
                         prefillAnimalId: String?, onDismiss: () -> Unit, onSave: suspend ((ManagementData) -> ManagementData) -> String?) {
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("other") }
    var priority by rememberSaveable { mutableStateOf("medium") }
    var date by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("") }
    var remind by rememberSaveable { mutableStateOf("") }
    var worker by rememberSaveable { mutableStateOf<String?>(null) }
    var zone by rememberSaveable { mutableStateOf(prefillZoneId) }
    var animal by rememberSaveable { mutableStateOf(prefillAnimalId) }
    val geo = ManagementViewModel.geoIds(project)
    FormDialog("Nueva actividad", onDismiss, onSave = {
        onSave { d ->
            vm.ops.createActivity(d, geo, title, category, priority, date.ifBlank { null }, time.ifBlank { null },
                remindMinutesBefore = remind.trim().ifEmpty { null }?.toIntOrNull()
                    ?: if (remind.isBlank()) null else throw IllegalArgumentException("El recordatorio va en minutos."),
                assigneeWorkerId = worker, links = listOfNotNull(zone?.let { "zone" to it }, animal?.let { "animal" to it })).first
        }
    }) {
        OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("titulo"))
        Picker("Categoría", Catalogs.activityCategory.toList(), category, { category = it })
        Picker("Prioridad", Catalogs.activityPriority.toList(), priority, { priority = it })
        OutlinedTextField(date, { date = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(time, { time = it }, label = { Text("Hora (HH:MM, opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(remind, { remind = it }, label = { Text("Recordar minutos antes (opcional)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Picker("Responsable", listOf<Pair<String?, String>>(null to "Sin asignar") + vm.ops.activeWorkers(data).map { it.id to it.fullName }, worker, { worker = it })
        Picker("Potrero", listOf<Pair<String?, String>>(null to "Ninguno") + project.zones.map { it.id to it.name }, zone, { zone = it })
        Picker("Animal", listOf<Pair<String?, String>>(null to "Ninguno") + data.animals.filter { it.status == "active" || it.id == animal }
            .sortedBy { it.tag }.map { it.id to it.tag + if (it.name.isNotBlank()) " · ${it.name}" else "" }, animal, { animal = it })
    }
}

@Composable
private fun ComprasSection(data: ManagementData, vm: ManagementViewModel, apply: SaveManagement, today: LocalDate) {
    var openList by rememberSaveable { mutableStateOf<String?>(null) }
    var newList by rememberSaveable { mutableStateOf(false) }
    var newItem by rememberSaveable { mutableStateOf(false) }
    var pricing by rememberSaveable { mutableStateOf<String?>(null) }
    var paying by rememberSaveable { mutableStateOf(false) }
    val list = data.purchaseLists.firstOrNull { it.id == openList && it.deletedAt == null }
    RecyclerList(Modifier.fillMaxSize().padding(horizontal = GeoLuxDimens.screenMargin)) {
        if (list == null) {
            item { Spacer(Modifier.height(8.dp)); PrimaryButton("Nueva lista", onClick = { newList = true }); Spacer(Modifier.height(8.dp)) }
            val lists = data.purchaseLists.filter { it.deletedAt == null }.sortedByDescending { it.createdAt }
            if (lists.isEmpty()) item { EmptyMessage("Aún no hay listas de compra.") }
            items(lists, key = { it.id }) { l ->
                val items = vm.ops.items(data, l.id)
                ListRow(title = l.name, overline = Catalogs.purchaseListType[l.listType],
                    subtitle = "${items.count { it.status == "bought" }} de ${items.size} comprados",
                    trailing = { StatusChip(Catalogs.purchaseListStatus.getValue(l.status), if (l.status == "done") ChipFamily.Green else ChipFamily.Neutral) },
                    showChevron = true, modifier = Modifier.clickable { openList = l.id })
                HorizontalDivider()
            }
        } else {
            val items = vm.ops.items(data, list.id)
            item {
                TextButton(onClick = { openList = null }) { Text("← Todas las listas") }
                SectionHeader(list.name, trailing = Catalogs.purchaseListStatus[list.status])
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Agregar artículo", onClick = { newItem = true })
                val payable = items.filter { it.status in setOf("bought", "partial") && it.transactionId == null && it.actualPrice != null }
                if (payable.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("Registrar el pago (${payable.size})", onClick = { paying = true }, modifier = Modifier.testTag("registrar_pago"))
                }
            }
            items(items, key = { it.id }) { item ->
                ListRow(
                    title = item.name,
                    overline = "${item.quantity} ${Catalogs.unit[item.unit]}",
                    subtitle = listOfNotNull(item.estimatedPrice?.let { "Estimado $it" }, item.actualPrice?.let { "Real $it" },
                        if (item.transactionId != null) "Pago registrado" else null).joinToString(" · ").ifEmpty { null },
                    trailing = {
                        Checkbox(checked = item.status == "bought", enabled = item.transactionId == null, onCheckedChange = { bought ->
                            if (bought) pricing = item.id else apply({ vm.ops.setItemStatus(it, item.id, "pending") }, "Artículo pendiente.")
                        })
                    },
                )
                HorizontalDivider()
            }
        }
    }
    if (newList) {
        var name by rememberSaveable { mutableStateOf("") }
        var type by rememberSaveable { mutableStateOf("general") }
        FormDialog("Nueva lista", { newList = false }, {
            apply.submit({ vm.ops.createList(it, name, type).first }, "Lista creada.")
        }) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("lista_nombre"))
            Picker("Tipo", Catalogs.purchaseListType.toList(), type, { type = it })
        }
    }
    if (newItem && list != null) {
        var name by rememberSaveable { mutableStateOf("") }
        var qty by rememberSaveable { mutableStateOf("1") }
        var unit by rememberSaveable { mutableStateOf("unit") }
        var price by rememberSaveable { mutableStateOf("") }
        FormDialog("Agregar artículo", { newItem = false }, {
            apply.submit({ vm.ops.addItem(it, list.id, name, qty, unit, price.ifBlank { null }).first }, "Artículo agregado.")
        }) {
            OutlinedTextField(name, { name = it }, label = { Text("Artículo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(qty, { qty = it }, label = { Text("Cantidad") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Picker("Unidad", Catalogs.unit.toList(), unit, { unit = it })
            OutlinedTextField(price, { price = it }, label = { Text("Precio estimado (opcional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        }
    }
    pricing?.let { itemId ->
        var real by rememberSaveable { mutableStateOf("") }
        FormDialog("Comprado", { pricing = null }, {
            apply.submit({ vm.ops.setItemStatus(it, itemId, "bought", real.ifBlank { null }) }, "Marcado como comprado (aún sin gasto).")
        }) {
            Text("Marcar como comprado no registra un gasto. El gasto se registra al confirmar el pago.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(real, { real = it }, label = { Text("Valor real (opcional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        }
    }
    if (paying && list != null) {
        var category by rememberSaveable { mutableStateOf("general_purchases") }
        var method by rememberSaveable { mutableStateOf("cash") }
        val payable = vm.ops.items(data, list.id).filter { it.status in setOf("bought", "partial") && it.transactionId == null && it.actualPrice != null }
        FormDialog("Registrar el pago", { paying = false }, {
            apply.submit({ vm.ops.confirmPayment(it, payable.map { i -> i.id }, today.toString(), category, method).first }, "Pago registrado como gasto.")
        }) {
            Text(payable.joinToString("\n") { "${it.name}: ${it.actualPrice}" })
            Picker("Categoría", vm.ops.categories(data).toList(), category, { category = it })
            Picker("Método de pago", Catalogs.paymentMethod.toList(), method, { method = it })
        }
    }
}

@Composable
private fun TrabajadoresSection(data: ManagementData, vm: ManagementViewModel, apply: SaveManagement) {
    var adding by rememberSaveable { mutableStateOf(false) }
    val workers = data.workers.filter { it.deletedAt == null }.sortedBy { it.fullName.lowercase() }
    RecyclerList(Modifier.fillMaxSize().padding(horizontal = GeoLuxDimens.screenMargin)) {
        item { Spacer(Modifier.height(8.dp)); PrimaryButton("Nuevo trabajador", onClick = { adding = true }); Spacer(Modifier.height(8.dp)) }
        if (workers.isEmpty()) item { EmptyMessage("Aún no hay trabajadores registrados.") }
        items(workers, key = { it.id }) { w ->
            val pending = vm.ops.liveActivities(data).count { it.assigneeWorkerId == w.id && it.status in Catalogs.openActivityStatus }
            val paid = Finance.summary(data.transactions.filter { it.workerId == w.id }).workerPayments
            ListRow(title = w.fullName, overline = "${w.code} · ${w.role.ifEmpty { "Sin cargo" }}",
                subtitle = "$pending pendiente(s) · pagado $paid",
                trailing = { StatusChip(Catalogs.workerStatus.getValue(w.status), if (w.status == "active") ChipFamily.Green else ChipFamily.Neutral) },
                modifier = Modifier.clickable {
                    apply({ vm.ops.setWorkerStatus(it, w.id, if (w.status == "active") "inactive" else "active") }, "Estado actualizado.")
                })
            HorizontalDivider()
        }
    }
    if (adding) {
        var code by rememberSaveable { mutableStateOf("") }
        var name by rememberSaveable { mutableStateOf("") }
        var role by rememberSaveable { mutableStateOf("") }
        FormDialog("Nuevo trabajador", { adding = false }, {
            apply.submit({ vm.ops.createWorker(it, code, name, role).first }, "Trabajador registrado.")
        }) {
            OutlinedTextField(code, { code = it }, label = { Text("Identificación (p. ej. T-01)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(name, { name = it }, label = { Text("Nombre completo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(role, { role = it }, label = { Text("Cargo (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DineroSection(data: ManagementData, vm: ManagementViewModel, apply: SaveManagement, today: LocalDate) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var voiding by rememberSaveable { mutableStateOf<String?>(null) }
    val summary = Finance.summary(data.transactions)
    val voided = data.transactions.mapNotNull { it.voidsTransactionId }.toSet()
    RecyclerList(Modifier.fillMaxSize().padding(horizontal = GeoLuxDimens.screenMargin)) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("Ingresos", summary.income, Modifier.weight(1f))
                MetricCard("Gastos", summary.expenses, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            MetricCard("Balance (${summary.currency})", summary.balance, Modifier.fillMaxWidth())
            Text("Solo movimientos registrados; los anulados no cuentan.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Nuevo movimiento", onClick = { adding = true })
            Spacer(Modifier.height(8.dp))
        }
        val rows = data.transactions.sortedWith(compareByDescending<me.luiscruzandresgomezrodolfohernandez.geolux.core.management.FinancialTransaction> { it.date }.thenByDescending { it.createdAt })
        if (rows.isEmpty()) item { EmptyMessage("Aún no hay movimientos.") }
        items(rows, key = { it.id }) { t ->
            val isVoided = t.id in voided || t.voidsTransactionId != null
            ListRow(title = t.concept, overline = "${t.date} · ${Catalogs.transactionType[t.type]?.first}",
                subtitle = (vm.ops.categories(data)[t.category] ?: t.category) + if (isVoided) " · anulado" else "",
                trailing = { Text((if (t.direction == "out") "−" else if (t.direction == "in") "+" else "") + "${t.amount} ${t.currency}") },
                modifier = Modifier.clickable(enabled = !isVoided) { voiding = t.id })
            HorizontalDivider()
        }
    }
    if (adding) {
        var type by rememberSaveable { mutableStateOf("expense") }
        var direction by rememberSaveable { mutableStateOf("out") }
        var concept by rememberSaveable { mutableStateOf("") }
        var amount by rememberSaveable { mutableStateOf("") }
        var category by rememberSaveable { mutableStateOf("other") }
        var date by rememberSaveable { mutableStateOf(today.toString()) }
        var worker by rememberSaveable { mutableStateOf<String?>(null) }
        FormDialog("Nuevo movimiento", { adding = false }, {
            apply.submit({
                vm.ops.recordTransaction(it, type = type, date = date, concept = concept, category = category, amount = amount,
                    direction = if (type == "other") direction else null, workerId = worker).first
            }, "Movimiento registrado.")
        }) {
            Picker("Tipo", Catalogs.transactionType.map { it.key to it.value.first }, type, { type = it })
            if (type == "other") Picker("Sentido", listOf("in" to "Entrada", "out" to "Salida"), direction, { direction = it })
            OutlinedTextField(concept, { concept = it }, label = { Text("Concepto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(amount, { amount = it }, label = { Text("Valor (COP)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Picker("Categoría", vm.ops.categories(data).toList(), category, { category = it })
            OutlinedTextField(date, { date = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (type in Catalogs.workerTransactionTypes) {
                Picker("Trabajador", listOf<Pair<String?, String>>(null to "Elegir…") + data.workers.filter { it.deletedAt == null }.map { it.id to it.fullName }, worker, { worker = it })
            }
        }
    }
    voiding?.let { id ->
        var reason by rememberSaveable { mutableStateOf("") }
        FormDialog("Anular movimiento", { voiding = null }, {
            apply.submit({ vm.ops.voidTransaction(it, id, reason, today).first }, "Movimiento anulado.")
        }) {
            Text("Los movimientos no se borran: la anulación queda en el historial y ninguno de los dos cuenta.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(reason, { reason = it }, label = { Text("Motivo") }, modifier = Modifier.fillMaxWidth())
        }
    }
}
