package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.io.File
import java.time.Instant
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El informe del teléfono sale de los datos registrados, con los mismos totales que Desktop. */
class ManagementReportTest {
    private val glx = File(System.getProperty("geolux.glxFixtures")!!, "finca-gestion-v1.glx")
    private val project = GeoluxReader.read(glx).data!!
    private val data = ManagementPackage.read(glx, project.project.id)

    @Test
    fun totalesTablasYSeccionesVacias() {
        val report = ManagementReport.build(data, "Finca", "2026-11-01", "2026-11-30",
            zoneNames = project.zones.associate { it.id to it.name }, generated = Instant.parse("2026-11-20T12:00:00Z"))
        assertTrue(report.summary.contains("Gastos 185000"))
        assertTrue(report.summary.contains("Pagos a trabajadores 0")) // el pago está anulado
        val movements = report.tables.first { it.title == "Movimientos" }
        assertEquals(3, movements.rows.size)
        assertTrue(movements.rows.any { it.last() == "anulado" } && movements.rows.any { it.last() == "anulación" })
        val weights = report.tables.first { it.title == "Pesajes" }
        assertEquals(listOf("2068-38", "2026-11-01", "412.5", ""), weights.rows.single())
        assertTrue(weights.note.contains("Activo 1") && weights.note.contains("Vendido 1"))
        val empty = ManagementReport.build(ManagementData(project.project.id), "Finca", "2026-11-01", "2026-11-30")
        assertTrue(empty.tables.all { it.rows.isEmpty() })
        assertTrue(empty.summary.contains("Balance 0 COP"))
    }
}
