package app.ridetracker.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/** Made-up numbers. */
class IncomeEstimatorTest {
    private fun d(day: Int) = LocalDate(2026, 9, day)
    private val last = d(30)

    @Test
    fun monthlyTotalMinusExactDaysIsSpreadByFares() {
        val r = IncomeEstimator.estimate(
            exactByDay = mapOf(d(15) to 30000),
            tripFaresByDay = mapOf(d(15) to 40000, d(16) to 10000, d(17) to 30000),
            monthlyEarningsMinor = 100000,
            summaryFaresMinor = null, summaryCancellationMinor = null, summaryTipsMinor = null,
            keepRate = 0.8,
            lastDay = last,
        )
        assertEquals(EstimateBasis.MONTHLY_TOTAL, r.basis)
        // 1000.00 - 300.00 exact = 700.00 over the 16th and 17th (fares 100 : 300); the 15th is exact, untouched.
        assertEquals(listOf(EstimatedDay(d(16), 17500), EstimatedDay(d(17), 52500)), r.days)
        assertEquals(70000, r.days.sumOf { it.amountMinor })
    }

    @Test
    fun roundingNeverLosesACent() {
        val r = IncomeEstimator.estimate(
            exactByDay = emptyMap(),
            tripFaresByDay = mapOf(d(1) to 1, d(2) to 1, d(3) to 1),
            monthlyEarningsMinor = 1000,
            summaryFaresMinor = null, summaryCancellationMinor = null, summaryTipsMinor = null,
            keepRate = null,
            lastDay = last,
        )
        assertEquals(1000, r.days.sumOf { it.amountMinor })
    }

    @Test
    fun summaryPdfAfterCommissionPlusTips() {
        val r = IncomeEstimator.estimate(
            exactByDay = emptyMap(),
            tripFaresByDay = mapOf(d(20) to 50000),
            monthlyEarningsMinor = null,
            summaryFaresMinor = 100000, summaryCancellationMinor = 1000, summaryTipsMinor = 2000,
            keepRate = 0.75,
            lastDay = last,
        )
        assertEquals(EstimateBasis.MONTHLY_SUMMARY, r.basis)
        assertEquals(listOf(EstimatedDay(d(20), 101000 * 3 / 4 + 2000)), r.days)
    }

    @Test
    fun onlyTripsUsesTheirFaresAfterCommission() {
        val r = IncomeEstimator.estimate(
            exactByDay = mapOf(d(5) to 99999),
            tripFaresByDay = mapOf(d(5) to 20000, d(6) to 10000),
            monthlyEarningsMinor = null,
            summaryFaresMinor = null, summaryCancellationMinor = null, summaryTipsMinor = null,
            keepRate = 0.8,
            lastDay = last,
        )
        assertEquals(EstimateBasis.TRIP_FARES, r.basis)
        assertEquals(listOf(EstimatedDay(d(6), 8000)), r.days) // the 5th has a screenshot
    }

    @Test
    fun monthlyTotalWithoutTripsGoesOnTheLastDay() {
        val r = IncomeEstimator.estimate(
            exactByDay = mapOf(d(2) to 10000),
            tripFaresByDay = emptyMap(),
            monthlyEarningsMinor = 50000,
            summaryFaresMinor = null, summaryCancellationMinor = null, summaryTipsMinor = null,
            keepRate = null,
            lastDay = last,
        )
        assertEquals(listOf(EstimatedDay(last, 40000)), r.days)
    }

    @Test
    fun nothingWhenExactDaysAlreadyCoverTheMonth() {
        val r = IncomeEstimator.estimate(
            exactByDay = mapOf(d(2) to 60000),
            tripFaresByDay = mapOf(d(3) to 100),
            monthlyEarningsMinor = 50000,
            summaryFaresMinor = null, summaryCancellationMinor = null, summaryTipsMinor = null,
            keepRate = null,
            lastDay = last,
        )
        assertTrue(r.days.isEmpty())
    }

    @Test
    fun keepRateFromBreakdowns() {
        assertEquals(0.8, IncomeEstimator.keepRate(10000, -2000))
        assertEquals(null, IncomeEstimator.keepRate(10000, 0))
    }

    @Test
    fun asksForMonthlyTotalsOfFinishedMonthsOnly() {
        val aug = Period.Month.containing(LocalDate(2026, 8, 1))
        val sep = Period.Month.containing(d(1))
        val oct = Period.Month.containing(LocalDate(2026, 10, 1))
        val missing = IncomeEstimator.missingMonthlyTotals(
            estimateDays = listOf(1L to LocalDate(2026, 8, 3), 1L to d(4), 1L to d(5), 2L to d(6), 1L to LocalDate(2026, 10, 2)),
            monthlyTotals = listOf(1L to aug),
            today = LocalDate(2026, 10, 9),
        )
        // August has Bolt's total; October is not over yet.
        assertEquals(listOf(MissingMonthlyTotal(1, sep), MissingMonthlyTotal(2, sep)), missing.sortedBy { it.platformId })
        assertTrue(oct !in missing.map { it.month })
    }
}
