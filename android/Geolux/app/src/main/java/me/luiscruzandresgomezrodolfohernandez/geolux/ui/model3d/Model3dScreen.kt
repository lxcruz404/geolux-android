package me.luiscruzandresgomezrodolfohernandez.geolux.ui.model3d

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import io.github.sceneview.rememberView
import io.github.sceneview.rememberRenderer
import me.luiscruzandresgomezrodolfohernandez.geolux.data.ViewingModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberEngine
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberModelLoader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Product
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog.ChipFamily
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.components.StatusChip
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.format.formatDate
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.screens.PlaceholderScreen
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxDimens

/**
 * Pestaña 3D (AND-R6, DESIGN.md §9 pantalla 07): el GLB del modelo 3D de la finca
 * con SceneView. El origen del producto se ve siempre: un modelo sintético nunca
 * pasa por real (11_3D_SPEC). La cámara gira sobre el piso y nunca lo atraviesa
 * ([TerrainCamera]); se maneja con gestos o con la botonera ([TerrainControls]).
 */
@Composable
fun Model3dScreen(
    data: ProjectData,
    /** Archivo local del producto (`ProjectRepository.productFile`): la copia de Desktop. */
    productFile: suspend (productId: String) -> File?,
    modifier: Modifier = Modifier,
    /**
     * El modelo con las texturas a la mejor calidad que admite este teléfono
     * (`ProjectRepository.viewingModel`); recibe dónde avisar del avance. null si no
     * se puede preparar: entonces se usa [productFile].
     */
    viewingModel: (suspend (productId: String, onProgress: (Int, Int) -> Unit) -> ViewingModel?)? = null,
    /**
     * Pantalla completa: el visor ocupa todo, la ficha pasa a un panel que se abre con
     * «Información» y la botonera se reduce. Lo decide quien contiene la pestaña (al girar a
     * horizontal entra solo; ver `GeoLuxApp`), porque también oculta las barras de la app.
     */
    immersive: Boolean = false,
    /** Pedir entrar (true) o salir (false) de la pantalla completa; null = sin botón. */
    onImmersiveChange: ((Boolean) -> Unit)? = null,
) {
    val model = remember(data) { data.products.firstOrNull { it.kind == "model3d" } }
    val state by produceState<ModelState>(ModelState.Loading, model) {
        value = when (val m = model) {
            null -> ModelState.None
            else -> try {
                // Primero el del motor preparado para este teléfono; si no, la copia de Desktop.
                val prepared = viewingModel?.let { prepare ->
                    runCatching { prepare(m.id) { done, total -> value = ModelState.Preparing(done, total) } }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
                }
                prepared?.let { ModelState.Ready(it.file, it.textureCap) }
                    ?: productFile(m.id)?.let { ModelState.Ready(it, null) }
                    ?: ModelState.Unavailable
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ModelState.Unavailable
            }
        }
    }

    val current = state
    if (model == null || current !is ModelState.Ready) {
        PlaceholderScreen(
            title = stringResource(R.string.tab_3d),
            body = when (current) {
                is ModelState.Preparing -> stringResource(R.string.model3d_preparing, current.done, current.total)
                ModelState.Loading -> stringResource(R.string.model3d_loading)
                ModelState.Unavailable -> stringResource(R.string.model3d_unavailable)
                else -> stringResource(R.string.model3d_none)
            },
            icon = R.drawable.ic_symbol_view_in_ar,
            modifier = modifier,
        )
        return
    }

    // La cámara vive aquí y el visor ocupa siempre el mismo sitio de la composición: entrar o
    // salir de la pantalla completa, o girar el teléfono, no recarga el modelo ni la reinicia.
    val camera = remember(current.file) { TerrainCamera() }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    // En horizontal con las barras de la app, la ficha se abre con «Información» como en
    // pantalla completa: abajo dejaría al modelo una franja.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val panelMode = immersive || landscape
    if (immersive && onImmersiveChange != null) BackHandler { onImmersiveChange(false) }
    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            ModelViewer(current.file, camera, Modifier.fillMaxSize().testTag("visor_3d"))
            if (panelMode) {
                // Arriba a la izquierda, discreto: de qué modelo se trata y si es real.
                Row(
                    Modifier.align(Alignment.TopStart).padding(8.dp).widthIn(max = 360.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusChip(
                        text = stringResource(if (model.isSynthetic) R.string.chip_synthetic else R.string.chip_real_model),
                        family = if (model.isSynthetic) ChipFamily.Amber else ChipFamily.Green,
                    )
                    Text(model.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OverlayButton(R.drawable.ic_info, stringResource(R.string.model3d_info), "3d_info") { showInfo = !showInfo }
                    if (onImmersiveChange != null && immersive) {
                        OverlayButton(R.drawable.ic_fullscreen_exit, stringResource(R.string.model3d_exit_fullscreen), "3d_salir_pantalla_completa") {
                            showInfo = false
                            onImmersiveChange(false)
                        }
                    } else if (onImmersiveChange != null) {
                        OverlayButton(R.drawable.ic_fullscreen, stringResource(R.string.model3d_fullscreen), "3d_pantalla_completa") {
                            onImmersiveChange(true)
                        }
                    }
                }
                TerrainControls(camera.view, Modifier.align(Alignment.BottomEnd).padding(8.dp), compact = true)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showInfo,
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd).padding(start = 8.dp, top = 60.dp, bottom = 8.dp, end = 8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 3.dp,
                        shadowElevation = 6.dp,
                        modifier = Modifier.widthIn(max = 340.dp).testTag("3d_panel_info"),
                    ) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.model3d_info), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = { showInfo = false }, modifier = Modifier.testTag("3d_cerrar_info")) {
                                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.model3d_info_close))
                                }
                            }
                            ModelFicha(model, current.textureCap)
                            Text(
                                stringResource(R.string.model3d_help_short),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            )
                        }
                    }
                }
            } else {
                Column(
                    Modifier.align(Alignment.TopStart).padding(GeoLuxDimens.screenMargin),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusChip(
                        text = stringResource(if (model.isSynthetic) R.string.chip_synthetic else R.string.chip_real_model),
                        family = if (model.isSynthetic) ChipFamily.Amber else ChipFamily.Green,
                    )
                    Text(
                        text = stringResource(R.string.model3d_help),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onImmersiveChange != null) {
                    OverlayButton(
                        R.drawable.ic_fullscreen, stringResource(R.string.model3d_fullscreen), "3d_pantalla_completa",
                        Modifier.align(Alignment.TopEnd).padding(GeoLuxDimens.screenMargin),
                    ) { onImmersiveChange(true) }
                }
                TerrainControls(camera.view, Modifier.align(Alignment.BottomEnd).padding(GeoLuxDimens.screenMargin))
            }
        }
        if (!panelMode) ModelFicha(model, current.textureCap)
    }
}

