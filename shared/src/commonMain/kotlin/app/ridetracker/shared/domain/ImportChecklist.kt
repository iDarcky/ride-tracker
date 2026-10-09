package app.ridetracker.shared.domain

import app.ridetracker.shared.data.IncomeEntryEntity
import app.ridetracker.shared.data.PeriodSummaryEntity
import app.ridetracker.shared.data.TripWithPlatform
import kotlinx.datetime.LocalDate

/** What a month has from a platform's reports, so the Import screen can say what's still missing. */
data class ImportChecklist(
    val month: Period.Month,
    /** The platform's own monthly total (Bolt's Monthly tab). */
    val hasMonthlyTotal: Boolean,
    /** False while the month is still running: the monthly total only makes sense once it's over. */
    val monthOver: Boolean,
    /** Days with a daily screenshot (or typed in). */
    val exactDays: Int,
    /** Days with trips or estimated income but no daily screenshot. */
    val daysWithoutScreenshot: Int,
    val hasTrips: Boolean,
    val hasHours: Boolean,
) {
    /** Nothing at all imported for this month yet. */
    val empty: Boolean get() = !hasMonthlyTotal && exactDays == 0 && !hasTrips && !hasHours && daysWithoutScreenshot == 0
}

object ImportChecklists {

    fun compute(
        month: Period.Month,
        platformId: Long,
        entries: List<IncomeEntryEntity>,
        trips: List<TripWithPlatform>,
        summaries: List<PeriodSummaryEntity>,
        today: LocalDate,
    ): ImportChecklist {
        val start = month.range.start.toEpochDays()
        val end = month.range.endInclusive.toEpochDays()
        val mine = entries.filter { it.platformId == platformId && it.date in start..end }
        val exact = mine.filter { it.source != IncomeSource.ESTIMATE.id }.map { it.date }.toSet()
        val other = (mine.filter { it.source == IncomeSource.ESTIMATE.id }.map { it.date } +
            trips.filter { it.platformId == platformId && it.date in start..end }.map { it.date }).toSet() - exact
        val own = summaries.filter { it.platformId == platformId }
        return ImportChecklist(
            month = month,
            hasMonthlyTotal = own.any { it.earningsMinor != null && it.periodStart == start && it.periodEnd == end },
            monthOver = month.range.endInclusive < today,
            exactDays = exact.size,
            daysWithoutScreenshot = other.size,
            hasTrips = trips.any { it.platformId == platformId && it.date in start..end },
            hasHours = own.any { (it.onlineMinutes ?: 0) > 0 && it.periodStart in start..end },
        )
    }
}
