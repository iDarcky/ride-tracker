package app.ridetracker.shared.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** How often a recurring expense repeats. [id] is stored: never rename. */
enum class Frequency(val id: String) {
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    YEARLY("yearly"),
    ;

    companion object {
        fun fromId(id: String?): Frequency = entries.firstOrNull { it.id == id } ?: MONTHLY
    }
}

object Recurrence {

    /**
     * The [n]th occurrence (0 = [anchor]). Always computed from the anchor, so a payment on the 31st
     * falls on the last day of shorter months and goes back to the 31st afterwards (no drift).
     */
    fun occurrence(anchor: LocalDate, frequency: Frequency, n: Int): LocalDate = when (frequency) {
        Frequency.WEEKLY -> anchor.plus(DatePeriod(days = 7 * n))
        Frequency.MONTHLY -> anchor.plus(DatePeriod(months = n))
        Frequency.YEARLY -> anchor.plus(DatePeriod(years = n))
    }

    /** First occurrence strictly after [after]. */
    fun nextAfter(after: LocalDate, anchor: LocalDate, frequency: Frequency): LocalDate {
        var n = 0
        while (true) {
            val date = occurrence(anchor, frequency, n)
            if (date > after) return date
            n++
        }
    }

    /** True when [due] is on or before [today] and the series has not ended. */
    fun isPending(due: LocalDate, end: LocalDate?, today: LocalDate): Boolean =
        due <= today && (end == null || due <= end)
}
