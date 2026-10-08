package app.ridetracker.shared.domain

import app.ridetracker.shared.data.IncomeEntryEntity
import app.ridetracker.shared.data.LineInRange
import app.ridetracker.shared.data.PeriodSummaryEntity
import app.ridetracker.shared.data.TripWithPlatform
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Income of one day, per platform (platform id -> amount), and its trips (imported, or as reported). */
data class DayIncome(
    val date: LocalDate,
    val byPlatform: Map<Long, Long>,
    val tripCount: Int = 0,
    /** Part of the day's income that is estimated (no screenshot that day). */
    val estimatedMinor: Long = 0,
) {
    val totalMinor: Long get() = byPlatform.values.sum()
}

/**
 * Everything Home shows beyond the totals, from the period's entries, their imported breakdown lines
 * and imported trips. Figures a report doesn't give stay null, so Home can leave them out.
 */
data class HomeStats(
    /** What the apps paid: the entries' amounts (after commission). */
    val netIncomeMinor: Long,
    /** Part of [netIncomeMinor] estimated for days without a screenshot. */
    val estimatedMinor: Long = 0,
    /** Before commission: breakdown lines where known, the entry amount where not. */
    val grossMinor: Long,
    /** Commission and other platform fees (negative or 0). */
    val feesMinor: Long,
    /** False when some income was typed in without a breakdown, so its fees are unknown. */
    val feesKnownForAll: Boolean,
    val faresMinor: Long,
    val bonusesAndTipsMinor: Long,
    /** Gross that is neither fares nor bonuses/tips: tolls, cancellation fees, other. */
    val otherIncomeMinor: Long,
    /** Imported trips, plus trip counts reported on entries of platforms without imported trips. */
    val tripCount: Int,
    /** Average fare per imported trip; null without trips. */
    val averageFareMinor: Long?,
    val onlineMinutes: Int?,
    /** Income per hour online, from the entries that have online time; null without any. */
    val perHourMinor: Long?,
    /** Paid km: trip distances, or a platform's own monthly total when the period is exactly that month. */
    val distanceMeters: Long?,
    /** Income per paid km of the platforms that have km; null without any. */
    val perKmMinor: Long?,
    /** Platforms with income in the period, and those that each figure covers (for "Uber only" labels). */
    val incomePlatformIds: Set<Long>,
    val tripPlatformIds: Set<Long>,
    val hourPlatformIds: Set<Long>,
    val kmPlatformIds: Set<Long>,
    /**
     * One item per day of the range (empty days included), for the activity chart and sparkline; for ranges longer
     * than [HomeStatsCalculator.MAX_CHART_DAYS], one item per month instead (dated on its first day).
     */
    val days: List<DayIncome>,
    /** True when [days] holds months. */
    val monthly: Boolean = false,
    /** Trip fares by ISO weekday (index 0 = Monday) and 4-hour slot (index 0 = 00–04). */
    val heat: List<List<Long>>,
)

object HomeStatsCalculator {

    private val feeKinds = setOf(IncomeLineKind.COMMISSION.id, IncomeLineKind.OTHER_FEE.id)
    private val bonusKinds = setOf(IncomeLineKind.BONUS.id, IncomeLineKind.TIP.id, IncomeLineKind.PROMOTION.id)

    /** Longest range the daily activity chart draws (custom ranges can be years). */
    const val MAX_CHART_DAYS = 62

