package me.luiscruzandresgomezrodolfohernandez.geolux.core.model

import java.io.File
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** El área de «Mis fincas» y de «Mi finca»: project.json y, si falta, el área de trabajo. */
class TotalAreaTest {
    private val demo = GeoluxReader.read(File(System.getProperty("geolux.fixtures")!!, "finca-demo.geolux")).data!!

    @Test
    fun conAreaEnElProyectoSeUsaEsa() {
        assertEquals(Measure(22.5, "ha"), demo.totalArea())
        assertEquals(Measure(22.5, "ha"), demo.summary().area)
    }

    @Test
    fun sinAreaEnElProyectoSeUsaLaDelAreaDeTrabajo() {
        // Como La Giralda: Desktop no guarda el área si el usuario no la escribe.
        val data = demo.copy(project = demo.project.copy(area = null))
        assertEquals(Measure(245_000.0, "m2"), data.totalArea())
        assertEquals(Measure(245_000.0, "m2"), data.summary().area)
    }

    @Test
    fun sinNingunaDeLasDosNoSeInventa() {
        // Como Country Colors: sin área ni área de trabajo.
        val data = demo.copy(project = demo.project.copy(area = null), workArea = null)
        assertNull(data.totalArea())
        assertNull(data.summary().area)
    }
}
