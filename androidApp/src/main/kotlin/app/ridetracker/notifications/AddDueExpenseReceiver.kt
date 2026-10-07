package app.ridetracker.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.ridetracker.RideTrackerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/** Handles "Add" on a due-expense notification: logs the expense and clears the notification. */
class AddDueExpenseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getLongExtra(EXTRA_RULE, -1)
        val due = intent.getLongExtra(EXTRA_DUE, Long.MIN_VALUE)
        if (ruleId < 0 || due == Long.MIN_VALUE) return
        val app = context.applicationContext as RideTrackerApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.recurringRepository.accept(ruleId, LocalDate.fromEpochDays(due), Clock.System.now().toEpochMilliseconds())
                NotificationManagerCompat.from(context).cancel(DueExpenseNotifier.notificationId(ruleId))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_RULE = "rule"
        private const val EXTRA_DUE = "due"

        fun intent(context: Context, ruleId: Long, dueEpochDay: Long): Intent =
            Intent(context, AddDueExpenseReceiver::class.java).putExtra(EXTRA_RULE, ruleId).putExtra(EXTRA_DUE, dueEpochDay)
    }
}
