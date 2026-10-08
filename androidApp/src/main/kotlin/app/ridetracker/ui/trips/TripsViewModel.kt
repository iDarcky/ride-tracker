package app.ridetracker.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.data.TripWithPlatform
import app.ridetracker.shared.domain.ImportRepository
import app.ridetracker.shared.domain.PaymentMethod
import app.ridetracker.shared.domain.Period
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Cash or paid in the app (card, wallet, business account). */
enum class PaymentFilter { ALL, CASH, IN_APP }

/** Trips of one day, newest first. */
data class TripDay(val date: LocalDate, val trips: List<TripWithPlatform>) {
    val totalMinor: Long get() = trips.sumOf { it.fareMinor }
}

data class PlatformChoice(val id: Long, val name: String)

data class TripsUiState(
    val loading: Boolean = true,
    /** Months that have trips, newest first. */
    val months: List<Period.Month> = emptyList(),
    val month: Period.Month? = null,
    /** Apps with trips this month; the selector shows only when there are two or more. */
    val platforms: List<PlatformChoice> = emptyList(),
    val platformId: Long? = null,
    val payment: PaymentFilter = PaymentFilter.ALL,
    val days: List<TripDay> = emptyList(),
    val currencyCode: String? = null,
) {
    val tripCount: Int get() = days.sumOf { it.trips.size }
    val totalMinor: Long get() = days.sumOf { it.totalMinor }
    val hasAnyTrips: Boolean get() = months.isNotEmpty()
}

private data class Filters(val month: Period.Month? = null, val platformId: Long? = null, val payment: PaymentFilter = PaymentFilter.ALL)

@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModel(
    private val importRepository: ImportRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(Filters())

    private val months = importRepository.observeTripDays().map { days ->
        days.map { Period.Month.containing(it) }.distinct()
    }

    val state: StateFlow<TripsUiState> = combine(months, filters, settingsRepository.settings) { months, f, settings ->
        Triple(months, f.copy(month = f.month?.takeIf { it in months } ?: months.firstOrNull()), settings.currencyCode)
    }.flatMapLatest { (months, f, currency) ->
        val month = f.month ?: return@flatMapLatest flowOf(TripsUiState(loading = false, currencyCode = currency))
        importRepository.observeTrips(month.range).map { trips ->
            val platforms = trips.distinctBy { it.platformId }.map { PlatformChoice(it.platformId, it.platformName) }
            val platformId = f.platformId?.takeIf { id -> platforms.any { it.id == id } }
            val shown = trips.filter { t ->
                (platformId == null || t.platformId == platformId) && when (f.payment) {
                    PaymentFilter.ALL -> true
                    PaymentFilter.CASH -> t.paymentMethod == PaymentMethod.CASH.id
                    PaymentFilter.IN_APP -> t.paymentMethod != PaymentMethod.CASH.id
                }
            }
            TripsUiState(
                loading = false,
                months = months,
                month = month,
                platforms = platforms,
                platformId = platformId,
                payment = f.payment,
                days = shown.groupBy { it.date }.map { (day, list) -> TripDay(LocalDate.fromEpochDays(day), list) },
                currencyCode = currency,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsUiState())

    fun selectMonth(month: Period.Month) = filters.update { it.copy(month = month) }
    fun selectPlatform(id: Long?) = filters.update { it.copy(platformId = id) }
    fun selectPayment(payment: PaymentFilter) = filters.update { it.copy(payment = payment) }
}

class TripDetailsViewModel(
    tripId: Long,
    importRepository: ImportRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val trip = MutableStateFlow<TripWithPlatform?>(null)

    val state: StateFlow<Pair<TripWithPlatform?, String?>> = combine(trip, settingsRepository.settings) { t, s -> t to s.currencyCode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null to null)

    init {
        viewModelScope.launch { trip.value = importRepository.getTrip(tripId) }
    }
}
