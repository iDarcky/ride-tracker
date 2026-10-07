package app.ridetracker.shared.domain

import app.ridetracker.shared.data.EntryWithPlatform
import app.ridetracker.shared.data.ExpenseEntity
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CsvExportTest {
    private val labels = CsvLabels(
        date = "Date", type = "Type", item = "App or category", group = "Group", amount = "Amount",
        currency = "Currency", note = "Note", income = "Income", expense = "Expense",
        category = { it.id }, groupName = { it.name.lowercase() },
    )
    private fun day(d: Int) = LocalDate(2026, 10, d).toEpochDays()

    @Test
    fun buildsOneSortedFileWithNegativeExpenses() {
        val csv = CsvExport.build(
            income = listOf(EntryWithPlatform(1, 1, 31025, day(7), "evening, airport", 2, "Uber", 0)),
            expenses = listOf(ExpenseEntity(1, 25000, day(6), "fuel", "OMV \"Gold\"", 1)),
            currencyCode = "RON",
            fractionDigits = 2,
            labels = labels,
        )
        val lines = csv.trim().lines()
        assertEquals("Date,Type,App or category,Group,Amount,Currency,Note", lines[0])
        assertEquals("2026-10-06,Expense,fuel,vehicle,-250.00,RON,\"OMV \"\"Gold\"\"\"", lines[1])
        assertEquals("2026-10-07,Income,Uber,,310.25,RON,\"evening, airport\"", lines[2])
    }
}
