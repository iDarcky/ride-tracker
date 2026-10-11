package app.ridetracker.shared.domain

/** What a comparison row measures, and so how it's shown (money, time, count). */
enum class ComparisonMetric { INCOME, KEPT, EXPENSES, HOURS, PER_HOUR, TRIPS, PLATFORM }

/**
 * One row of Home's comparison card: this period against the one it's compared with. Money in minor units, hours in
 * minutes. Null where a period has no such figure (no hours imported, say).
 */
data class ComparisonRow(
    val metric: ComparisonMetric,
    val current: Long?,
    val previous: Long?,
    /** For [ComparisonMetric.PLATFORM]: which app. */
    val platformId: Long? = null,
) {
    /** Relative change, or null when it can't be said (no previous figure, or zero). */
    val change: Double? get() = if (current == null || previous == null) null else Comparisons.percentChange(current, previous)

    /** Up is good for everything but expenses. */
    val higherIsBetter: Boolean get() = metric != ComparisonMetric.EXPENSES
}

object PeriodComparisons {

    /**
     * The rows for a period and the one it's compared with: income, money kept and expenses (once expenses are
     * tracked), hours online and income per hour (when either period has hours), trips (when either has some), then
     * each platform with income in either period.
     */
    fun rows(
        current: HomeStats,
        currentExpensesMinor: Long,
        previous: HomeStats,
        previousExpensesMinor: Long,
        tracksExpenses: Boolean,
        currentByPlatform: Map<Long, Long>,
        previousByPlatform: Map<Long, Long>,
    ): List<ComparisonRow> = buildList {
        add(ComparisonRow(ComparisonMetric.INCOME, current.netIncomeMinor, previous.netIncomeMinor))
        if (tracksExpenses) {
            add(ComparisonRow(ComparisonMetric.KEPT, current.netIncomeMinor - currentExpensesMinor, previous.netIncomeMinor - previousExpensesMinor))
            add(ComparisonRow(ComparisonMetric.EXPENSES, currentExpensesMinor, previousExpensesMinor))
        }
        if (current.onlineMinutes != null || previous.onlineMinutes != null) {
            add(ComparisonRow(ComparisonMetric.HOURS, current.onlineMinutes?.toLong(), previous.onlineMinutes?.toLong()))
            add(ComparisonRow(ComparisonMetric.PER_HOUR, current.perHourMinor, previous.perHourMinor))
        }
        if (current.tripCount > 0 || previous.tripCount > 0) {
            add(ComparisonRow(ComparisonMetric.TRIPS, current.tripCount.toLong(), previous.tripCount.toLong()))
        }
        // Platforms only say something when there's more than one.
        val platforms = (currentByPlatform.keys + previousByPlatform.keys).filter { (currentByPlatform[it] ?: 0) != 0L || (previousByPlatform[it] ?: 0) != 0L }
        if (platforms.size > 1) {
            platforms.sortedByDescending { currentByPlatform[it] ?: 0 }.forEach { id ->
                add(ComparisonRow(ComparisonMetric.PLATFORM, currentByPlatform[id] ?: 0, previousByPlatform[id] ?: 0, platformId = id))
            }
        }
    }

    /** True when the earlier period has anything at all to compare with. */
    fun hasPrevious(previous: HomeStats, previousExpensesMinor: Long): Boolean =
        previous.netIncomeMinor != 0L || previousExpensesMinor != 0L || previous.tripCount > 0 || (previous.onlineMinutes ?: 0) > 0
}
