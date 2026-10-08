package app.ridetracker.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.shared.data.EntryWithPlatform
import app.ridetracker.shared.data.PlatformTotal
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.domain.DeletedEntry
import app.ridetracker.shared.domain.IncomeBreakdown
import app.ridetracker.shared.domain.IncomeBreakdownCalculator
import app.ridetracker.shared.domain.ImportRepository
import app.ridetracker.shared.domain.IncomeRepository
import app.ridetracker.shared.domain.Period
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class IncomeDay(val date: LocalDate, val totalMinor: Long, val entries: List<EntryWithPlatform>)

data class IncomeListState(
    val months: List<Period.Month> = emptyList(),
    val month: Period.Month? = null,
    val totalMinor: Long = 0,
    /** The whole previous month's income; null when it has none. */
    val previousMinor: Long? = null,
    val platforms: List<PlatformTotal> = emptyList(),
    /** What the month's income is made of, and how it was paid. */
    val breakdown: IncomeBreakdown? = null,
    val days: List<IncomeDay> = emptyList(),
    val currencyCode: String? = null,
    val loading: Boolean = true,
)

/** Income of one month: total, by platform and by type, and the list. */
@OptIn(ExperimentalCoroutinesApi::class)
class IncomeListViewModel(
    private val incomeRepository: IncomeRepository,
    private val importRepository: ImportRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val selectedMonth = MutableStateFlow<Period.Month?>(null)

    val state: StateFlow<IncomeListState> = combine(
        incomeRepository.observeAllEntries(),
        selectedMonth,
        settingsRepository.settings,
    ) { all, selected, settings -> Triple(all, selected, settings.currencyCode) }
        .flatMapLatest { (all, selected, currency) ->
            val current = Period.Month.containing(Clock.System.todayIn(TimeZone.currentSystemDefault()))
            val months = (all.map { Period.Month.containing(LocalDate.fromEpochDays(it.date)) } + current).distinct()
                .sortedByDescending { it.range.start }
            val month = selected?.takeIf { it in months } ?: current
            fun inMonth(m: Period.Month) = all.filter { LocalDate.fromEpochDays(it.date) in m.range.start..m.range.endInclusive }
            val monthly = inMonth(month)
            val previous = inMonth(month.previous() as Period.Month)
            combine(
                incomeRepository.observeTotals(month.range),
                incomeRepository.observeEntryDetails(month.range),
                incomeRepository.observeLines(month.range),
                importRepository.observeSummaries(month.range),
                importRepository.observeTrips(month.range),
            ) { totals, details, lines, summaries, trips ->
                IncomeListState(
                    months = months,
                    month = month,
                    totalMinor = monthly.sumOf { it.amountMinor },
                    previousMinor = previous.takeIf { it.isNotEmpty() }?.sumOf { it.amountMinor },
                    platforms = totals,
                    breakdown = IncomeBreakdownCalculator.compute(month.range, details, lines, trips, summaries),
                    days = monthly.groupBy { it.date }.map { (day, list) ->
                        IncomeDay(LocalDate.fromEpochDays(day), list.sumOf { it.amountMinor }, list)
                    },
                    currencyCode = currency,
                    loading = false,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncomeListState())

    fun selectMonth(month: Period.Month) {
        selectedMonth.value = month
    }

    /** Deletes the entry and returns it so the UI can offer Undo. */
    suspend fun delete(id: Long): DeletedEntry? = incomeRepository.deleteForUndo(id)

    fun restore(deleted: DeletedEntry) {
        viewModelScope.launch { incomeRepository.restore(deleted) }
    }
}
