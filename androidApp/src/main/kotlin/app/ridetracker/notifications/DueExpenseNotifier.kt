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
import app.ridetracker.AppContainer
import app.ridetracker.MainActivity
import app.ridetracker.R
import app.ridetracker.shared.domain.ExpenseCategory
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.label
import app.ridetracker.ui.common.resolveCurrency
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.util.Locale
import kotlin.time.Clock

/** Posts one notification per recurring expense when it becomes due ("ask first": nothing is added by itself). */
object DueExpenseNotifier {
    const val CHANNEL_ID = "recurring_expenses"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notificationId(ruleId: Long): Int = (ruleId % Int.MAX_VALUE).toInt()

    /** Notifies each due occurrence once; already-notified ones wait on Home instead of nagging. */
    suspend fun notifyDue(context: Context, container: AppContainer) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val currency = resolveCurrency(container.settingsRepository.settings.first().currencyCode)
        val money = MoneyFormat(currency, Locale.getDefault())
        val manager = NotificationManagerCompat.from(context)
        container.recurringRepository.getPending(today)
            .filter { it.rule.notifiedDueDate != it.rule.nextDueDate }
            .forEach { pending ->
                val rule = pending.rule
                val name = rule.note ?: context.getString(ExpenseCategory.fromId(rule.category).label)
                val id = notificationId(rule.id)
                val open = PendingIntent.getActivity(
                    context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val add = PendingIntent.getBroadcast(
                    context, id, AddDueExpenseReceiver.intent(context, rule.id, rule.nextDueDate),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_car)
                    .setContentTitle(context.getString(R.string.notification_due_title, name))
                    .setContentText(context.getString(R.string.notification_due_body, money.format(rule.amountMinor)))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .addAction(0, context.getString(R.string.add), add)
                    .build()
                @Suppress("MissingPermission") // checked above
                manager.notify(id, notification)
                container.recurringRepository.markNotified(rule)
            }
    }
}
