package app.ridetracker.shared.domain

import app.ridetracker.shared.data.EntryWithPlatform
import app.ridetracker.shared.data.ExpenseEntity
import kotlinx.datetime.LocalDate

/** One app's income in the month and its share of the total. */
data class ReportPlatform(val name: String, val colorArgb: Long, val totalMinor: Long, val share: Double)

data class ReportCategory(val category: ExpenseCategory, val totalMinor: Long)

/**
 * The month at a glance, for the shareable report (PDF or image): income, expenses, profit, income by app and
 * expenses by category. No names and no day-by-day table (owner, 13 Oct 2026).
 */
data class MonthlyReport(
    val month: Period.Month,
    val incomeMinor: Long,
    val expensesMinor: Long,
    /** The previous month's profit; null when it has no income or expenses (then no comparison is shown). */
    val previousProfitMinor: Long?,
    val platforms: List<ReportPlatform>,
    val categories: List<ReportCategory>,
    /** Days of the month with income, out of [daysSoFar]: a day without a screenshot counts as 0. */
    val daysWithIncome: Int,
    /** The month's days up to today (all of them once it's over). */
    val daysSoFar: Int,
) {
    val profitMinor: Long get() = incomeMinor - expensesMinor

    /** Profit change against the previous month, as a fraction (0.12 = +12%); null without a usable comparison. */
    val profitChange: Double?
        get() = previousProfitMinor?.takeIf { it > 0 }?.let { (profitMinor - it).toDouble() / it }
}

object MonthlyReports {
    /** [income] and [expenses] may cover more than the month (the previous one is used for the comparison). */
    fun build(month: Period.Month, income: List<EntryWithPlatform>, expenses: List<ExpenseEntity>, today: LocalDate): MonthlyReport {
        fun inMonth(date: Long, m: Period.Month) = LocalDate.fromEpochDays(date).let { it >= m.range.start && it <= m.range.endInclusive }
        val previous = month.previous() as Period.Month
        val monthIncome = income.filter { inMonth(it.date, month) }
        val monthExpenses = expenses.filter { inMonth(it.date, month) }
        val incomeMinor = monthIncome.sumOf { it.amountMinor }
        val previousIncome = income.filter { inMonth(it.date, previous) }
        val previousExpenses = expenses.filter { inMonth(it.date, previous) }
        val days = month.range.lengthInDays
        val daysSoFar = when {
            today > month.range.endInclusive -> days
            today < month.range.start -> 0
            else -> today.day
        }
        return MonthlyReport(
            month = month,
            incomeMinor = incomeMinor,
            expensesMinor = monthExpenses.sumOf { it.amountMinor },
            previousProfitMinor = if (previousIncome.isEmpty() && previousExpenses.isEmpty()) null
            else previousIncome.sumOf { it.amountMinor } - previousExpenses.sumOf { it.amountMinor },
            platforms = monthIncome.groupBy { it.platformId }.values
                .map { list ->
                    val total = list.sumOf { it.amountMinor }
                    ReportPlatform(list.first().platformName, list.first().platformColorArgb, total, if (incomeMinor > 0) total.toDouble() / incomeMinor else 0.0)
                }
                .sortedByDescending { it.totalMinor },
            categories = monthExpenses.groupBy { ExpenseCategory.fromId(it.category) }
                .map { (category, list) -> ReportCategory(category, list.sumOf { it.amountMinor }) }
                .sortedByDescending { it.totalMinor },
            daysWithIncome = monthIncome.filter { it.amountMinor != 0L }.map { it.date }.distinct().size,
            daysSoFar = daysSoFar,
        )
    }
}
