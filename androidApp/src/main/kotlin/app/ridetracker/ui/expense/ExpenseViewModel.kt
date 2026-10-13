package app.ridetracker.ui.expense

import app.ridetracker.shared.domain.Country
import app.ridetracker.shared.domain.DrivingType
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.data.VehicleDocumentEntity
import app.ridetracker.shared.domain.DocumentKind
import app.ridetracker.shared.domain.DocumentRenewal
import app.ridetracker.shared.domain.VehicleRepository
import app.ridetracker.shared.domain.ExpenseCategory
import app.ridetracker.shared.domain.ExpenseGroup
import app.ridetracker.shared.domain.ExpenseRepository
import app.ridetracker.shared.domain.Frequency
import app.ridetracker.shared.domain.RecurringRepository
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
    /** New expenses only: also start a recurring series from this one. */
    val repeat: Boolean = false,
    val frequency: Frequency = Frequency.MONTHLY,
    val endDate: LocalDate? = null,
    /** PFA drivers in Romania can mark an expense as tax deductible. */
    val offerTaxDeductible: Boolean = false,
    val taxDeductible: Boolean = false,
    /** All car documents; the ones matching [category] can be linked. */
    val documents: List<VehicleDocumentEntity> = emptyList(),
    /** The car document this expense paid for. */
    val documentId: Long? = null,
    /** The link saved with the expense (editing). */
    val savedDocumentId: Long? = null,
    /** Linking a document anew also moves its expiry here. */
    val newExpiry: LocalDate? = null,
) {
    /** Documents of this category (an RCA for insurance…), plus the linked one. */
    val offeredDocuments: List<VehicleDocumentEntity>
        get() = documents.filter { DocumentKind.fromId(it.kind).expenseCategory == category || it.id == documentId }
}

class ExpenseViewModel(
    savedStateHandle: SavedStateHandle,
    private val expenseRepository: ExpenseRepository,
    private val recurringRepository: RecurringRepository,
    private val settingsRepository: SettingsRepository,
    private val vehicleRepository: VehicleRepository,
) : ViewModel() {

    private val expenseId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }

    private val _state = MutableStateFlow(
        ExpenseUiState(isEdit = expenseId != null, date = Clock.System.todayIn(TimeZone.currentSystemDefault())),
    )
    val state: StateFlow<ExpenseUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val currency = resolveCurrency(settings.currencyCode)
            val pfa = settings.country == Country.ROMANIA && settings.drivingType == DrivingType.PFA
            val digits = currency.defaultFractionDigits.coerceAtLeast(0)
            val expense = expenseId?.let { expenseRepository.get(it) }
            val documents = vehicleRepository.getAllDocuments()
            _state.update {
                if (expense == null) {
                    it.copy(loading = false, currency = currency, offerTaxDeductible = pfa, documents = documents)
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
                        // Kept visible on an expense already marked, even if the driving type changed since.
                        offerTaxDeductible = pfa || expense.taxDeductible,
                        taxDeductible = expense.taxDeductible,
                        documents = documents,
                        documentId = expense.documentId?.takeIf { id -> documents.any { d -> d.id == id } },
                        savedDocumentId = expense.documentId,
                    )
                }
            }
        }
    }

    fun setAmount(text: String) = _state.update { it.copy(amountText = text, amountInvalid = false) }

    /** Switching group picks that group's first category. */
    fun setGroup(group: ExpenseGroup) = _state.update {
        if (it.group == group) it else it.copy(group = group, category = ExpenseCategory.inGroup(group).first()).keepingMatchingDocument()
    }

    fun setCategory(category: ExpenseCategory) = _state.update { it.copy(category = category, group = category.group).keepingMatchingDocument() }

    /** Linking a document suggests its next expiry (a year on); the link already saved changes nothing. */
    fun setDocument(id: Long?) = _state.update { s ->
        val document = s.documents.firstOrNull { it.id == id }
        s.copy(
            documentId = document?.id,
            newExpiry = document?.takeIf { it.id != s.savedDocumentId }?.let { DocumentRenewal.suggestedExpiry(it.expiryDate, s.date) },
        )
    }

    fun setNewExpiry(date: LocalDate) = _state.update { it.copy(newExpiry = date) }

    /** A different category drops a link that no longer fits (fuel isn't a rovinietă). */
    private fun ExpenseUiState.keepingMatchingDocument(): ExpenseUiState =
        if (documentId == null || documents.any { it.id == documentId && DocumentKind.fromId(it.kind).expenseCategory == category }) {
            this
        } else {
            copy(documentId = null, newExpiry = null)
        }
    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }
    fun setNote(note: String) = _state.update { it.copy(note = note) }
    fun setTaxDeductible(deductible: Boolean) = _state.update { it.copy(taxDeductible = deductible) }
    fun setRepeat(repeat: Boolean) = _state.update { it.copy(repeat = repeat) }
    fun setFrequency(frequency: Frequency) = _state.update { it.copy(frequency = frequency) }
    fun setEndDate(date: LocalDate?) = _state.update { it.copy(endDate = date) }

    fun save() {
        val s = _state.value
        val minor = Money.parseToMinor(s.amountText, s.currency.defaultFractionDigits.coerceAtLeast(0))
        if (minor == null || minor == 0L) {
            _state.update { it.copy(amountInvalid = true) }
            return
        }
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            // A link to a document deleted since stays as it was (the document's Undo brings it back).
            val documentId = s.documentId ?: s.savedDocumentId?.takeIf { id -> s.documents.none { it.id == id } }
            expenseRepository.save(
                expenseId, minor, s.date, s.category, s.note, now,
                taxDeductible = s.offerTaxDeductible && s.taxDeductible, documentId = documentId,
            )
            val renewed = s.documents.firstOrNull { it.id == s.documentId }
            if (renewed != null && s.newExpiry != null) {
                vehicleRepository.saveDocument(renewed.copy(expiryDate = s.newExpiry.toEpochDays(), costMinor = minor))
            }
            if (expenseId == null && s.repeat) {
                recurringRepository.startFrom(s.date, minor, s.category, s.note, s.frequency, s.endDate, now)
            }
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
