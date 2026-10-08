package app.ridetracker.shared.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** What the current period is compared with, so the label can say it plainly. */
sealed interface Comparison {
    val range: DateRange

    /** Month or week still in progress: same number of days at the start of the previous one. */
    data class SameDaysPrevious(override val range: DateRange, val type: PeriodType) : Comparison

    /** A finished month or week: the whole previous one. */
    data class WholePrevious(override val range: DateRange, val type: PeriodType) : Comparison

    /** A day: the same weekday one week earlier. */
    data class SameWeekdayLastWeek(override val range: DateRange) : Comparison

    /** A custom range: the same number of days just before it. */
    data class PreviousDays(override val range: DateRange, val days: Int) : Comparison
}

object Comparisons {

    /** Returns null for periods entirely in the future (nothing to compare yet). */
    fun of(period: Period, today: LocalDate): Comparison? {
        val range = period.range
        if (range.start > today) return null
        return when (period) {
            is Period.Day -> {
                val lastWeek = period.date.minus(DatePeriod(days = 7))
                Comparison.SameWeekdayLastWeek(DateRange(lastWeek, lastWeek))
            }
            is Period.Week, is Period.Month -> {
                val previous = period.previous().range
                val type = period.type
                if (today <= range.endInclusive) {
                    // In progress: compare day 1..N of this period with day 1..N of the previous one.
                    val elapsed = range.start.daysUntil(today)
                    val end = minOf(previous.start.plus(DatePeriod(days = elapsed)), previous.endInclusive)
                    Comparison.SameDaysPrevious(DateRange(previous.start, end), type)
                } else {
                    Comparison.WholePrevious(previous, type)
                }
            }
            // Everything so far: there is nothing before it to compare with.
            is Period.All -> null
            is Period.Custom -> {
                val days = range.lengthInDays
                Comparison.PreviousDays(
                    DateRange(range.start.minus(DatePeriod(days = days)), range.start.minus(DatePeriod(days = 1))),
                    days,
                )
            }
        }
    }

    /** Percentage change, or null when the previous value is zero or negative (a % would mislead). */
    fun percentChange(current: Long, previous: Long): Double? =
        if (previous <= 0) null else (current - previous).toDouble() / previous
}