/** Botón redondo pequeño sobre el visor (información, pantalla completa). */
@Composable
private fun OverlayButton(icon: Int, description: String, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shadowElevation = 2.dp,
        modifier = modifier.size(44.dp).testTag(tag).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ModelViewer(file: File, camera: TerrainCamera, modifier: Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    // Las texturas del GLB se decodifican en hilos de gltfio (asyncBeginLoad). Si se sale del
    // visor antes de que terminen, SceneView destruye el ResourceLoader con trabajos en curso y
    // Filament aborta el proceso. Este efecto se declara después de rememberModelLoader, así que
    // se libera antes: cancela la carga pendiente y deja que SceneView destruya lo demás.
    androidx.compose.runtime.DisposableEffect(modelLoader) {
        onDispose { runCatching { modelLoader.resourceLoader.asyncCancelLoad() } }
    }
    // El modelo se normaliza a 1 unidad (sirve igual para un GLB real de cientos de metros)
    // y SceneView deja su centro en el origen (autoCenterContent). El piso es el plano
    // horizontal que pasa por ese centro; el terreno se pone sobre él ([groundRotation]).
    // Render fiel (como el visor de Desktop): se aplica después del primer fotograma,
    // porque SceneView pone su preset por defecto al arrancar.
    val view = rememberView(engine)
    val renderer = rememberRenderer(engine)
    val background = MaterialTheme.colorScheme.surfaceVariant
    // El motor puede destruirse mientras se espera ese fotograma (salir del visor enseguida):
    // [alive] lo apaga el onDispose de abajo, que corre antes de que SceneView destruya el motor.
    val alive = remember(engine) { java.util.concurrent.atomic.AtomicBoolean(true) }
    androidx.compose.runtime.DisposableEffect(engine) { onDispose { alive.set(false) } }
    LaunchedEffect(view, background) {
        withFrameNanos { }
        if (alive.get()) view.applyFaithfulRendering(engine)
    }
    // Superficie opaca (ver abajo): el fondo neutro es un cielo de color liso. SceneView
    // vuelve a poner el de su entorno en cada recomposición, así que se repone por fotograma.
    val skybox = remember(engine, background) {
        com.google.android.filament.Skybox.Builder()
            .color(linear(background.red).toFloat(), linear(background.green).toFloat(), linear(background.blue).toFloat(), 1f)
            .build(engine)
    }
    androidx.compose.runtime.DisposableEffect(skybox) { onDispose { engine.destroySkybox(skybox) } }
    // Se lee el GLB en E/S y Filament lo crea en el hilo principal (como pide ModelLoader).
    // (rememberModelInstance con «file:/…» de File.toURI() no lo carga.) Un GLB dañado o
    // que no cabe en memoria no cierra la app: se avisa.
    var failed by remember(file) { mutableStateOf(false) }
    val instance by produceState<ModelInstance?>(null, modelLoader, file) {
        val buffer = withContext(Dispatchers.IO) {
            try {
                readDirect(file)
            } catch (e: IOException) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
        }
        // Filament crea el modelo en el hilo principal, nunca en uno de fondo.
        value = buffer?.let { withContext(Dispatchers.Main.immediate) { runCatching { modelLoader.createModelInstance(it) }.getOrNull() } }
        failed = value == null
    }
    if (failed) {
        Text(
            text = stringResource(R.string.model3d_load_failed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(GeoLuxDimens.screenMargin).testTag("modelo_no_cargado"),
        )
        return
    }
    // SurfaceView opaca, detrás de la ventana: los botones de Compose quedan por encima y,
    // al cambiar de tamaño (girar el teléfono, pantalla completa), Filament solo redimensiona.
    // Con TextureSurface SceneView 4.18 creaba una cadena nueva sobre la misma superficie
    // antes de soltar la anterior (EGL_BAD_ALLOC) y el visor se quedaba congelado.
    Box(modifier) {
        SceneView(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            surfaceType = SurfaceType.Surface,
            isOpaque = true,
            renderer = renderer,
            engine = engine,
            modelLoader = modelLoader,
            view = view,
            cameraManipulator = camera,
            onFrame = {
                view.scene?.let { if (it.skybox !== skybox) it.skybox = skybox }
            },
        ) {
            instance?.let {
                ModelNode(modelInstance = it, scaleToUnits = 1f, rotation = groundRotation(it.asset.boundingBox.halfExtent))
            }
        }
    }
}

/** De sRGB (lo que da el tema) a lineal (lo que espera Filament en el color de limpiado). */
private fun linear(c: Float): Double {
    val v = c.toDouble()
    return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
}

/**
 * Giro que deja el terreno sobre el piso (el plano XZ de SceneView, con Y hacia arriba).
 * Los modelos del motor vienen con los ejes este/norte/arriba (SPEC §6: Z hacia arriba) y
 * de frente se veían como una lámina de pie; el fixture antiguo `finca-demo` ya trae Y
 * hacia arriba. Un terreno siempre es más plano que ancho, así que el eje más delgado de
 * los dos es el de arriba. Con Z arriba se gira −90° en X: arriba pasa a +Y y el norte a −Z.
 */
internal fun groundRotation(halfExtent: FloatArray): Rotation =
    if (halfExtent[2] < halfExtent[1]) Rotation(x = -90f) else Rotation(x = 0f)

/**
 * El GLB entero en un búfer nativo, sin pasar por el heap de Java: leerlo antes a un
 * ByteArray ocupaba su tamaño en el heap, y el modelo del motor (151 MB) superaba el
 * límite de la app (OutOfMemoryError con 192 MB).
 */
internal fun readDirect(file: File): ByteBuffer = FileInputStream(file).channel.use { channel ->
    val size = channel.size()
    if (size > Int.MAX_VALUE) throw IOException("Modelo demasiado grande: $size bytes")
    ByteBuffer.allocateDirect(size.toInt()).also { buffer ->
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) throw IOException("El modelo terminó antes de lo esperado.")
        }
        buffer.rewind()
    }
}

