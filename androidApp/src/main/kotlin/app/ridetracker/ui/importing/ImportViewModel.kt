package app.ridetracker.ui.importing

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.importing.ReadReport
import app.ridetracker.importing.ReportReader
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.ImportChecklist
import app.ridetracker.shared.domain.ImportChecklists
import app.ridetracker.shared.domain.ImportKind
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.ImportOutcome
import app.ridetracker.shared.domain.ImportRepository
import app.ridetracker.shared.domain.IncomeRepository
import app.ridetracker.shared.domain.IncomeSource
import app.ridetracker.shared.domain.importing.OnlineTime
import app.ridetracker.ui.common.resolveCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Currency
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

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
    /** The driver corrected or typed in what was read. */
    val edited: Boolean = false,
) {
    val canSave: Boolean
        get() = !alreadyImported && when (report) {
            is ReadReport.BoltDay, is ReadReport.BoltTrips, is ReadReport.BoltMonth, is ReadReport.BoltPeriod,
            is ReadReport.BoltActivity, is ReadReport.UberDays, is ReadReport.UberTrips, is ReadReport.UberTotals,
            is ReadReport.UberHours, is ReadReport.UberDay, is ReadReport.UberWeek -> true
            // Half of the Payments screen can't be saved until the other half joins it.
            is ReadReport.UberPaymentsScreen -> report.week.complete
            else -> false
        }
}

