package app.ridetracker.notifications

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
import app.ridetracker.MainActivity
import app.ridetracker.R
import app.ridetracker.RideTrackerApplication
import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.ImportReminderSettings
import app.ridetracker.shared.domain.ImportReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Clock

/**
 * The optional evening reminder to import the day's screenshots. Like the Raportul Z one: an alarm at the driver's
 * time every day (exact once "Alarms & reminders" is allowed). It stays quiet when today is already imported.
 */
object ImportReminderScheduler {
    const val CHANNEL_ID = "import_reminder"
    private const val NOTIFICATION_ID = 7_000_002

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.import_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.import_reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Sets the next alarm, or cancels it when the reminder is off. Safe to call any time. */
    fun schedule(context: Context, reminder: ImportReminderSettings) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        alarms.cancel(pending)
        if (!reminder.enabled) return
        val now = LocalDateTime.now()
        var next = now.with(LocalTime.of(reminder.hour, reminder.minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (ZReportReminderScheduler.canBeExact(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    suspend fun reschedule(context: Context) {
        val settings = (context.applicationContext as RideTrackerApplication).container.settingsRepository
        schedule(context, settings.importReminder.first())
    }

    fun notify(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_IMPORT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ring)
            .setContentTitle(context.getString(R.string.import_reminder_title))
            .setContentText(context.getString(R.string.import_reminder_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked above
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, NOTIFICATION_ID,
        Intent(context, ImportReminderReceiver::class.java).setAction(ImportReminderReceiver.ACTION_ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** The reminder's alarm: notifies unless today is already imported, then sets tomorrow's. */
class ImportReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return
        val container = (context.applicationContext as RideTrackerApplication).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (container.settingsRepository.importReminder.first().enabled) {
                    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                    val range = DateRange(today.minus(DatePeriod(days = ImportReminders.LOOKBACK_DAYS)), today)
                    val entries = container.incomeRepository.observeEntryDetails(range).first()
                    if (ImportReminders.shouldRemind(today, entries)) ImportReminderScheduler.notify(context)
                }
                ImportReminderScheduler.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "app.ridetracker.IMPORT_REMINDER_ALARM"
    }
}
