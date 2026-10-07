package app.ridetracker.shared.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComparisonTest {
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate(y, m, day)
    private val today = d(10, 7) // Wednesday

    @Test
    fun monthInProgressComparesSameDaysOfLastMonth() {
        val c = Comparisons.of(Period.Month.containing(today), today)
        assertEquals(Comparison.SameDaysPrevious(DateRange(d(9, 1), d(9, 7)), PeriodType.MONTH), c)
    }

    @Test
    fun monthInProgressIsClampedToShorterPreviousMonth() {
        val c = Comparisons.of(Period.Month.containing(d(3, 31)), d(3, 31))
        assertEquals(DateRange(d(2, 1), d(2, 28)), c!!.range)
    }

    @Test
    fun finishedMonthComparesWholePreviousMonth() {
        val c = Comparisons.of(Period.Month.containing(d(9, 10)), today)
        assertEquals(Comparison.WholePrevious(DateRange(d(8, 1), d(8, 31)), PeriodType.MONTH), c)
    }

    @Test
    fun weekInProgress() {
        val week = Period.Week.containing(today, DayOfWeek.MONDAY) // Mon 5 Oct
        assertEquals(DateRange(d(9, 28), d(9, 30)), Comparisons.of(week, today)!!.range)
    }

    @Test
    fun dayComparesSameWeekdayLastWeek() {
        assertEquals(Comparison.SameWeekdayLastWeek(DateRange(d(9, 30), d(9, 30))), Comparisons.of(Period.Day(today), today))
    }

    @Test
    fun customComparesPreviousDays() {
        val c = Comparisons.of(Period.Custom(DateRange(d(10, 1), d(10, 10))), today)
        assertEquals(Comparison.PreviousDays(DateRange(d(9, 21), d(9, 30)), 10), c)
    }

    @Test
    fun futurePeriodHasNoComparison() {
        assertNull(Comparisons.of(Period.Month.containing(d(11, 5)), today))
    }

    @Test
    fun percentChange() {
        assertEquals(0.25, Comparisons.percentChange(125, 100))
        assertNull(Comparisons.percentChange(125, 0))
        assertNull(Comparisons.percentChange(125, -10))
    }
}
