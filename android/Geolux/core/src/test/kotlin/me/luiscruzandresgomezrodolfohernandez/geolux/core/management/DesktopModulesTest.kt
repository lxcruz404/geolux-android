package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Desktop exporta la gestión en dos módulos que se eligen por separado (12 nov. 2026):
 * gestión ganadera (lotes, animales, eventos) y gestión administrativa (trabajadores,
 * actividades, compras, dinero). Un paquete con un solo módulo trae vacío el otro, y al
 * importarlo el teléfono conserva lo que ya tenía de ese otro módulo: nada se pierde ni se
 * duplica, y repetir la importación no cambia nada.
 */
class DesktopModulesTest {
    private val ops = ManagementOps()
    private val p = "finca-modulos"

    private fun sample(): ManagementData {
        val lot = LivestockLot("lote-1", p, null, "bovine", 0, mode = "individual", updatedAt = "2026-11-01T00:00:00+00:00")
        val cow = Animal("vaca-1", p, "LG-V001", category = "bovine", sex = "female", lotId = lot.id,
            updatedAt = "2026-11-01T00:00:00+00:00")
        val entry = AnimalEvent("e-1", p, cow.id, Livestock.PURCHASE, "2026-01-10", lotId = lot.id, sequence = 1,
            createdAt = "2026-01-10T00:00:00+00:00")
        val weighing = AnimalEvent("e-2", p, cow.id, Livestock.WEIGHING, "2026-03-14", detailsJson = "{\"peso_kg\": 420.5}",
            sequence = 2, createdAt = "2026-03-14T00:00:00+00:00")
        val herd = ManagementData(p, livestockLots = listOf(lot), animals = listOf(cow), animalEvents = listOf(entry, weighing))
        val (withWorker, worker) = ops.createWorker(herd, "T-01", "Trabajador demostrativo")
        val withActivity = ops.createActivity(withWorker, emptyMap(), "Pesaje", "weighing", "high", scheduledDate = "2026-11-14",
            scheduledTime = "06:30", remindMinutesBefore = 60, assigneeWorkerId = worker.id, links = listOf("animal" to cow.id)).first
        return ops.rebuildAnimals(withActivity)
    }

    private fun livestockOnly(d: ManagementData) =
        ManagementData(p, livestockLots = d.livestockLots, animals = d.animals, animalEvents = d.animalEvents)

    private fun administrationOnly(d: ManagementData) =
        d.copy(livestockLots = emptyList(), animals = emptyList(), animalEvents = emptyList())

    @Test
    fun aPartialPackageKeepsTheOtherModuleOnThePhone() {
        val full = sample()
        val afterLivestock = ops.merge(full, livestockOnly(full))
        assertTrue(SameContent.of(full, afterLivestock))
        val afterAdmin = ops.merge(afterLivestock, administrationOnly(full))
        assertTrue(SameContent.of(full, afterAdmin))
    }

    @Test
    fun twoPartialPackagesBuildTheWholeFarmAndRepeatingChangesNothing() {
        val full = sample()
        var phone = ops.merge(ManagementData(p), livestockOnly(full))
        assertEquals(1, phone.animals.size)
        assertTrue(phone.workers.isEmpty() && phone.activities.isEmpty())
        phone = ops.merge(phone, administrationOnly(full))
        assertTrue(SameContent.of(full, phone))
        repeat(2) { phone = ops.merge(phone, full) }
        assertTrue(SameContent.of(full, phone))
        // La actividad conserva su responsable y su enlace al animal.
        assertEquals(phone.workers.single().id, phone.activities.single().assigneeWorkerId)
        assertEquals("vaca-1", phone.activityLinks.single { it.entity == "animal" }.entityId)
    }

