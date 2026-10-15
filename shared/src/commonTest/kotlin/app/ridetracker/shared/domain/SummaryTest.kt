package app.ridetracker.shared.domain

import app.ridetracker.shared.data.ExpenseEntity
import app.ridetracker.shared.data.IncomeEntryEntity
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SummaryTest {
    private val week = DateRange(LocalDate(2026, 10, 5), LocalDate(2026, 10, 11))

    private fun entry(platform: Long, day: Int, amount: Long, minutes: Int? = null) = IncomeEntryEntity(
        platformId = platform, amountMinor = amount, date = LocalDate(2026, 10, day).toEpochDays(), createdAt = 0,
        source = IncomeSource.SCREENSHOT.id, onlineMinutes = minutes,
    )

    private fun compute(entries: List<IncomeEntryEntity>, off: Set<Int> = emptySet(), expenses: List<ExpenseEntity> = emptyList()) =
        SummaryCalculator.compute(
            week, monthly = false, today = LocalDate(2026, 10, 12), entries = entries, lines = emptyList(), trips = emptyList(),
            summaries = emptyList(), expenses = expenses, daysOff = off.map { LocalDate(2026, 10, it).toEpochDays() }.toSet(),
            previousKeptMinor = 30000,
        )

    @Test
    fun countsDaysDrivenOffAndMissing() {
        val s = compute(listOf(entry(1, 5, 20000), entry(2, 5, 5000), entry(1, 6, 30000), entry(1, 7, 10000)), off = setOf(8, 9))
        assertEquals(3, s.daysDriven)
        assertEquals(2, s.daysOff)
        assertEquals(listOf(LocalDate(2026, 10, 8), LocalDate(2026, 10, 9)), s.offDays)
        assertEquals(listOf(LocalDate(2026, 10, 10), LocalDate(2026, 10, 11)), s.missingDays)
        assertEquals(LocalDate(2026, 10, 6), s.bestDay?.date)
        assertEquals(65000, s.incomeMinor)
        assertEquals(1L, s.apps.first().platformId) // the app that paid most first
        assertTrue(s.payout != null)
    }

    @Test
    fun keptAndPerHourAfterFuel() {
        val fuel = ExpenseEntity(amountMinor = 6000, date = LocalDate(2026, 10, 6).toEpochDays(), category = "fuel", createdAt = 0)
        val s = compute(listOf(entry(1, 5, 30000, minutes = 300), entry(1, 6, 30000, minutes = 300)), expenses = listOf(fuel))
        assertEquals(54000, s.keptMinor)
        assertEquals(6000, s.real?.perHourMinor)
        assertEquals(5400, s.real?.afterFuelPerHourMinor)
        assertEquals(1L, s.apps.single().platformId)
        assertEquals(6000, s.apps.single().perHourMinor)
    }

    @Test
    fun weeksAndMonths() {
        assertEquals(LocalDate(2026, 10, 5), SummaryCalculator.lastWeek(LocalDate(2026, 10, 12), DayOfWeek.MONDAY).start)
        assertTrue(SummaryCalculator.isWeeklyDay(LocalDate(2026, 10, 12), DayOfWeek.MONDAY))
        assertFalse(SummaryCalculator.isWeeklyDay(LocalDate(2026, 10, 13), DayOfWeek.MONDAY))
        assertTrue(SummaryCalculator.isMonth(DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))))
        assertFalse(SummaryCalculator.isMonth(week))
        assertNull(compute(emptyList()).bestDay)
    }
}
