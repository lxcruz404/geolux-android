package me.luiscruzandresgomezrodolfohernandez.geolux.ui.model3d

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pestaña 3D (AND-R6, AND-R8): estados y visor con un GLB mínimo válido. */
@RunWith(AndroidJUnit4::class)
class Model3dScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val demo = FakeProjectRepository.demo

    private fun show(data: ProjectData, file: File?) =
        compose.setContent { GeoLuxTheme { Model3dScreen(data, productFile = { file }) } }

    @Test
    fun sinModeloLoDice() {
        show(demo.copy(products = demo.products.filter { it.kind != "model3d" }), null)
        compose.onNodeWithText("Esta finca no incluye modelo 3D.").assertIsDisplayed()
    }

    @Test
    fun modeloSinArchivoAvisa() {
        show(demo, null)
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("No se pudo abrir", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun modeloSinteticoSeMuestraMarcado() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val glb = File(context.cacheDir, "triangulo.glb").apply { writeBytes(triangleGlb()) }
        // SceneView pide un fotograma tras otro: con el reloj automático Compose nunca queda
        // ocioso. Se pausa y se avanza a mano.
        compose.mainClock.autoAdvance = false
        show(demo, glb)
        repeat(20) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag("visor_3d").assertIsDisplayed()
        compose.onNodeWithText("Sintético · datos de prueba").assertIsDisplayed()
        compose.onNodeWithText("Modelo 3D sintético").assertIsDisplayed()
        // La botonera va sobre el visor, con la ayuda de los gestos.
        compose.onNodeWithTag("3d_avanzar").assertIsDisplayed()
        compose.onNodeWithTag("3d_recentrar").assertIsDisplayed()
        compose.onNodeWithText("Dos dedos: mover", substring = true).assertIsDisplayed()
    }

    @Test
    fun pantallaCompletaSinFichaConPanelDeInformacionYSalida() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Un archivo que no es GLB: el visor avisa sin cargar nada en Filament, y la botonera,
        // la información y la salida se prueban sin un motor 3D de por medio.
        val glb = File(context.cacheDir, "no-es-glb.glb").apply { writeText("no es un modelo") }
        var immersive by androidx.compose.runtime.mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            GeoLuxTheme { Model3dScreen(demo, productFile = { glb }, immersive = immersive, onImmersiveChange = { immersive = it }) }
        }
        repeat(20) { compose.mainClock.advanceTimeByFrame() }
        // A pantalla completa: sin la ficha ni las flechas; botonera compacta, información y salida.
        compose.onNodeWithTag("calidad_texturas").assertDoesNotExist()
        compose.onNodeWithTag("3d_avanzar").assertDoesNotExist()
        compose.onNodeWithTag("3d_ajustar").assertIsDisplayed()
        compose.onNodeWithTag("3d_recentrar").assertIsDisplayed()
        compose.onNodeWithTag("3d_info").performClick()
        repeat(30) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag("3d_panel_info").assertIsDisplayed()
        compose.onNodeWithTag("calidad_texturas").assertIsDisplayed()
        compose.onNodeWithTag("3d_cerrar_info").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("3d_panel_info").assertDoesNotExist()
        compose.onNodeWithTag("3d_salir_pantalla_completa").performClick()
        repeat(20) { compose.mainClock.advanceTimeByFrame() }
        assertEquals(false, immersive)
        // Sin pantalla completa (vertical en la prueba): vuelve la ficha y el botón para entrar.
        compose.onNodeWithTag("calidad_texturas").assertIsDisplayed()
        compose.onNodeWithTag("3d_pantalla_completa").assertIsDisplayed()
    }

    @Test
    fun salirYVolverMientrasCargaNoCierraLaApp() {
        // Ciclo de vida de SceneView/gltfio: entrar y salir del visor varias veces, a veces
        // antes de que termine la carga, y volver a entrar. Antes abortaba en ResourceLoader.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val glb = File(context.cacheDir, "triangulo-ciclo.glb").apply { writeBytes(triangleGlb()) }
        var shown by androidx.compose.runtime.mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { GeoLuxTheme { if (shown) Model3dScreen(demo, productFile = { glb }) } }
        repeat(6) { round ->
            repeat(if (round % 2 == 0) 1 else 12) { compose.mainClock.advanceTimeByFrame() }
            shown = false
            repeat(3) { compose.mainClock.advanceTimeByFrame() }
            shown = true
        }
        repeat(30) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag("visor_3d").assertIsDisplayed()
        compose.onNodeWithTag("3d_recentrar").assertIsDisplayed()
    }

    @Test
    fun unModeloMayorQueElHeapSeLeeSinOutOfMemory() {
        // El GLB del motor (151 MB) superaba el heap de la app (192 MB en el emulador)
        // cuando se leía a un ByteArray. Un archivo del 90 % del heap: con readBytes()
        // no cabría junto a lo que ya ocupa la app; en memoria nativa sí.
        val heap = Runtime.getRuntime().maxMemory()
        val size = (heap * 0.9).toLong()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val big = File(context.cacheDir, "modelo-grande.glb")
        try {
            java.io.RandomAccessFile(big, "rw").use { it.setLength(size) }
            val buffer = readDirect(big)
            assertEquals(size, buffer.capacity().toLong())
            assertEquals(0, buffer.position())
            assertTrue(buffer.isDirect)
        } finally {
            big.delete()
        }
    }

    /** GLB de un triángulo (glTF 2.0 binario), como el del fixture finca-demo: este/norte/arriba (Z arriba, SPEC §6). */
    private fun triangleGlb(): ByteArray {
        val bin = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)
        floatArrayOf(0f, 0f, 0f, 100f, 0f, 0f, 0f, 100f, 0f).forEach { bin.putFloat(it) }
        var json = "{\"asset\":{\"version\":\"2.0\"},\"scene\":0,\"scenes\":[{\"nodes\":[0]}],\"nodes\":[{\"mesh\":0}]," +
            "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0}}]}],\"buffers\":[{\"byteLength\":36}]," +
            "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":36}]," +
            "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\",\"min\":[0,0,0],\"max\":[100,100,0]}]}"
        while (json.length % 4 != 0) json += " "
        val out = ByteBuffer.allocate(12 + 8 + json.length + 8 + 36).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67).putInt(2).putInt(out.capacity())
        out.putInt(json.length).putInt(0x4E4F534A).put(json.toByteArray())
        out.putInt(36).putInt(0x004E4942).put(bin.array())
        return out.array()
    }
}
