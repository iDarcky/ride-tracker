package app.ridetracker.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.shared.data.ExpenseEntity
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.domain.ExpenseCategory
import app.ridetracker.shared.domain.ExpenseRepository
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.RecurringRepository
import app.ridetracker.ui.common.shownMonth
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
    /** Today's month: a "This month" button shows while another one is selected. */
    val currentMonth: Period.Month? = null,
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

/** Expenses of one month: total, by category, and the list. */
class ExpenseListViewModel(
    private val expenseRepository: ExpenseRepository,
    recurringRepository: RecurringRepository,
    settingsRepository: SettingsRepository,
    /** App-wide, shared with Home: this list shows its month. */
    private val period: MutableStateFlow<Period>,
) : ViewModel() {

    val recurringCount: StateFlow<Int> = recurringRepository.observeAll().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val category = MutableStateFlow<ExpenseCategory?>(null)

    val state: StateFlow<ExpenseListState> = combine(
        expenseRepository.observeAll(),
        combine(period, category) { p, c -> p to c },
        settingsRepository.settings,
    ) { all, (p, selectedCategory), settings ->
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val current = Period.Month.containing(today)
        val month = p.shownMonth(today)
        val months = (all.map { Period.Month.containing(LocalDate.fromEpochDays(it.date)) } + current + month).distinct()
            .sortedByDescending { it.range.start }
        fun inMonth(m: Period.Month) = all.filter { LocalDate.fromEpochDays(it.date) in m.range.start..m.range.endInclusive }
        val monthly = inMonth(month)
        // The month still running is compared with the same days of the previous one, like Home.
        val previous = inMonth(month.previous() as Period.Month)
            .filter { month != current || LocalDate.fromEpochDays(it.date).day <= today.day }
        val shown = if (selectedCategory == null) monthly else monthly.filter { ExpenseCategory.fromId(it.category) == selectedCategory }
        ExpenseListState(
            months = months,
            month = month,
            totalMinor = monthly.sumOf { it.amountMinor },
            previousMinor = previous.takeIf { it.isNotEmpty() }?.sumOf { it.amountMinor },
            categories = monthly.groupBy { ExpenseCategory.fromId(it.category) }
                .map { (category, list) -> CategoryTotal(category, list.sumOf { it.amountMinor }) }
                .sortedByDescending { it.totalMinor },
            currentMonth = current,
            category = selectedCategory,
            days = shown.groupBy { it.date }.map { (day, list) ->
                ExpenseDay(LocalDate.fromEpochDays(day), list.sumOf { it.amountMinor }, list)
            },
            currencyCode = settings.currencyCode,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseListState())

    fun selectMonth(month: Period.Month) {
        period.value = month
        category.value = null
    }

    /** Tapping the selected category again clears the filter. */
    fun toggleCategory(tapped: ExpenseCategory?) = category.update { if (it == tapped) null else tapped }

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
