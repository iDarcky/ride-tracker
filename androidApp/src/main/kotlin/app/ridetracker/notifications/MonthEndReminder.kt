package app.ridetracker.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.ridetracker.MainActivity
import app.ridetracker.R
import app.ridetracker.RideTrackerApplication
import app.ridetracker.shared.domain.MonthEnd
import app.ridetracker.shared.domain.Period
import app.ridetracker.ui.common.DateFormats
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

/**
 * On the 1st (and the 3rd, if still needed) around 10:00: "September is over", to import last month's files from Bolt
 * and Uber. It goes with the evening import reminder: on when that one is on, quiet once every app has its files.
 */
class MonthEndWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as RideTrackerApplication
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        if (today.day !in MonthEnd.REMINDER_DAYS) return Result.success()
        if (!app.container.settingsRepository.importReminder.first().enabled) return Result.success()
        val last = Period.Month.containing(today).previous()
        val status = app.container.importRepository.observeMonthEnd(last).first()
        if (status.apps.isNotEmpty() && !status.complete) notify(app, last)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "month_end_reminder"
        private const val NAME = "month-end-reminder"
        private const val NOTIFICATION_ID = 7_000_003

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.month_end_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.month_end_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** Once a day around 10:00; the worker itself only speaks on the reminder days. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.with(LocalTime.of(10, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<MonthEndWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun notify(context: Context, month: Period.Month) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val open = PendingIntent.getActivity(
                context, NOTIFICATION_ID,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_ROUTE, "import?month=true")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val locale = context.resources.configuration.locales[0]
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ring)
                .setContentTitle(context.getString(R.string.month_end_title, DateFormats(locale).monthName(month)))
                .setContentText(context.getString(R.string.month_end_body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            @Suppress("MissingPermission") // checked above
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
