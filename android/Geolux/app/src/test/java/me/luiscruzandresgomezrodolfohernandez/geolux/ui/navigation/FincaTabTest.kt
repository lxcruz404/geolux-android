package me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation

import me.luiscruzandresgomezrodolfohernandez.geolux.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** La pestaña Ganado usa la cabeza de res (no la huella de perro) y las demás no cambian. */
class FincaTabTest {
    @Test
    fun ganadoUsesTheCattleHeadInBothStates() {
        assertEquals(R.drawable.ic_cattle, FincaTab.Ganado.icon)
        assertEquals(R.drawable.ic_cattle_filled, FincaTab.Ganado.selectedIcon)
        assertNotEquals(FincaTab.Ganado.icon, FincaTab.Ganado.selectedIcon)
        assertEquals(R.string.tab_ganado, FincaTab.Ganado.label)
    }

    @Test
    fun theOtherTabsKeepTheirIconsAndOrder() {
        assertEquals(listOf("MiFinca", "Mapa", "Potreros", "Ganado", "Modelo3D", "Gestion"), FincaTab.entries.map { it.name })
        assertEquals(R.drawable.ic_symbol_landscape to R.drawable.ic_symbol_landscape_filled, FincaTab.MiFinca.icon to FincaTab.MiFinca.selectedIcon)
        assertEquals(R.drawable.ic_symbol_map to R.drawable.ic_symbol_map_filled, FincaTab.Mapa.icon to FincaTab.Mapa.selectedIcon)
        assertEquals(R.drawable.ic_symbol_grid_view to R.drawable.ic_symbol_grid_view_filled, FincaTab.Potreros.icon to FincaTab.Potreros.selectedIcon)
        assertEquals(R.drawable.ic_symbol_view_in_ar to R.drawable.ic_symbol_view_in_ar_filled, FincaTab.Modelo3D.icon to FincaTab.Modelo3D.selectedIcon)
        assertEquals(R.drawable.ic_check_circle to R.drawable.ic_check_circle, FincaTab.Gestion.icon to FincaTab.Gestion.selectedIcon)
    }
}
