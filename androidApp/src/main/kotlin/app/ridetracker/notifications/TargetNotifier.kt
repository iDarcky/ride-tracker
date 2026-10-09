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
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.TargetBasis
import app.ridetracker.shared.domain.TargetSettings
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.resolveCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.util.Locale
import kotlin.time.Clock

/**
 * "Monthly target reached": once a month, when the month's income (or money kept) goes from below its target to the
 * target or above, e.g. after an import. Setting a target that is already reached doesn't notify.
 */
object TargetNotifier {
    private const val CHANNEL_ID = "targets"
    private const val NOTIFICATION_ID = 7_000_002

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.target_channel), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.target_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun watch(context: Context, container: AppContainer, scope: CoroutineScope) {
        val settings = container.settingsRepository
        // The current month, looked at again every 15 minutes so a new month is noticed.
        val months = flow {
            while (true) {
                emit(Period.Month.containing(Clock.System.todayIn(TimeZone.currentSystemDefault())))
                delay(15 * 60_000L)
            }
        }.distinctUntilChanged()
        // Below the target seen for this month and amount: only then does reaching it notify.
        val below = mutableSetOf<String>()
        scope.launch {
            combine(months, settings.target) { month, target -> month to target }
                .flatMapLatest { (month, target) ->
                    val amount = target.targetFor(month) ?: return@flatMapLatest flowOf(null)
                    combine(container.incomeRepository.observeTotals(month.range), container.expenseRepository.observeInRange(month.range)) { totals, expenses ->
                        val income = totals.sumOf { it.totalMinor }
                        val achieved = if (target.basis == TargetBasis.KEPT) income - expenses.sumOf { it.amountMinor } else income
                        Triple(month, amount, achieved)
                    }
                }
                .collect { state ->
                    val (month, amount, achieved) = state ?: return@collect
                    val key = "${TargetSettings.key(month)}:$amount"
                    if (achieved < amount) {
                        below += key
                    } else if (key in below) {
                        below -= key
                        if (settings.target.first().notifiedMonth != TargetSettings.key(month)) {
                            notify(context, container, month, achieved, amount)
                            settings.markTargetNotified(month)
                        }
                    }
                }
        }
    }

    private suspend fun notify(context: Context, container: AppContainer, month: Period.Month, achieved: Long, target: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val money = MoneyFormat(resolveCurrency(container.settingsRepository.settings.first().currencyCode), Locale.getDefault())
        val monthName = DateFormats(Locale.getDefault()).period(month)
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_car)
            .setContentTitle(context.getString(R.string.target_notification_title))
            .setContentText(context.getString(R.string.target_notification_body, money.format(achieved), monthName, money.format(target)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked above
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
