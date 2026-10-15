package app.ridetracker.shared.domain

import app.ridetracker.shared.data.ExpenseEntity
import app.ridetracker.shared.data.IncomeEntryEntity
import app.ridetracker.shared.data.PlatformEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/** Made-up numbers. */
class WrappedTest {
    private val september = Period.Month(YearMonth(2026, 9))
    private val platforms = listOf(PlatformEntity(1, "Bolt", 0xFF34D186, 0), PlatformEntity(2, "Uber", 0xFF000000, 1))

    private fun entry(platform: Long, date: LocalDate, minor: Long) =
        IncomeEntryEntity(platformId = platform, amountMinor = minor, date = date.toEpochDays(), createdAt = 0)

    @Test
    fun aMonthWrapped() {
        // Fridays (4, 11, 18) pay most; one Uber day.
        val entries = listOf(
            entry(1, LocalDate(2026, 9, 4), 30000), entry(1, LocalDate(2026, 9, 11), 32000), entry(1, LocalDate(2026, 9, 18), 34000),
            entry(1, LocalDate(2026, 9, 7), 15000), entry(1, LocalDate(2026, 9, 14), 16000),
            entry(2, LocalDate(2026, 9, 18), 6000),
        )
        val expenses = listOf(ExpenseEntity(amountMinor = 13000, date = LocalDate(2026, 9, 5).toEpochDays(), category = "fuel", createdAt = 0))
        val stats = HomeStatsCalculator.compute(september.range, entries, emptyList(), emptyList())
        val targets = TargetSettings(amounts = mapOf("2026-09" to 100000))
        val w = WrappedCalculator.compute(september.range, false, entries, expenses, 90000, 10000, stats, platforms, targets, LocalDate(2026, 10, 2))
        assertEquals(5, w.daysDriven)
        assertEquals(133000, w.incomeMinor)
        assertEquals(120000, w.keptMinor)
        assertEquals(LocalDate(2026, 9, 18), w.bestDay)
        assertEquals(40000, w.bestDayMinor)
        assertEquals(DayOfWeek.FRIDAY, w.bestWeekday)
        assertEquals(listOf("Bolt", "Uber"), w.apps.map { it.name })
        assertEquals(0.5, w.keptChange) // 1,200 vs 800
        assertTrue(w.target!!.reached)
        assertTrue(w.worthShowing)
        assertNull(w.bestMonth)
        assertEquals(listOf(ReportCategory(ExpenseCategory.FUEL, 13000)), w.expenseCategories)
    }

    @Test
    fun whichWrappedHomeOffers() {
        assertEquals(september.range to false, WrappedCalculator.offered(LocalDate(2026, 10, 3)))
        assertNull(WrappedCalculator.offered(LocalDate(2026, 10, 15)))
        assertEquals(WrappedCalculator.yearRange(2026) to true, WrappedCalculator.offered(LocalDate(2026, 12, 2)))
        assertEquals(WrappedCalculator.yearRange(2026) to true, WrappedCalculator.offered(LocalDate(2027, 1, 10)))
    }
}
