package me.luiscruzandresgomezrodolfohernandez.geolux.ui.map

import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.luiscruzandresgomezrodolfohernandez.geolux.data.TileSource
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Estilo MapLibre de la finca (DESIGN.md §7): capas, orden de dibujo, mapa base y ortomosaicos. En el dispositivo por org.json. */
@RunWith(AndroidJUnit4::class)
class FarmStyleTest {
    private val layers = farmLayers(FakeProjectRepository.demo) { "#868E96" }

    private fun ids(style: String): List<String> {
        val array = JSONObject(style).getJSONArray("layers")
        return (0 until array.length()).map { array.getJSONObject(it).getString("id") }
    }

    @Test
    fun ordenDeDibujo() {
        val ids = ids(farmStyleJson(layers, MapStyleConfig(background = Color.White)))
        assertEquals("fondo", ids.first())
        assertEquals("gps-punto", ids.last())
        // área → potreros → misiones → líneas → elementos → boceto → GPS
        val order = listOf("area-relleno", "potreros-relleno", "misiones-relleno", "lineas-fence", "elementos", "boceto-linea", "gps-precision")
        assertEquals(order, order.sortedBy { ids.indexOf(it) })
        assertTrue(LINE_STYLES.keys.all { "lineas-$it" in ids })
    }

    @Test
    fun mapaBaseDeDesktopDebajoDeTodo() {
        // Sin mapa base: ninguna fuente de Internet.
        assertFalse(JSONObject(farmStyleJson(layers, MapStyleConfig())).getJSONObject("sources").has("base"))
        val esri = JSONObject(farmStyleJson(layers, MapStyleConfig(baseMap = BaseMap.Satellite)))
        val base = esri.getJSONObject("sources").getJSONObject("base")
        // La misma URL que core/base_layers.py de Desktop, con {z}/{y}/{x} y sin la tesela gris de Esri.
        assertEquals(
            "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}?blankTile=false",
            base.getJSONArray("tiles").getString(0),
        )
        assertTrue(base.getString("attribution").startsWith("Imágenes: Esri"))
        assertEquals("mapa-base", ids(esri.toString())[1]) // justo encima del fondo
        val osm = JSONObject(farmStyleJson(layers, MapStyleConfig(baseMap = BaseMap.Standard))).getJSONObject("sources").getJSONObject("base")
        assertEquals("https://tile.openstreetmap.org/{z}/{x}/{y}.png", osm.getJSONArray("tiles").getString(0))
    }

    @Test
    fun cadaOrtomosaicoEsUnaCapaAparteEncimaDelMapaBase() {
        val original = OrthoLayer("orto-original", TileSource("file:///a/{z}/{x}/{y}.png", 14, 18), listOf(-70.2, 6.3, -70.19, 6.31))
        val recortado = OrthoLayer("orto-recorte", TileSource("file:///b/{z}/{x}/{y}.png", 15, 19), listOf(-70.198, 6.302, -70.195, 6.305))
        val style = JSONObject(farmStyleJson(layers, MapStyleConfig(BaseMap.Satellite, listOf(original, recortado))))
        val ids = ids(style.toString())
        assertEquals(listOf("fondo", "mapa-base", "ortomosaico-orto-original", "ortomosaico-orto-recorte", "ortomosaico-limites"), ids.take(5))
        val src = style.getJSONObject("sources").getJSONObject("ortho-orto-recorte")
        assertEquals("file:///b/{z}/{x}/{y}.png", src.getJSONArray("tiles").getString(0))
        assertEquals(15, src.getInt("minzoom"))
        assertEquals(19, src.getInt("maxzoom"))
        // Los límites: un rectángulo por ortomosaico con su extensión exacta [w, s, e, n].
        val limits = style.getJSONObject("sources").getJSONObject("ortomosaicos-limites").getJSONObject("data").getJSONArray("features")
        assertEquals(2, limits.length())
        val ring = limits.getJSONObject(1).getJSONObject("geometry").getJSONArray("coordinates").getJSONArray(0)
        assertEquals(-70.198, ring.getJSONArray(0).getDouble(0), 0.0)
        assertEquals(6.305, ring.getJSONArray(2).getDouble(1), 0.0)
    }

    @Test
    fun lasCapasDeToqueSePuedenConsultar() {
        // Con opacidad 0, MapLibre no devolvía la línea en queryRenderedFeatures: tocar una
        // cerca nunca la elegía. Tienen que ser casi invisibles, pero no del todo.
        val layers = JSONObject(farmStyleJson(layers, MapStyleConfig())).getJSONArray("layers")
        val touch = (0 until layers.length()).map { layers.getJSONObject(it) }.filter { it.getString("id") in setOf("lineas-toque", "area-toque") }
        assertEquals(2, touch.size)
        touch.forEach { assertTrue(it.getString("id"), it.getJSONObject("paint").getDouble("line-opacity") > 0.0) }
        // Y las líneas llevan un contorno claro para leerse sobre el satélite.
        assertTrue("lineas-contorno" in ids(farmStyleJson(this.layers, MapStyleConfig())))
    }
}
