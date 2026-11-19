package me.luiscruzandresgomezrodolfohernandez.geolux.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** RecyclerList: Adapter + ViewHolder con filas Compose, clic por elemento y DiffUtil. */
@RunWith(AndroidJUnit4::class)
class RecyclerListTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cabeceraFilasClicYActualizacionSinDuplicar() {
        val data = mutableStateOf(listOf(1, 2, 3))
        val clicked = mutableListOf<Int>()
        compose.setContent {
            RecyclerList(Modifier.fillMaxSize()) {
                item { Text("Cabecera") }
                items(data.value, key = { it }, onItemClick = { clicked += it }) { Text("Fila $it") }
            }
        }
        compose.onNodeWithText("Cabecera").assertIsDisplayed()
        // La cabecera va antes que la primera fila.
        val top = { text: String -> compose.onNodeWithText(text).fetchSemanticsNode().boundsInWindow.top }
        assertTrue(top("Cabecera") < top("Fila 1"))

        compose.onNodeWithText("Fila 2").performClick()
        assertEquals(listOf(2), clicked)

        // submitList con datos nuevos: aparece la fila nueva, se quita la borrada y nada se repite.
        compose.runOnIdle { data.value = listOf(3, 1, 4) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Fila 4").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Fila 2").assertCountEquals(0)
        listOf("Fila 1", "Fila 3", "Fila 4", "Cabecera").forEach { compose.onAllNodesWithText(it).assertCountEquals(1) }
    }
}
