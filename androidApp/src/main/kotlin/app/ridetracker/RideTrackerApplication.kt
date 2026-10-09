package app.ridetracker

import android.app.Application
import android.content.Context
import android.net.Uri
import app.ridetracker.importing.ReportReader
import app.ridetracker.notifications.DueExpenseNotifier
import app.ridetracker.notifications.DueExpensesWorker
import app.ridetracker.notifications.ZReportReminderScheduler
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.appcompat.app.AppCompatDelegate
import app.ridetracker.shared.data.BackupService
import app.ridetracker.shared.data.createAppDatabase
import app.ridetracker.shared.data.createSettingsRepository
import app.ridetracker.shared.domain.ExpenseRepository
import app.ridetracker.shared.domain.ImportRepository
import app.ridetracker.shared.domain.IncomeRepository
import app.ridetracker.shared.domain.RecurringRepository
import app.ridetracker.shared.domain.ThemeMode
import app.ridetracker.shared.domain.VehicleRepository
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** App-wide singletons (manual dependency injection). */
class AppContainer(context: Context) {
    private val database = createAppDatabase(context)
    val incomeRepository = IncomeRepository(database)
    val expenseRepository = ExpenseRepository(database)
    val vehicleRepository = VehicleRepository(database)
    val recurringRepository = RecurringRepository(database)
    val settingsRepository = createSettingsRepository(context)
    val backupService = BackupService(database, settingsRepository)
    val importRepository = ImportRepository(database)
    val reportReader = ReportReader(context)

    /** Files shared to the app from the gallery or a file manager, waiting for the Import screen. */
    val sharedFiles = MutableStateFlow<List<Uri>>(emptyList())
}

class RideTrackerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Apply the saved theme before the first frame so there is no light/dark flash.
        // A tiny local file read; done once at startup.
        applyThemeMode(runBlocking { container.settingsRepository.settings.first().themeMode })
        DueExpenseNotifier.createChannel(this)
        DueExpensesWorker.schedule(this)
        ZReportReminderScheduler.createChannel(this)
        // The Raportul Z alarm follows its settings (on/off, time) and the country, whatever changes them.
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            combine(container.settingsRepository.zReport, container.settingsRepository.settings.map { it.country }) { z, country -> z to country }
                .distinctUntilChanged { a, b -> a.first.enabled == b.first.enabled && a.first.minuteOfDay == b.first.minuteOfDay && a.second == b.second }
                .collect { (z, country) -> ZReportReminderScheduler.schedule(this@RideTrackerApplication, z, country) }
        }
        // Fix breakdown lines imported before the reader recognised their label.
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            container.importRepository.reclassifyLines()
            container.importRepository.refreshEstimates(kotlin.time.Clock.System.now().toEpochMilliseconds())
        }
    }
}

/** AppCompat night mode drives both the window and Compose's isSystemInDarkTheme(). */
fun applyThemeMode(mode: ThemeMode) {
    AppCompatDelegate.setDefaultNightMode(
        when (mode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        },
    )
}
