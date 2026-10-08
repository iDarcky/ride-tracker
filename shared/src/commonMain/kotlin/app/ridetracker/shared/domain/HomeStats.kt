package app.ridetracker.shared.domain

import app.ridetracker.shared.data.IncomeEntryEntity
import app.ridetracker.shared.data.LineInRange
import app.ridetracker.shared.data.PeriodSummaryEntity
import app.ridetracker.shared.data.TripWithPlatform
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/** Income of one day, per platform (platform id -> amount), and its trips (imported, or as reported). */
data class DayIncome(val date: LocalDate, val byPlatform: Map<Long, Long>, val tripCount: Int = 0) {
    val totalMinor: Long get() = byPlatform.values.sum()
}

/**
 * Everything Home shows beyond the totals, from the period's entries, their imported breakdown lines
 * and imported trips. Figures a report doesn't give stay null, so Home can leave them out.
 */
data class HomeStats(
    /** What the apps paid: the entries' amounts (after commission). */
    val netIncomeMinor: Long,
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
    /** One item per day of the range (empty days included), for the activity chart and sparkline. */
    val days: List<DayIncome>,
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
        for (entry in entries) {
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
        fares += entries.filter { linesByEntry[it.id].isNullOrEmpty() }.sumOf { it.amountMinor }

        val timed = entries.filter { (it.onlineMinutes ?: 0) > 0 }
        val minutes = timed.sumOf { it.onlineMinutes ?: 0 }

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

        val days = if (range.dayCount() <= MAX_CHART_DAYS) {
            val byDay = entries.groupBy { it.date }
            val tripsByDay = trips.groupBy { it.date }
            generateSequence(range.start) { it.plus(DatePeriod(days = 1)) }
                .takeWhile { it <= range.endInclusive }
                .map { day ->
                    val list = byDay[day.toEpochDays()].orEmpty()
                    val imported = tripsByDay[day.toEpochDays()].orEmpty()
                    // Imported trips per platform; an entry's own trip count where a platform has none imported.
                    val reported = list.filter { e -> imported.none { it.platformId == e.platformId } }.sumOf { it.tripCount ?: 0 }
                    DayIncome(day, list.groupBy { it.platformId }.mapValues { (_, e) -> e.sumOf { it.amountMinor } }, imported.size + reported)
                }
                .toList()
        } else {
            emptyList()
        }

        val heat = List(7) { MutableList(6) { 0L } }
        for (trip in trips) {
            val weekday = LocalDate.fromEpochDays(trip.date).dayOfWeek.isoDayNumber - 1
            val slot = (trip.startMinute / 240).coerceIn(0, 5)
            heat[weekday][slot] += trip.fareMinor
        }

        return HomeStats(
            netIncomeMinor = entries.sumOf { it.amountMinor },
            grossMinor = gross,
            feesMinor = fees,
            feesKnownForAll = feesKnown,
            faresMinor = fares,
            bonusesAndTipsMinor = bonuses,
            otherIncomeMinor = gross - fares - bonuses,
            tripCount = trips.size + reportedTrips.sumOf { it.tripCount ?: 0 },
            averageFareMinor = if (trips.isEmpty()) null else trips.sumOf { it.fareMinor } / trips.size,
            onlineMinutes = minutes.takeIf { it > 0 },
            perHourMinor = if (minutes > 0) (timed.sumOf { it.amountMinor } * 60 + minutes / 2) / minutes else null,
            distanceMeters = meters.takeIf { it > 0 },
            perKmMinor = if (meters > 0 && kmIncome > 0) (kmIncome * 1000 + meters / 2) / meters else null,
            incomePlatformIds = entries.filter { it.amountMinor != 0L }.map { it.platformId }.toSet(),
            tripPlatformIds = (trips.map { it.platformId } + reportedTrips.map { it.platformId }).toSet(),
            hourPlatformIds = timed.map { it.platformId }.toSet(),
            kmPlatformIds = metersByPlatform.keys,
            days = days,
            heat = heat,
        )
    }

    private fun DateRange.dayCount(): Long = endInclusive.toEpochDays() - start.toEpochDays() + 1
}
