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
import app.ridetracker.MainActivity
import app.ridetracker.R
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.Summary
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.summaryRoute

/** "Last week: €1,840 kept" on Monday, and "Your September is ready" once the month is complete. */
object SummaryNotifier {
    const val CHANNEL_ID = "summaries"
    private const val WEEK_ID = 7_000_004
    private const val MONTH_ID = 7_000_005

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.summary_channel), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.summary_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun week(context: Context, summary: Summary, currencyCode: String?) {
        val money = MoneyFormat(resolveCurrency(currencyCode), locale(context))
        post(
            context, WEEK_ID, summaryRoute(summary.range),
            context.getString(R.string.summary_week_notification, money.format(summary.keptMinor)),
            context.getString(R.string.summary_week_notification_body),
        )
    }

    fun month(context: Context, month: Period.Month, complete: Boolean) {
        val name = DateFormats(locale(context)).monthName(month)
        post(
            context, MONTH_ID, summaryRoute(month.range),
            context.getString(R.string.summary_month_ready, name).replaceFirstChar { it.titlecase(locale(context)) },
            context.getString(if (complete) R.string.summary_month_notification_body else R.string.summary_month_notification_body_partial),
        )
    }

    private fun locale(context: Context) = context.resources.configuration.locales[0]

    private fun post(context: Context, id: Int, route: String, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ring)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked above
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
