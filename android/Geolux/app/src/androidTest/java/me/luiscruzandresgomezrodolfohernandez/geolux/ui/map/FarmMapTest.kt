package me.luiscruzandresgomezrodolfohernandez.geolux.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.luiscruzandresgomezrodolfohernandez.geolux.data.LocationState
import me.luiscruzandresgomezrodolfohernandez.geolux.data.fake.FakeProjectRepository
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mapa MapLibre real (AND-R3, AND-R8): carga el estilo de finca-demo y, al tocar
 * el centro de la finca (encuadrada al abrir), elige un objeto de la finca.
 */
@RunWith(AndroidJUnit4::class)
class FarmMapTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tocarLaFincaEligeUnObjeto() {
        var selection: MapSelection? = null
        compose.setContent {
            FarmMap(
                data = FakeProjectRepository.demo,
                location = LocationState.Fix(6.301, -70.1985, 5f),
                selected = selection,
                background = Color.LightGray,
                elementColor = { "#868E96" },
                onSelect = { selection = it },
                modifier = Modifier.fillMaxSize().testTag("mapa"),
            )
        }
        // MapLibre carga el estilo en segundo plano. Un toque y se espera: tocar seguido sería un
        // doble toque (zoom) y MapLibre no confirmaría el toque simple.
        for (attempt in 1..10) {
            compose.onNodeWithTag("mapa").performClick()
            val chosen = runCatching { compose.waitUntil(timeoutMillis = 1_500) { selection != null } }.isSuccess
            if (chosen) break
        }
        val chosen = requireNotNull(selection) { "el toque no eligió nada" }
        val demo = FakeProjectRepository.demo
        val known = when (chosen.kind) {
            FeatureKind.Zone -> demo.zones.map { it.id }
            FeatureKind.Element -> demo.elements.map { it.id }
            FeatureKind.Line -> demo.lines.map { it.id }
            FeatureKind.Mission -> demo.missions.map { it.id }
            FeatureKind.WorkArea -> listOfNotNull(demo.workArea?.id)
        }
        assertTrue("$chosen no es de finca-demo", chosen.id in known)
    }
}
