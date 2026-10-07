package app.ridetracker.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.shared.data.PlatformTotal
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.ExpenseCategory
import app.ridetracker.shared.domain.ExpenseGroup
import app.ridetracker.shared.domain.ExpenseRepository
import app.ridetracker.shared.domain.IncomeRepository
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.PeriodType
import app.ridetracker.shared.domain.periodOf
import app.ridetracker.shared.domain.type
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
    val loading: Boolean = true,
) {
    val keptMinor: Long get() = totalMinor - expenseMinor
}

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModel(
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private val selected = MutableStateFlow<Period>(Period.Month.containing(today()))

    val uiState: StateFlow<OverviewUiState> = combine(selected, settingsRepository.settings) { period, settings ->
        // Re-align weeks when the first-day-of-week setting changes.
        val aligned = if (period is Period.Week) Period.Week.containing(period.start, settings.firstDayOfWeek) else period
        aligned to settings.currencyCode
    }.flatMapLatest { (period, currencyCode) ->
        combine(
            incomeRepository.observeEntries(period.range),
            incomeRepository.observeTotals(period.range),
            expenseRepository.observeInRange(period.range),
            expenseRepository.observeAny(),
        ) { entries, totals, expenses, anyExpense ->
            OverviewUiState(
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

    fun selectType(type: PeriodType) {
        val current = uiState.value.period
        if (current.type == type) return
        val today = today()
        val anchor = if (today in current.range.start..current.range.endInclusive) today else current.range.start
        viewModelScope.launch {
            val firstDay = settingsRepository.settings.first().firstDayOfWeek
            selected.value = periodOf(type, anchor, firstDay, current)
        }
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
