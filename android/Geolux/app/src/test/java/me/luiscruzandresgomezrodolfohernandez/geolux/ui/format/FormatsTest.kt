package me.luiscruzandresgomezrodolfohernandez.geolux.ui.format

import java.time.ZoneId
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Measure
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatsTest {

    private val bogota = ZoneId.of("America/Bogota")

    @Test
    fun fechaEnHoraLocal() {
        assertEquals("09/10/2026", formatDate("2026-10-09T12:00:00+00:00", bogota))
        // 03:00 UTC del 10 de octubre todavía es 9 de octubre en Colombia (UTC-5).
        assertEquals("09/10/2026", formatDate("2026-10-10T03:00:00+00:00", bogota))
    }

    @Test
    fun fechaIlegibleSeDejaIgual() {
        assertEquals("ayer", formatDate("ayer", bogota))
    }

    @Test
    fun areaEnHectareas() {
        assertEquals("245.30 ha", formatArea(2_453_000.0))
        assertEquals("7.35 ha", formatArea(73_500.0))
    }

    @Test
    fun areaPequenaMuestraMetrosCuadrados() {
        assertEquals("0.01 ha", formatArea(100.0))
        assertEquals("0.01 ha (85 m²)", formatArea(85.0))
        assertEquals("0.00 ha (40 m²)", formatArea(40.0))
    }

    @Test
    fun longitudEnMetrosYKilometros() {
        assertEquals("850 m", formatLength(850.0))
        assertEquals("999 m", formatLength(999.4))
        assertEquals("1.00 km", formatLength(1000.0))
        assertEquals("2.30 km", formatLength(2300.0))
    }

    @Test
    fun areaDelProyecto() {
        assertEquals("22.50 ha", formatMeasureArea(Measure(22.5, "ha")))
        assertEquals("24.50 ha", formatMeasureArea(Measure(245_000.0, "m2")))
        assertEquals("3.00 acres", formatMeasureArea(Measure(3.0, "acres")))
    }

    @Test
    fun tamanoDeArchivo() {
        assertEquals("512 B", formatFileSize(512))
        assertEquals("6.1 KB", formatFileSize(6196))
        assertEquals("48.6 MB", formatFileSize(50_960_000))
        assertEquals("2.0 GB", formatFileSize(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun conteos() {
        assertEquals("40", formatCount(40.0))
        assertEquals("40.50", formatCount(40.5))
    }

    @Test
    fun cifraYUnidad() {
        assertEquals("245.30" to "ha", splitValueUnit("245.30 ha"))
        assertEquals("850" to "m", splitValueUnit("850 m"))
        assertEquals("1.10" to "km", splitValueUnit("1.10 km"))
        assertEquals("No especificado" to "", splitValueUnit("No especificado"))
        assertEquals("Sin definir" to "", splitValueUnit("Sin definir"))
    }
}
