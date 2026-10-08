package app.ridetracker.shared.domain

import kotlin.math.roundToLong
import kotlinx.datetime.LocalDate

/** Where a month's estimate came from, best first. Shown with the estimated days. */
enum class EstimateBasis { MONTHLY_TOTAL, MONTHLY_SUMMARY, TRIP_FARES }

/** Estimated income for one day without a screenshot. */
data class EstimatedDay(val date: LocalDate, val amountMinor: Long)

/**
 * Income for the days of one month that have no exact entry (daily screenshot or typed in), from the platform's
 * own reports. Exact days are never touched; the estimate only fills the rest, so nothing is counted twice.
 *
 * Best source first:
 * 1. [monthlyEarningsMinor], the platform's own "your earnings" for the month (Bolt's monthly breakdown):
 *    the rest of the month is that minus the exact days.
 * 2. [summaryFaresMinor] (+ cancellation and tips) from a monthly summary PDF, after commission at [keepRate].
 *    It has no campaigns, so it can only be lower than the real figure.
 * 3. The imported trips' fares after commission at [keepRate].
 *
 * The rest is spread over the days with trips, in proportion to each day's fares. Without trips it goes on
 * [lastDay] as one "rest of the month" entry.
 */
object IncomeEstimator {

    data class Result(val days: List<EstimatedDay>, val basis: EstimateBasis?)

    fun estimate(
        exactByDay: Map<LocalDate, Long>,
        tripFaresByDay: Map<LocalDate, Long>,
        monthlyEarningsMinor: Long?,
        summaryFaresMinor: Long?,
        summaryCancellationMinor: Long?,
        summaryTipsMinor: Long?,
        /** Share of fares the driver keeps after commission (e.g. 0.8); null when no breakdown shows it yet. */
        keepRate: Double?,
        lastDay: LocalDate,
    ): Result {
        val open = tripFaresByDay.filterKeys { it !in exactByDay }.filterValues { it > 0 }
        val exactSum = exactByDay.values.sum()
        val rate = keepRate ?: 1.0
        val (target, basis) = when {
            monthlyEarningsMinor != null -> (monthlyEarningsMinor - exactSum) to EstimateBasis.MONTHLY_TOTAL
            summaryFaresMinor != null -> {
                val net = ((summaryFaresMinor + (summaryCancellationMinor ?: 0)) * rate).roundToLong() + (summaryTipsMinor ?: 0)
                (net - exactSum) to EstimateBasis.MONTHLY_SUMMARY
            }
            open.isNotEmpty() -> (open.values.sum() * rate).roundToLong() to EstimateBasis.TRIP_FARES
            else -> return Result(emptyList(), null)
        }
        if (target <= 0) return Result(emptyList(), null)
        if (open.isEmpty()) return Result(listOf(EstimatedDay(lastDay, target)), basis)

        // Spread by fares; the rounding remainder goes to the busiest day so the total is exact.
        val fares = open.values.sum()
        val shares = open.entries.sortedBy { it.key }.map { (day, fare) -> day to target * fare / fares }
        val remainder = target - shares.sumOf { it.second }
        val busiest = open.maxBy { it.value }.key
        return Result(shares.map { (day, amount) -> EstimatedDay(day, if (day == busiest) amount + remainder else amount) }, basis)
    }

    /**
     * Share of fares kept after commission, from imported breakdowns: (fares + commission) / fares.
     * Null when no breakdown has both.
     */
    fun keepRate(faresMinor: Long, commissionMinor: Long): Double? =
        if (faresMinor <= 0 || commissionMinor == 0L) null else (faresMinor + commissionMinor).toDouble() / faresMinor
}
