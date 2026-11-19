package me.luiscruzandresgomezrodolfohernandez.geolux.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.data.LocationSource
import me.luiscruzandresgomezrodolfohernandez.geolux.data.TileSource
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ViewingModel
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.GeoLuxTopBar
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.RecyclerListState
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.rememberRecyclerListState
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.finca.FincaUiState
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.finca.FincaViewModel
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.finca.MiFincaScreen
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.finca.PotreroDetailScreen
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.finca.PotrerosScreen
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.importing.ImportActions
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.importing.ImportSheet
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.importing.ImportViewModel
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.livestock.GanadoScreen
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.location.ExploreMapScreen
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.location.MapaScreen
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.management.GestionScreen
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.management.ManagementViewModel
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.model3d.ImmersiveSystemBarsEffect
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.model3d.Model3dScreen
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.model3d.ViewerOrientationEffect
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation.ExplorarMapaRoute
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation.FincaRoute
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation.FincaTab
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation.GanadoRoute
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation.GestionRoute
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation.InformeRoute
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation.MapaRoute
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation.MiFincaRoute
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation.MisFincasRoute
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation.Modelo3DRoute
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation.PotreroDetalleRoute
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation.PotrerosRoute
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation.fincaTab
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation.route
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.projects.ProjectsScreen
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.projects.ProjectsViewModel
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.report.ReportScreen
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.screens.PlaceholderScreen

/**
 * Navegación de la app con navigation-compose (DESIGN.md §8–9), en dos niveles:
 *
 * 1. Grafo principal ([navController]): «Mis fincas» → «Explorar el mapa» o una finca.
 * 2. Dentro de la finca, un segundo `NavHost` con la barra inferior Mi finca · Mapa ·
 *    Potreros · Ganado · 3D · Gestión y los detalles (informe, detalle de potrero).
 *
 * Atrás deshace un paso con la pila de navegación: detalle → su pestaña → Mi finca →
 * Mis fincas → salir. Cada pestaña guarda su estado al cambiar de una a otra.
 * Sin transiciones animadas: así nunca hay dos mapas o dos visores 3D vivos a la vez.
 */
@Composable
fun GeoLuxApp(
    projectsViewModel: ProjectsViewModel,
    fincaViewModel: FincaViewModel,
    importViewModel: ImportViewModel,
    locationSource: LocationSource,
    orthoTiles: suspend (projectId: String, productId: String) -> TileSource?,
    productFile: suspend (projectId: String, productId: String) -> java.io.File?,
    /** Modelo 3D preparado con la mejor calidad que admite el teléfono (`ProjectRepository.viewingModel`). */
    viewingModel: (suspend (projectId: String, productId: String, onProgress: (Int, Int) -> Unit) -> ViewingModel?)? = null,
    /** Exporta la finca abierta a un `.glx` en el archivo elegido; null si salió bien, o el error. */
    exportPackage: (suspend (projectId: String, destination: android.net.Uri) -> String?)? = null,
    /** Gestión de la finca (pestañas Ganado y Gestión); sin ella esas pestañas muestran un aviso. */
    managementViewModel: ManagementViewModel? = null,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = MisFincasRoute,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable<MisFincasRoute> {
            MisFincasDestination(
                projectsViewModel = projectsViewModel,
                importViewModel = importViewModel,
                onOpenFinca = { id -> navController.navigate(FincaRoute(id)) { launchSingleTop = true } },
                onExploreMap = { navController.navigate(ExplorarMapaRoute) { launchSingleTop = true } },
            )
        }
        composable<ExplorarMapaRoute> {
            Scaffold(
                topBar = { GeoLuxTopBar(title = stringResource(R.string.screen_explore_map), onBack = { navController.popBackStack() }) },
                containerColor = MaterialTheme.colorScheme.background,
            ) { innerPadding ->
                ExploreMapScreen(locationSource = locationSource, modifier = Modifier.padding(innerPadding))
            }
        }
        composable<FincaRoute> { entry ->
            FincaDestination(
                projectId = entry.toRoute<FincaRoute>().projectId,
                fincaViewModel = fincaViewModel,
                managementViewModel = managementViewModel,
                locationSource = locationSource,
                orthoTiles = orthoTiles,
                productFile = productFile,
                viewingModel = viewingModel,
                exportPackage = exportPackage,
            )
        }
    }
}