@Composable
private fun ModelFicha(model: Product, textureCap: Int?) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(GeoLuxDimens.screenMargin), verticalArrangement = Arrangement.spacedBy(GeoLuxDimens.cardSpacing)) {
            Text(model.name, style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth()) {
                Field(stringResource(R.string.model3d_date), formatDate(model.createdAt), Modifier.weight(1f))
                Field(
                    stringResource(R.string.model3d_origin),
                    stringResource(if (model.isSynthetic) R.string.origin_synthetic else R.string.origin_real),
                    Modifier.weight(1f),
                )
            }
            // Qué calidad de textura se está viendo: la del teléfono o la copia de Desktop.
            Field(
                stringResource(R.string.model3d_textures),
                if (textureCap != null) stringResource(R.string.model3d_textures_device, textureCap) else stringResource(R.string.model3d_textures_mobile),
                Modifier.fillMaxWidth().testTag("calidad_texturas"),
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** Estado del modelo 3D en la pestaña. */
private sealed interface ModelState {
    data object Loading : ModelState

    /** Preparando las texturas del modelo del motor para este teléfono (la primera vez). */
    data class Preparing(val done: Int, val total: Int) : ModelState

    /** [textureCap]: tamaño máximo de textura preparado aquí; null = la copia de Desktop. */
    data class Ready(val file: File, val textureCap: Int?) : ModelState

    /** La finca no trae modelo 3D. */
    data object None : ModelState

    /** La finca lo trae pero no se encontró el archivo. */
    data object Unavailable : ModelState
}
