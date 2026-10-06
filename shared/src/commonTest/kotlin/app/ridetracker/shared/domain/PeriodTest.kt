package app.ridetracker.shared.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)

    @Test
    fun weekStartsOnMonday() {
        // 2026-10-08 is a Thursday.
        val week = Period.Week.containing(d(2026, 10, 8), DayOfWeek.MONDAY)
        assertEquals(DateRange(d(2026, 10, 5), d(2026, 10, 11)), week.range)
    }

    @Test
    fun weekStartsOnSundayAcrossMonthBoundary() {
        val week = Period.Week.containing(d(2026, 10, 1), DayOfWeek.SUNDAY)
        assertEquals(DateRange(d(2026, 9, 27), d(2026, 10, 3)), week.range)
    }

    @Test
    fun weekContainingItsFirstDayStartsThatDay() {
        val week = Period.Week.containing(d(2026, 10, 5), DayOfWeek.MONDAY)
        assertEquals(d(2026, 10, 5), week.start)
        assertEquals(Period.Week(d(2026, 10, 12)), week.next())
    }

    @Test
    fun monthCoversWholeMonthIncludingLeapDay() {
        assertEquals(DateRange(d(2028, 2, 1), d(2028, 2, 29)), Period.Month.containing(d(2028, 2, 10)).range)
        assertEquals(DateRange(d(2027, 2, 1), d(2027, 2, 28)), Period.Month.containing(d(2027, 2, 10)).range)
    }

    @Test
    fun monthNavigationCrossesYear() {
        val dec = Period.Month.containing(d(2026, 12, 31))
        assertEquals(DateRange(d(2027, 1, 1), d(2027, 1, 31)), dec.next().range)
        assertEquals(DateRange(d(2026, 11, 1), d(2026, 11, 30)), dec.previous().range)
    }

    @Test
    fun dayNavigation() {
        assertEquals(Period.Day(d(2027, 1, 1)), Period.Day(d(2026, 12, 31)).next())
    }

    @Test
    fun customShiftsByItsLength() {
        val custom = Period.Custom(DateRange(d(2026, 10, 1), d(2026, 10, 10)))
        assertEquals(DateRange(d(2026, 10, 11), d(2026, 10, 20)), custom.next().range)
        assertEquals(DateRange(d(2026, 9, 21), d(2026, 9, 30)), custom.previous().range)
    }
}
