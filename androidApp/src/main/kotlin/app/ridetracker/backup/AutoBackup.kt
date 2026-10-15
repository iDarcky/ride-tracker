package app.ridetracker.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.ridetracker.BuildConfig
import app.ridetracker.RideTrackerApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

/**
 * The weekly backup off the phone: the same file as Your data → Back up, written over one file the driver chose once
 * (on Google Drive, or anywhere the phone's file picker offers). Google Drive can't give an app a folder, only a file,
 * so it's one file, replaced each week. Nothing is sent anywhere else.
 */
object AutoBackup {
    private const val NAME = "auto-backup"

    /** Starts the weekly backup (the first one runs now), or stops it. */
    fun schedule(context: Context, enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build()
        work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Writes the backup over the chosen file and records the result. Skipped (true, nothing written) while the app has
     * no data, so a new or erased phone never overwrites a good backup with an empty one.
     */
    suspend fun run(context: Context): Boolean {
        val container = (context.applicationContext as RideTrackerApplication).container
        val settings = container.settingsRepository
        val uri = settings.autoBackup.first().fileUri?.let(Uri::parse) ?: return false
        if (container.incomeRepository.firstDay() == null) return true
        val now = Clock.System.now().toEpochMilliseconds()
        val ok = runCatching {
            val text = container.backupService.export(BuildConfig.VERSION_NAME, now)
            withContext(Dispatchers.IO) {
                // "wt" truncates first; some providers (Google Drive among them) only take "w".
                val stream = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
                    ?: context.contentResolver.openOutputStream(uri, "w")
                stream!!.use { it.write(text.toByteArray()) }
            }
        }.onFailure { Log.w("AutoBackup", "Could not write the backup", it) }.isSuccess
        settings.setAutoBackupResult(ok, now)
        return ok
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AutoBackup.run(applicationContext)
        return Result.success()
    }
}
