package app.ridetracker.ui.platforms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.shared.data.PlatformEntity
import app.ridetracker.shared.domain.IncomeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlatformsViewModel(private val incomeRepository: IncomeRepository) : ViewModel() {

    val platforms: StateFlow<List<PlatformEntity>> = incomeRepository.observePlatforms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String, colorArgb: Long) {
        viewModelScope.launch { incomeRepository.addPlatform(name, colorArgb) }
    }

    fun update(platform: PlatformEntity) {
        viewModelScope.launch { incomeRepository.updatePlatform(platform) }
    }
}
