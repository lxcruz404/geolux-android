package me.luiscruzandresgomezrodolfohernandez.geolux.ui.location

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit.AddMenu
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit.DeleteDialog
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit.EditActions
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit.EditSession
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit.EditTarget
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit.EditToolbar
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit.FeatureFields
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit.FeatureForm
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit.FichaEditActions
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit.FormRequest
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.edit.VertexHandles
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.FarmMapController
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.LayersSheetContent
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.MapLayerGroup
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.BaseMap
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.BaseMapPreference
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.OrthoAvailability
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.OrthoLayer
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.rememberIsOnline
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.farmBounds
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.FeatureKind
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.geo.Bounds
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.geo.boundsOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.produceState
import me.luiscruzandresgomezrodolfohernandez.geolux.data.TileSource
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.ChipFamily
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.StatusChip
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.FarmMap
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.MapFicha
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.map.MapSelection
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.elementTypeColor
import androidx.compose.ui.graphics.toArgb
import kotlin.coroutines.cancellation.CancellationException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.data.LocationSource
import me.luiscruzandresgomezrodolfohernandez.geolux.data.LocationState
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens

/**
 * Pestaña Mapa: capas de la finca y posición GPS (AND-R3) sobre los ortomosaicos
 * (AND-R4) y el mapa base (Satélite Esri u OpenStreetMap, elegible en Capas), con el estado del GPS (AND-R5) encima y la ficha del objeto tocado.
 * Botones flotantes Capas · Ver toda la finca · Mi ubicación (DESIGN.md §8) y
 * hoja «Capas del mapa» (04e).
 *
 * La posición solo se escucha mientras esta pestaña está visible y la app en
 * primer plano (`collectAsStateWithLifecycle`), para no gastar batería.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapaScreen(
    data: ProjectData,
    locationSource: LocationSource,
    /** Teselas de un ortomosaico de esta finca (`ProjectRepository.orthoTiles`). */
    orthoTiles: suspend (productId: String) -> TileSource?,
    /** «Ver detalle del potrero» en la ficha. */
    onOpenZone: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** «Crear actividad» desde la ficha de un potrero; null la oculta. */
    onCreateActivity: ((String) -> Unit)? = null,
    /** Potrero que hay que elegir y encuadrar al abrir («Ver en el mapa» del detalle). */
    focusZoneId: String? = null,
    /** Se llama al encuadrarlo, para no repetirlo al volver a la pestaña. */
    onFocusConsumed: () -> Unit = {},
    /**
     * Guarda una edición de la finca (`FincaViewModel.edit`): null si se guardó, o el
     * mensaje para el usuario. Sin él, el mapa es solo de consulta.
     */
    onEdit: (suspend ((ProjectData) -> ProjectData) -> String?)? = null,
) {
    val context = LocalContext.current
    // Todos los ortomosaicos de la finca (el original y sus recortes, si Desktop los exporta).
    val orthoProducts = remember(data) { data.products.filter { it.isOrthomosaic } }
    // La primera vez extrae las teselas de cada MBTiles: puede tardar un poco.
    val orthoStates by produceState<Map<String, OrthoState>>(
        initialValue = orthoProducts.associate { it.id to OrthoState.Loading },
        orthoProducts,
    ) {
        orthoProducts.forEach { product ->
            val state = try {
                orthoTiles(product.id)?.let(OrthoState::Ready) ?: OrthoState.Unavailable
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OrthoState.Unavailable
            }
            value = value + (product.id to state)
        }
    }
    val orthoLayers = remember(orthoStates) {
        orthoProducts.mapNotNull { p -> (orthoStates[p.id] as? OrthoState.Ready)?.let { OrthoLayer(p.id, it.tiles, p.bounds) } }
    }
    var baseMap by remember { mutableStateOf(BaseMapPreference.load(context)) }
    var hiddenOrthos by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val online = rememberIsOnline()
    var selection by remember(data.project.id) { mutableStateOf<MapSelection?>(null) }
    // Si lo elegido ya no existe (se eliminó o se deshizo su creación), se cierra la ficha.
    LaunchedEffect(data, selection) {
        val sel = selection ?: return@LaunchedEffect
        val exists = when (sel.kind) {
            FeatureKind.Zone -> data.zones.any { it.id == sel.id }
            FeatureKind.Line -> data.lines.any { it.id == sel.id }
            FeatureKind.Element -> data.elements.any { it.id == sel.id }
            FeatureKind.WorkArea -> data.workArea?.id == sel.id
            FeatureKind.Mission -> data.missions.any { it.id == sel.id }
        }
        if (!exists) selection = null
    }

    // ── Edición (misión, req. 4): dibujar, cambiar forma, datos, eliminar, deshacer ──
    var session by remember(data.project.id) { mutableStateOf<EditSession?>(null) }
    var form by remember(data.project.id) { mutableStateOf<FormRequest?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<MapSelection?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    /**
     * Guarda [change]; si sale bien llama a [onSaved] y ofrece «Deshacer» (vuelve a
     * dejar las capas como estaban); si no, [onError] con el mensaje de Desktop.
     */
    fun save(change: (ProjectData) -> ProjectData, done: String, onError: (String) -> Unit, onSaved: () -> Unit) {
        val edit = onEdit ?: return
        val previous = data
        busy = true
        scope.launch {
            val error = edit(change)
            busy = false
            if (error != null) {
                onError(error)
                return@launch
            }
            onSaved()
            val result = snackbar.showSnackbar(done, actionLabel = "Deshacer", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) {
                edit(EditActions.restore(previous))?.let { snackbar.showSnackbar(it) }
            }
        }
    }
    val showError: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message, duration = SnackbarDuration.Long) } }
    // AndroidLocationSource comprueba el permiso al empezar a escuchar: tras
    // concederlo o volver de Ajustes hay que volver a escuchar. Este contador lo fuerza.
    var restartKey by remember { mutableIntStateOf(0) }
    var permissionBlocked by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.any { it }
        // Si se negó y Android ya no deja volver a preguntar, solo queda ir a Ajustes.
        val activity = context as? Activity
        permissionBlocked = !granted && activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        restartKey++
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { restartKey++ }

    val locations = remember(locationSource, restartKey) { locationSource.locations() }
    val state: LocationState? by locations.collectAsStateWithLifecycle(initialValue = null)
    val requestPermission = { permissionLauncher.launch(locationSource.requiredPermissions.toTypedArray()) }
    val openLocationSettings = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }

    val controller = remember { FarmMapController() }
    var hiddenLayers by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val visibleLayers = MapLayerGroup.entries.filter { it.name !in hiddenLayers }.toSet()
    var showLayers by rememberSaveable { mutableStateOf(false) }
    val wholeFarm = remember(data) { farmBounds(data, orthoProducts.firstOrNull()?.bounds) }
    var mapReady by remember { mutableStateOf(false) }
    // La ficha ocupa hasta 420 dp abajo: el potrero se encuadra por encima.
    val fichaPaddingPx = with(LocalDensity.current) { 440.dp.roundToPx() }
    LaunchedEffect(focusZoneId, mapReady) {
        val id = focusZoneId ?: return@LaunchedEffect
        if (!mapReady) return@LaunchedEffect
        data.zones.firstOrNull { it.id == id }?.let { zone ->
            selection = MapSelection(FeatureKind.Zone, zone.id)
            boundsOf(zone.polygon.flatten())?.let { controller.fit(it, bottomPaddingPx = fichaPaddingPx) }
        }
        onFocusConsumed()
    }

    Box(modifier.fillMaxSize()) {
        FarmMap(
            data = data,
            baseMap = baseMap,
            orthos = orthoLayers,
            hiddenOrthos = hiddenOrthos.toSet(),
            location = state,
            selected = selection,
            background = MaterialTheme.colorScheme.surfaceVariant,
            elementColor = { type -> String.format("#%06X", elementTypeColor(type).toArgb() and 0xFFFFFF) },
            onSelect = { selection = it },
            modifier = Modifier.fillMaxSize().testTag("mapa_finca"),
            controller = controller,
            visibleLayers = visibleLayers,
            onReady = { mapReady = true },
            sketch = session?.sketch,
            onMapTap = if (session != null) {
                { p -> session = session?.tap(p); true }
            } else {
                null
            },
            showOverlay = session == null,
            editOverlay = session?.let { current -> { map, tick -> VertexHandles(map, tick, current) { session = it } } },
        )
        // Estado de los ortomosaicos y marca de sintético, abajo a la derecha; sin Internet, aviso del mapa base.
        val orthoLabel = when {
            orthoProducts.isEmpty() -> null
            orthoStates.values.any { it is OrthoState.Loading } -> stringResource(R.string.map_ortho_loading)
            orthoStates.values.all { it is OrthoState.Unavailable } -> stringResource(R.string.map_ortho_unavailable)
            orthoProducts.all { it.isSynthetic } -> stringResource(R.string.chip_synthetic)
            !online && baseMap != BaseMap.None -> stringResource(R.string.map_offline_basemap)
            else -> null
        }
        if (orthoLabel != null && selection == null && session == null) {
            StatusChip(
                text = orthoLabel,
                family = ChipFamily.Amber,
                modifier = Modifier.align(Alignment.BottomEnd).padding(GeoLuxDimens.screenMargin),
            )
        }
        // Botones flotantes encima del chip del ortomosaico; se esconden con la ficha abierta o al dibujar.
        if (selection == null && session == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = GeoLuxDimens.screenMargin, bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.cardSpacing),
            ) {
                if (onEdit != null) {
                    MapFab(R.drawable.ic_add, stringResource(R.string.fab_add), "fab_agregar") { showAdd = true }
                }
                MapFab(R.drawable.ic_layers, stringResource(R.string.fab_layers), "fab_capas") { showLayers = true }
                MapFab(R.drawable.ic_zoom_out_map, stringResource(R.string.fab_whole_farm), "fab_toda_la_finca") {
                    wholeFarm?.let { controller.fit(it) }
                }
                MapFab(R.drawable.ic_my_location, stringResource(R.string.fab_my_location), "fab_mi_ubicacion") {
                    when (val l = state) {
                        is LocationState.Fix -> controller.center(Position(lon = l.lon, lat = l.lat), zoom = 17.0)
                        LocationState.NoPermission -> requestPermission()
                        LocationState.Disabled -> openLocationSettings()
                        LocationState.Searching, null -> Unit // El panel ya dice «Buscando señal GPS…».
                    }
                }
            }
        }
        selection?.takeIf { session == null }?.let { current ->
            val target = EditTarget.of(current.kind)
            MapFicha(
                data = data,
                selection = current,
                onClose = { selection = null },
                onOpenZone = onOpenZone,
                onCreateActivity = onCreateActivity,
                onShowOrtho = if (orthoLayers.isNotEmpty()) {
                    { mission ->
                        // Enciende el ortomosaico de esa misión (por si estaba apagado) y lo encuadra.
                        val layer = orthoLayers.firstOrNull { l -> orthoProducts.first { it.id == l.productId }.missionId == mission.id }
                            ?: orthoLayers.first()
                        hiddenLayers = hiddenLayers - MapLayerGroup.Ortho.name
                        hiddenOrthos = hiddenOrthos - layer.productId
                        layer.bounds.takeIf { it.size == 4 }?.let { (w, s, e, n) -> controller.fit(Bounds(w, s, e, n)) }
                        selection = null
                    }
                } else {
                    null
                },
                modifier = Modifier.align(Alignment.BottomCenter),
                actions = if (onEdit != null && target != null) {
                    {
                        FichaEditActions(
                            kind = current.kind,
                            onEditData = {
                                FeatureFields.of(data, target, current.id)?.let {
                                    formError = null
                                    form = FormRequest(target, current.id, null, it)
                                }
                            },
                            onEditShape = { session = EditSession.reshape(data, current.kind, current.id) },
                            onDelete = { confirmDelete = current },
                        )
                    }
                } else {
                    null
                },
            )
        }
        session?.let { current ->
            EditToolbar(
                session = current,
                busy = busy,
                onUndo = { session = current.undo() },
                onRemoveVertex = { session = current.removeSelected() },
                onCancel = { session = null },
                onDone = {
                    when {
                        current.featureId != null -> save(EditActions.saveShape(current), "Forma guardada", showError) { session = null }
                        current.target == EditTarget.WorkArea ->
                            save(EditActions.createNew(EditTarget.WorkArea, current.points, FeatureFields()), "Área de trabajo guardada", showError) { session = null }
                        else -> {
                            formError = null
                            form = FormRequest(current.target, null, current.points, FeatureFields.forNew(current.target))
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (session != null) 180.dp else 0.dp))
        if (session == null) LocationPanel(
            state = state,
            data = data,
            permissionBlocked = permissionBlocked,
            onRequestPermission = requestPermission,
            onOpenAppSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
            onOpenLocationSettings = openLocationSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(GeoLuxDimens.screenMargin),
        )
    }

    if (showAdd) {
        ModalBottomSheet(
            onDismissRequest = { showAdd = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = GeoLuxShape.bottomSheet,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            AddMenu(hasWorkArea = data.workArea != null) { target ->
                showAdd = false
                selection = null
                session = EditSession.new(target)
            }
        }
    }
    form?.let { request ->
        ModalBottomSheet(
            onDismissRequest = { form = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = GeoLuxShape.bottomSheet,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            FeatureForm(
                target = request.target,
                initial = request.initial,
                isNew = request.featureId == null,
                error = formError,
                onSave = { fields ->
                    val change = if (request.featureId == null) {
                        EditActions.createNew(request.target, request.points.orEmpty(), fields)
                    } else {
                        EditActions.updateDetails(request.target, request.featureId, fields)
                    }
                    val done = if (request.featureId == null) "Guardado en la finca" else "Datos guardados"
                    save(change, done, onError = { formError = it }) {
                        form = null
                        session = null
                    }
                },
                onCancel = { form = null },
            )
        }
    }
    confirmDelete?.let { target ->
        DeleteDialog(
            name = EditActions.displayName(data, target.kind, target.id),
            onConfirm = {
                confirmDelete = null
                save(EditActions.delete(target.kind, target.id), "Eliminado", showError) { selection = null }
            },
            onDismiss = { confirmDelete = null },
        )
    }

    if (showLayers) {
        ModalBottomSheet(
            onDismissRequest = { showLayers = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = GeoLuxShape.bottomSheet,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            LayersSheetContent(
                data = data,
                orthos = orthoProducts.map { OrthoAvailability(it, orthoStates[it.id] is OrthoState.Ready) },
                visible = visibleLayers,
                onToggle = { group, on -> hiddenLayers = if (on) hiddenLayers - group.name else hiddenLayers + group.name },
                baseMap = baseMap,
                onBaseMap = { baseMap = it; BaseMapPreference.save(context, it) },
                hiddenOrthos = hiddenOrthos.toSet(),
                onToggleOrtho = { id, on -> hiddenOrthos = if (on) hiddenOrthos - id else hiddenOrthos + id },
            )
        }
    }
}

/** Botón flotante del mapa: 48 dp (DESIGN.md §5), con descripción para lectores de pantalla. */
@Composable
private fun MapFab(@DrawableRes icon: Int, description: String, tag: String, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(GeoLuxDimens.mapFloatingButton).testTag(tag),
    ) {
        Icon(painterResource(icon), contentDescription = description)
    }
}

/** Estado del ortomosaico en la pestaña Mapa. */
private sealed interface OrthoState {
    data object Loading : OrthoState
    data class Ready(val tiles: TileSource) : OrthoState

    /** La finca no trae ortomosaico. */
    data object None : OrthoState

    /** La finca lo trae pero no se pudieron preparar las teselas. */
    data object Unavailable : OrthoState
}
