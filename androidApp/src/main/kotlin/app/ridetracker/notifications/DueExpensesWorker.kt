package app.ridetracker.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.ridetracker.RideTrackerApplication
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Runs once a day (around 9:00) on the phone and notifies recurring expenses that became due. No server. */
class DueExpensesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as RideTrackerApplication
        DueExpenseNotifier.notifyDue(app, app.container)
        return Result.success()
    }

    companion object {
        private const val NAME = "due-expenses"

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.with(LocalTime.of(9, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<DueExpensesWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
