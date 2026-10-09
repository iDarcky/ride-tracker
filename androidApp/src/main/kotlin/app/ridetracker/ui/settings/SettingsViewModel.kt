package app.ridetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.applyThemeMode
import app.ridetracker.shared.data.AppSettings
import app.ridetracker.shared.data.BackupFile
import app.ridetracker.shared.data.BackupService
import app.ridetracker.shared.data.InvalidBackupException
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.domain.Country
import app.ridetracker.shared.domain.DrivingType
import app.ridetracker.shared.domain.ThemeMode
import app.ridetracker.shared.domain.ZReportReminder
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.TargetBasis
import app.ridetracker.shared.domain.TargetSettings
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlin.time.Clock

/** One-off results shown as a snackbar. */
enum class SettingsMessage { BACKUP_SAVED, BACKUP_FAILED, RESTORE_DONE, RESTORE_INVALID, RESTORE_NEWER }

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupService: BackupService,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A backup that was read and is waiting for the user to confirm the restore. */
    private val _pendingRestore = MutableStateFlow<BackupFile?>(null)
    val pendingRestore: StateFlow<BackupFile?> = _pendingRestore.asStateFlow()

    private val _message = MutableStateFlow<SettingsMessage?>(null)
    val message: StateFlow<SettingsMessage?> = _message.asStateFlow()

    fun messageShown() {
        _message.value = null
    }

    fun setCountry(country: Country, otherCurrencyCode: String? = null) {
        viewModelScope.launch { settingsRepository.setCountry(country, otherCurrencyCode) }
    }

    fun setDrivingType(type: DrivingType) {
        viewModelScope.launch { settingsRepository.setDrivingType(type) }
    }

    fun setThemeMode(mode: ThemeMode) {
        applyThemeMode(mode)
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    val zReport: StateFlow<ZReportReminder> = settingsRepository.zReport
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZReportReminder())

    fun setZReportEnabled(enabled: Boolean) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays()
        viewModelScope.launch { settingsRepository.setZReportEnabled(enabled, today) }
    }

    fun setZReportTime(hour: Int, minute: Int) {
        viewModelScope.launch { settingsRepository.setZReportTime(hour * 60 + minute) }
    }

    val target: StateFlow<TargetSettings> = settingsRepository.target
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TargetSettings())

    fun setTarget(month: Period.Month, amountMinor: Long) {
        viewModelScope.launch { settingsRepository.setTarget(month, amountMinor) }
    }

    fun setTargetBasis(basis: TargetBasis) {
        viewModelScope.launch { settingsRepository.setTargetBasis(basis) }
    }

    fun setDrivingDays(days: Set<DayOfWeek>) {
        viewModelScope.launch { settingsRepository.setDrivingDays(days) }
    }

    fun setFirstDayOfWeek(day: DayOfWeek) {
        viewModelScope.launch { settingsRepository.setFirstDayOfWeek(day) }
    }

    /** Produces the backup text; the screen writes it to the file the user picked. */
    suspend fun backupText(appVersion: String): String =
        backupService.export(appVersion, Clock.System.now().toEpochMilliseconds())

    fun backupFinished(success: Boolean) {
        _message.value = if (success) SettingsMessage.BACKUP_SAVED else SettingsMessage.BACKUP_FAILED
    }

    /** Checks a picked file; if valid, asks the user to confirm. */
    fun onRestoreFileRead(text: String?) {
        if (text == null) {
            _message.value = SettingsMessage.RESTORE_INVALID
            return
        }
        try {
            _pendingRestore.value = backupService.read(text)
        } catch (e: InvalidBackupException) {
            _message.value = if (e.message?.contains("newer") == true) SettingsMessage.RESTORE_NEWER else SettingsMessage.RESTORE_INVALID
        }
    }

    fun cancelRestore() {
        _pendingRestore.value = null
    }

    fun confirmRestore() {
        val file = _pendingRestore.value ?: return
        _pendingRestore.value = null
        viewModelScope.launch {
            backupService.restore(file)
            applyThemeMode(ThemeMode.fromId(file.settings.themeMode))
            _message.value = SettingsMessage.RESTORE_DONE
        }
    }

    fun eraseAll(onDone: () -> Unit) {
        viewModelScope.launch {
            backupService.eraseAll()
            applyThemeMode(ThemeMode.SYSTEM)
            onDone()
        }
    }
}
