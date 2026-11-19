package me.luiscruzandresgomezrodolfohernandez.geolux.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.luiscruzandresgomezrodolfohernandez.geolux.GeoLuxApplication
import me.luiscruzandresgomezrodolfohernandez.geolux.R
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ReminderPlanner

/**
 * Recordatorios de actividades con AlarmManager (sin dependencias nuevas). Se usan alarmas
 * inexactas que respetan el ahorro de batería (`setAndAllowWhileIdle`): pueden llegar unos
 * minutos tarde, pero no piden el permiso de alarmas exactas. Cada aviso tiene una clave
 * (actividad + fecha + hora + minutos), así que reprogramar no duplica; los que ya no
 * corresponden se cancelan. Tras reiniciar el teléfono, [BootReceiver] los vuelve a programar.
 */
object ReminderScheduler {
    private const val PREFS = "recordatorios"
    /** Avisos ya mostrados (por clave): no se vuelven a programar. */
    private const val SHOWN = "recordatorios_mostrados"
    private const val CHANNEL = "actividades"
    const val EXTRA_TITLE = "titulo"
    const val EXTRA_TEXT = "texto"
    const val EXTRA_KEY = "clave"

    fun reschedule(context: Context, data: ManagementData, now: Instant = Instant.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prefix = "${data.projectId}::"
        val shown = context.getSharedPreferences(SHOWN, Context.MODE_PRIVATE).all.keys
        val planned = ReminderPlanner.plan(data, now).filter { prefix + it.key !in shown }
        val wanted = planned.associateBy { prefix + it.key }
        // Cancela los de esta finca que ya no corresponden (completada, borrada, otra hora).
        prefs.all.keys.filter { it.startsWith(prefix) && it !in wanted }.forEach { key ->
            alarms.cancel(pending(context, key, null, null))
            prefs.edit().remove(key).apply()
        }
        for ((key, reminder) in wanted) {
            val trigger = maxOf(reminder.at.toEpochMilli(), now.toEpochMilli() + 5_000)
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending(context, key, reminder.title, reminder.text))
            prefs.edit().putLong(key, trigger).apply()
        }
    }

    private fun pending(context: Context, key: String, title: String?, text: String?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction("geolux.recordatorio.$key")
            .putExtra(EXTRA_KEY, key).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text)
        return PendingIntent.getBroadcast(context, key.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Actividades de la finca", NotificationManager.IMPORTANCE_DEFAULT))
    }

    internal fun show(context: Context, key: String, title: String, text: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permiso retirado entre la comprobación y el aviso: no se muestra.
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).apply()
        context.getSharedPreferences(SHOWN, Context.MODE_PRIVATE).edit().putLong(key, System.currentTimeMillis()).apply()
    }

    /**
     * Tras eliminar una finca: cancela sus avisos pendientes y olvida los ya mostrados. Solo
     * las claves de esa finca (`<id>::…`); las de las demás fincas no se tocan.
     */
    fun forgetProject(context: Context, projectId: String) {
        reschedule(context, ManagementData(projectId))
        val prefix = "$projectId::"
        val shown = context.getSharedPreferences(SHOWN, Context.MODE_PRIVATE)
        shown.edit().apply { shown.all.keys.filter { it.startsWith(prefix) }.forEach(::remove) }.apply()
    }

    /** Reprograma los avisos de todas las fincas (al arrancar el teléfono). */
    fun rescheduleAll(context: Context) {
        val app = context.applicationContext as? GeoLuxApplication ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val repository = app.projectRepository
            // Cada finca por separado: un problema en una no deja sin avisos a las demás.
            val projects = runCatching { repository.projects().first() }.getOrDefault(emptyList())
            projects.forEach { summary -> runCatching { reschedule(context, repository.management(summary.id)) } }
        }
    }
}

/** Muestra el aviso cuando llega la alarma. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(ReminderScheduler.EXTRA_KEY) ?: return
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: return
        ReminderScheduler.show(context, key, title, intent.getStringExtra(ReminderScheduler.EXTRA_TEXT).orEmpty())
    }
}

/** Tras reiniciar el teléfono las alarmas se pierden: se vuelven a programar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) ReminderScheduler.rescheduleAll(context)
    }
}
