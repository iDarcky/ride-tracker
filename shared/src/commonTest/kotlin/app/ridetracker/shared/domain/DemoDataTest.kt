package app.ridetracker.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class DemoDataTest {
    @Test
    fun aBelievableMonthThatAddsUp() {
        val today = LocalDate(2026, 10, 15)
        val demo = DemoData.generate(today)
        assertTrue(demo.days.all { it.day.date <= today && it.day.date >= LocalDate(2026, 9, 1) })
        assertTrue(demo.days.all { it.day.lines.sumOf { l -> l.amountMinor } == it.day.earningsMinor })
        assertTrue(demo.days.count { it.day.date.month == today.month } >= 8)
        assertTrue(demo.expenses.any { it.category == ExpenseCategory.FUEL })
        // Rides add up to each day's fares and match its ride count.
        assertTrue(demo.days.all { d -> d.rides.size == d.trips && d.rides.sumOf { it.fareMinor } == d.day.lines.filter { it.kind == IncomeLineKind.FARE }.sumOf { it.amountMinor } })
        assertTrue(demo.days.any { it.app == "Uber" } && demo.days.any { it.app == "Bolt" })
        assertEquals(demo, DemoData.generate(today)) // the same every time
    }
}
