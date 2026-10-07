package app.ridetracker

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import app.ridetracker.shared.data.BackupService
import app.ridetracker.shared.data.createAppDatabase
import app.ridetracker.shared.data.createSettingsRepository
import app.ridetracker.shared.domain.ExpenseRepository
import app.ridetracker.shared.domain.IncomeRepository
import app.ridetracker.shared.domain.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** App-wide singletons (manual dependency injection). */
class AppContainer(context: Context) {
    private val database = createAppDatabase(context)
    val incomeRepository = IncomeRepository(database)
    val expenseRepository = ExpenseRepository(database)
    val settingsRepository = createSettingsRepository(context)
    val backupService = BackupService(database, settingsRepository)
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
