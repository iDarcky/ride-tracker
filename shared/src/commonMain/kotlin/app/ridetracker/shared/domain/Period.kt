package app.ridetracker.shared.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** An inclusive date range. */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(start <= endInclusive) { "start $start is after end $endInclusive" }
    }

    val lengthInDays: Int get() = start.daysUntil(endInclusive) + 1
}

/** A period the overview totals are shown for. */
sealed interface Period {
    val range: DateRange
    fun next(): Period
    fun previous(): Period

    data class Day(val date: LocalDate) : Period {
        override val range get() = DateRange(date, date)
        override fun next() = Day(date.plus(DatePeriod(days = 1)))
        override fun previous() = Day(date.minus(DatePeriod(days = 1)))
    }

    data class Week(val start: LocalDate) : Period {
        override val range get() = DateRange(start, start.plus(DatePeriod(days = 6)))
        override fun next() = Week(start.plus(DatePeriod(days = 7)))
        override fun previous() = Week(start.minus(DatePeriod(days = 7)))

        companion object {
            /** The week containing [date], starting on [firstDayOfWeek]. */
            fun containing(date: LocalDate, firstDayOfWeek: DayOfWeek): Week {
                val offset = (date.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber + 7) % 7
                return Week(date.minus(DatePeriod(days = offset)))
            }
        }
    }

    data class Month(val yearMonth: YearMonth) : Period {
        override val range get() = DateRange(yearMonth.firstDay, yearMonth.lastDay)
        override fun next() = Month(yearMonth.plus(1, DateTimeUnit.MONTH))
        override fun previous() = Month(yearMonth.minus(1, DateTimeUnit.MONTH))

        companion object {
            fun containing(date: LocalDate) = Month(YearMonth(date.year, date.month))
        }
    }

    /** A user-picked range; next/previous shift by the range's own length. */
    data class Custom(override val range: DateRange) : Period {
        override fun next() = shift(range.lengthInDays)
        override fun previous() = shift(-range.lengthInDays)

        private fun shift(days: Int) = Custom(
            DateRange(range.start.plus(DatePeriod(days = days)), range.endInclusive.plus(DatePeriod(days = days))),
        )
    }
}

enum class PeriodType { DAY, WEEK, MONTH, CUSTOM }

val Period.type: PeriodType
    get() = when (this) {
        is Period.Day -> PeriodType.DAY
        is Period.Week -> PeriodType.WEEK
        is Period.Month -> PeriodType.MONTH
        is Period.Custom -> PeriodType.CUSTOM
    }

/** Builds the period of [type] that contains [anchor]. CUSTOM keeps [current] if it is custom, else the anchor's month. */
fun periodOf(type: PeriodType, anchor: LocalDate, firstDayOfWeek: DayOfWeek, current: Period? = null): Period =
    when (type) {
        PeriodType.DAY -> Period.Day(anchor)
        PeriodType.WEEK -> Period.Week.containing(anchor, firstDayOfWeek)
        PeriodType.MONTH -> Period.Month.containing(anchor)
        PeriodType.CUSTOM -> current as? Period.Custom ?: Period.Custom(Period.Month.containing(anchor).range)
    }

