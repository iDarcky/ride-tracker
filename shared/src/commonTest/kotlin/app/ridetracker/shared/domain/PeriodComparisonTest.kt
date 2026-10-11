package app.ridetracker.shared.domain

import app.ridetracker.shared.data.IncomeEntryEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class PeriodComparisonTest {
    private val thisWeek = DateRange(LocalDate(2026, 10, 5), LocalDate(2026, 10, 11))
    private val lastWeek = DateRange(LocalDate(2026, 9, 28), LocalDate(2026, 10, 4))

    private fun entry(platform: Long, date: LocalDate, amount: Long, minutes: Int? = null) =
        IncomeEntryEntity(platformId = platform, amountMinor = amount, date = date.toEpochDays(), createdAt = 0, onlineMinutes = minutes)

    private val current = HomeStatsCalculator.compute(
        thisWeek,
        listOf(entry(1, LocalDate(2026, 10, 6), 60000, minutes = 300), entry(2, LocalDate(2026, 10, 7), 30000)),
        emptyList(), emptyList(),
    )
    private val previous = HomeStatsCalculator.compute(
        lastWeek, listOf(entry(1, LocalDate(2026, 9, 29), 50000, minutes = 300)), emptyList(), emptyList(),
    )

    @Test
    fun comparesIncomeHoursAndPlatforms() {
        val rows = PeriodComparisons.rows(
            current, 0, previous, 0, tracksExpenses = false,
            currentByPlatform = mapOf(1L to 60000L, 2L to 30000L), previousByPlatform = mapOf(1L to 50000L),
        )
        assertEquals(
            listOf(ComparisonMetric.INCOME, ComparisonMetric.HOURS, ComparisonMetric.PER_HOUR, ComparisonMetric.PLATFORM, ComparisonMetric.PLATFORM),
            rows.map { it.metric },
        )
        val income = rows.first()
        assertEquals(90000, income.current)
        assertEquals(0.8, income.change!!, 1e-9) // 900 against 500
        assertNull(rows.last().change) // the second app had nothing last week: no %
        assertEquals(listOf(1L, 2L), rows.filter { it.metric == ComparisonMetric.PLATFORM }.map { it.platformId })
    }

    @Test
    fun expensesGoingUpIsNotGood() {
        val rows = PeriodComparisons.rows(current, 20000, previous, 10000, tracksExpenses = true, emptyMap(), emptyMap())
        val expenses = rows.single { it.metric == ComparisonMetric.EXPENSES }
        assertEquals(1.0, expenses.change!!, 1e-9)
        assertFalse(expenses.higherIsBetter)
        assertEquals(70000, rows.single { it.metric == ComparisonMetric.KEPT }.current)
    }

    @Test
    fun knowsWhenThereIsNothingBefore() {
        val empty = HomeStatsCalculator.compute(lastWeek, emptyList(), emptyList(), emptyList())
        assertFalse(PeriodComparisons.hasPrevious(empty, 0))
        assertTrue(PeriodComparisons.hasPrevious(previous, 0))
    }
}
