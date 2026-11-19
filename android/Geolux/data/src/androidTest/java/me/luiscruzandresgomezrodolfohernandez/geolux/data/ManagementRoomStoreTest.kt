package me.luiscruzandresgomezrodolfohernandez.geolux.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.LivestockOps
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementOps
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ValidationException
import me.luiscruzandresgomezrodolfohernandez.geolux.data.management.ManagementDatabase
import me.luiscruzandresgomezrodolfohernandez.geolux.data.management.ManagementRoomStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gestión en Room (base en memoria): migración desde los archivos de la versión anterior,
 * guardado y lectura sin pérdida, fusión al reinstalar, fincas separadas y transacciones
 * que no dejan nada a medias.
 */
@RunWith(AndroidJUnit4::class)
class ManagementRoomStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = ManagementDatabase.inMemory(context)
    private var n = 0
    private val ops = ManagementOps(now = { Instant.parse("2099-01-01T00:00:00Z") }, newId = { "id-${++n}" })

    @After fun close() = db.close()

    /** Una finca con todo tipo de registros, como la deja la app. */
    private fun farm(projectId: String): ManagementData {
        var d = ManagementData(projectId)
        val (d1, w) = ops.createWorker(d, "T-1", "Juan Pérez"); d = d1
        val (d2, _) = ops.createActivity(d, emptyMap(), "Reparar cerca", "fence_repair", scheduledDate = "2026-11-14",
            scheduledTime = "08:00", remindMinutesBefore = 30, assigneeWorkerId = w.id); d = d2
        val (d3, list) = ops.createList(d, "Mercado", "workers_groceries"); d = d3
        val (d4, item) = ops.addItem(d, list.id, "Arroz", "10", "kg", "4200"); d = d4
        d = ops.setItemStatus(d, item.id, "bought", "41000.5")
        d = ops.confirmPayment(d, listOf(item.id), "2026-11-15", "workers_food").first
        val herd = LivestockOps(ops)
        val (d5, lot) = herd.createIndividualLot(d, "bovine", null); d = d5
        val (d6, bull) = herd.registerEntry(d, "2068-38", "bovine", "purchase", "2026-10-01", lot.id); d = d6
        d = herd.recordWeighing(d, bull.id, "2026-11-01", "412.5")
        d = herd.recordHealth(d, bull.id, "2026-11-02", "vaccination", product = "Registrado por el usuario")
        return d
    }

    private fun store(legacy: Map<String, ManagementData> = emptyMap(), pkg: Map<String, ManagementData> = emptyMap(),
                      migrated: MutableList<String> = mutableListOf()) =
        ManagementRoomStore(db.dao(), { legacy[it] }, { pkg[it] }, { migrated += it })

    @Test
    fun laGestionEnArchivosPasaARoomSinPerderNada() = runBlocking<Unit> {
        val old = farm("finca-1")
        val migrated = mutableListOf<String>()
        val s = store(legacy = mapOf("finca-1" to old), migrated = migrated)
        val loaded = s.load("finca-1")!!
        assertTrue(ManagementRoomStore.sameContent(old, loaded))
        assertEquals(listOf("finca-1"), migrated)
        assertEquals("archivos", db.dao().state("finca-1")!!.source)
        // La segunda vez se lee de Room, aunque los archivos ya no estén.
        assertTrue(ManagementRoomStore.sameContent(old, store().load("finca-1")!!))
    }

    @Test
    fun sinArchivosSeTomaDelPaqueteYUnaFincaNoImportadaNoTieneGestion() = runBlocking<Unit> {
        val s = store(pkg = mapOf("finca-2" to farm("finca-2")))
        assertEquals("2068-38", s.load("finca-2")!!.animals.single().tag)
        assertEquals("paquete", db.dao().state("finca-2")!!.source)
        assertNull(s.load("no-existe"))
    }

    @Test
    fun guardarYLeerConservaCadaCampoYSeparaLasFincas() = runBlocking<Unit> {
        val s = store(pkg = mapOf("a" to ManagementData("a"), "b" to ManagementData("b")))
        s.load("a"); s.load("b")
        val a = farm("a")
        s.save(a)
        val back = s.load("a")!!
        assertTrue(ManagementRoomStore.sameContent(a, back))
        assertEquals("41000.5", back.transactions.single().amount) // texto decimal exacto
        assertTrue(s.load("b")!!.isEmpty)
        // Editar y volver a guardar: anular el pago y completar la actividad.
        val edited = ops.voidTransaction(back, back.transactions.single().id, "error", LocalDate.of(2026, 11, 20)).first
            .let { ops.setActivityStatus(it, it.activities.single().id, "done") }
        s.save(edited)
        val again = s.load("a")!!
        assertEquals(2, again.transactions.size)
        assertEquals("done", again.activities.single().status)
    }

    @Test
    fun loYaGuardadoPasaTalCualAunqueUnaReglaNuevaSeaMasEstricta() = runBlocking<Unit> {
        // Datos de una versión anterior con un importe no canónico y una referencia colgante.
        val old = farm("c").let { d ->
            d.copy(transactions = d.transactions.map { it.copy(amount = "41000.50") },
                purchaseItems = d.purchaseItems + d.purchaseItems.first().copy(id = "huerfano", listId = "lista-que-no-existe"))
        }
        val s = store(legacy = mapOf("c" to old))
        assertTrue(ManagementRoomStore.sameContent(old, s.load("c")!!))
        // Y se puede seguir editando.
        s.save(ops.setActivityStatus(s.load("c")!!, old.activities.single().id, "done"))
        assertEquals("done", s.load("c")!!.activities.single().status)
    }

    @Test
    fun dosFincasConLosMismosIdsNoChocan() = runBlocking<Unit> {
        val s = store(pkg = mapOf("x" to ManagementData("x"), "y" to ManagementData("y")))
        s.load("x"); s.load("y")
        n = 0; s.save(farm("x"))
        n = 0; s.save(farm("y")) // mismos ids de registro, otra finca
        assertEquals("2068-38", s.load("x")!!.animals.single().tag)
        assertEquals("2068-38", s.load("y")!!.animals.single().tag)
    }

    @Test
    fun reinstalarFusionaLoDelTelefonoConElPaquete() = runBlocking<Unit> {
        val s = store(pkg = mapOf("finca-demo" to ManagementData("finca-demo")))
        s.load("finca-demo")
        val phone = farm("finca-demo")
        s.save(phone)
        // Paquete sin gestión (el de prueba): lo del teléfono se conserva.
        val pkg = File(context.cacheDir, "finca-demo-v1.glx")
        InstrumentationRegistry.getInstrumentation().context.assets.open("finca-demo-v1.glx").use { input ->
            pkg.outputStream().use { input.copyTo(it) }
        }
        val id = me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux.GeoluxReader.read(pkg).data!!.project.id
        val s2 = store(pkg = mapOf(id to ManagementData(id)))
        s2.load(id)
        s2.save(farm(id))
        val merged = s2.prepareReinstall(id, pkg)!!
        s2.save(merged)
        assertEquals("2068-38", s2.load(id)!!.animals.single().tag)
        // Una fusión imposible se detecta antes de tocar nada: el mismo código en dos equipos.
        val twin = farm(id).let { d -> d.copy(workers = d.workers + d.workers.first().copy(id = "otro")) }
        try {
            ops.merge(twin, twin); fail("debía rechazarse")
        } catch (e: ValidationException) {
            // esperado: el repositorio no instala el paquete en este caso
        }
        pkg.delete()
    }
}
