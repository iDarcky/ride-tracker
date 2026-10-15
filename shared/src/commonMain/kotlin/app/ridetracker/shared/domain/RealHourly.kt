package app.ridetracker.shared.domain

/**
 * Money per hour online, then what's left of it after fuel and after every expense (owner's to-do: "real money per
 * hour after fuel"). The apps' figure is already after their commission. Costs count only in the share of the
 * period's income that has hours, so "Bolt only" hours aren't charged Uber's fuel too. No estimates: fuel is the
 * fuel and charging expenses entered for the period.
 */
data class RealHourly(
    val onlineMinutes: Int,
    /** What the apps paid per hour online. */
    val perHourMinor: Long,
    /** After fuel and charging; null while the period has none entered. */
    val afterFuelPerHourMinor: Long?,
    /** After every expense; null without expenses other than fuel (it would equal [afterFuelPerHourMinor]). */
    val afterExpensesPerHourMinor: Long?,
    /** Hours cover only some of the period's income (some apps or days), so costs were shared out. */
    val partial: Boolean,
)

object RealHourlyCalculator {

    fun compute(stats: HomeStats, fuelMinor: Long, expensesMinor: Long): RealHourly? {
        val minutes = stats.onlineMinutes ?: return null
        val perHour = stats.perHourMinor ?: return null
        val income = stats.netIncomeMinor
        if (minutes <= 0 || income <= 0) return null
        val covered = stats.hourIncomeMinor.coerceAtMost(income)
        fun perHourAfter(costMinor: Long): Long {
            val share = costMinor * covered / income
            return ((covered - share) * 60 + minutes / 2) / minutes
        }
        return RealHourly(
            onlineMinutes = minutes,
            perHourMinor = perHour,
            afterFuelPerHourMinor = if (fuelMinor > 0) perHourAfter(fuelMinor) else null,
            afterExpensesPerHourMinor = if (expensesMinor > fuelMinor) perHourAfter(expensesMinor) else null,
            partial = covered < income,
        )
    }
}
