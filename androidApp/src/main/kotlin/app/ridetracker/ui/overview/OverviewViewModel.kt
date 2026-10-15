package app.ridetracker.ui.overview

import kotlinx.coroutines.flow.map
import app.ridetracker.shared.domain.WrappedCalculator
import kotlinx.coroutines.flow.distinctUntilChanged
import app.ridetracker.shared.domain.PeriodComparisons
import app.ridetracker.shared.domain.ComparisonRow
import app.ridetracker.shared.domain.TargetBasis
import app.ridetracker.shared.domain.TargetCalculator
import app.ridetracker.shared.domain.TargetProgress
import app.ridetracker.shared.domain.Country
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.toLocalDateTime
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.shared.data.PlatformEntity
import app.ridetracker.shared.data.PlatformTotal
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.data.VehicleDocumentEntity
import app.ridetracker.shared.domain.DocumentReminders
import app.ridetracker.shared.domain.VehicleRepository
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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import app.ridetracker.shared.domain.IncomeSource
import app.ridetracker.shared.domain.MonthEnd
import app.ridetracker.shared.domain.DrivingType
import app.ridetracker.shared.domain.RealHourly
import app.ridetracker.shared.domain.RealHourlyCalculator
import app.ridetracker.shared.domain.WeekPayout
import app.ridetracker.shared.domain.WeekPayouts
import app.ridetracker.shared.domain.MonthEndStatus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Expenses that count as fuel for money per hour. */
private val fuelCategories = setOf(ExpenseCategory.FUEL, ExpenseCategory.CHARGING)

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
    /** The month's target and where it stands (the month of the period shown); null without a target. */
    val target: TargetProgress? = null,
    /** This period against the previous one; null when there's nothing to compare with. */
    val comparisonCard: ComparisonCard? = null,
    /** This week's screenshots, whatever period is shown; null when no app was used lately. */
    val week: WeekLog? = null,
    /** Fuel and charging expenses in the period, for money per hour after fuel. */
    val fuelMinor: Long = 0,
    /** This week's payout so far, whatever period is shown; null with nothing in this week. */
    val payout: WeekPayout? = null,
    /** Drives through a fleet (Romania): the fleet pays, not the apps. */
    val fleet: Boolean = false,
) {
    /** Money per hour, after fuel and after all expenses; null without hours. */
    val realHourly: RealHourly? get() = stats?.let { RealHourlyCalculator.compute(it, fuelMinor, expenseMinor) }

    val keptMinor: Long get() = totalMinor - expenseMinor

    /** The headline number: money kept once expenses are tracked, otherwise income. */
    val headlineMinor: Long get() = if (tracksExpenses) keptMinor else totalMinor
    val previousHeadlineMinor: Long? get() = previous?.let { if (tracksExpenses) it.incomeMinor - it.expenseMinor else it.incomeMinor }
}

/**
 * This week, day by day: which apps have their day in. A day without one counts as 0 (days off are normal), so this
 * only shows what's in, never a warning. [apps] are the apps used in the last four weeks.
 */
data class WeekLog(
    val days: List<LocalDate>,
    val today: LocalDate,
    val apps: List<PlatformEntity>,
    val logged: Map<LocalDate, Set<Long>>,
    /** Days marked "Didn't drive": they count as in. */
    val daysOff: Set<LocalDate> = emptySet(),
    /** This month so far: days in (or off) and days gone, for "October: 13 of 15 days in". */
    val month: Period.Month? = null,
    val monthDays: Pair<Int, Int> = 0 to 0,
    /** Last month's files, in the first days of a month (see [MonthEnd.statusMonth]); null otherwise. */
    val files: MonthEndStatus? = null,
) {
    /** Days so far with at least one app in, or marked as a day off. */
    val daysIn: Int get() = days.count { it <= today && (!logged[it].isNullOrEmpty() || it in daysOff) }
    val todayIn: Boolean get() = !logged[today].isNullOrEmpty() || today in daysOff
}

/** What "Needs attention" on Home lists. */
data class Attention(
    val pending: List<PendingExpense> = emptyList(),
    /** The day whose Raportul Z is still waiting for "Done" (Romania). */
    val zReportDay: LocalDate? = null,
    /** Offer to set up the Raportul Z reminder (Romania, not set up, not dismissed). */
    val suggestZReport: Boolean = false,
    /** Offer to set a monthly target (none set, not dismissed). */
    val suggestTarget: Boolean = false,
    /** Car documents expiring within a month, or expired; soonest first. */
    val documents: List<VehicleDocumentEntity> = emptyList(),
) {
    fun isEmpty(): Boolean =
        pending.isEmpty() && zReportDay == null && !suggestZReport && !suggestTarget && documents.isEmpty()
}