data class ImportUiState(
    val items: List<ImportItem> = emptyList(),
    val currency: Currency = resolveCurrency(null),
    val saving: Boolean = false,
    /** Set once saving finished: how many items were saved. */
    val savedCount: Int? = null,
    /** What last month and this month have from Bolt, newest first. */
    val checklists: List<ImportChecklist> = emptyList(),
    /** The same for Uber. */
    val uberChecklists: List<ImportChecklist> = emptyList(),
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
        viewModelScope.launch {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val current = Period.Month.containing(today)
            val months = listOf(current, current.previous() as Period.Month)
            val range = DateRange(months.last().range.start, current.range.endInclusive)
            combine(
                incomeRepository.observePlatforms(),
                incomeRepository.observeEntryDetails(range),
                importRepository.observeTrips(range),
                importRepository.observeSummaries(range),
            ) { platforms, entries, trips, summaries ->
                fun id(name: String) = platforms.firstOrNull { it.name.trim().equals(name, ignoreCase = true) }?.id ?: -1L
                val bolt = id(BOLT)
                val uber = id(UBER)
                months.map { ImportChecklists.compute(it, bolt, entries, trips, summaries, today) } to
                    months.map { ImportChecklists.compute(it, uber, entries, trips, summaries, today) }
            }.collect { (bolt, uber) -> _state.update { it.copy(checklists = bolt, uberChecklists = uber) } }
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
                val replaces = if (already) 0 else replaces(report)
                replace(uri) { it.copy(report = report, alreadyImported = already, replaces = replaces) }
                joinPaymentParts()
            }
        }
    }

    /** Back to the start screen after saving. */
    fun startOver() = _state.update { it.copy(savedCount = null) }

    fun toggle(uri: Uri) = replace(uri) { it.copy(selected = !it.selected) }

    /** Keeps the driver's corrections (or a screenshot typed in by hand) in place of what was read. */
    fun edit(uri: Uri, report: ReadReport) {
        replace(uri) { it.copy(report = report, edited = true, selected = true) }
        if (report is ReadReport.BoltDay || report is ReadReport.UberDays || report is ReadReport.UberDay) {
            viewModelScope.launch {
                val replaces = replaces(report)
                replace(uri) { it.copy(replaces = replaces) }
            }
        }
    }

    /** Entries already in the app that saving [report] replaces (same app and day). */
    private suspend fun replaces(report: ReadReport): Int = when (report) {
        is ReadReport.BoltDay -> importRepository.existingEntries(platformId(BOLT, BOLT_COLOR), report.day)
        is ReadReport.UberDays -> platformId(UBER, UBER_COLOR).let { uber -> report.payments.days.sumOf { importRepository.existingEntries(uber, it) } }
        is ReadReport.UberDay -> importRepository.existingEntries(platformId(UBER, UBER_COLOR), report.day)
        else -> 0
    }

    fun remove(uri: Uri) = _state.update { s -> s.copy(items = s.items.filterNot { it.uri == uri }) }

    fun save() {
        val items = _state.value.toSave
        if (items.isEmpty() || _state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val platformId = platformId(BOLT, BOLT_COLOR)
            val uber = platformId(UBER, UBER_COLOR)
            var saved = 0
            // Oldest days first, so a later screenshot of the same day wins.
            for (item in items.sortedBy { (it.report as? ReadReport.BoltDay)?.day?.date ?: (it.report as? ReadReport.UberDay)?.day?.date }) {
                val now = Clock.System.now().toEpochMilliseconds()
                val outcome = when (val r = item.report) {
                    is ReadReport.BoltDay ->
                        importRepository.saveDay(platformId, ImportKind.BOLT_DAILY_SCREENSHOT, IncomeSource.SCREENSHOT, r.fileHash, r.day, now)
                    is ReadReport.BoltTrips -> importRepository.saveTrips(platformId, ImportKind.BOLT_RIDER_INVOICES_CSV, r.fileHash, r.trips, now)
                    is ReadReport.BoltMonth -> importRepository.saveSummary(platformId, ImportKind.BOLT_MONTHLY_PDF, r.fileHash, r.summary, now)
                    is ReadReport.BoltActivity ->
                        importRepository.saveOnlineTimes(platformId, ImportKind.BOLT_ACTIVITY_SCREENSHOT, r.fileHash, r.times, now)
                    is ReadReport.BoltPeriod -> importRepository.saveSummary(
                        platformId,
                        if (r.monthly) ImportKind.BOLT_MONTHLY_SCREENSHOT else ImportKind.BOLT_WEEKLY_SCREENSHOT,
                        r.fileHash,
                        r.summary,
                        now,
                    )
                    is ReadReport.UberDays -> importRepository.saveDays(
                        uber, ImportKind.UBER_PAYMENTS_CSV, IncomeSource.CSV, r.fileHash, r.payments.period, r.payments.days,
                        r.payments.tripFares, now,
                    )
                    is ReadReport.UberDay -> importRepository.saveDay(
                        uber, ImportKind.UBER_DAILY_SCREENSHOT, IncomeSource.SCREENSHOT, r.fileHash, r.day, now,
                        onlineMinutes = r.onlineMinutes, tripCount = r.trips,
                    )
                    is ReadReport.UberWeek -> importRepository.saveSummary(
                        uber, ImportKind.UBER_WEEKLY_SCREENSHOT, r.fileHash, r.summary, now, onlineMinutes = r.onlineMinutes,
                    )
                    is ReadReport.UberPaymentsScreen -> r.week.asSummary()?.let {
                        importRepository.saveSummary(uber, ImportKind.UBER_PAYMENTS_SCREENSHOT, r.fileHash, it, now)
                    }
                    is ReadReport.UberTrips -> importRepository.mergeTrips(uber, ImportKind.UBER_TRIPS_CSV, r.fileHash, r.trips, now)
                    is ReadReport.UberTotals -> importRepository.saveSummary(uber, ImportKind.UBER_TOTALS_CSV, r.fileHash, r.summary, now)
                    is ReadReport.UberHours -> importRepository.saveOnlineTimes(
                        uber, ImportKind.UBER_TIME_DISTANCE_CSV, r.fileHash,
                        listOf(OnlineTime(r.time.period, r.time.onlineMinutes)), now, distanceMeters = r.time.distanceMeters,
                    )
                    else -> null
                }
                if (outcome is ImportOutcome.Saved) saved++
            }
            // Days without a screenshot are filled from the new totals and trips (never the exact days).
            importRepository.refreshEstimates(Clock.System.now().toEpochMilliseconds())
            _state.update { s -> s.copy(items = emptyList(), saving = false, savedCount = saved) }
        }
    }

    /**
     * Uber's Payments screen is taller than the phone: the part with the week and the part below it, picked
     * together, become one. Parts are matched by a figure both show (Uber's fee, say); with just one of each, they go
     * together anyway.
     */
    private fun joinPaymentParts() = _state.update { s ->
        fun parts() = s.items.mapNotNull { item -> (item.report as? ReadReport.UberPaymentsScreen)?.let { item to it } }
            .filter { (_, r) -> !r.week.complete }
        var items = s.items
        val tops = parts().filter { it.second.week.week != null }
        val bottoms = parts().filter { it.second.week.week == null }
        for ((topItem, top) in tops) {
            val bottom = bottoms.firstOrNull { it.second.week.overlaps(top.week) }
                ?: bottoms.singleOrNull()?.takeIf { tops.size == 1 }
                ?: continue
            val (bottomItem, part) = bottom
            items = items.map {
                when (it.uri) {
                    topItem.uri -> it.copy(report = top.copy(week = top.week.merge(part.week), joined = top.joined + part.fileHash))
                    bottomItem.uri -> it.copy(report = ReadReport.UberJoined(part.fileHash, top.fileHash))
                    else -> it
                }
            }
        }
        s.copy(items = items)
    }

    /** The user's Bolt or Uber app, created if they removed or renamed it. */
    private suspend fun platformId(name: String, color: Long): Long = platformLock.withLock {
        val platforms = incomeRepository.observePlatforms().first()
        val found = platforms.firstOrNull { it.name.trim().equals(name, ignoreCase = true) && !it.archived }
            ?: platforms.firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
        found?.id ?: incomeRepository.addPlatform(name, color)
    }

    private companion object {
        const val BOLT = "Bolt"
        const val UBER = "Uber"
        const val BOLT_COLOR = 0xFF34D186
        const val UBER_COLOR = 0xFF000000
    }

    private fun replace(uri: Uri, change: (ImportItem) -> ImportItem) =
        _state.update { s -> s.copy(items = s.items.map { if (it.uri == uri) change(it) else it }) }
}
