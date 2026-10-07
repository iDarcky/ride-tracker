package app.ridetracker.shared.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VehicleMathTest {
    private fun p(y: Int, m: Int, d: Int, km: Long) = OdometerPoint(LocalDate(y, m, d).toEpochDays(), km)
    private val october = DateRange(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31))

    @Test
    fun usesLastReadingBeforeTheMonthAsStart() {
        val readings = listOf(p(2026, 9, 28, 235_000), p(2026, 10, 15, 236_200), p(2026, 10, 30, 237_100))
        assertEquals(2_100, VehicleMath.kmDriven(readings, october))
    }

    @Test
    fun fallsBackToFirstReadingInsideTheMonth() {
        val readings = listOf(p(2026, 10, 3, 100_000), p(2026, 10, 20, 101_500))
        assertEquals(1_500, VehicleMath.kmDriven(readings, october))
    }

    @Test
    fun missingWithoutTwoReadings() {
        assertNull(VehicleMath.kmDriven(listOf(p(2026, 10, 3, 100_000)), october))
        assertNull(VehicleMath.kmDriven(listOf(p(2026, 9, 3, 100_000)), october))
        assertNull(VehicleMath.kmDriven(emptyList(), october))
    }

    @Test
    fun fuelEstimate() {
        // 1,000 km at 7.0 L/100 km and 7.50 RON/L = 525.00 RON
        assertEquals(52_500, VehicleMath.estimatedFuelCostMinor(km = 1_000, consumptionCenti = 700, fuelPriceMinor = 750))
    }

    @Test
    fun plausibility() {
        assertEquals(true, VehicleMath.isPlausible(235_000, 0, 236_420, 9))
        assertEquals(true, VehicleMath.isPlausible(235_000, 5, 236_900, 5)) // same day, 1,900 km
        assertEquals(false, VehicleMath.isPlausible(236_420, 9, 23_000_023, 9)) // typo
    }

    @Test
    fun costPerKm() {
        assertEquals(38, VehicleMath.costPerKmMinor(costMinor = 75_000, km = 2_000)) // 0.375 rounds half up to 0.38
        assertNull(VehicleMath.costPerKmMinor(1_000, null))
        assertNull(VehicleMath.costPerKmMinor(1_000, 0))
    }
}
