package app.ridetracker.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ridetracker.shared.data.OdometerReadingEntity
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.data.VehicleEntity
import app.ridetracker.shared.domain.ExpenseCategory
import app.ridetracker.shared.domain.ExpenseGroup
import app.ridetracker.shared.domain.ExpenseRepository
import app.ridetracker.shared.domain.OdometerImplausibleException
import app.ridetracker.shared.domain.OdometerOutOfOrderException
import app.ridetracker.shared.domain.OdometerPoint
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.VehicleMath
import app.ridetracker.shared.domain.VehicleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class CategoryTotal(val category: ExpenseCategory, val totalMinor: Long)

data class VehicleUiState(
    val loading: Boolean = true,
    val vehicle: VehicleEntity? = null,
    val readings: List<OdometerReadingEntity> = emptyList(),
    val month: Period.Month,
    val currencyCode: String? = null,
    /** Null = "Missing": not enough odometer readings for this month. */
    val kmDriven: Long? = null,
    val vehicleCostMinor: Long = 0,
    val fuelSpentMinor: Long = 0,
    val costPerKmMinor: Long? = null,
    /** Null when consumption, fuel price or km are missing. */
    val fuelEstimateMinor: Long? = null,
    val byCategory: List<CategoryTotal> = emptyList(),
) {
    val latest: OdometerReadingEntity? get() = readings.firstOrNull()
}

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleViewModel(
    private val vehicleRepository: VehicleRepository,
    expenseRepository: ExpenseRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val month = MutableStateFlow(Period.Month.containing(today()))

    private val readings = vehicleRepository.observeVehicle().flatMapLatest { vehicle ->
        if (vehicle == null) flowOf(null to emptyList()) else vehicleRepository.observeReadings(vehicle.id).let { flow ->
            combine(flowOf(vehicle), flow) { v, r -> v to r }
        }
    }

    val state: StateFlow<VehicleUiState> = combine(readings, month, settingsRepository.settings) { (vehicle, readings), month, settings ->
        Triple(vehicle to readings, month, settings.currencyCode)
    }.flatMapLatest { (vehicleAndReadings, month, currencyCode) ->
        val (vehicle, readings) = vehicleAndReadings
        expenseRepository.observeInRange(month.range).let { expensesFlow ->
            combine(expensesFlow, flowOf(Unit)) { expenses, _ ->
                val vehicleExpenses = expenses.filter { ExpenseCategory.fromId(it.category).group == ExpenseGroup.VEHICLE }
                val km = VehicleMath.kmDriven(readings.map { OdometerPoint(it.date, it.km) }, month.range)
                val cost = vehicleExpenses.sumOf { it.amountMinor }
                val fuelSpent = vehicleExpenses
                    .filter { ExpenseCategory.fromId(it.category) in setOf(ExpenseCategory.FUEL, ExpenseCategory.CHARGING) }
                    .sumOf { it.amountMinor }
                val consumption = vehicle?.consumptionCenti
                val price = vehicle?.fuelPriceMinor
                VehicleUiState(
                    loading = false,
                    vehicle = vehicle,
                    readings = readings,
                    month = month,
                    currencyCode = currencyCode,
                    kmDriven = km,
                    vehicleCostMinor = cost,
                    fuelSpentMinor = fuelSpent,
                    costPerKmMinor = VehicleMath.costPerKmMinor(cost, km),
                    fuelEstimateMinor = if (km != null && consumption != null && price != null) {
                        VehicleMath.estimatedFuelCostMinor(km, consumption, price)
                    } else {
                        null
                    },
                    byCategory = vehicleExpenses.groupBy { ExpenseCategory.fromId(it.category) }
                        .map { (category, list) -> CategoryTotal(category, list.sumOf { it.amountMinor }) }
                        .sortedByDescending { it.totalMinor },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleUiState(month = month.value))

    fun previousMonth() {
        month.value = month.value.previous()
    }

    fun nextMonth() {
        month.value = month.value.next()
    }

    enum class ReadingResult { SAVED, OUT_OF_ORDER, IMPLAUSIBLE }

    suspend fun addReading(date: LocalDate, km: Long): ReadingResult {
        val s = state.value
        val vehicle = s.vehicle ?: return ReadingResult.OUT_OF_ORDER
        return try {
            vehicleRepository.addReading(vehicle.id, date, km, s.readings, Clock.System.now().toEpochMilliseconds())
            ReadingResult.SAVED
        } catch (_: OdometerOutOfOrderException) {
            ReadingResult.OUT_OF_ORDER
        } catch (_: OdometerImplausibleException) {
            ReadingResult.IMPLAUSIBLE
        }
    }

    /** Deletes the reading and returns it so the screen can offer Undo. */
    suspend fun deleteReading(id: Long): OdometerReadingEntity? {
        val reading = state.value.readings.firstOrNull { it.id == id } ?: return null
        vehicleRepository.deleteReading(id)
        return reading
    }

    fun restoreReading(reading: OdometerReadingEntity) {
        viewModelScope.launch { vehicleRepository.restoreReading(reading) }
    }
}