    /**
     * Los paquetes reales de La Giralda que exporta Desktop (ambos módulos, solo ganadera, solo
     * administrativa). Solo corre con `GLX_LA_GIRALDA_DIR` (no se publican: llevan las
     * coordenadas de la finca). `GLX_LA_GIRALDA_ESPERADO` (animals=106,workers=5…) y
     * `GLX_LA_GIRALDA_DINERO` (ingresos,gastos) son las cifras que da Desktop.
     */
    @Test
    fun laGiraldaFromDesktop() {
        val dir = System.getenv("GLX_LA_GIRALDA_DIR")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        fun load(name: String): Pair<ManagementData, Map<String, String>> {
            val file = File(dir, name)
            val report = GeoluxReader.read(file)
            assertTrue("$name: ${report.errors}", report.ok)
            val data = report.data!!
            return ManagementPackage.read(file, data.project.id) to data.zones.associate { it.id to it.name }
        }
        val (both, zones) = load("la-giralda-ambos.glx")
        val (livestock, _) = load("la-giralda-solo-ganadera.glx")
        val (admin, _) = load("la-giralda-solo-administrativa.glx")
        fun counts(d: ManagementData) = mapOf(
            "animals" to d.animals.size, "lots" to d.livestockLots.size, "events" to d.animalEvents.size,
            "workers" to d.workers.size, "activities" to d.activities.size, "links" to d.activityLinks.size,
            "lists" to d.purchaseLists.size, "items" to d.purchaseItems.size, "transactions" to d.transactions.size,
        )
        val c = counts(both)
        println("La Giralda en Android: $c")
        System.getenv("GLX_LA_GIRALDA_ESPERADO").orEmpty().split(",").filter { it.isNotBlank() }
            .forEach { assertEquals(it, it.substringAfter("=").toInt(), c[it.substringBefore("=")]) }
        // Cada paquete parcial trae solo su módulo.
        assertEquals(c["animals"], livestock.animals.size)
        assertTrue(livestock.workers.isEmpty() && livestock.activities.isEmpty() && livestock.transactions.isEmpty() &&
            livestock.purchaseItems.isEmpty())
        assertEquals(c["workers"], admin.workers.size)
        assertTrue(admin.animals.isEmpty() && admin.livestockLots.isEmpty() && admin.animalEvents.isEmpty())
        // El estado de cada animal coincide con sus eventos (como Desktop) y cada lote cuenta sus activos.
        assertTrue(SameContent.of(both, ops.rebuildAnimals(both)))
        val today = LocalDate.parse("2026-11-12")
        val summary = HerdView.summary(both, zones, today)
        println("Hato: total=${summary.total} activos=${summary.active} vendidos=${summary.sold} muertos=${summary.dead} " +
            "clases=${summary.byClass}")
        println("Lotes: " + summary.lots.joinToString { "${it.label}=${it.count} (${it.zoneName})" })
        assertEquals(summary.active, summary.lots.sumOf { it.count })
        assertTrue(summary.lots.all { it.zoneName.isNotBlank() })
        // Fichas: cada animal activo tiene lote y potrero; sin huérfanos.
        val rows = HerdView.rows(both, zones, today)
        assertEquals(both.animals.size, rows.size)
        assertTrue(rows.filter { it.status == "active" }.all { it.lotLabel.isNotBlank() && it.zoneName.isNotBlank() })
        val ids = both.animals.map { it.id }.toSet()
        assertTrue(both.animalEvents.all { it.animalId in ids })
        val workers = both.workers.map { it.id }.toSet()
        assertTrue(both.activities.all { it.assigneeWorkerId == null || it.assigneeWorkerId in workers })
        val lists = both.purchaseLists.map { it.id }.toSet()
        assertTrue(both.purchaseItems.all { it.listId in lists })
        // Compras pagadas = su movimiento; dinero igual que Desktop.
        both.transactions.filter { it.type == "purchase" }.forEach { t ->
            val paid = both.purchaseItems.filter { it.transactionId == t.id }.sumOf { BigDecimal(it.actualPrice) }
            assertEquals(0, paid.compareTo(BigDecimal(t.amount)))
        }
        val money = Finance.summary(both.transactions)
        println("Dinero: ingresos=${money.income} gastos=${money.expenses} saldo=${money.balance}")
        System.getenv("GLX_LA_GIRALDA_DINERO")?.split(",")?.let { (income, expenses) ->
            assertEquals(income, money.income)
            assertEquals(expenses, money.expenses)
        }
        // Importar parcial + parcial = completo; repetir no duplica nada.
        var phone = ops.merge(ManagementData(both.projectId), livestock)
        phone = ops.merge(phone, admin)
        assertTrue(SameContent.of(both, phone))
        phone = ops.merge(ops.merge(phone, both), both)
        assertEquals(c, counts(phone))
        // Informes del teléfono con datos.
        val herdReport = HerdView.report(both, "La Giralda", zones, today)
        assertTrue(herdReport.tables.any { it.rows.size >= summary.active })
        val gestion = ManagementReport.build(both, "La Giralda", "2026-01-01", "2026-11-12", zones)
        assertTrue(gestion.tables.first { it.title == "Movimientos" }.rows.isNotEmpty())
    }
}

/** Mismo contenido sin importar el orden (como `ManagementRoomStore.sameContent`). */
private object SameContent {
    fun of(a: ManagementData, b: ManagementData): Boolean = a.projectId == b.projectId && listOf(
        a.workers to b.workers, a.financialCategories to b.financialCategories, a.activitySeries to b.activitySeries,
        a.activities to b.activities, a.activityLinks to b.activityLinks, a.purchaseLists to b.purchaseLists,
        a.transactions to b.transactions, a.purchaseItems to b.purchaseItems, a.attachments to b.attachments,
        a.livestockLots to b.livestockLots, a.animals to b.animals, a.animalEvents to b.animalEvents,
    ).all { (x, y) -> x.toSet() == y.toSet() && x.size == y.size }
}