/** «Mis fincas»: la lista, importar un paquete y abrir una finca o el mapa sin finca. */
@Composable
private fun MisFincasDestination(
    projectsViewModel: ProjectsViewModel,
    importViewModel: ImportViewModel,
    onOpenFinca: (String) -> Unit,
    onExploreMap: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // Selector de archivos del sistema (Storage Access Framework). `.geolux` no tiene
    // tipo MIME registrado, así que se muestran todos los archivos y el repositorio
    // valida el elegido (SPEC §7).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        importViewModel.onFilePicked(uri)
    }
    val onImport = { picker.launch(arrayOf("*/*")) }
    val projectsState by projectsViewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { GeoLuxTopBar(title = stringResource(R.string.screen_my_farms)) },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        ProjectsScreen(
            state = projectsState,
            onImport = onImport,
            onExploreMap = onExploreMap,
            onOpen = onOpenFinca,
            modifier = Modifier.padding(innerPadding),
            onDelete = { id, name ->
                projectsViewModel.delete(id, name) { error ->
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(error ?: "Se eliminó «$name» del teléfono.")
                    }
                }
            },
        )
    }
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val importProgress by importViewModel.progress.collectAsStateWithLifecycle()
    ImportSheet(
        state = importState,
        progress = importProgress,
        actions = ImportActions(
            onImport = importViewModel::confirmImport,
            onRequestReplace = importViewModel::requestReplace,
            onConfirmReplace = importViewModel::confirmReplace,
            onDismissReplaceConfirmation = importViewModel::dismissReplaceConfirmation,
            onOpenFinca = { id ->
                importViewModel.cancel()
                onOpenFinca(id)
            },
            onPickAnother = onImport,
            onCancel = importViewModel::cancel,
        ),
    )
}

/**
 * Una finca abierta: barra superior, barra inferior de pestañas y el `NavHost` de la
 * finca. La finca se carga una vez aquí; cada pestaña es un destino con su ruta.
 */