/** Home's comparison card: what it's compared with, the rows, and the "same days / whole month" choice. */
data class ComparisonCard(
    val comparison: Comparison,
    val rows: List<ComparisonRow>,
    /** The period is still running, so the driver can compare with the same days or the whole previous one. */
    val canCompareWhole: Boolean,
    val whole: Boolean,
)

/** The earlier period's figures, read once per period (not on every change to the current one). */
private data class PreviousStats(val comparison: Comparison, val stats: HomeStats, val expensesMinor: Long, val byPlatform: Map<Long, Long>)

data class PreviousTotals(val incomeMinor: Long, val expenseMinor: Long)

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModel(
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val importRepository: ImportRepository,
    private val recurringRepository: RecurringRepository,
    private val settingsRepository: SettingsRepository,
    private val vehicleRepository: VehicleRepository,
    /** App-wide, shared with Income and Expenses. */
    private val selected: MutableStateFlow<Period>,
) : ViewModel() {

    private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private val _months = MutableStateFlow(listOf(Period.Month.containing(today())))

    /** Months the title's list offers: from the first month with data to this one, newest first. */
    val months: StateFlow<List<Period.Month>> = _months

    init {
        refreshMonths()
    }

    /** Re-reads the first month with data (imports can add older months). */
    fun refreshMonths() {
        viewModelScope.launch {
            val current = Period.Month.containing(today())
            val first = incomeRepository.firstDay()?.let { Period.Month.containing(it) } ?: current
            _months.value = generateSequence(current) { (it.previous() as Period.Month).takeIf { m -> m.range.start >= first.range.start } }.toList()
        }
    }

    fun selectMonth(month: Period.Month) {
        selected.value = month
    }

    val uiState: StateFlow<OverviewUiState> = combine(selected, settingsRepository.settings) { period, settings ->
        // Re-align weeks when the first-day-of-week setting changes.
        val aligned = if (period is Period.Week) Period.Week.containing(period.start, settings.firstDayOfWeek) else period
        Triple(aligned, settings.currencyCode, settings.homeWidgets to (settings.drivingType == DrivingType.FLEET))
    }.flatMapLatest { (period, currencyCode, widgetsAndFleet) ->
        val (widgets, fleet) = widgetsAndFleet
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
                fuelMinor = expenses.filter { ExpenseCategory.fromId(it.category) in fuelCategories }.sumOf { it.amountMinor },
                fleet = fleet,
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

    /** Last month's files from Bolt and Uber (End of month), for Home's files line and "May still change". */
    val monthEnd: StateFlow<MonthEndStatus?> = importRepository.observeMonthEnd(Period.Month.containing(today()).previous())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val week: StateFlow<WeekLog?> = settingsRepository.settings.map { it.firstDayOfWeek }.distinctUntilChanged().flatMapLatest { first ->
        val today = today()
        val week = Period.Week.containing(today, first)
        val month = Period.Month.containing(today)
        val recent = DateRange(today.minus(DatePeriod(days = 27)), today)
        combine(
            incomeRepository.observeEntries(week.range),
            incomeRepository.observeEntries(recent),
            incomeRepository.observePlatforms(),
            combine(incomeRepository.observeEntryDetails(month.range), importRepository.observeDaysOff()) { m, off -> m to off },
            monthEnd,
        ) { entries, lately, platforms, (monthEntries, daysOff), files ->
            val used = lately.filter { IncomeSource.countsAsDay(it.source) }.map { it.platformId }.toSet()
            val apps = platforms.filter { it.id in used }
            if (apps.isEmpty()) return@combine null
            WeekLog(
                days = (0..6).map { week.start.plus(DatePeriod(days = it)) },
                today = today,
                apps = apps,
                logged = entries.filter { IncomeSource.countsAsDay(it.source) }
                    .groupBy({ LocalDate.fromEpochDays(it.date) }, { it.platformId })
                    .mapValues { it.value.toSet() },
                daysOff = daysOff.map(LocalDate::fromEpochDays).toSet(),
                month = month,
                monthDays = MonthEnd.daysIn(month.range, today, monthEntries, daysOff),
                files = files?.takeIf { MonthEnd.statusMonth(today) != null && it.apps.isNotEmpty() },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** This week's payout so far, whatever period Home shows. */
    val payout: StateFlow<WeekPayout?> = settingsRepository.settings.map { it.firstDayOfWeek }.distinctUntilChanged().flatMapLatest { first ->
        incomeRepository.observeEntryDetails(Period.Week.containing(today(), first).range).map(WeekPayouts::compute)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Recurring expenses waiting for Add or Skip, documents running out, Raportul Z and suggestions. */
    val attention: StateFlow<Attention> = combine(
        combine(recurringRepository.observePending(today()), vehicleRepository.observeAllDocuments()) { p, d -> p to d },
        combine(settingsRepository.zReport, settingsRepository.target) { z, target -> z to target },
        settingsRepository.settings,
        // The Raportul Z day waits from the reminder's time on: look again every minute.
        flow { while (true) { emit(Unit); delay(60_000) } },
    ) { (pending, documents), (z, target), settings, _ ->
        val romania = settings.country == Country.ROMANIA
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        Attention(
            pending,
            zReportDay = if (romania) z.waitingDay(now) else null,
            suggestZReport = romania && !z.enabled && !z.suggestionDismissed,
            suggestTarget = !target.hasAny && !target.suggestionDismissed,
            documents = documents.filter { DocumentReminders.needsAttention(it.expiryDate, now.date) },
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Attention())

    /**
     * Last month's or the year's Wrapped, while Home offers it (see [WrappedCalculator.offered]) and it has at least
     * two days driven; null otherwise.
     */
    val wrappedOffer: StateFlow<Pair<DateRange, Boolean>?> =
        monthEnd.flatMapLatest { status ->
            val offer = WrappedCalculator.offered(today(), lastMonthComplete = status?.let { it.complete && it.apps.isNotEmpty() } == true)
                ?: return@flatMapLatest flowOf(null)
            incomeRepository.observeEntryDetails(offer.first)
                .map { entries -> offer.takeIf { entries.filter { it.amountMinor > 0 }.map { it.date }.distinct().size >= 2 } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The target of the month shown (the month the period starts in; this month for "All"). */
    val target: StateFlow<TargetProgress?> = combine(selected, settingsRepository.target) { period, settings ->
        val month = if (period is Period.All) Period.Month.containing(today()) else Period.Month.containing(period.range.start)
        month to settings
    }.flatMapLatest { (month, settings) ->
        val amount = settings.targetFor(month) ?: return@flatMapLatest flowOf(null)
        combine(incomeRepository.observeTotals(month.range), expenseRepository.observeInRange(month.range)) { totals, expenses ->
            val achieved = TargetCalculator.achieved(settings.basis, totals.sumOf { it.totalMinor }, expenses.sumOf { it.amountMinor })
            TargetCalculator.progress(month, amount, achieved, settings.drivingDays, today())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val wholePrevious = MutableStateFlow(false)

    /** Compare a running month or week with the whole previous one instead of the same days. */
    fun setCompareWhole(whole: Boolean) {
        wholePrevious.value = whole
    }

    val comparisonCard: StateFlow<ComparisonCard?> = run {
        val previous: Flow<PreviousStats?> = combine(uiState, wholePrevious) { state, whole ->
            Triple(state.period, state.comparison, whole)
        }.distinctUntilChanged().flatMapLatest { (period, base, whole) ->
            if (base == null) return@flatMapLatest flowOf(null)
            val comparison = if (whole && base is Comparison.SameDaysPrevious) Comparison.WholePrevious(period.previous().range, base.type) else base
            val range = comparison.range
            combine(
                incomeRepository.observeEntryDetails(range),
                incomeRepository.observeLines(range),
                importRepository.observeTrips(range),
                importRepository.observeSummaries(range),
                expenseRepository.observeInRange(range),
            ) { details, lines, trips, summaries, expenses ->
                PreviousStats(
                    comparison,
                    HomeStatsCalculator.compute(range, details, lines, trips, summaries),
                    expenses.sumOf { it.amountMinor },
                    details.groupBy { it.platformId }.mapValues { (_, list) -> list.sumOf { it.amountMinor } },
                )
            }
        }
        combine(uiState, previous, wholePrevious) { state, prev, whole ->
            val stats = state.stats ?: return@combine null
            if (prev == null || !PeriodComparisons.hasPrevious(prev.stats, prev.expensesMinor)) return@combine null
            ComparisonCard(
                comparison = prev.comparison,
                rows = PeriodComparisons.rows(
                    stats, state.expenseMinor, prev.stats, prev.expensesMinor, state.tracksExpenses,
                    state.totals.associate { it.platformId to it.totalMinor }, prev.byPlatform,
                ),
                canCompareWhole = state.comparison is Comparison.SameDaysPrevious,
                whole = whole && state.comparison is Comparison.SameDaysPrevious,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    /** Home's week card: marks a day with nothing in as "Didn't drive", or unmarks it. */
    fun toggleDayOff(day: LocalDate) {
        viewModelScope.launch { importRepository.setDayOff(day, day !in (week.value?.daysOff ?: emptySet())) }
    }

    fun dismissTargetSuggestion() {
        viewModelScope.launch { settingsRepository.dismissTargetSuggestion() }
    }

    fun zReportDone(day: LocalDate) {
        viewModelScope.launch { settingsRepository.markZReportDone(day.toEpochDays()) }
    }

    fun dismissZReportSuggestion() {
        viewModelScope.launch { settingsRepository.dismissZReportSuggestion() }
    }

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

