package app.ridetracker.shared.domain

import app.ridetracker.shared.data.ExpenseEntity
import app.ridetracker.shared.data.IncomeEntryEntity
import app.ridetracker.shared.data.PlatformEntity
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/** One app's share of the period's income. */
data class WrappedApp(val name: String, val colorArgb: Long, val totalMinor: Long, val share: Double)

/**
 * A month or a year, wrapped (owner, 15 Oct 2026: "like Spotify Wrapped"): the story cards' numbers. Anything the
 * driver's data doesn't have (hours, trips, km, a target) is null, and its card is left out.
 */
data class Wrapped(
    val range: DateRange,
    /** The whole year, not a month. */
    val yearly: Boolean,
    val daysDriven: Int,
    val incomeMinor: Long,
    val expensesMinor: Long,
    /** Expenses by category, biggest first (the "where your money went" card). */
    val expenseCategories: List<ReportCategory>,
    /** Money kept, before: the previous month or year; null when it has no data. */
    val previousKeptMinor: Long?,
    val bestDay: LocalDate?,
    val bestDayMinor: Long,
    /** The weekday with the highest average per day driven, and that average; null with fewer than 2 weeks driven. */
    val bestWeekday: DayOfWeek?,
    val bestWeekdayAverageMinor: Long,
    /** Yearly: the month with the most income. */
    val bestMonth: Period.Month?,
    val bestMonthMinor: Long,
    val onlineMinutes: Int?,
    val perHourMinor: Long?,
    val trips: Int,
    val distanceMeters: Long?,
    /** Apps by income, biggest first; one entry when the driver uses one app. */
    val apps: List<WrappedApp>,
    /** Monthly: the target and how far it got; yearly: months whose target was reached, of those with one. */
    val target: TargetProgress?,
    val monthsTargetReached: Int,
    val monthsWithTarget: Int,
) {
    val keptMinor: Long get() = incomeMinor - expensesMinor

    /** Change in money kept against the previous period (0.12 = +12%); null without a usable comparison. */
    val keptChange: Double? get() = previousKeptMinor?.takeIf { it > 0 }?.let { (keptMinor - it).toDouble() / it }

    /** Enough to be worth a story: at least two days driven. */
    val worthShowing: Boolean get() = daysDriven >= 2
}

object WrappedCalculator {

    /** The year as a range, for a yearly Wrapped. */
    fun yearRange(year: Int): DateRange = DateRange(LocalDate(year, 1, 1), LocalDate(year, 12, 31))

    fun compute(
        range: DateRange,
        yearly: Boolean,
        entries: List<IncomeEntryEntity>,
        expenses: List<ExpenseEntity>,
        previousIncomeMinor: Long?,
        previousExpensesMinor: Long?,
        stats: HomeStats,
        platforms: List<PlatformEntity>,
        targets: TargetSettings,
        today: LocalDate,
    ): Wrapped {
        val inRange = entries.filter { LocalDate.fromEpochDays(it.date).let { d -> d >= range.start && d <= range.endInclusive } }
        val byDay = inRange.groupBy { LocalDate.fromEpochDays(it.date) }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
            .filterValues { it > 0 }
        val best = byDay.maxByOrNull { it.value }
        // Best weekday by average, only once each weekday has a few days behind it.
        val weekdays = byDay.entries.groupBy { it.key.dayOfWeek }
            .filterValues { it.size >= 2 }
            .mapValues { (_, days) -> days.sumOf { it.value } / days.size }
        val bestWeekday = weekdays.maxByOrNull { it.value }
        val months = byDay.entries.groupBy { Period.Month.containing(it.key) }.mapValues { (_, days) -> days.sumOf { it.value } }
        val bestMonth = if (yearly) months.maxByOrNull { it.value } else null
        val income = inRange.sumOf { it.amountMinor }
        val names = platforms.associateBy { it.id }
        val apps = inRange.groupBy { it.platformId }
            .map { (id, list) ->
                val total = list.sumOf { it.amountMinor }
                WrappedApp(names[id]?.name ?: "?", names[id]?.colorArgb ?: 0, total, if (income > 0) total.toDouble() / income else 0.0)
            }
            .filter { it.totalMinor > 0 }
            .sortedByDescending { it.totalMinor }
        val periodExpenses = expenses.filter { LocalDate.fromEpochDays(it.date).let { d -> d >= range.start && d <= range.endInclusive } }
        val expensesMinor = periodExpenses.sumOf { it.amountMinor }
        val categories = periodExpenses.groupBy { ExpenseCategory.fromId(it.category) }
            .map { (category, list) -> ReportCategory(category, list.sumOf { it.amountMinor }) }
            .sortedByDescending { it.totalMinor }

        // Targets: this month's progress, or, for a year, how many of its months reached theirs.
        var target: TargetProgress? = null
        var reached = 0
        var withTarget = 0
        val monthsInRange = generateSequence(Period.Month(YearMonth(range.start.year, range.start.month))) { it.next() as Period.Month }
            .takeWhile { it.range.start <= range.endInclusive && it.range.start <= today }
            .toList()
        for (month in monthsInRange) {
            val amount = targets.targetFor(month) ?: continue
            val monthIncome = inRange.filter { Period.Month.containing(LocalDate.fromEpochDays(it.date)) == month }.sumOf { it.amountMinor }
            val monthExpenses = expenses.filter { Period.Month.containing(LocalDate.fromEpochDays(it.date)) == month }.sumOf { it.amountMinor }
            val progress = TargetCalculator.progress(month, amount, TargetCalculator.achieved(targets.basis, monthIncome, monthExpenses), targets.drivingDays, today)
            withTarget++
            if (progress.reached) reached++
            if (!yearly) target = progress
        }

        return Wrapped(
            range = range,
            yearly = yearly,
            daysDriven = byDay.size,
            incomeMinor = income,
            expensesMinor = expensesMinor,
            expenseCategories = categories,
            previousKeptMinor = if (previousIncomeMinor == null && previousExpensesMinor == null) null
            else (previousIncomeMinor ?: 0) - (previousExpensesMinor ?: 0),
            bestDay = best?.key,
            bestDayMinor = best?.value ?: 0,
            bestWeekday = bestWeekday?.key,
            bestWeekdayAverageMinor = bestWeekday?.value ?: 0,
            bestMonth = bestMonth?.key,
            bestMonthMinor = bestMonth?.value ?: 0,
            onlineMinutes = stats.onlineMinutes,
            perHourMinor = stats.perHourMinor,
            trips = stats.tripCount,
            distanceMeters = stats.distanceMeters,
            apps = apps,
            target = target,
            monthsTargetReached = reached,
            monthsWithTarget = withTarget,
        )
    }

    /**
     * Which Wrapped Home offers today: last month's once its month-end files are all in ([lastMonthComplete]), or from
     * the 10th anyway (it may still change), to the end of the month (owner, 15 Oct 2026); the year's from 1 December
     * to 15 January (the year just ended, in January). Null otherwise.
     */
    fun offered(today: LocalDate, lastMonthComplete: Boolean = false): Pair<DateRange, Boolean>? = when {
        today.month == kotlinx.datetime.Month.DECEMBER -> yearRange(today.year) to true
        today.month == kotlinx.datetime.Month.JANUARY && today.day <= 15 -> yearRange(today.year - 1) to true
        lastMonthComplete || today.day >= MonthEnd.STATUS_DAYS -> Period.Month.containing(today).previous().range to false
        else -> null
    }
}