@Composable
private fun FincaDestination(
    projectId: String,
    fincaViewModel: FincaViewModel,
    managementViewModel: ManagementViewModel?,
    locationSource: LocationSource,
    orthoTiles: suspend (projectId: String, productId: String) -> TileSource?,
    productFile: suspend (projectId: String, productId: String) -> java.io.File?,
    viewingModel: (suspend (projectId: String, productId: String, onProgress: (Int, Int) -> Unit) -> ViewingModel?)?,
    exportPackage: (suspend (projectId: String, destination: android.net.Uri) -> String?)?,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val showMessage: (String) -> Unit = { message ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }
    LaunchedEffect(projectId) {
        fincaViewModel.open(projectId)
        managementViewModel?.open(projectId)
    }
    // «Exportar paquete .glx»: el sistema pregunta dónde guardarlo.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val export = exportPackage
        if (uri != null && export != null) {
            scope.launch {
                snackbar.showSnackbar("Exportando la finca…")
                val error = export(projectId, uri)
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(error ?: "Paquete .glx guardado. Ábrelo en GeoLux Desktop con «Importar».")
            }
        }
    }
    // Aquí y no en la pantalla: al volver de un informe, Mi finca sigue donde estaba.
    val miFincaListState = rememberRecyclerListState()

    val fincaNav = rememberNavController()
    val backStack by fincaNav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val currentTab = destination.fincaTab() ?: FincaTab.MiFinca
    val showingDetail = destination?.hasRoute<PotreroDetalleRoute>() == true
    val showingReport = destination?.hasRoute<InformeRoute>() == true

    val fincaState by fincaViewModel.state.collectAsStateWithLifecycle()
    val ready = fincaState as? FincaUiState.Ready

    // Visor 3D a pantalla completa: entra solo al girar el teléfono a horizontal y sale al
    // volver a vertical; los botones del visor lo cambian a mano (null = lo decide el giro).
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var fullscreen3d by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var lastLandscape by rememberSaveable { mutableStateOf(landscape) }
    if (lastLandscape != landscape) {
        lastLandscape = landscape
        fullscreen3d = null
    }
    val on3d = destination?.hasRoute<Modelo3DRoute>() == true && ready != null
    val immersive3d = on3d && (fullscreen3d ?: landscape)
    ViewerOrientationEffect(on3d)
    ImmersiveSystemBarsEffect(immersive3d)

    Scaffold(
        topBar = {
            if (!immersive3d) GeoLuxTopBar(
                title = when {
                    showingDetail -> stringResource(R.string.screen_zone_detail)
                    showingReport -> stringResource(R.string.screen_report)
                    else -> stringResource(currentTab.label)
                },
                onBack = if (showingDetail || showingReport) ({ fincaNav.popBackStack() }) else null,
            )
        },
        bottomBar = {
            // Solo con la finca cargada: el NavHost de la finca aún no existe mientras carga.
            if (!immersive3d && ready != null) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                FincaTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { fincaNav.goToTab(tab, reselect = selected) },
                        icon = {
                            Icon(painter = painterResource(if (selected) tab.selectedIcon else tab.icon), contentDescription = null)
                        },
                        label = { Text(stringResource(tab.label)) },
                        modifier = Modifier.testTag("tab_${tab.name}"),
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (val state = fincaState) {
            FincaUiState.Loading -> Box(contentModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            FincaUiState.Error -> PlaceholderScreen(
                title = stringResource(R.string.finca_error_title),
                body = stringResource(R.string.finca_error_body),
                icon = currentTab.icon,
                modifier = contentModifier,
            )
            is FincaUiState.Ready -> FincaNavHost(
                fincaNav = fincaNav,
                data = state.data,
                contentModifier = contentModifier,
                fullModifier = if (immersive3d) Modifier else contentModifier,
                immersive3d = immersive3d,
                onImmersiveChange = { fullscreen3d = it },
                miFincaListState = miFincaListState,
                fincaViewModel = fincaViewModel,
                managementViewModel = managementViewModel,
                locationSource = locationSource,
                orthoTiles = orthoTiles,
                productFile = productFile,
                viewingModel = viewingModel,
                onExport = if (exportPackage != null) {
                    { exportLauncher.launch(exportFileName(state.data.project.name)) }
                } else {
                    null
                },
                onMessage = showMessage,
            )
        }
    }
}

/** Destinos de una finca abierta: una ruta por pestaña y los detalles que cuelgan de ellas. */
@Composable
private fun FincaNavHost(
    fincaNav: NavHostController,
    data: ProjectData,
    contentModifier: Modifier,
    fullModifier: Modifier,
    immersive3d: Boolean,
    onImmersiveChange: (Boolean?) -> Unit,
    miFincaListState: RecyclerListState,
    fincaViewModel: FincaViewModel,
    managementViewModel: ManagementViewModel?,
    locationSource: LocationSource,
    orthoTiles: suspend (projectId: String, productId: String) -> TileSource?,
    productFile: suspend (projectId: String, productId: String) -> java.io.File?,
    viewingModel: (suspend (projectId: String, productId: String, onProgress: (Int, Int) -> Unit) -> ViewingModel?)?,
    onExport: (() -> Unit)?,
    onMessage: (String) -> Unit,
) {
    val projectId = data.project.id
    NavHost(
        navController = fincaNav,
        startDestination = MiFincaRoute,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable<MiFincaRoute> {
            MiFincaScreen(
                data = data,
                onShowZones = { fincaNav.goToTab(FincaTab.Potreros) },
                onExploreMap = { fincaNav.goToTab(FincaTab.Mapa) },
                onOpenReport = { fincaNav.navigate(InformeRoute(it)) },
                modifier = contentModifier,
                listState = miFincaListState,
                onExport = onExport,
            )
        }
        composable<InformeRoute> { entry ->
            val report = data.products.firstOrNull { it.id == entry.toRoute<InformeRoute>().reportId }
            if (report != null) {
                ReportScreen(report = report, productFile = { productFile(projectId, it) }, modifier = contentModifier)
            } else {
                // Informe que ya no está (finca reemplazada): se vuelve a Mi finca.
                LaunchedEffect(Unit) { fincaNav.popBackStack() }
            }
        }
        composable<MapaRoute> { entry ->
            // El encuadre de «Ver en el mapa» se hace una sola vez, aunque se gire el teléfono.
            var focusConsumed by rememberSaveable(entry.id) { mutableStateOf(false) }
            MapaScreen(
                data = data,
                locationSource = locationSource,
                orthoTiles = { productId -> orthoTiles(projectId, productId) },
                onOpenZone = { zoneId -> fincaNav.openZone(zoneId) },
                focusZoneId = entry.toRoute<MapaRoute>().focusZoneId.takeUnless { focusConsumed },
                onFocusConsumed = { focusConsumed = true },
                onEdit = { change -> fincaViewModel.edit(change) },
                modifier = contentModifier,
                onCreateActivity = managementViewModel?.let { { zoneId -> fincaNav.createActivity(zoneId = zoneId) } },
            )
        }
        composable<PotrerosRoute> {
            PotrerosScreen(zones = data.zones, onZoneClick = { fincaNav.navigate(PotreroDetalleRoute(it)) }, modifier = contentModifier)
        }
        composable<PotreroDetalleRoute> { entry ->
            val zone = data.zones.firstOrNull { it.id == entry.toRoute<PotreroDetalleRoute>().zoneId }
            if (zone != null) {
                PotreroDetailScreen(
                    data = data,
                    zone = zone,
                    onShowOnMap = { fincaNav.showOnMap(zone.id) },
                    modifier = contentModifier,
                    onCreateActivity = managementViewModel?.let { { fincaNav.createActivity(zoneId = zone.id) } },
                )
            } else {
                // Potrero eliminado desde el mapa: se vuelve a la lista.
                LaunchedEffect(Unit) { fincaNav.popBackStack() }
            }
        }
        composable<GanadoRoute> {
            if (managementViewModel != null) {
                val managementState by managementViewModel.state.collectAsStateWithLifecycle()
                GanadoScreen(
                    project = data,
                    state = managementState,
                    viewModel = managementViewModel,
                    onMessage = onMessage,
                    onCreateActivity = { animalId -> fincaNav.createActivity(animalId = animalId) },
                    modifier = contentModifier,
                )
            } else {
                PlaceholderScreen(
                    title = stringResource(R.string.tab_ganado),
                    body = "El software ganadero no está disponible en esta versión.",
                    icon = FincaTab.Ganado.icon,
                    modifier = contentModifier,
                )
            }
        }
        composable<GestionRoute> { entry ->
            if (managementViewModel != null) {
                val managementState by managementViewModel.state.collectAsStateWithLifecycle()
                val route = entry.toRoute<GestionRoute>()
                // La actividad ya ligada a un potrero o animal se ofrece una sola vez.
                var prefillConsumed by rememberSaveable(entry.id) { mutableStateOf(false) }
                GestionScreen(
                    project = data,
                    state = managementState,
                    viewModel = managementViewModel,
                    onMessage = onMessage,
                    prefillZoneId = route.zoneId.takeUnless { prefillConsumed },
                    prefillAnimalId = route.animalId.takeUnless { prefillConsumed },
                    onPrefillConsumed = { prefillConsumed = true },
                    modifier = contentModifier,
                )
            } else {
                PlaceholderScreen(
                    title = stringResource(R.string.tab_gestion),
                    body = "La gestión de la finca no está disponible en esta versión.",
                    icon = FincaTab.Gestion.icon,
                    modifier = contentModifier,
                )
            }
        }
        composable<Modelo3DRoute> {
            Model3dScreen(
                data = data,
                productFile = { productId -> productFile(projectId, productId) },
                modifier = fullModifier,
                immersive = immersive3d,
                onImmersiveChange = onImmersiveChange,
                viewingModel = viewingModel?.let { prepare -> { productId, onProgress -> prepare(projectId, productId, onProgress) } },
            )
        }
    }
}

/**
 * Abre una pestaña como la barra inferior estándar de navigation-compose: guarda el
 * estado de la pestaña que se deja y recupera el de la que se abre. Tocar la pestaña
 * en la que ya se está ([reselect]) vuelve a su raíz (de un detalle a la lista).
 */
internal fun NavHostController.goToTab(tab: FincaTab, reselect: Boolean = false) {
    if (reselect) {
        when (tab) {
            FincaTab.Potreros -> popBackStack<PotrerosRoute>(inclusive = false)
            FincaTab.MiFinca -> popBackStack<MiFincaRoute>(inclusive = false)
            else -> Unit
        }
        return
    }
    navigate(tab.route()) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** «Ver en el mapa» desde un potrero: el Mapa lo elige y lo encuadra. */
private fun NavHostController.showOnMap(zoneId: String) {
    navigate(MapaRoute(focusZoneId = zoneId)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

/** «Ver detalle del potrero» desde el mapa: la lista de Potreros y encima su detalle. */
private fun NavHostController.openZone(zoneId: String) {
    navigate(PotrerosRoute) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
    navigate(PotreroDetalleRoute(zoneId))
}

/** «Crear actividad» desde un potrero o un animal: Gestión con la actividad ya ligada. */
private fun NavHostController.createActivity(zoneId: String? = null, animalId: String? = null) {
    navigate(GestionRoute(zoneId = zoneId, animalId = animalId)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

/** Nombre propuesto para el paquete exportado: «La Giralda 2026-11-10 1430.glx», como los de Desktop. */
internal fun exportFileName(farmName: String, now: java.time.LocalDateTime = java.time.LocalDateTime.now()): String {
    val clean = farmName.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "Finca" }
    return "$clean ${now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm"))}.glx"
}
