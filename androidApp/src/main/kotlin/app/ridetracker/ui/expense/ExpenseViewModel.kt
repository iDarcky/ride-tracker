package app.ridetracker.ui.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.domain.ExpenseCategory
import app.ridetracker.shared.domain.ExpenseGroup
import app.ridetracker.shared.domain.ExpenseRepository
import app.ridetracker.shared.domain.Money
import app.ridetracker.ui.common.resolveCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.util.Currency
import kotlin.time.Clock

data class ExpenseUiState(
    val isEdit: Boolean,
    val loading: Boolean = true,
    val group: ExpenseGroup = ExpenseGroup.VEHICLE,
    val category: ExpenseCategory = ExpenseCategory.FUEL,
    val amountText: String = "",
    val date: LocalDate,
    val note: String = "",
    val currency: Currency = resolveCurrency(null),
    val amountInvalid: Boolean = false,
    val done: Boolean = false,
)

class ExpenseViewModel(
    savedStateHandle: SavedStateHandle,
    private val expenseRepository: ExpenseRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val expenseId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }

    private val _state = MutableStateFlow(
        ExpenseUiState(isEdit = expenseId != null, date = Clock.System.todayIn(TimeZone.currentSystemDefault())),
    )
    val state: StateFlow<ExpenseUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val currency = resolveCurrency(settingsRepository.settings.first().currencyCode)
            val digits = currency.defaultFractionDigits.coerceAtLeast(0)
            val expense = expenseId?.let { expenseRepository.get(it) }
            _state.update {
                if (expense == null) {
                    it.copy(loading = false, currency = currency)
                } else {
                    val category = ExpenseCategory.fromId(expense.category)
                    it.copy(
                        loading = false,
                        currency = currency,
                        group = category.group,
                        category = category,
                        amountText = Money.toPlainString(expense.amountMinor, digits),
                        date = LocalDate.fromEpochDays(expense.date),
                        note = expense.note ?: "",
                    )
                }
            }
        }
    }

    fun setAmount(text: String) = _state.update { it.copy(amountText = text, amountInvalid = false) }

    /** Switching group picks that group's first category. */
    fun setGroup(group: ExpenseGroup) = _state.update {
        if (it.group == group) it else it.copy(group = group, category = ExpenseCategory.inGroup(group).first())
    }

    fun setCategory(category: ExpenseCategory) = _state.update { it.copy(category = category, group = category.group) }
    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }
    fun setNote(note: String) = _state.update { it.copy(note = note) }

    fun save() {
        val s = _state.value
        val minor = Money.parseToMinor(s.amountText, s.currency.defaultFractionDigits.coerceAtLeast(0))
        if (minor == null || minor == 0L) {
            _state.update { it.copy(amountInvalid = true) }
            return
        }
        viewModelScope.launch {
            expenseRepository.save(expenseId, minor, s.date, s.category, s.note, Clock.System.now().toEpochMilliseconds())
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = expenseId ?: return
        viewModelScope.launch {
            expenseRepository.delete(id)
            _state.update { it.copy(done = true) }
        }
    }
}
