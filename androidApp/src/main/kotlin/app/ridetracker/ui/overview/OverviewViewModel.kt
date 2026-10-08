package app.ridetracker.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.shared.data.PlatformEntity
import app.ridetracker.shared.data.PlatformTotal
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.domain.Comparison
import app.ridetracker.shared.domain.Comparisons
import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.ExpenseCategory
import app.ridetracker.shared.domain.ExpenseGroup
import app.ridetracker.shared.domain.ExpenseRepository
import app.ridetracker.shared.domain.HomeStats
import app.ridetracker.shared.domain.HomeStatsCalculator
import app.ridetracker.shared.domain.HomeWidget
import app.ridetracker.shared.domain.ImportRepository
import app.ridetracker.shared.domain.IncomeBreakdown
import app.ridetracker.shared.domain.IncomeBreakdownCalculator
import app.ridetracker.shared.domain.IncomeRepository
import app.ridetracker.shared.domain.MissingMonthlyTotal
import app.ridetracker.shared.domain.PendingExpense
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.RecurringRepository
import app.ridetracker.shared.domain.PeriodType
import app.ridetracker.shared.domain.periodOf
import app.ridetracker.shared.domain.type
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class GroupTotal(val group: ExpenseGroup, val totalMinor: Long)

data class OverviewUiState(
    val period: Period,
    val today: LocalDate,
    /** App currency code, or null for the device default. */
    val currencyCode: String?,
    /** Income in the period. */
    val totalMinor: Long = 0,
    val totals: List<PlatformTotal> = emptyList(),
    val entryCount: Int = 0,
    val expenseMinor: Long = 0,
    val expenseGroups: List<GroupTotal> = emptyList(),
    /** Once any expense exists, Home shows "money kept" instead of "total income". */
    val tracksExpenses: Boolean = false,
    /** What this period is compared with; null for future periods. */
    val comparison: Comparison? = null,
    /** Totals of the comparison range; null when that range has no data (then nothing is shown). */
    val previous: PreviousTotals? = null,
    val loading: Boolean = true,
    /** Gross, fees, metrics, daily activity and heat map for the period. */
    val stats: HomeStats? = null,
    /** What the period's income is made of and how it was paid (Card and cash). */
    val breakdown: IncomeBreakdown? = null,
    /** All apps (names and colours for charts), including archived ones that still have history. */
    val platforms: List<PlatformEntity> = emptyList(),
    /** Cards shown below money kept, in the driver's order. */
    val widgets: List<HomeWidget> = HomeWidget.DEFAULT,
) {
    val keptMinor: Long get() = totalMinor - expenseMinor

    /** The headline number: money kept once expenses are tracked, otherwise income. */
    val headlineMinor: Long get() = if (tracksExpenses) keptMinor else totalMinor
    val previousHeadlineMinor: Long? get() = previous?.let { if (tracksExpenses) it.incomeMinor - it.expenseMinor else it.incomeMinor }
}

/** What "Needs attention" on Home lists. */
data class Attention(
    val pending: List<PendingExpense> = emptyList(),
    val missingMonthly: List<MissingMonthlyTotal> = emptyList(),
) {
    fun isEmpty(): Boolean = pending.isEmpty() && missingMonthly.isEmpty()
}

