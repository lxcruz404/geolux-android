package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Qué avisos programa el teléfono: solo actividades abiertas, sin duplicados, en su zona horaria. */
class ReminderPlannerTest {
    private var n = 0
    private val ops = ManagementOps(now = { Instant.parse("2026-11-11T12:00:00Z") }, newId = { "a${++n}" })
    private val empty = ManagementData("p")

    @Test
    fun planificaSoloLasAbiertasConRecordatorio() {
        var d = empty
        val (d1, toro) = ops.createActivity(d, emptyMap(), "Curar el toro 2068-38", "treatment",
            scheduledDate = "2026-11-15", scheduledTime = "08:00", remindMinutesBefore = 30)
        val (d2, sinAviso) = ops.createActivity(d1, emptyMap(), "Sin aviso", scheduledDate = "2026-11-15")
        val (d3, hecha) = ops.createActivity(d2, emptyMap(), "Hecha", scheduledDate = "2026-11-16", remindMinutesBefore = 0)
        d = ops.setActivityStatus(d3, hecha.id, "done")
        val plan = ReminderPlanner.plan(d, Instant.parse("2026-11-11T12:00:00Z"), ops)
        assertEquals(listOf(toro.id), plan.map { it.activityId })
        assertEquals(Instant.parse("2026-11-15T12:30:00Z"), plan.single().at) // 7:30 en Bogotá
        assertTrue(sinAviso.remindMinutesBefore == null)
    }

    @Test
    fun cambiarLaHoraCambiaLaClaveYLosVencidosHaceTiempoNoSeProgramanOtraVez() {
        val (d1, a) = ops.createActivity(empty, emptyMap(), "Pesaje lote 04", "weighing",
            scheduledDate = "2026-11-15", scheduledTime = "08:00", remindMinutesBefore = 60)
        val before = ReminderPlanner.plan(d1, Instant.parse("2026-11-11T00:00:00Z"), ops).single().key
        val moved = ops.reschedule(d1, a.id, "2026-11-15", "09:00")
        val after = ReminderPlanner.plan(moved, Instant.parse("2026-11-11T00:00:00Z"), ops).single().key
        assertNotEquals(before, after)
        assertTrue(ReminderPlanner.plan(moved, Instant.parse("2026-11-20T00:00:00Z"), ops).isEmpty())
    }
}
