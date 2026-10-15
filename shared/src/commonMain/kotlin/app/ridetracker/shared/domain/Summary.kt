package app.ridetracker.shared.domain

import app.ridetracker.shared.data.ExpenseEntity
import app.ridetracker.shared.data.IncomeEntryEntity
import app.ridetracker.shared.data.LineInRange
import app.ridetracker.shared.data.PeriodSummaryEntity
import app.ridetracker.shared.data.TripWithPlatform
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** One app in a summary: what it paid and per hour online (null without hours). */
data class SummaryApp(val platformId: Long, val incomeMinor: Long, val perHourMinor: Long?)

/**
 * A finished week or month at a glance (owner, 15 Oct 2026): the weekly one on Monday at 10:00, the monthly one once
 * its month-end files are in. Plain figures to read and check; Wrapped is the shareable story of the same month.
 */
data class Summary(
    val range: DateRange,
    val monthly: Boolean,
    val incomeMinor: Long,
    val expensesMinor: Long,
    /** Money kept in the period before (the whole previous week or month); null when it has no data. */
    val previousKeptMinor: Long?,
    val real: RealHourly?,
    val daysDriven: Int,
    val daysOff: Int,
    /** Days up to today with nothing in and not marked as a day off ("Sunday missing"). */
    val missingDays: List<LocalDate>,
    val bestDay: DayIncome?,
    val apps: List<SummaryApp>,
    /** What the week paid out (weekly only). */
    val payout: WeekPayout?,
    /** Month differences added from the platforms' monthly totals (monthly only). */
    val monthDifferenceMinor: Long,
) {
    val keptMinor: Long get() = incomeMinor - expensesMinor
    val empty: Boolean get() = incomeMinor == 0L && expensesMinor == 0L
}

object SummaryCalculator {
    private val fuel = setOf(ExpenseCategory.FUEL, ExpenseCategory.CHARGING)

    fun compute(
        range: DateRange,
        monthly: Boolean,
        today: LocalDate,
        entries: List<IncomeEntryEntity>,
        lines: List<LineInRange>,
        trips: List<TripWithPlatform>,
        summaries: List<PeriodSummaryEntity>,
        expenses: List<ExpenseEntity>,
        daysOff: Set<Long>,
        previousKeptMinor: Long?,
    ): Summary {
        val stats = HomeStatsCalculator.compute(range, entries, lines, trips, summaries)
        val expensesMinor = expenses.sumOf { it.amountMinor }
        val fuelMinor = expenses.filter { ExpenseCategory.fromId(it.category) in fuel }.sumOf { it.amountMinor }
        val dayEntries = entries.filter { IncomeSource.countsAsDay(it.source) }
        val driven = dayEntries.filter { it.amountMinor > 0 }.map { it.date }.toSet()
        val inDays = dayEntries.map { it.date }.toSet()
        val from = range.start.toEpochDays()
        val to = minOf(range.endInclusive, today).toEpochDays()
        val off = daysOff.filter { it in from..to && it !in inDays }
        val missing = if (to < from) emptyList() else (from..to).filter { it !in inDays && it !in daysOff }.map(LocalDate::fromEpochDays)
        val apps = entries.groupBy { it.platformId }.toSortedMap().map { (platformId, list) ->
            val own = HomeStatsCalculator.compute(
                range, list, lines.filter { it.platformId == platformId }, trips.filter { it.platformId == platformId },
                summaries.filter { it.platformId == platformId },
            )
            SummaryApp(platformId, list.sumOf { it.amountMinor }, own.perHourMinor)
        }.sortedByDescending { it.incomeMinor }
        return Summary(
            range = range,
            monthly = monthly,
            incomeMinor = entries.sumOf { it.amountMinor },
            expensesMinor = expensesMinor,
            previousKeptMinor = previousKeptMinor,
            real = RealHourlyCalculator.compute(stats, fuelMinor, expensesMinor),
            daysDriven = driven.size,
            daysOff = off.size,
            missingDays = missing,
            bestDay = stats.days.takeIf { !stats.monthly }?.filter { it.totalMinor > 0 }?.maxByOrNull { it.totalMinor },
            apps = apps,
            payout = if (monthly) null else WeekPayouts.compute(entries),
            monthDifferenceMinor = entries.filter { it.source == IncomeSource.MONTH_DIFFERENCE.id }.sumOf { it.amountMinor },
        )
    }

    /** The last finished week before [today] (weeks start on [firstDay]). */
    fun lastWeek(today: LocalDate, firstDay: DayOfWeek): Period.Week = Period.Week.containing(today, firstDay).previous()

    /** Whether today is the day after a week ends (Monday for weeks starting on Monday), when its summary goes out. */
    fun isWeeklyDay(today: LocalDate, firstDay: DayOfWeek): Boolean = today.dayOfWeek == firstDay

    /** Whether [range] is exactly one calendar month. */
    fun isMonth(range: DateRange): Boolean = Period.Month.containing(range.start).range == range
}

/** Reads what a [Summary] needs from the repositories (for the screen and the Monday notification). */
class SummaryRepository(
    private val income: IncomeRepository,
    private val expenses: ExpenseRepository,
    private val imports: ImportRepository,
) {
    suspend fun summary(range: DateRange, today: LocalDate): Summary {
        val monthly = SummaryCalculator.isMonth(range)
        val previous = if (monthly) Period.Month.containing(range.start).previous().range else
            DateRange(range.start.minus(DatePeriod(days = 7)), range.endInclusive.minus(DatePeriod(days = 7)))
        val previousIncome = income.observeEntryDetails(previous).first()
        val previousExpenses = expenses.observeInRange(previous).first()
        val previousKept = if (previousIncome.isEmpty() && previousExpenses.isEmpty()) null else
            previousIncome.sumOf { it.amountMinor } - previousExpenses.sumOf { it.amountMinor }
        return SummaryCalculator.compute(
            range = range,
            monthly = monthly,
            today = today,
            entries = income.observeEntryDetails(range).first(),
            lines = income.observeLines(range).first(),
            trips = imports.observeTrips(range).first(),
            summaries = imports.observeSummaries(range).first(),
            expenses = expenses.observeInRange(range).first(),
            daysOff = imports.observeDaysOff().first(),
            previousKeptMinor = previousKept,
        )
    }

    /** Finished weeks and months that have income, newest first, for the Summaries list. */
    suspend fun available(today: LocalDate, firstDay: DayOfWeek): Pair<List<Period.Week>, List<Period.Month>> {
        val first = income.firstDay() ?: return emptyList<Period.Week>() to emptyList()
        val weeks = generateSequence(SummaryCalculator.lastWeek(today, firstDay)) { it.previous() }
            .takeWhile { it.range.endInclusive >= first }.take(12).toList()
        val months = generateSequence(Period.Month.containing(today).previous()) { it.previous() }
            .takeWhile { it.range.endInclusive >= first }.toList()
        val withIncome = income.observeEntryDetails(DateRange(first, today)).first().map { it.date }.toSet()
        fun hasIncome(r: DateRange) = (r.start.toEpochDays()..r.endInclusive.toEpochDays()).any { it in withIncome }
        return weeks.filter { hasIncome(it.range) } to months.filter { hasIncome(it.range) }
    }
}