    fun compute(
        range: DateRange,
        entries: List<IncomeEntryEntity>,
        lines: List<LineInRange>,
        trips: List<TripWithPlatform>,
        summaries: List<PeriodSummaryEntity> = emptyList(),
    ): HomeStats {
        val linesByEntry = lines.groupBy { it.entryId }
        var gross = 0L
        var fees = 0L
        var fares = 0L
        var bonuses = 0L
        var feesKnown = true
        // A platform's own breakdown for exactly this period (Bolt's monthly screenshot) covers every day of it,
        // estimated ones included: take gross, fees and their parts from it instead of from the entries.
        val ownTotals = summaries
            .filter { it.earningsMinor != null && it.periodStart == range.start.toEpochDays() && it.periodEnd == range.endInclusive.toEpochDays() }
            .distinctBy { it.platformId }
        for (summary in ownTotals) {
            val summaryFees = summary.platformFeeMinor ?: 0
            val summaryGross = (summary.earningsMinor ?: 0) - summaryFees
            gross += summaryGross
            fees += summaryFees
            fares += summary.grossFareMinor ?: 0
            bonuses += (summary.bonusMinor ?: 0) + (summary.tipsMinor ?: 0)
        }
        val covered = ownTotals.map { it.platformId }.toSet()
        for (entry in entries.filter { it.platformId !in covered }) {
            val own = linesByEntry[entry.id]
            if (own.isNullOrEmpty()) {
                gross += entry.amountMinor
                if (entry.amountMinor != 0L) feesKnown = false
                continue
            }
            for (line in own) {
                when (line.kind) {
                    in feeKinds -> fees += line.amountMinor
                    else -> {
                        gross += line.amountMinor
                        if (line.kind == IncomeLineKind.FARE.id) fares += line.amountMinor
                        if (line.kind in bonusKinds) bonuses += line.amountMinor
                    }
                }
            }
        }
        // Income typed in without a breakdown counts as fares: it is what the driver was paid for rides.
        fares += entries.filter { it.platformId !in covered && linesByEntry[it.id].isNullOrEmpty() }.sumOf { it.amountMinor }

        val timed = entries.filter { (it.onlineMinutes ?: 0) > 0 }
        var minutes = timed.sumOf { it.onlineMinutes ?: 0 }
        var timedIncome = timed.sumOf { it.amountMinor }
        val hourPlatforms = timed.map { it.platformId }.toMutableSet()
        // Platforms whose entries have no online time: their own totals (Bolt's Activity screen), for exactly this
        // period, or else added up from single days inside it. Money per hour uses the same span's income.
        val onlineTotals = summaries.filter { (it.onlineMinutes ?: 0) > 0 && it.earningsMinor == null && it.grossFareMinor == null }
        val start = range.start.toEpochDays()
        val end = range.endInclusive.toEpochDays()
        for ((platformId, own) in onlineTotals.groupBy { it.platformId }) {
            if (platformId in hourPlatforms) continue
            // Totals that start inside the period (a week still in progress counts), longest first (month, week,
            // day), never overlapping; the newest import wins for the same span.
            val picked = mutableListOf<PeriodSummaryEntity>()
            own.filter { it.periodStart in start..end }
                .groupBy { it.periodStart to it.periodEnd }.map { (_, list) -> list.maxBy { it.id } }
                .sortedByDescending { it.periodEnd - it.periodStart }
                .forEach { candidate ->
                    if (picked.none { candidate.periodStart <= it.periodEnd && it.periodStart <= candidate.periodEnd }) picked += candidate
                }
            if (picked.isEmpty()) continue
            minutes += picked.sumOf { it.onlineMinutes ?: 0 }
            timedIncome += entries.filter { e -> e.platformId == platformId && picked.any { e.date in it.periodStart..it.periodEnd } }
                .sumOf { it.amountMinor }
            hourPlatforms += platformId
        }

        // Paid km per platform: its trips' distances, else its own total for exactly this period (Bolt's monthly PDF).
        val metersByPlatform = mutableMapOf<Long, Long>()
        trips.filter { (it.distanceMeters ?: 0) > 0 }.forEach {
            metersByPlatform[it.platformId] = (metersByPlatform[it.platformId] ?: 0) + (it.distanceMeters ?: 0)
        }
        summaries
            .filter { it.periodStart == range.start.toEpochDays() && it.periodEnd == range.endInclusive.toEpochDays() }
            .filter { (it.distanceMeters ?: 0) > 0 && it.platformId !in metersByPlatform }
            .distinctBy { it.platformId }
            .forEach { metersByPlatform[it.platformId] = it.distanceMeters ?: 0 }
        val meters = metersByPlatform.values.sum()
        val kmIncome = entries.filter { it.platformId in metersByPlatform }.sumOf { it.amountMinor }

        val importedTripPlatformDays = trips.map { it.platformId to it.date }.toSet()
        val reportedTrips = entries.filter { (it.platformId to it.date) !in importedTripPlatformDays && (it.tripCount ?: 0) > 0 }

        val byDay = entries.groupBy { it.date }
        val tripsByDay = trips.groupBy { it.date }
        fun bucket(date: LocalDate, from: LocalDate, to: LocalDate): DayIncome {
            val list = entries.filter { it.date in from.toEpochDays()..to.toEpochDays() }
            val imported = trips.filter { it.date in from.toEpochDays()..to.toEpochDays() }
            val importedDays = imported.map { it.platformId to it.date }.toSet()
            // Imported trips per platform-day; an entry's own trip count where that platform-day has none imported.
            val reported = list.filter { (it.platformId to it.date) !in importedDays }.sumOf { it.tripCount ?: 0 }
            return DayIncome(
                date,
                list.groupBy { it.platformId }.mapValues { (_, e) -> e.sumOf { it.amountMinor } },
                imported.size + reported,
                list.filter { it.source == IncomeSource.ESTIMATE.id }.sumOf { it.amountMinor },
            )
        }
        val monthly = range.dayCount() > MAX_CHART_DAYS
        val days = if (!monthly) {
            generateSequence(range.start) { it.plus(DatePeriod(days = 1)) }
                .takeWhile { it <= range.endInclusive }
                .map { day -> if (byDay[day.toEpochDays()] == null && tripsByDay[day.toEpochDays()] == null) DayIncome(day, emptyMap()) else bucket(day, day, day) }
                .toList()
        } else {
            generateSequence(LocalDate(range.start.year, range.start.month, 1)) { it.plus(DatePeriod(months = 1)) }
                .takeWhile { it <= range.endInclusive }
                .map { first ->
                    val last = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
                    bucket(first, maxOf(first, range.start), minOf(last, range.endInclusive))
                }
                .toList()
        }

        val heat = List(7) { MutableList(6) { 0L } }
        for (trip in trips) {
            val weekday = LocalDate.fromEpochDays(trip.date).dayOfWeek.isoDayNumber - 1
            val slot = (trip.startMinute / 240).coerceIn(0, 5)
            heat[weekday][slot] += trip.fareMinor
        }

        return HomeStats(
            netIncomeMinor = entries.sumOf { it.amountMinor },
            estimatedMinor = entries.filter { it.source == IncomeSource.ESTIMATE.id }.sumOf { it.amountMinor },
            grossMinor = gross,
            feesMinor = fees,
            feesKnownForAll = feesKnown,
            faresMinor = fares,
            bonusesAndTipsMinor = bonuses,
            otherIncomeMinor = gross - fares - bonuses,
            tripCount = trips.size + reportedTrips.sumOf { it.tripCount ?: 0 },
            averageFareMinor = if (trips.isEmpty()) null else trips.sumOf { it.fareMinor } / trips.size,
            onlineMinutes = minutes.takeIf { it > 0 },
            perHourMinor = if (minutes > 0 && timedIncome > 0) (timedIncome * 60 + minutes / 2) / minutes else null,
            distanceMeters = meters.takeIf { it > 0 },
            perKmMinor = if (meters > 0 && kmIncome > 0) (kmIncome * 1000 + meters / 2) / meters else null,
            incomePlatformIds = entries.filter { it.amountMinor != 0L }.map { it.platformId }.toSet(),
            tripPlatformIds = (trips.map { it.platformId } + reportedTrips.map { it.platformId }).toSet(),
            hourPlatformIds = hourPlatforms,
            kmPlatformIds = metersByPlatform.keys,
            days = days,
            monthly = monthly,
            heat = heat,
        )
    }

    private fun DateRange.dayCount(): Long = endInclusive.toEpochDays() - start.toEpochDays() + 1
}
