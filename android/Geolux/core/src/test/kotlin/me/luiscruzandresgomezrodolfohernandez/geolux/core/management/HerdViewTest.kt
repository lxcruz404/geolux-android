package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.io.File
import java.time.Instant
import java.time.LocalDate
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Software ganadero del teléfono: tabla, filtros, orden, resumen, lotes, registros y alertas
 * calculados solo con lo registrado (ganado de Desktop en `finca-gestion-v1.glx` más un
 * hato de prueba creado con las reglas de Desktop).
 */
class HerdViewTest {
    private val glx = File(System.getProperty("geolux.glxFixtures")!!, "finca-gestion-v1.glx")
    private val projectId = GeoluxReader.read(glx).data!!.project.id
    private var n = 0
    private val ops = ManagementOps(now = { Instant.parse("2026-11-11T12:00:00Z") }, newId = { "h-${++n}" })
    private val herd = LivestockOps(ops)
    private val today = LocalDate.parse("2026-11-11")
    private val zones = mapOf("z1" to "Potrero 03")

    /** Hato: vaca con parto, su cría, novilla, toro con clase registrada y una vendida. */
    private fun farm(): ManagementData {
        var d = ManagementData(projectId)
        val (d1, lot) = herd.createIndividualLot(d, "bovine", "z1", "Cría"); d = d1
        d = herd.registerEntry(d, "1001", "bovine", "purchase", "2025-01-10", lot.id, sex = "female", breed = "Brahman", birthDate = "2021-03-01").first
        d = herd.registerEntry(d, "1002", "bovine", "purchase", "2025-01-10", lot.id, sex = "female", breed = "Gyr", birthDate = "2025-01-01").first
        d = herd.registerEntry(d, "2005", "bovine", "birth", "2026-10-20", lot.id, sex = "male", breed = "Brahman").first
        val (d2, bull) = herd.registerEntry(d, "7", "bovine", "purchase", "2025-06-01", lot.id, name = "Rey", sex = "male", birthDate = "2022-01-01"); d = d2
        d = d.copy(animals = d.animals.map { if (it.id == bull.id) it.copy(productionClass = "bull") else it })
        val cow = d.animals.first { it.tag == "1001" }
        val calf = d.animals.first { it.tag == "2005" }
        // Parto registrado en la madre (como Desktop: evento calving con la cría).
        d = d.copy(animalEvents = d.animalEvents + AnimalEvent("parto", projectId, cow.id, Livestock.CALVING, "2026-10-20",
            detailsJson = """{"cria_id":"${calf.id}"}""", sequence = 9))
        d = herd.recordWeighing(d, cow.id, "2026-10-01", "455")
        d = herd.recordWeighing(d, bull.id, "2026-10-01", "700.5")
        d = herd.recordHealth(d, cow.id, "2026-10-05", "vaccination", product = "Registrado por el usuario", nextDate = "2026-11-05")
        d = herd.recordHealth(d, bull.id, "2026-10-05", "treatment", product = "Registrado", nextDate = "2026-11-20")
        val (d3, other) = herd.createIndividualLot(d, "bovine", null); d = d3
        d = herd.move(d, d.animals.first { it.tag == "1002" }.id, other.id, "2026-11-01")
        val (d4, sold) = herd.registerEntry(d, "3000", "bovine", "purchase", "2025-02-01", lot.id, sex = "male"); d = d4
        d = herd.registerExit(d, sold.id, Livestock.SALE, "2026-09-01")
        return d
    }

    @Test
    fun filasConCategoriaEdadPesoLoteYProcedencia() {
        val rows = HerdView.rows(farm(), zones, today).associateBy { it.tag }
        val cow = rows.getValue("1001")
        assertEquals("cow", cow.productionClass)          // hembra adulta con parto
        assertTrue(cow.classSuggested)
        assertEquals("Vaca (sugerida)", cow.classLabel)
        assertEquals("455 kg", cow.weightLabel)
        assertEquals("Potrero 03", cow.zoneName)
        assertEquals("Compra", cow.originLabel)
        assertEquals("heifer", rows.getValue("1002").productionClass)
        assertEquals("calf_male", rows.getValue("2005").productionClass)
        assertEquals("Nacimiento", rows.getValue("2005").originLabel)
        assertEquals("22 d", rows.getValue("2005").ageLabel)
        val bull = rows.getValue("7")
        assertEquals("Toro", bull.classLabel)              // registrada: sin «sugerida»
        assertEquals("700.5 kg", bull.weightLabel)
        assertEquals("—", rows.getValue("3000").ageLabel)  // sin fecha de nacimiento: no se inventa
    }

