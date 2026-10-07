package app.ridetracker.shared.domain

import app.ridetracker.shared.data.EntryWithPlatform
import app.ridetracker.shared.data.ExpenseEntity
import kotlinx.datetime.LocalDate

/** Column titles and names in the user's language; numbers and dates stay international. */
data class CsvLabels(
    val date: String,
    val type: String,
    val item: String,
    val group: String,
    val amount: String,
    val currency: String,
    val note: String,
    val income: String,
    val expense: String,
    val category: (ExpenseCategory) -> String,
    val groupName: (ExpenseGroup) -> String,
)

/**
 * One CSV with income and expenses, oldest first. International format: comma-separated,
 * dot decimals, ISO dates; expenses are negative. Opens in Google Sheets and Excel.
 */
object CsvExport {

    fun build(
        income: List<EntryWithPlatform>,
        expenses: List<ExpenseEntity>,
        currencyCode: String,
        fractionDigits: Int,
        labels: CsvLabels,
    ): String {
        data class Row(val epochDay: Long, val createdAt: Long, val cells: List<String>)

        val rows = income.map {
            Row(
                it.date,
                it.createdAt,
                listOf(day(it.date), labels.income, it.platformName, "", Money.toSignedString(it.amountMinor, fractionDigits), currencyCode, it.note ?: ""),
            )
        } + expenses.map {
            val category = ExpenseCategory.fromId(it.category)
            Row(
                it.date,
                it.createdAt,
                listOf(
                    day(it.date), labels.expense, labels.category(category), labels.groupName(category.group),
                    Money.toSignedString(-it.amountMinor, fractionDigits), currencyCode, it.note ?: "",
                ),
            )
        }
        val header = listOf(labels.date, labels.type, labels.item, labels.group, labels.amount, labels.currency, labels.note)
        return buildString {
            appendLine(header.joinToString(",") { escape(it) })
            rows.sortedWith(compareBy({ it.epochDay }, { it.createdAt })).forEach { row ->
                appendLine(row.cells.joinToString(",") { escape(it) })
            }
        }
    }

    private fun day(epochDay: Long) = LocalDate.fromEpochDays(epochDay).toString()

    /** RFC 4180: quote cells containing commas, quotes or line breaks; double inner quotes. */
    fun escape(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell
}
