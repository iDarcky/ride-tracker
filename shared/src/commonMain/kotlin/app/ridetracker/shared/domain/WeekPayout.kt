package app.ridetracker.shared.domain

import app.ridetracker.shared.data.IncomeEntryEntity

/** One app's week so far: what it paid, the cash already in hand, and what's coming to the driver's account. */
data class PlatformPayout(
    val platformId: Long,
    val earnedMinor: Long,
    /** Cash collected from riders; null when no day of the week says. */
    val cashInHandMinor: Long?,
) {
    /** Negative when the cash in hand is more than earned: the driver owes the app the difference (its commission). */
    val toAccountMinor: Long get() = earnedMinor - (cashInHandMinor ?: 0)
}

/** This week's payout (owner's to-do: "the week first", Bolt and Uber pay weekly), app by app. */
data class WeekPayout(val apps: List<PlatformPayout>) {
    val toAccountMinor: Long get() = apps.sumOf { it.toAccountMinor }
}

object WeekPayouts {
    /** From the week's entries: days that are in (estimates and month differences left out). Null with nothing in. */
    fun compute(entries: List<IncomeEntryEntity>): WeekPayout? {
        val days = entries.filter { IncomeSource.countsAsDay(it.source) }
        if (days.isEmpty()) return null
        return WeekPayout(
            days.groupBy { it.platformId }.toSortedMap().map { (platformId, list) ->
                val cash = list.mapNotNull { it.cashCollectedMinor }
                PlatformPayout(platformId, list.sumOf { it.amountMinor }, cash.takeIf { it.isNotEmpty() }?.sum())
            },
        )
    }
}