    @Test
    fun filtrosBusquedaYOrden() {
        val rows = HerdView.rows(farm(), zones, today)
        val active = HerdView.filterAndSort(rows, HerdFilter(), AnimalColumn.Tag, false)
        assertEquals(listOf("7", "1001", "1002", "2005"), active.map { it.tag }) // orden natural, sin la vendida
        assertEquals(listOf("3000"), HerdView.filterAndSort(rows, HerdFilter(status = "sold"), AnimalColumn.Tag, false).map { it.tag })
        assertEquals(5, HerdView.filterAndSort(rows, HerdFilter(status = null), AnimalColumn.Tag, false).size)
        assertEquals(listOf("1001", "1002"), HerdView.filterAndSort(rows, HerdFilter(sex = "female"), AnimalColumn.Tag, false).map { it.tag })
        assertEquals(listOf("1001", "2005"), HerdView.filterAndSort(rows, HerdFilter(breed = "brahman"), AnimalColumn.Tag, false).map { it.tag })
        assertEquals(listOf("1001"), HerdView.filterAndSort(rows, HerdFilter(productionClass = "cow"), AnimalColumn.Tag, false).map { it.tag })
        assertEquals(listOf("7"), HerdView.filterAndSort(rows, HerdFilter(query = "rey"), AnimalColumn.Tag, false).map { it.tag })
        assertEquals(3, HerdView.filterAndSort(rows, HerdFilter(zoneId = "z1"), AnimalColumn.Tag, false).size)
        // Por peso, de mayor a menor; los que no tienen pesaje al final.
        assertEquals(listOf("7", "1001"), HerdView.filterAndSort(rows, HerdFilter(), AnimalColumn.Weight, true).map { it.tag }.take(2))
        assertEquals(listOf("2005", "1002", "7", "1001"), HerdView.filterAndSort(rows, HerdFilter(), AnimalColumn.Age, false).map { it.tag })
    }

    @Test
    fun resumenLotesYAlertasConLosDatosReales() {
        val s = HerdView.summary(farm(), zones, today)
        assertEquals(5, s.total)
        assertEquals(4, s.active)
        assertEquals(2, s.males)
        assertEquals(2, s.females)
        assertEquals(1, s.count("cow"))
        assertEquals(1, s.count("heifer"))
        assertEquals(1, s.count("calf_male", "calf_female"))
        assertEquals(1, s.count("bull"))
        assertEquals(1, s.sold)
        assertEquals(listOf("1002"), s.recentMoves.map { it.animalTag })
        assertEquals(listOf("2005"), s.recentBirths.map { it.animalTag })
        // Vacuna vencida (05/11) y tratamiento próximo (20/11), en orden de fecha.
        assertEquals(listOf("1001" to true, "7" to false), s.alerts.map { it.tag to it.overdue })
        val cria = s.lots.first { it.label.contains("Cría") }
        assertEquals(3, cria.count)
        assertTrue(cria.composition.contains("Vaca 1"))
        assertEquals(577.75, cria.averageWeightKg!!, 1e-6)
        assertEquals(2, cria.weighedAnimals)
        val otro = s.lots.first { !it.label.contains("Cría") }
        assertNull(otro.averageWeightKg)  // sin pesajes: no hay promedio
    }

    @Test
    fun registrosPorTipoYDeUnAnimal() {
        val d = farm()
        assertEquals(1, HerdView.records(d, zones, RecordKind.Vaccinations).size)
        assertEquals(1, HerdView.records(d, zones, RecordKind.Treatments).size)
        assertEquals(1, HerdView.records(d, zones, RecordKind.Reproduction).size)
        assertTrue(HerdView.records(d, zones, RecordKind.Reproduction).single().detail.startsWith("Cría 2005"))
        assertEquals(1, HerdView.records(d, zones, RecordKind.Exits).size)
        assertEquals(1, HerdView.records(d, zones, RecordKind.Births).size)
        val cow = d.animals.first { it.tag == "1001" }
        val history = HerdView.records(d, zones, RecordKind.All, cow.id)
        assertEquals(listOf(Livestock.CALVING, Livestock.HEALTH, Livestock.WEIGHING, Livestock.PURCHASE), history.map { it.event.eventType })
    }

    @Test
    fun elGanadoDeDesktopSeVeEnLaTablaYElInforme() {
        val data = ManagementPackage.read(glx, projectId)
        val rows = HerdView.rows(data, emptyMap(), today)
        assertEquals(setOf("2068-38", "1150"), rows.map { it.tag }.toSet())
        assertFalse(rows.first { it.tag == "1150" }.status == "active")
        val report = HerdView.report(data, "Finca", emptyMap(), today)
        assertEquals(listOf("2068-38"), report.tables.first().rows.map { it.first() })
    }
}
