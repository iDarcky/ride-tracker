package app.ridetracker.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.shared.data.PlatformEntity
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.domain.IncomeRepository
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

data class EntryUiState(
    val isEdit: Boolean,
    val loading: Boolean = true,
    val platforms: List<PlatformEntity> = emptyList(),
    val platformId: Long? = null,
    val amountText: String = "",
    val date: LocalDate,
    val note: String = "",
    val currency: Currency = resolveCurrency(null),
    val amountInvalid: Boolean = false,
    val done: Boolean = false,
)

class EntryViewModel(
    savedStateHandle: SavedStateHandle,
    private val incomeRepository: IncomeRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val entryId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }

    private val _state = MutableStateFlow(
        EntryUiState(isEdit = entryId != null, date = Clock.System.todayIn(TimeZone.currentSystemDefault())),
    )
    val state: StateFlow<EntryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val currency = resolveCurrency(settingsRepository.settings.first().currencyCode)
            val digits = currency.defaultFractionDigits.coerceAtLeast(0)
            val entry = entryId?.let { incomeRepository.getEntry(it) }
            _state.update {
                it.copy(
                    loading = false,
                    currency = currency,
                    platformId = entry?.platformId,
                    amountText = entry?.let { e -> Money.toPlainString(e.amountMinor, digits) } ?: "",
                    date = entry?.let { e -> LocalDate.fromEpochDays(e.date) } ?: it.date,
                    note = entry?.note ?: "",
                )
            }
            // Collect platforms only after the entry is loaded, so its (possibly archived) platform stays visible.
            incomeRepository.observePlatforms().collect { all ->
                _state.update { s ->
                    // Show active platforms, plus the entry's own platform even if it was archived since.
                    val visible = all.filter { !it.archived || it.id == s.platformId }
                    s.copy(platforms = visible, platformId = s.platformId ?: visible.firstOrNull()?.id)
                }
            }
        }
    }

    fun setAmount(text: String) = _state.update { it.copy(amountText = text, amountInvalid = false) }
    fun setPlatform(id: Long) = _state.update { it.copy(platformId = id) }
    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }
    fun setNote(note: String) = _state.update { it.copy(note = note) }

    fun save() {
        val s = _state.value
        val platformId = s.platformId ?: return
        val minor = Money.parseToMinor(s.amountText, s.currency.defaultFractionDigits.coerceAtLeast(0))
        if (minor == null || minor == 0L) {
            _state.update { it.copy(amountInvalid = true) }
            return
        }
        viewModelScope.launch {
            incomeRepository.saveEntry(
                id = entryId,
                platformId = platformId,
                amountMinor = minor,
                date = s.date,
                note = s.note,
                nowEpochMillis = Clock.System.now().toEpochMilliseconds(),
            )
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = entryId ?: return
        viewModelScope.launch {
            incomeRepository.deleteEntry(id)
            _state.update { it.copy(done = true) }
        }
    }
}
