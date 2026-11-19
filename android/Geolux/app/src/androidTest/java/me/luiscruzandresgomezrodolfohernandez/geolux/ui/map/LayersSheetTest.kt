package me.luiscruzandresgomezrodolfohernandez.geolux.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Hoja «Capas del mapa» (04e) con finca-demo. */
@RunWith(AndroidJUnit4::class)
class LayersSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val demo = FakeProjectRepository.demo

    private fun orthos(ready: Boolean) = demo.products.filter { it.kind == "orthomosaic" }.map { OrthoAvailability(it, ready) }

    @Test
    fun capasSeApaganYEncienden() {
        var visible by mutableStateOf(MapLayerGroup.entries.toSet())
        compose.setContent {
            GeoLuxTheme {
                LayersSheetContent(demo, orthos(true), visible = visible, onToggle = { group, on ->
                    visible = if (on) visible + group else visible - group
                })
            }
        }
        compose.onNodeWithText("2 potreros").assertIsDisplayed()
        compose.onNodeWithText("3 elementos").assertIsDisplayed()
        compose.onNodeWithTag("capa_Zones").assertIsOn().performClick()
        compose.onNodeWithTag("capa_Zones").assertIsOff()
        assertFalse(MapLayerGroup.Zones in visible)
        compose.onNodeWithTag("capa_Zones").performClick()
        assertTrue(MapLayerGroup.Zones in visible)
        assertEquals(MapLayerGroup.entries.toSet(), visible)
    }

    @Test
    fun sinOrtomosaicoSuInterruptorEstaDesactivado() {
        compose.setContent {
            GeoLuxTheme { LayersSheetContent(demo, emptyList(), visible = MapLayerGroup.entries.toSet(), onToggle = { _, _ -> }) }
        }
        compose.onNodeWithTag("capa_Ortho").assertIsNotEnabled().assertIsOff()
    }

    @Test
    fun cadaGrupoTieneSusCapasDelEstilo() {
        // Si cambian los nombres de capa de farmStyleJson, esta prueba lo avisa.
        val style = org.json.JSONObject(farmStyleJson(farmLayers(demo) { "#000000" }))
        val ids = (0 until style.getJSONArray("layers").length()).map { style.getJSONArray("layers").getJSONObject(it).getString("id") }.toSet()
        MapLayerGroup.entries.filter { it != MapLayerGroup.Ortho }.forEach { group ->
            group.layerIds().forEach { id -> assertTrue("Falta la capa $id del grupo $group", id in ids) }
        }
    }

    @Test
    fun seEligeElMapaBaseYCadaOrtomosaicoSeApagaAparte() {
        var base by mutableStateOf(BaseMap.Standard)
        var hidden by mutableStateOf(emptySet<String>())
        val id = demo.products.first { it.kind == "orthomosaic" }.id
        compose.setContent {
            GeoLuxTheme {
                LayersSheetContent(
                    demo, orthos(true), visible = MapLayerGroup.entries.toSet(), onToggle = { _, _ -> },
                    baseMap = base, onBaseMap = { base = it },
                    hiddenOrthos = hidden, onToggleOrtho = { o, on -> hidden = if (on) hidden - o else hidden + o },
                )
            }
        }
        compose.onNodeWithText("Satélite (Esri)").assertIsDisplayed()
        compose.onNodeWithTag("base_satellite_esri").performClick()
        assertEquals(BaseMap.Satellite, base)
        compose.onNodeWithTag("orto_$id").assertIsOn().performClick()
        assertEquals(setOf(id), hidden)
        compose.onNodeWithTag("capa_OrthoBounds").assertIsOn()
    }
}
