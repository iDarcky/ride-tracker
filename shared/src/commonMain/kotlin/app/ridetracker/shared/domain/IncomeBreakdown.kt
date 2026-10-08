package app.ridetracker.shared.domain

import app.ridetracker.shared.data.IncomeEntryEntity
import app.ridetracker.shared.data.LineInRange
import app.ridetracker.shared.data.PeriodSummaryEntity
import app.ridetracker.shared.data.TripWithPlatform

/** One kind of income (positive) or deduction (negative) in a period. */
data class IncomePart(val kind: IncomeLineKind, val amountMinor: Long)

/**
 * How income was paid. Money figures come from imported breakdowns (Bolt's "In-app" and "Cash" groups, before
 * commission), so they cover only days that have one; trip figures come from imported trips (rider invoices).
 */
data class PaymentSplit(
    val inAppMinor: Long,
    val cashMinor: Long,
    /** Cash the driver kept ("Cash in hand"); null when no breakdown reports it. */
    val cashInHandMinor: Long?,
    /** What the days with a breakdown earned (after commission). */
    val coveredEarnedMinor: Long,
    /** True when some of the period's income has no breakdown, so the money split is for part of it. */
    val partial: Boolean,
    val inAppTrips: Int,
    val cashTrips: Int,
    val inAppFaresMinor: Long,
    val cashFaresMinor: Long,
) {
    /** Paid into the driver's account for the days with a breakdown: earned minus the cash already in hand. */
    val toBankMinor: Long? get() = cashInHandMinor?.let { coveredEarnedMinor - it }
    val hasMoney: Boolean get() = inAppMinor != 0L || cashMinor != 0L
    val hasTrips: Boolean get() = inAppTrips + cashTrips > 0
}

/** What a period's income is made of, for all platforms or one. */
data class IncomeBreakdown(
    /** What the platforms paid (after commission): the entries' amounts. */
    val earnedMinor: Long,
    val estimatedMinor: Long,
    /** Income kinds first (largest first), then deductions; zero parts left out. */
    val parts: List<IncomePart>,
    /** Income with no breakdown at all (typed in, or estimated without the platform's monthly total). */
    val withoutBreakdownMinor: Long,
    /** Null when nothing says how anything was paid. */
    val payment: PaymentSplit?,
) {
    val incomeParts: List<IncomePart> get() = parts.filter { it.amountMinor > 0 }
    val deductions: List<IncomePart> get() = parts.filter { it.amountMinor < 0 }
}

object IncomeBreakdownCalculator {

    private val deductionKinds = setOf(IncomeLineKind.COMMISSION, IncomeLineKind.OTHER_FEE)

    /**
     * [platformId] null = every platform. A platform's own total for exactly [range] (Bolt's Monthly tab) gives
     * that platform's parts, estimated days included; otherwise the entries' breakdown lines do.
     */
    fun compute(
        range: DateRange,
        entries: List<IncomeEntryEntity>,
        lines: List<LineInRange>,
        trips: List<TripWithPlatform>,
        summaries: List<PeriodSummaryEntity>,
        platformId: Long? = null,
    ): IncomeBreakdown {
        fun mine(id: Long) = platformId == null || id == platformId
        val myEntries = entries.filter { mine(it.platformId) }
        val myLines = lines.filter { mine(it.platformId) }
        val myTrips = trips.filter { mine(it.platformId) }
        val linesByEntry = myLines.groupBy { it.entryId }
        val totals = mutableMapOf<IncomeLineKind, Long>()
        fun add(kind: IncomeLineKind, amount: Long?) {
            if (amount != null && amount != 0L) totals[kind] = (totals[kind] ?: 0) + amount
        }

        val ownTotals = summaries
            .filter { mine(it.platformId) && it.earningsMinor != null }
            .filter { it.periodStart == range.start.toEpochDays() && it.periodEnd == range.endInclusive.toEpochDays() }
            .groupBy { it.platformId }.map { (_, list) -> list.maxBy { it.id } }
        for (s in ownTotals) {
            val fee = -kotlin.math.abs(s.platformFeeMinor ?: 0)
            add(IncomeLineKind.FARE, s.grossFareMinor)
            add(IncomeLineKind.TIP, s.tipsMinor)
            add(IncomeLineKind.BONUS, s.bonusMinor)
            add(IncomeLineKind.CANCELLATION_FEE, s.cancellationMinor)
            add(IncomeLineKind.COMMISSION, fee)
            // Whatever the total has beyond the named parts (promotions, tolls).
            val named = listOfNotNull(s.grossFareMinor, s.tipsMinor, s.bonusMinor, s.cancellationMinor).sum() + fee
            add(IncomeLineKind.OTHER, (s.earningsMinor ?: 0) - named)
        }
        val covered = ownTotals.map { it.platformId }.toSet()
        var withoutBreakdown = 0L
        for (entry in myEntries.filter { it.platformId !in covered }) {
            val own = linesByEntry[entry.id]
            if (own.isNullOrEmpty()) withoutBreakdown += entry.amountMinor
            else own.forEach { add(IncomeLineKind.fromId(it.kind), it.amountMinor) }
        }

        val parts = totals.filterValues { it != 0L }.map { (kind, amount) -> IncomePart(kind, amount) }
            .sortedWith(compareBy<IncomePart> { it.kind in deductionKinds }.thenByDescending { kotlin.math.abs(it.amountMinor) })

        // How it was paid: breakdown lines (exact days only) and imported trips.
        val withLines = myEntries.filter { !linesByEntry[it.id].isNullOrEmpty() }
        val incomeLines = myLines.filter { IncomeLineKind.fromId(it.kind) !in deductionKinds }
        val inCash = myTrips.filter { it.paymentMethod == PaymentMethod.CASH.id }
        val inApp = myTrips.filter { it.paymentMethod != PaymentMethod.CASH.id }
        val cashReports = withLines.mapNotNull { it.cashCollectedMinor }
        val payment = PaymentSplit(
            inAppMinor = incomeLines.filter { !it.inCash }.sumOf { it.amountMinor },
            cashMinor = incomeLines.filter { it.inCash }.sumOf { it.amountMinor },
            cashInHandMinor = cashReports.takeIf { it.isNotEmpty() }?.sum(),
            coveredEarnedMinor = withLines.sumOf { it.amountMinor },
            partial = withLines.size < myEntries.count { it.amountMinor != 0L },
            inAppTrips = inApp.size,
            cashTrips = inCash.size,
            inAppFaresMinor = inApp.sumOf { it.fareMinor },
            cashFaresMinor = inCash.sumOf { it.fareMinor },
        ).takeIf { it.hasMoney || it.hasTrips }

        return IncomeBreakdown(
            earnedMinor = myEntries.sumOf { it.amountMinor },
            estimatedMinor = myEntries.filter { it.source == IncomeSource.ESTIMATE.id }.sumOf { it.amountMinor },
            parts = parts,
            withoutBreakdownMinor = withoutBreakdown,
            payment = payment,
        )
    }
}
