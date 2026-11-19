package me.luiscruzandresgomezrodolfohernandez.geolux.ui.livestock

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.LivestockOps
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementOps
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.management.ManagementViewModel
import me.luiscruzandresgomezrodolfohernandez.geolux.ui.theme.GeoLuxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Software ganadero (pestaña Ganado): resumen con cifras de los registros, tabla con
 * búsqueda y filtros, ficha con secciones, lotes, registros y «Crear actividad».
 */
@RunWith(AndroidJUnit4::class)
class GanadoScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val demo = FakeProjectRepository.demo
    private val today = LocalDate.parse("2026-11-11")
    private var n = 0
    private val ops = ManagementOps(now = { Instant.parse("2026-11-11T12:00:00Z") }, newId = { "g-${++n}" })

    private fun show(onActivity: (String) -> Unit = {}): ManagementViewModel {
        val repo = FakeProjectRepository()
        val herd = LivestockOps(ops)
        runBlocking {
            repo.editManagement(demo.project.id) { d0 ->
                var d = d0
                val (d1, lot) = herd.createIndividualLot(d, "bovine", "zone-demo-1", "Cría"); d = d1
                d = herd.registerEntry(d, "1001", "bovine", "purchase", "2025-01-10", lot.id, sex = "female", breed = "Brahman", birthDate = "2021-03-01").first
                d = herd.registerEntry(d, "1002", "bovine", "purchase", "2025-01-10", lot.id, sex = "female", breed = "Gyr", birthDate = "2025-01-01").first
                d = herd.registerEntry(d, "7", "bovine", "purchase", "2025-06-01", lot.id, name = "Rey", sex = "male", birthDate = "2022-01-01").first
                d = herd.recordWeighing(d, d.animals.first { it.tag == "1001" }.id, "2026-10-01", "455")
                d = herd.recordHealth(d, d.animals.first { it.tag == "1001" }.id, "2026-10-05", "vaccination", product = "Registrado", nextDate = "2026-11-05")
                d
            }
        }
        val vm = ManagementViewModel(repo, ops)
        vm.open(demo.project.id)
        compose.setContent {
            GeoLuxTheme {
                val state by vm.state.collectAsState()
                GanadoScreen(demo, state, vm, onMessage = {}, onCreateActivity = onActivity, today = today)
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("stat_total")).fetchSemanticsNodes().isNotEmpty() }
        return vm
    }

    @Test
    fun resumenConCifrasRealesYAlertaSanitaria() {
        show()
        compose.onNodeWithTag("stat_total").assertTextContains("3", substring = true)
        compose.onNodeWithTag("stat_hembras").assertTextContains("2", substring = true)
        compose.onNodeWithTag("stat_machos").assertTextContains("1", substring = true)
        compose.onNodeWithTag("stat_vacas").assertTextContains("0", substring = true) // la vaca no tiene partos: novilla sugerida
        compose.onNodeWithTag("stat_novillas").assertTextContains("2", substring = true)
        compose.onNodeWithText("1001 · Vacunación").assertExists()
        compose.onNodeWithText("Vencida").assertExists()
    }

    @Test
    fun tablaConBusquedaFiltroYFicha() {
        show()
        compose.onNodeWithTag("ganado_Animales").performClick()
        compose.onNodeWithTag("conteo_animales").assertTextContains("3 de 3 animales", substring = true)
        compose.onNodeWithTag("animal_7").assertIsDisplayed()
        compose.onNodeWithTag("buscar_animal").performTextInput("rey")
        compose.onNodeWithTag("conteo_animales").assertTextContains("1 de 3 animales", substring = true)
        compose.onNodeWithTag("animal_7").performClick()
        compose.onNodeWithTag("ficha_animal").assertIsDisplayed()
        compose.onNodeWithText("7 · Rey").assertIsDisplayed()
        // Las secciones de la ficha se pueden recorrer sin perder el animal.
        compose.onNodeWithTag("ficha_Pesajes").performClick()
        compose.onNodeWithText("Sin pesajes registrados.").assertExists()
        compose.onNodeWithTag("ficha_General").performClick()
        compose.onAllNodesWithText("Alta por compra")[0].assertExists()
        compose.onNodeWithText("← Ganado").performClick()
        compose.onNodeWithTag("tabla_animales").assertIsDisplayed()
    }

    @Test
    fun filtroPorSexoYOrdenPorPeso() {
        show()
        compose.onNodeWithTag("ganado_Animales").performClick()
        compose.onNodeWithTag("filtro_sexo").performClick()
        compose.onNodeWithTag("opcion_sexo_female").performClick()
        compose.onNodeWithTag("conteo_animales").assertTextContains("2 de 3 animales", substring = true)
        compose.onNodeWithTag("col_Weight").performScrollTo().performClick()
        compose.onNodeWithTag("conteo_animales").assertTextContains("ordenado por Peso", substring = true)
    }

    @Test
    fun crearActividadDesdeLaFichaYPesaje() {
        val asked = mutableListOf<String>()
        val vm = show { asked += it }
        compose.onNodeWithTag("ganado_Animales").performClick()
        compose.onNodeWithTag("animal_1002").performClick()
        compose.onNodeWithTag("accion_actividad").performClick()
        val id = (vm.state.value as me.luiscruzandresgomezrodolfohernandez.geolux.ui.management.ManagementUiState.Ready)
            .data.animals.first { it.tag == "1002" }.id
        assertEquals(listOf(id), asked)
        compose.onNodeWithTag("accion_pesaje").performClick()
        compose.onNodeWithTag("peso_kg").performTextInput("380")
        compose.onNodeWithTag("guardar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("380 kg", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun lotesYRegistros() {
        show()
        compose.onNodeWithTag("ganado_Lotes").performClick()
        compose.onNodeWithText("Lote Bovino · Potrero La Ceiba · Cría").performClick()
        compose.onNodeWithTag("lote_titulo").assertIsDisplayed()
        compose.onNodeWithTag("conteo_animales").assertTextContains("3 de 3 animales", substring = true)
        compose.onNodeWithTag("lote_historial").performClick()
        compose.onAllNodesWithText("Alta por compra", substring = true)[0].assertExists()
        compose.onNodeWithTag("ganado_Registros").performClick()
        compose.onNodeWithTag("registro_Vaccinations").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("1001 · Registro sanitario").assertExists()
    }
}

