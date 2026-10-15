package app.ridetracker.shared.domain

import app.ridetracker.shared.data.IncomeEntryEntity
import kotlinx.datetime.LocalDate

/** The optional evening reminder to import the day's screenshots. One time for every day. */
data class ImportReminderSettings(
    val enabled: Boolean = false,
    /** Minutes after midnight. */
    val minuteOfDay: Int = DEFAULT_MINUTE,
) {
    val hour: Int get() = minuteOfDay / 60
    val minute: Int get() = minuteOfDay % 60

    companion object {
        /** 21:30: after most driving days, before the Raportul Z at 22:00. */
        const val DEFAULT_MINUTE = 21 * 60 + 30
    }
}

object ImportReminders {
    /** How far back the reminder looks to learn which apps the driver imports day by day. */
    const val LOOKBACK_DAYS = 14

    /**
     * Whether today's reminder is still needed: not when every app the driver imported a day from in the last two
     * weeks already has today (a screenshot, a file or a typed amount; estimates don't count). Someone who hasn't
     * imported days lately is reminded until today has something. Never on a day marked "Didn't drive".
     */
    fun shouldRemind(today: LocalDate, entries: List<IncomeEntryEntity>, daysOff: Set<Long> = emptySet()): Boolean {
        val t = today.toEpochDays()
        if (t in daysOff) return false
        val exact = entries.filter { IncomeSource.countsAsDay(it.source) }
        val usual = exact.filter { it.date in (t - LOOKBACK_DAYS) until t }.map { it.platformId }.toSet()
        val done = exact.filter { it.date == t }.map { it.platformId }.toSet()
        return if (usual.isEmpty()) done.isEmpty() else !done.containsAll(usual)
    }

    /** The days in the week up to [today] that have an exact entry (not an estimate) from [platformId]. */
    fun daysWithEntries(platformId: Long, entries: List<IncomeEntryEntity>, days: List<LocalDate>): Set<LocalDate> {
        val wanted = days.associateBy { it.toEpochDays() }
        return entries.filter { it.platformId == platformId && IncomeSource.countsAsDay(it.source) }
            .mapNotNull { wanted[it.date] }.toSet()
    }
}
