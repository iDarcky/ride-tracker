package app.ridetracker.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.shared.data.EntryWithPlatform
import app.ridetracker.shared.data.IncomeEntryEntity
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.domain.IncomeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class IncomeDay(val date: LocalDate, val totalMinor: Long, val entries: List<EntryWithPlatform>)

data class IncomeListState(
    val days: List<IncomeDay> = emptyList(),
    val currencyCode: String? = null,
    val loading: Boolean = true,
)

/** All income, newest first, grouped by day. */
class IncomeListViewModel(
    private val incomeRepository: IncomeRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<IncomeListState> = combine(
        incomeRepository.observeAllEntries(),
        settingsRepository.settings,
    ) { entries, settings ->
        IncomeListState(
            days = entries.groupBy { it.date }.map { (day, list) ->
                IncomeDay(LocalDate.fromEpochDays(day), list.sumOf { it.amountMinor }, list)
            },
            currencyCode = settings.currencyCode,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncomeListState())

    /** Deletes the entry and returns it so the UI can offer Undo. */
    suspend fun delete(id: Long): IncomeEntryEntity? {
        val entry = incomeRepository.getEntry(id) ?: return null
        incomeRepository.deleteEntry(id)
        return entry
    }

    fun restore(entry: IncomeEntryEntity) {
        viewModelScope.launch { incomeRepository.restoreEntry(entry) }
    }
}
