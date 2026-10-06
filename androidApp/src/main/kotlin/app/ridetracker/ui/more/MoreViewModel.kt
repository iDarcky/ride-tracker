package app.ridetracker.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.shared.data.AppSettings
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.applyThemeMode
import app.ridetracker.shared.domain.Country
import app.ridetracker.shared.domain.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

class MoreViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCountry(country: Country, otherCurrencyCode: String? = null) {
        viewModelScope.launch { settingsRepository.setCountry(country, otherCurrencyCode) }
    }

    fun setThemeMode(mode: ThemeMode) {
        applyThemeMode(mode)
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setFirstDayOfWeek(day: DayOfWeek) {
        viewModelScope.launch { settingsRepository.setFirstDayOfWeek(day) }
    }
}
