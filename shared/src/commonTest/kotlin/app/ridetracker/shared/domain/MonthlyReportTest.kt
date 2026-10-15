package app.ridetracker.shared.domain

import app.ridetracker.shared.data.EntryWithPlatform
import app.ridetracker.shared.data.ExpenseEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/** Made-up numbers. */
class MonthlyReportTest {
    private val october = Period.Month(YearMonth(2026, 10))

    private fun income(platform: Long, name: String, date: LocalDate, minor: Long) = EntryWithPlatform(
        id = 0, platformId = platform, amountMinor = minor, date = date.toEpochDays(), note = null, createdAt = 0,
        source = IncomeSource.SCREENSHOT.id, platformName = name, platformColorArgb = 0,
    )

    private fun expense(category: ExpenseCategory, date: LocalDate, minor: Long) =
        ExpenseEntity(amountMinor = minor, date = date.toEpochDays(), category = category.id, createdAt = 0)

    @Test
    fun summarisesTheMonth() {
        val income = listOf(
            income(1, "Bolt", LocalDate(2026, 10, 2), 30000),
            income(1, "Bolt", LocalDate(2026, 10, 3), 30000),
            income(2, "Uber", LocalDate(2026, 10, 3), 20000),
            income(1, "Bolt", LocalDate(2026, 9, 30), 50000), // September
        )
        val expenses = listOf(
            expense(ExpenseCategory.FUEL, LocalDate(2026, 10, 2), 15000),
            expense(ExpenseCategory.FUEL, LocalDate(2026, 10, 5), 5000),
            expense(ExpenseCategory.PARKING, LocalDate(2026, 10, 5), 1000),
        )
        val r = MonthlyReports.build(october, income, expenses, today = LocalDate(2026, 11, 2))
        assertEquals(80000, r.incomeMinor)
        assertEquals(21000, r.expensesMinor)
        assertEquals(59000, r.profitMinor)
        assertEquals(listOf("Bolt", "Uber"), r.platforms.map { it.name })
        assertEquals(0.75, r.platforms.first().share)
        assertEquals(listOf(ReportCategory(ExpenseCategory.FUEL, 20000), ReportCategory(ExpenseCategory.PARKING, 1000)), r.categories)
        assertEquals(2, r.daysWithIncome)
        assertEquals(31, r.daysSoFar)
        assertEquals(50000, r.previousProfitMinor)
        assertEquals(0.18, r.profitChange)
    }

    @Test
    fun noComparisonWithoutAPreviousMonth() {
        val r = MonthlyReports.build(october, listOf(income(1, "Bolt", LocalDate(2026, 10, 2), 100)), emptyList(), LocalDate(2026, 11, 2))
        assertNull(r.previousProfitMinor)
        assertNull(r.profitChange)
        assertEquals(31, r.daysSoFar)
    }

    @Test
    fun aRunningMonthIsComparedWithTheSameDays() {
        val income = listOf(
            income(1, "Bolt", LocalDate(2026, 10, 2), 10000),
            income(1, "Bolt", LocalDate(2026, 9, 5), 8000), // counted: 1–13 Sep
            income(1, "Bolt", LocalDate(2026, 9, 25), 50000), // not: after the 13th
        )
        val r = MonthlyReports.build(october, income, emptyList(), today = LocalDate(2026, 10, 13))
        assertEquals(8000, r.previousProfitMinor)
        assertEquals(13, r.daysSoFar)
    }
}