data class PreviousTotals(val incomeMinor: Long, val expenseMinor: Long)

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModel(
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val importRepository: ImportRepository,
    private val recurringRepository: RecurringRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private val selected = MutableStateFlow<Period>(Period.Month.containing(today()))

    val uiState: StateFlow<OverviewUiState> = combine(selected, settingsRepository.settings) { period, settings ->
        // Re-align weeks when the first-day-of-week setting changes.
        val aligned = if (period is Period.Week) Period.Week.containing(period.start, settings.firstDayOfWeek) else period
        Triple(aligned, settings.currencyCode, settings.homeWidgets)
    }.flatMapLatest { (period, currencyCode, widgets) ->
        val comparison = Comparisons.of(period, today())
        val previousFlow: Flow<PreviousTotals?> = if (comparison == null) {
            flowOf(null)
        } else {
            combine(
                incomeRepository.observeTotals(comparison.range),
                expenseRepository.observeInRange(comparison.range),
            ) { totals, expenses ->
                val income = totals.sumOf { it.totalMinor }
                val expense = expenses.sumOf { it.amountMinor }
                // Only compare when the earlier period actually has data.
                if (totals.isEmpty() && expenses.isEmpty()) null else PreviousTotals(income, expense)
            }
        }
        val statsFlow: Flow<Pair<HomeStats, IncomeBreakdown>> = combine(
            incomeRepository.observeEntryDetails(period.range),
            incomeRepository.observeLines(period.range),
            importRepository.observeTrips(period.range),
            importRepository.observeSummaries(period.range),
        ) { details, lines, trips, summaries ->
            HomeStatsCalculator.compute(period.range, details, lines, trips, summaries) to
                IncomeBreakdownCalculator.compute(period.range, details, lines, trips, summaries)
        }
        val extras = combine(statsFlow, incomeRepository.observePlatforms()) { stats, platforms -> stats to platforms }
        combine(
            incomeRepository.observeEntries(period.range),
            incomeRepository.observeTotals(period.range),
            expenseRepository.observeInRange(period.range),
            combine(expenseRepository.observeAny(), previousFlow) { any, previous -> any to previous },
            extras,
        ) { entries, totals, expenses, (anyExpense, previous), (statsAndBreakdown, platforms) ->
            val (stats, breakdown) = statsAndBreakdown
            OverviewUiState(
                widgets = widgets,
                stats = stats,
                breakdown = breakdown,
                platforms = platforms,
                comparison = comparison,
                previous = previous,
                period = period,
                today = today(),
                currencyCode = currencyCode,
                totalMinor = totals.sumOf { it.totalMinor },
                totals = totals,
                entryCount = entries.size,
                expenseMinor = expenses.sumOf { it.amountMinor },
                expenseGroups = expenses
                    .groupBy { ExpenseCategory.fromId(it.category).group }
                    .map { (group, list) -> GroupTotal(group, list.sumOf { it.amountMinor }) }
                    .sortedByDescending { it.totalMinor },
                tracksExpenses = anyExpense,
                loading = false,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        OverviewUiState(period = selected.value, today = today(), currencyCode = null),
    )

    /** Recurring expenses waiting for Add or Skip, and months still missing the platform's own monthly total. */
    val attention: StateFlow<Attention> = combine(
        recurringRepository.observePending(today()),
        importRepository.observeMissingMonthlyTotals(today()),
    ) { pending, missing -> Attention(pending, missing) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Attention())

    fun accept(item: PendingExpense) {
        viewModelScope.launch {
            recurringRepository.accept(item.rule.id, item.dueDate, Clock.System.now().toEpochMilliseconds())
        }
    }

    fun skip(item: PendingExpense) {
        viewModelScope.launch { recurringRepository.skip(item.rule.id, item.dueDate) }
    }

    fun selectType(type: PeriodType) {
        val current = uiState.value.period
        if (current.type == type) return
        val today = today()
        val anchor = if (today in current.range.start..current.range.endInclusive) today else current.range.start
        viewModelScope.launch {
            val firstDay = settingsRepository.settings.first().firstDayOfWeek
            selected.value = periodOf(type, anchor, firstDay, current, firstDate = incomeRepository.firstDay(), today = today)
        }
    }

    /** Saves the Home layout from Customise. */
    fun setWidgets(widgets: List<HomeWidget>) {
        viewModelScope.launch { settingsRepository.setHomeWidgets(widgets) }
    }

    /** Opens one day from the activity chart, or the month when its bars are months. */
    fun openDay(date: LocalDate) {
        selected.value = if (uiState.value.stats?.monthly == true) Period.Month.containing(date) else Period.Day(date)
    }

    fun setCustomRange(range: DateRange) {
        selected.value = Period.Custom(range)
    }

    fun next() {
        selected.value = uiState.value.period.next()
    }

    fun previous() {
        selected.value = uiState.value.period.previous()
    }

    fun goToToday() {
        val current = uiState.value.period
        if (current is Period.Custom) return
        viewModelScope.launch {
            val firstDay = settingsRepository.settings.first().firstDayOfWeek
            selected.value = periodOf(current.type, today(), firstDay)
        }
    }
}
