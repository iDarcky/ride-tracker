package app.ridetracker.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.shared.data.ExpenseEntity
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.domain.ExpenseCategory
import app.ridetracker.shared.domain.ExpenseRepository
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.RecurringRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class ExpenseDay(val date: LocalDate, val totalMinor: Long, val expenses: List<ExpenseEntity>)

data class CategoryTotal(val category: ExpenseCategory, val totalMinor: Long)

data class ExpenseListState(
    val months: List<Period.Month> = emptyList(),
    val month: Period.Month? = null,
    val totalMinor: Long = 0,
    /** The whole previous month's total; null when it has no expenses (then no comparison is shown). */
    val previousMinor: Long? = null,
    val categories: List<CategoryTotal> = emptyList(),
    /** Tapped category: the list below shows only it. */
    val category: ExpenseCategory? = null,
    /** The month's expenses (filtered by [category]), newest first, by day. */
    val days: List<ExpenseDay> = emptyList(),
    val currencyCode: String? = null,
    val loading: Boolean = true,
)

private data class ExpenseFilters(val month: Period.Month? = null, val category: ExpenseCategory? = null)

/** Expenses of one month: total, by category, and the list. */
class ExpenseListViewModel(
    private val expenseRepository: ExpenseRepository,
    recurringRepository: RecurringRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val recurringCount: StateFlow<Int> = recurringRepository.observeAll().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val filters = MutableStateFlow(ExpenseFilters())

    val state: StateFlow<ExpenseListState> = combine(
        expenseRepository.observeAll(),
        filters,
        settingsRepository.settings,
    ) { all, f, settings ->
        val current = Period.Month.containing(Clock.System.todayIn(TimeZone.currentSystemDefault()))
        val months = (all.map { Period.Month.containing(LocalDate.fromEpochDays(it.date)) } + current).distinct()
            .sortedByDescending { it.range.start }
        val month = f.month?.takeIf { it in months } ?: current
        fun inMonth(m: Period.Month) = all.filter { LocalDate.fromEpochDays(it.date) in m.range.start..m.range.endInclusive }
        val monthly = inMonth(month)
        val previous = inMonth(month.previous() as Period.Month)
        val shown = if (f.category == null) monthly else monthly.filter { ExpenseCategory.fromId(it.category) == f.category }
        ExpenseListState(
            months = months,
            month = month,
            totalMinor = monthly.sumOf { it.amountMinor },
            previousMinor = previous.takeIf { it.isNotEmpty() }?.sumOf { it.amountMinor },
            categories = monthly.groupBy { ExpenseCategory.fromId(it.category) }
                .map { (category, list) -> CategoryTotal(category, list.sumOf { it.amountMinor }) }
                .sortedByDescending { it.totalMinor },
            category = f.category,
            days = shown.groupBy { it.date }.map { (day, list) ->
                ExpenseDay(LocalDate.fromEpochDays(day), list.sumOf { it.amountMinor }, list)
            },
            currencyCode = settings.currencyCode,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseListState())

    fun selectMonth(month: Period.Month) = filters.update { it.copy(month = month, category = null) }

    /** Tapping the selected category again clears the filter. */
    fun toggleCategory(category: ExpenseCategory?) = filters.update { it.copy(category = if (it.category == category) null else category) }

    /** Deletes the expense and returns it so the UI can offer Undo. */
    suspend fun delete(id: Long): ExpenseEntity? {
        val expense = expenseRepository.get(id) ?: return null
        expenseRepository.delete(id)
        return expense
    }

    fun restore(expense: ExpenseEntity) {
        viewModelScope.launch { expenseRepository.restore(expense) }
    }
}
