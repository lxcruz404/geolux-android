package me.luiscruzandresgomezrodolfohernandez.geolux.ui.catalog

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogTest {

    @Test
    fun coloresDeElemento() {
        assertEquals(Color(0xFF1C7ED6), elementTypeColor("trough"))
        assertEquals(Color(0xFF868E96), elementTypeColor("tipo_nuevo"))
    }

    @Test
    fun colorHexDelContrato() {
        assertEquals(Color(0xFF2A9D8F), parseHexColor("#2A9D8F", Color.Black))
        assertEquals(Color.Black, parseHexColor("verde", Color.Black))
        assertEquals(Color.Black, parseHexColor("", Color.Black))
    }

    @Test
    fun familiasDeEstado() {
        assertEquals(ChipFamily.Green, zoneStatusFamily("active"))
        assertEquals(ChipFamily.Amber, zoneStatusFamily("resting"))
        assertEquals(ChipFamily.Neutral, zoneStatusFamily("inactive"))
        assertEquals(ChipFamily.Green, planStatusFamily("exported"))
        assertEquals(ChipFamily.Amber, planStatusFamily("with_warnings"))
        assertEquals(ChipFamily.Red, planStatusFamily("invalid"))
        assertEquals(ChipFamily.Neutral, planStatusFamily("draft"))
    }
}
