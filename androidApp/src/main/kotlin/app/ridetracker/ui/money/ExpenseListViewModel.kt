package app.ridetracker.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.shared.data.ExpenseEntity
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.domain.ExpenseRepository
import app.ridetracker.shared.domain.RecurringRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class ExpenseDay(val date: LocalDate, val totalMinor: Long, val expenses: List<ExpenseEntity>)

data class ExpenseListState(
    val days: List<ExpenseDay> = emptyList(),
    val currencyCode: String? = null,
    val loading: Boolean = true,
)

/** All expenses, newest first, grouped by day. */
class ExpenseListViewModel(
    private val expenseRepository: ExpenseRepository,
    recurringRepository: RecurringRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val recurringCount: StateFlow<Int> = recurringRepository.observeAll().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)


    val state: StateFlow<ExpenseListState> = combine(expenseRepository.observeAll(), settingsRepository.settings) { expenses, settings ->
        ExpenseListState(
            days = expenses.groupBy { it.date }.map { (day, list) ->
                ExpenseDay(LocalDate.fromEpochDays(day), list.sumOf { it.amountMinor }, list)
            },
            currencyCode = settings.currencyCode,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseListState())

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
