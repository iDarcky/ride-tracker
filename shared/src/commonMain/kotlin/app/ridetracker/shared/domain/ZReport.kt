package app.ridetracker.shared.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus

/**
 * The daily reminder to print the cash register's Z report ("Raportul Z"), offered in Romania to every driver who
 * takes cash. One time for every day; a day stays waiting (on Home) until marked done.
 */
data class ZReportReminder(
    val enabled: Boolean = false,
    /** Minutes after midnight, the same every day. */
    val minuteOfDay: Int = DEFAULT_MINUTE,
    /** The last day marked done ("Gata"), epoch days. */
    val doneThrough: Long? = null,
    /** The day the reminder was turned on: days before it never wait. */
    val enabledFrom: Long? = null,
    /** The driver closed Home's suggestion to set it up. */
    val suggestionDismissed: Boolean = false,
) {
    val hour: Int get() = minuteOfDay / 60
    val minute: Int get() = minuteOfDay % 60

    /**
     * The day whose Z report is still waiting at [now]: today once the reminder time has passed, otherwise
     * yesterday (a missed one stays until the next reminder). Null when it's done, or the reminder is off.
     */
    fun waitingDay(now: LocalDateTime): LocalDate? {
        if (!enabled) return null
        val today = now.date
        val day = if (now.hour * 60 + now.minute >= minuteOfDay) today else today.minus(DatePeriod(days = 1))
        val epoch = day.toEpochDays()
        if (enabledFrom != null && epoch < enabledFrom) return null
        if (doneThrough != null && epoch <= doneThrough) return null
        return day
    }

    companion object {
        /** 22:00, the end of a usual driving day. */
        const val DEFAULT_MINUTE = 22 * 60
    }
}
