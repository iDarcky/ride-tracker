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
import app.ridetracker.shared.domain.Country
import app.ridetracker.shared.domain.ZReportReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The daily Raportul Z reminder: an alarm at the driver's time every day, then a notification with "Gata". Exact when
 * the driver allows "Alarms & reminders" (Android 14+ asks); otherwise Android may deliver it up to an hour late.
 * Nothing leaves the phone.
 */
object ZReportReminderScheduler {
    const val CHANNEL_ID = "z_report"
    private const val NOTIFICATION_ID = 7_000_001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.z_channel), NotificationManager.IMPORTANCE_HIGH)
            .apply { description = context.getString(R.string.z_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Sets the next alarm, or cancels it when the reminder is off. Safe to call any time. */
    fun schedule(context: Context, reminder: ZReportReminder, country: Country?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        alarms.cancel(pending)
        if (!reminder.enabled || country != Country.ROMANIA) return
        val now = LocalDateTime.now()
        var next = now.with(LocalTime.of(reminder.hour, reminder.minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (canBeExact(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    /** Exact alarms are allowed (always before Android 12; on 14+ only after the driver allows them). */
    fun canBeExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    suspend fun reschedule(context: Context) {
        val settings = (context.applicationContext as RideTrackerApplication).container.settingsRepository
        schedule(context, settings.zReport.first(), settings.settings.first().country)
    }

    fun notify(context: Context, day: LocalDate) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val done = PendingIntent.getBroadcast(
            context, NOTIFICATION_ID,
            Intent(context, ZReportReceiver::class.java).setAction(ZReportReceiver.ACTION_DONE).putExtra(ZReportReceiver.EXTRA_DAY, day.toEpochDay()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_car)
            .setContentTitle(context.getString(R.string.z_notification_title))
            .setContentText(context.getString(R.string.z_notification_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.z_done), done)
            .build()
        @Suppress("MissingPermission") // checked above
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, NOTIFICATION_ID,
        Intent(context, ZReportReceiver::class.java).setAction(ZReportReceiver.ACTION_ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** The reminder's alarm, its "Gata" button, and the phone restarting or changing time (the alarm is set again). */
class ZReportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = (context.applicationContext as RideTrackerApplication).container.settingsRepository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_ALARM -> {
                        val reminder = settings.zReport.first()
                        val today = LocalDate.now()
                        val done = reminder.doneThrough?.let { it >= today.toEpochDay() } == true
                        if (reminder.enabled && !done && settings.settings.first().country == Country.ROMANIA) {
                            ZReportReminderScheduler.notify(context, today)
                        }
                    }
                    ACTION_DONE -> {
                        val day = intent.getLongExtra(EXTRA_DAY, LocalDate.now().toEpochDay())
                        settings.markZReportDone(day)
                        ZReportReminderScheduler.cancelNotification(context)
                    }
                }
                // Every case ends with tomorrow's (or the next) alarm in place.
                ZReportReminderScheduler.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "app.ridetracker.Z_REPORT_ALARM"
        const val ACTION_DONE = "app.ridetracker.Z_REPORT_DONE"
        const val EXTRA_DAY = "day"
    }
}

/** After a restart, an app update or a change of time or time zone, the reminder's alarm is set again. */
class ZReportRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ON) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ZReportReminderScheduler.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val RESCHEDULE_ON = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (Android 12+): "Alarms & reminders" allowed.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
