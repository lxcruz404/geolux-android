package me.luiscruzandresgomezrodolfohernandez.geolux.ui.components

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

// Listados de la app con RecyclerView (requisito académico): un Adapter con
// DiffUtil y un ViewHolder por fila. Cada fila es un ComposeView, así el diseño
// (ListRow, GeoLuxCard…) es el mismo de Compose. El DSL imita a LazyColumn
// (item / items con key) para que cada pantalla cambie lo mínimo.

/** Una fila de la lista: clave estable, contenido y, si se puede tocar, su acción. */
class RecyclerRow(
    val key: Any,
    val onClick: (() -> Unit)?,
    val content: @Composable () -> Unit,
)

/** Construye las filas en orden, como `LazyListScope`: cabeceras con [item] y datos con [items]. */
class RecyclerListScope internal constructor() {
    internal val rows = mutableListOf<RecyclerRow>()
    private var section = 0

    /** Una fila suelta (título, filtros, botón, mensaje de lista vacía…). */
    fun item(key: Any? = null, content: @Composable () -> Unit) {
        rows += RecyclerRow(listOf(section++, key), null, content)
    }

    /**
     * Una fila por elemento de [items]. [key] da la identidad estable del dato (DiffUtil
     * reconoce el mismo elemento aunque cambie de posición) y [onItemClick] es el
     * evento al tocar la fila.
     */
    fun <T> items(
        items: List<T>,
        key: ((T) -> Any)? = null,
        onItemClick: ((T) -> Unit)? = null,
        itemContent: @Composable (T) -> Unit,
    ) {
        val s = section++
        items.forEachIndexed { index, item ->
            rows += RecyclerRow(
                key = listOf(s, key?.invoke(item) ?: index),
                onClick = onItemClick?.let { click -> { click(item) } },
                content = { itemContent(item) },
            )
        }
    }

    /** [count] filas numeradas (por ejemplo, las páginas de un PDF). */
    fun items(count: Int, itemContent: @Composable (Int) -> Unit) = items((0 until count).toList(), itemContent = itemContent)
}

/** Las filas cambian de contenido al recomponer; DiffUtil solo decide cuáles son la misma por su clave. */
private object RowDiff : DiffUtil.ItemCallback<RecyclerRow>() {
    override fun areItemsTheSame(oldItem: RecyclerRow, newItem: RecyclerRow) = oldItem.key == newItem.key
    override fun areContentsTheSame(oldItem: RecyclerRow, newItem: RecyclerRow) = false
}

/** ViewHolder: guarda el ComposeView de una fila y le pone el contenido del dato al enlazarla. */
class ComposeViewHolder(private val view: ComposeView) : RecyclerView.ViewHolder(view) {
    fun bind(row: RecyclerRow) {
        view.setContent {
            // Column: como en LazyColumn, si la fila emite varios elementos van uno debajo de otro
            // (la raíz de un ComposeView los apilaría en la misma posición).
            val click = row.onClick
            Column(if (click == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clickable(onClick = click)) { row.content() }
        }
    }
}

/**
 * Adapter de todos los listados. `onCreateViewHolder` crea el ComposeView de una fila
 * (hereda tema y demás valores de la pantalla por [compositionParent]); `onBindViewHolder` le
 * enlaza el dato de esa posición.
 */
class ComposeListAdapter(private val compositionParent: CompositionContext) : ListAdapter<RecyclerRow, ComposeViewHolder>(
    // ponytail: DiffUtil en el hilo principal (listas de cientos de filas); a un hilo aparte si llegan a miles.
    // Así la lista queda al día en el mismo cuadro que la recomposición, sin filas viejas a la vista.
    AsyncDifferConfig.Builder(RowDiff).setBackgroundThreadExecutor { it.run() }.build(),
) {
    override fun onCreateViewHolder(parentView: ViewGroup, viewType: Int): ComposeViewHolder {
        val view = ComposeView(parentView.context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setParentCompositionContext(compositionParent)
        }
        return ComposeViewHolder(view)
    }

    override fun onBindViewHolder(holder: ComposeViewHolder, position: Int) = holder.bind(getItem(position))
}

/** Separación entre filas (el `verticalArrangement = spacedBy(…)` de LazyColumn). */
private class SpacingDecoration(private val spacingPx: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.getChildAdapterPosition(view) > 0) outRect.top = spacingPx
    }
}

/**
 * Posición de la lista, guardable (sobrevive a cambiar de pestaña o girar). [scrollBy]
 * desplaza la lista desde fuera, por ejemplo al llegar al borde de una página acercada.
 */
@Stable
class RecyclerListState(position: Int = 0, offset: Int = 0) {
    var firstVisiblePosition = position
        private set
    var firstVisibleOffset = offset
        private set
    internal var view: RecyclerView? = null
    internal var restored = false

    fun scrollBy(dyPx: Float) {
        view?.scrollBy(0, dyPx.roundToInt())
    }

    internal fun remember(recycler: RecyclerView) {
        val manager = recycler.layoutManager as? LinearLayoutManager ?: return
        val first = manager.findFirstVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return
        firstVisiblePosition = first
        firstVisibleOffset = (manager.findViewByPosition(first)?.top ?: 0) - recycler.paddingTop
    }

    companion object {
        val Saver = listSaver<RecyclerListState, Int>(
            save = { listOf(it.firstVisiblePosition, it.firstVisibleOffset) },
            restore = { RecyclerListState(it[0], it[1]) },
        )
    }
}

@Composable
fun rememberRecyclerListState(): RecyclerListState = rememberSaveable(saver = RecyclerListState.Saver) { RecyclerListState() }

/**
 * Lista con RecyclerView para pantallas Compose: [content] declara las filas como en
 * LazyColumn y cada recomposición se las entrega al Adapter con `submitList`.
 */
@Composable
fun RecyclerList(
    modifier: Modifier = Modifier,
    state: RecyclerListState = rememberRecyclerListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: RecyclerListScope.() -> Unit,
) {
    val parent = rememberCompositionContext()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val spacing = with(density) { verticalArrangement.spacing.roundToPx() }
    val padding = with(density) {
        listOf(
            contentPadding.calculateLeftPadding(direction).roundToPx(),
            contentPadding.calculateTopPadding().roundToPx(),
            contentPadding.calculateRightPadding(direction).roundToPx(),
            contentPadding.calculateBottomPadding().roundToPx(),
        )
    }
    val rows = RecyclerListScope().apply(content).rows.toList()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = ComposeListAdapter(parent)
                clipToPadding = false
                setPadding(padding[0], padding[1], padding[2], padding[3])
                // Sin animaciones: cada recomposición vuelve a enlazar las filas visibles y la lista
                // debe mostrar el estado nuevo al instante (como LazyColumn).
                itemAnimator = null
                if (spacing > 0) addItemDecoration(SpacingDecoration(spacing))
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = state.remember(recyclerView)
                })
                state.view = this
                state.restored = false
            }
        },
        update = { recycler ->
            (recycler.adapter as ComposeListAdapter).submitList(rows) {
                // Al volver a la pantalla, la lista sigue donde estaba.
                if (!state.restored) {
                    state.restored = true
                    (recycler.layoutManager as LinearLayoutManager)
                        .scrollToPositionWithOffset(state.firstVisiblePosition, state.firstVisibleOffset)
                }
            }
        },
        onRelease = { recycler -> if (state.view === recycler) state.view = null },
    )
}
