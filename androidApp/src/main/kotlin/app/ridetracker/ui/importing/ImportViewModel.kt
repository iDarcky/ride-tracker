package app.ridetracker.ui.importing

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.importing.ReadReport
import app.ridetracker.importing.ReportReader
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.domain.ImportKind
import app.ridetracker.shared.domain.ImportOutcome
import app.ridetracker.shared.domain.ImportRepository
import app.ridetracker.shared.domain.IncomeRepository
import app.ridetracker.shared.domain.IncomeSource
import app.ridetracker.ui.common.resolveCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Currency
import kotlin.time.Clock

/** One picked or shared file and what was found in it. */
data class ImportItem(
    val uri: Uri,
    /** Null while reading. */
    val report: ReadReport? = null,
    val failed: Boolean = false,
    val alreadyImported: Boolean = false,
    /** Entries for the same app and day that saving will replace. */
    val replaces: Int = 0,
    val selected: Boolean = true,
) {
    val canSave: Boolean
        get() = !alreadyImported && when (report) {
            is ReadReport.BoltDay, is ReadReport.BoltTrips, is ReadReport.BoltMonth, is ReadReport.BoltPeriod -> true
            else -> false
        }
}

data class ImportUiState(
    val items: List<ImportItem> = emptyList(),
    val currency: Currency = resolveCurrency(null),
    val saving: Boolean = false,
    /** Set once saving finished: how many items were saved. */
    val savedCount: Int? = null,
) {
    val reading: Boolean get() = items.any { it.report == null && !it.failed }
    val toSave: List<ImportItem> get() = items.filter { it.canSave && it.selected }
}

class ImportViewModel(
    private val reader: ReportReader,
    private val importRepository: ImportRepository,
    private val incomeRepository: IncomeRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val platformLock = Mutex()
    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val currency = resolveCurrency(settingsRepository.settings.first().currencyCode)
            _state.update { it.copy(currency = currency) }
        }
    }

    fun add(uris: List<Uri>) {
        val fresh = uris.filter { uri -> _state.value.items.none { it.uri == uri } }
        if (fresh.isEmpty()) return
        _state.update { s -> s.copy(items = s.items + fresh.map { ImportItem(it) }, savedCount = null) }
        fresh.forEach { uri ->
            viewModelScope.launch {
                val report = runCatching { reader.read(uri) }.onFailure { Log.w("Import", "Could not read $uri", it) }.getOrNull()
                if (report == null) {
                    replace(uri) { it.copy(failed = true) }
                    return@launch
                }
                val duplicateInList = _state.value.items.any { it.uri != uri && it.report?.fileHash == report.fileHash }
                val already = duplicateInList || importRepository.isImported(report.fileHash)
                val replaces = if (report is ReadReport.BoltDay && !already) {
                    importRepository.existingEntries(boltPlatformId(), report.day)
                } else {
                    0
                }
                replace(uri) { it.copy(report = report, alreadyImported = already, replaces = replaces) }
            }
        }
    }

    /** Back to the start screen after saving. */
    fun startOver() = _state.update { it.copy(savedCount = null) }

    fun toggle(uri: Uri) = replace(uri) { it.copy(selected = !it.selected) }

    fun remove(uri: Uri) = _state.update { s -> s.copy(items = s.items.filterNot { it.uri == uri }) }

    fun save() {
        val items = _state.value.toSave
        if (items.isEmpty() || _state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val platformId = boltPlatformId()
            var saved = 0
            // Oldest days first, so a later screenshot of the same day wins.
            for (item in items.sortedBy { (it.report as? ReadReport.BoltDay)?.day?.date }) {
                val now = Clock.System.now().toEpochMilliseconds()
                val outcome = when (val r = item.report) {
                    is ReadReport.BoltDay ->
                        importRepository.saveDay(platformId, ImportKind.BOLT_DAILY_SCREENSHOT, IncomeSource.SCREENSHOT, r.fileHash, r.day, now)
                    is ReadReport.BoltTrips -> importRepository.saveTrips(platformId, ImportKind.BOLT_RIDER_INVOICES_CSV, r.fileHash, r.trips, now)
                    is ReadReport.BoltMonth -> importRepository.saveSummary(platformId, ImportKind.BOLT_MONTHLY_PDF, r.fileHash, r.summary, now)
                    is ReadReport.BoltPeriod -> importRepository.saveSummary(
                        platformId,
                        if (r.monthly) ImportKind.BOLT_MONTHLY_SCREENSHOT else ImportKind.BOLT_WEEKLY_SCREENSHOT,
                        r.fileHash,
                        r.summary,
                        now,
                    )
                    else -> null
                }
                if (outcome is ImportOutcome.Saved) saved++
            }
            _state.update { s -> s.copy(items = emptyList(), saving = false, savedCount = saved) }
        }
    }

    /** The user's Bolt app, created if they removed or renamed it. */
    private suspend fun boltPlatformId(): Long = platformLock.withLock {
        val platforms = incomeRepository.observePlatforms().first()
        val bolt = platforms.firstOrNull { it.name.trim().equals("Bolt", ignoreCase = true) && !it.archived }
            ?: platforms.firstOrNull { it.name.trim().equals("Bolt", ignoreCase = true) }
        bolt?.id ?: incomeRepository.addPlatform("Bolt", 0xFF34D186)
    }

    private fun replace(uri: Uri, change: (ImportItem) -> ImportItem) =
        _state.update { s -> s.copy(items = s.items.map { if (it.uri == uri) change(it) else it }) }
}
