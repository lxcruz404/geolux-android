package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.time.Instant

/** Un recordatorio que el teléfono debe mostrar a la hora [at]. */
data class PlannedReminder(
    /** Identidad del aviso: cambiar fecha, hora o minutos hace otro (como `reminder_key` de Desktop). */
    val key: String,
    val activityId: String,
    val projectId: String,
    val at: Instant,
    val title: String,
    val text: String,
)

/**
 * Qué avisos programar: actividades abiertas con recordatorio cuya hora aún no pasó (o pasó
 * hace menos de [graceMinutes], por si el teléfono estaba apagado). Kotlin puro; la app los
 * pasa a AlarmManager.
 */
object ReminderPlanner {
    fun key(a: Activity) = "${a.id}|${a.scheduledDate}|${a.scheduledTime}|${a.remindMinutesBefore}"

    fun plan(data: ManagementData, now: Instant, ops: ManagementOps = ManagementOps(), graceMinutes: Long = 60): List<PlannedReminder> =
        ops.liveActivities(data)
            .filter { it.status in Catalogs.openActivityStatus }
            .mapNotNull { a ->
                val at = ops.reminderAt(a)?.toInstant() ?: return@mapNotNull null
                if (at.isBefore(now.minusSeconds(graceMinutes * 60))) return@mapNotNull null
                val whenText = listOfNotNull(a.scheduledDate, a.scheduledTime).joinToString(" ")
                PlannedReminder(key(a), a.id, a.projectId, at, a.title, "${Catalogs.activityCategory[a.category] ?: "Actividad"} · $whenText")
            }
            .sortedBy { it.at }
}
