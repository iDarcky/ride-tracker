package app.ridetracker.shared.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** What a monthly target counts. [id] is stored: never rename. */
enum class TargetBasis(val id: String) {
    /** What the platforms paid (after their fees). */
    INCOME("income"),

    /** Income minus expenses (fuel, car...). */
    KEPT("kept"),
    ;

    companion object {
        fun fromId(id: String?): TargetBasis = entries.firstOrNull { it.id == id } ?: INCOME
    }
}

/**
 * The driver's monthly targets. Each month keeps its own amount; a month without one takes the latest earlier
 * month's, so a new month starts with last month's target.
 */
data class TargetSettings(
    /** Amounts in minor units by month ("2026-10"). */
    val amounts: Map<String, Long> = emptyMap(),
    val basis: TargetBasis = TargetBasis.INCOME,
    /** Days of the week the driver usually drives: "per day" and the pace count only these. */
    val drivingDays: Set<DayOfWeek> = DEFAULT_DRIVING_DAYS,
    /** The month whose "target reached" notification was shown. */
    val notifiedMonth: String? = null,
    /** The driver closed Home's suggestion to set a target. */
    val suggestionDismissed: Boolean = false,
) {
    fun targetFor(month: Period.Month): Long? {
        val key = key(month)
        amounts[key]?.let { return it.takeIf { a -> a > 0 } }
        return amounts.filterKeys { it < key }.maxByOrNull { it.key }?.value?.takeIf { it > 0 }
    }

    val hasAny: Boolean get() = amounts.values.any { it > 0 }

    companion object {
        val DEFAULT_DRIVING_DAYS: Set<DayOfWeek> = DayOfWeek.entries.toSet() - DayOfWeek.SUNDAY

        fun key(month: Period.Month): String {
            val start = month.range.start
            return "${start.year}-${start.month.ordinal.plus(1).toString().padStart(2, '0')}"
        }

        fun formatAmounts(amounts: Map<String, Long>): String = amounts.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }

        fun parseAmounts(text: String?): Map<String, Long> = text.orEmpty().split(';').mapNotNull { part ->
            val (k, v) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            v.toLongOrNull()?.let { k.trim() to it }
        }.toMap()

        fun formatDays(days: Set<DayOfWeek>): String = days.sortedBy { it.ordinal }.joinToString(",") { (it.ordinal + 1).toString() }

        fun parseDays(text: String?): Set<DayOfWeek> =
            text?.split(',')?.mapNotNull { it.trim().toIntOrNull()?.let { n -> DayOfWeek.entries.getOrNull(n - 1) } }?.toSet()
                ?.takeIf { it.isNotEmpty() } ?: DEFAULT_DRIVING_DAYS
    }
}

/** Where a month stands against its target. Amounts in minor units. */
data class TargetProgress(
    val month: Period.Month,
    val targetMinor: Long,
    val achievedMinor: Long,
    /** Driving days from today to the end of the month (today included); 0 for a finished month. */
    val drivingDaysLeft: Int,
    /** What each driving day left needs; null when none are left or it's reached. */
    val perDayMinor: Long?,
    /** Achieved minus where the driver should be by now (by driving days before today); null outside the month. */
    val paceMinor: Long?,
    /** The month is over. */
    val finished: Boolean,
) {
    val reached: Boolean get() = achievedMinor >= targetMinor
    val remainingMinor: Long get() = (targetMinor - achievedMinor).coerceAtLeast(0)
    val fraction: Float get() = if (targetMinor <= 0) 0f else (achievedMinor.toFloat() / targetMinor).coerceIn(0f, 1f)
}

object TargetCalculator {

    fun progress(
        month: Period.Month,
        targetMinor: Long,
        achievedMinor: Long,
        drivingDays: Set<DayOfWeek>,
        today: LocalDate,
    ): TargetProgress {
        val days = generateSequence(month.range.start) { it.plus(DatePeriod(days = 1)) }
            .takeWhile { it <= month.range.endInclusive }.toList()
        val driving = days.filter { it.dayOfWeek in drivingDays.ifEmpty { DayOfWeek.entries.toSet() } }
        val finished = today > month.range.endInclusive
        val current = !finished && today >= month.range.start
        val left = when {
            finished -> 0
            current -> driving.count { it >= today }
            else -> driving.size
        }
        val remaining = (targetMinor - achievedMinor).coerceAtLeast(0)
        val perDay = if (left > 0 && remaining > 0) (remaining + left - 1) / left else null
        val pace = if (current && driving.isNotEmpty()) {
            val done = driving.count { it < today }
            achievedMinor - targetMinor * done / driving.size
        } else {
            null
        }
        return TargetProgress(month, targetMinor, achievedMinor, left, perDay, pace, finished)
    }
}
