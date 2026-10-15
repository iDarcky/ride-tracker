package app.ridetracker.shared.domain

import app.ridetracker.shared.data.IncomeEntryEntity
import app.ridetracker.shared.data.PeriodSummaryEntity
import app.ridetracker.shared.data.PlatformEntity
import kotlinx.datetime.LocalDate

/**
 * One app's month-end files for a finished month (owner, 15 Oct 2026: two steps per app). Daily screenshots give each
 * day; the month-end files add what screenshots can't: the platform's own monthly total (campaigns, bonuses, its
 * fee) and the hours online.
 */
data class MonthEndApp(
    val platformId: Long,
    /** The platform's own total for exactly this month (Bolt's Monthly tab, Uber's payments_organization). */
    val hasMonthlyTotal: Boolean,
    /** Hours online known for every day the app was driven (Bolt's Activity month, Uber's time and distance file). */
    val hasHours: Boolean,
    /**
     * The monthly total minus what the month already has (days and any difference added before); null without a
     * monthly total or when it's under [MonthEnd.MIN_DIFFERENCE_MINOR] either way.
     */
    val differenceMinor: Long?,
    /** A difference already added to the month. */
    val addedMinor: Long,
) {
    val complete: Boolean get() = hasMonthlyTotal && hasHours
}

data class MonthEndStatus(val month: Period.Month, val apps: List<MonthEndApp>) {
    /** Every app driven that month has its files (a month nobody drove is complete). */
    val complete: Boolean get() = apps.all { it.complete }
    val appsDone: Int get() = apps.count { it.complete }
}

object MonthEnd {
    /** Differences smaller than one unit (1 RON, 1 €) are rounding, not money to add. */
    const val MIN_DIFFERENCE_MINOR = 100L

    /** Home shows the status of last month's files in the first days of a month. */
    const val STATUS_DAYS = 10

    /** The days of a month the reminder to import last month's files goes out (the second only if still needed). */
    val REMINDER_DAYS = setOf(1, 3)

    /** The apps that have month-end files: Bolt and Uber, found by name like the importers do. */
    fun platformIds(platforms: List<PlatformEntity>): Set<Long> =
        platforms.filter { p -> listOf("Bolt", "Uber").any { p.name.trim().equals(it, ignoreCase = true) } }.map { it.id }.toSet()

    /** Last month, while Home shows how its files are coming in; null later in the month. */
    fun statusMonth(today: LocalDate): Period.Month? =
        if (today.day <= STATUS_DAYS) Period.Month.containing(today).previous() else null

    /** [platformIds]: the apps that have month-end files (Bolt, Uber); null = every app driven that month. */
    fun compute(
        month: Period.Month,
        entries: List<IncomeEntryEntity>,
        summaries: List<PeriodSummaryEntity>,
        platformIds: Set<Long>? = null,
    ): MonthEndStatus {
        val start = month.range.start.toEpochDays()
        val end = month.range.endInclusive.toEpochDays()
        val inMonth = entries.filter { it.date in start..end }
        val days = inMonth.filter { IncomeSource.countsAsDay(it.source) && (platformIds == null || it.platformId in platformIds) }
        val apps = days.map { it.platformId }.distinct().sorted().map { platformId ->
            val mine = days.filter { it.platformId == platformId }
            val added = inMonth.filter { it.platformId == platformId && it.source == IncomeSource.MONTH_DIFFERENCE.id }.sumOf { it.amountMinor }
            val own = summaries.filter { it.platformId == platformId }
            val total = own.lastOrNull { it.earningsMinor != null && it.periodStart == start && it.periodEnd == end }?.earningsMinor
            val covered = own.filter { (it.onlineMinutes ?: 0) > 0 }.flatMap { (it.periodStart..it.periodEnd).toList() }.toSet() +
                mine.filter { (it.onlineMinutes ?: 0) > 0 }.map { it.date }
            val difference = total?.let { it - mine.sumOf { e -> e.amountMinor } - added }
            MonthEndApp(
                platformId = platformId,
                hasMonthlyTotal = total != null,
                hasHours = mine.all { it.date in covered },
                differenceMinor = difference?.takeIf { kotlin.math.abs(it) >= MIN_DIFFERENCE_MINOR },
                addedMinor = added,
            )
        }
        return MonthEndStatus(month, apps)
    }

    /**
     * Days of [range] up to [today] that are in: a screenshot, file or typed amount from any app, or marked as a day
     * off. Returns (days in, days so far).
     */
    fun daysIn(range: DateRange, today: LocalDate, entries: List<IncomeEntryEntity>, daysOff: Set<Long>): Pair<Int, Int> {
        val from = range.start.toEpochDays()
        val to = minOf(range.endInclusive, today).toEpochDays()
        if (to < from) return 0 to 0
        val done = entries.filter { IncomeSource.countsAsDay(it.source) }.map { it.date }.toSet() + daysOff
        return (from..to).count { it in done } to (to - from + 1).toInt()
    }
}
