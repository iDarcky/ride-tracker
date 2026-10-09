package app.ridetracker.shared.domain.importing

import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.IncomeLineKind
import kotlin.math.abs
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Uber Driver's Earnings screen for a week ("Oct 5 - Oct 12"): with a day's bar tapped it shows that day, otherwise
 * the week. The text is the same either way; only the tapped day's label turns blue, so the caller says which day
 * is selected ([UberEarningsParser.parse]'s selectedDayOfMonth), from the screenshot's colours.
 */
data class UberEarnings(
    /** Uber's week, Monday to Sunday. */
    val week: DateRange,
    /** The tapped day, or null for the whole week. */
    val day: LocalDate?,
    /** Total earnings (after Uber's fee) and what they're made of. */
    val earningsMinor: Long,
    val lines: List<ParsedLine>,
    val onlineMinutes: Int?,
    val trips: Int?,
) {
    val addsUp: Boolean get() = lines.isNotEmpty() && lines.sumOf { it.amountMinor } == earningsMinor

    fun asDay(): ParsedDay? = day?.let { ParsedDay(it, earningsMinor, null, lines, addsUp) }

    /** The week's totals; Uber's fee isn't on this screen. */
    fun asSummary(): ParsedSummary {
        fun sum(vararg kinds: IncomeLineKind) = lines.filter { it.kind in kinds }.takeIf { it.isNotEmpty() }?.sumOf { it.amountMinor }
        return ParsedSummary(
            periodStart = week.start,
            periodEnd = week.endInclusive,
            // Uber's net fare: the fee isn't on this screen, so it counts as fares with no fee.
            grossFareMinor = sum(IncomeLineKind.FARE),
            tipsMinor = sum(IncomeLineKind.TIP),
            bonusMinor = sum(IncomeLineKind.BONUS, IncomeLineKind.PROMOTION),
            earningsMinor = earningsMinor,
        )
    }
}

/**
 * Uber Driver's Payments screen for a week ("Uber | Payments", "Sep 28 - Oct 5"): customer fares, Uber's service fee,
 * promotions and quests, third-party fees (airport) and tips. It is taller than the screen: one long screenshot, or
 * two parts that [merge] joins (only the first part shows the week).
 */
data class UberPaymentsWeek(
    val week: DateRange?,
    val customerFareMinor: Long? = null,
    val serviceFeeMinor: Long? = null,
    /** Quest, Boost and other money Uber added, beyond paying back customer promotions. */
    val bonusMinor: Long? = null,
    /** Airport fees and other third-party fees (negative). */
    val thirdPartyMinor: Long? = null,
    val tipsMinor: Long? = null,
    val earningsMinor: Long? = null,
) {
    /** Has the week and its total: can be saved. */
    val complete: Boolean get() = week != null && earningsMinor != null

    val addsUp: Boolean
        get() = earningsMinor != null && customerFareMinor != null && serviceFeeMinor != null &&
            customerFareMinor + serviceFeeMinor + (bonusMinor ?: 0) + (thirdPartyMinor ?: 0) + (tipsMinor ?: 0) == earningsMinor

    /** Joins the other part of the same screen: each figure from whichever part shows it. */
    fun merge(other: UberPaymentsWeek) = UberPaymentsWeek(
        week = week ?: other.week,
        customerFareMinor = customerFareMinor ?: other.customerFareMinor,
        serviceFeeMinor = serviceFeeMinor ?: other.serviceFeeMinor,
        bonusMinor = bonusMinor ?: other.bonusMinor,
        thirdPartyMinor = thirdPartyMinor ?: other.thirdPartyMinor,
        tipsMinor = tipsMinor ?: other.tipsMinor,
        earningsMinor = earningsMinor ?: other.earningsMinor,
    )

    /** The two parts show the same screen: a figure both show is the same. */
    fun overlaps(other: UberPaymentsWeek): Boolean =
        listOf(
            serviceFeeMinor to other.serviceFeeMinor, customerFareMinor to other.customerFareMinor,
            thirdPartyMinor to other.thirdPartyMinor, bonusMinor to other.bonusMinor,
        ).any { (a, b) -> a != null && a == b }

    fun asSummary(): ParsedSummary? {
        val w = week ?: return null
        return ParsedSummary(
            periodStart = w.start,
            periodEnd = w.endInclusive,
            grossFareMinor = customerFareMinor,
            tipsMinor = tipsMinor,
            bonusMinor = bonusMinor,
            platformFeeMinor = serviceFeeMinor,
            earningsMinor = earningsMinor,
        )
    }
}

/** Uber's week label, "Sep 28 - Oct 5": Monday to the next Monday, so the week ends the day before the second date. */
internal object UberWeekLabel {
    fun parse(row: String, reference: LocalDate): DateRange? {
        val parts = row.split('-', '–', '—').map { it.trim() }
        if (parts.size != 2) return null
        val (startDay, startMonth) = ReportText.dayAndMonth(parts[0]) ?: return null
        ReportText.dayAndMonth(parts[1]) ?: return null
        // The second date may be after the screenshot (the week is still running), so the year comes from the first.
        val start = ReportText.inferYear(startDay, startMonth, reference) ?: return null
        return DateRange(start, start.plus(DatePeriod(days = 6)))
    }
}

object UberEarningsParser {

    private val duration = Regex("""^(?:(\d+)\s*h\s*)?(\d+)\s*m(?:in)?\b""")

    /** Earnings screen, English (Romanian labels to be checked against real screenshots). */
    fun recognises(rows: List<String>): Boolean {
        val plain = rows.map(ReportText::plain)
        val hasTotal = plain.any { it.startsWith("total earnings") || it.startsWith("castiguri totale") || it.startsWith("total castiguri") }
        val hasStats = plain.any { "online" in it } && plain.any { it == "stats" || it == "statistici" || "trips" in it || "curse" in it }
        return hasTotal && hasStats
    }

    /** [selectedDayOfMonth]: the day whose label is highlighted, or null when none is (the week). */
    fun parse(rows: List<String>, reference: LocalDate, selectedDayOfMonth: Int?): UberEarnings? {
        if (!recognises(rows)) return null
        val week = rows.firstNotNullOfOrNull { UberWeekLabel.parse(it, reference) } ?: return null
        val day = selectedDayOfMonth?.let { d ->
            (0..6).map { week.start.plus(DatePeriod(days = it)) }.firstOrNull { it.day == d } ?: return null
        }
        val plain = rows.map(ReportText::plain)

        // "Online Trips" over "2 h 20 m 8": time first, then the trip count, which text recognition sometimes puts
        // on a row of its own. The stats end at "Points".
        var online: Int? = null
        var trips: Int? = null
        val statsRow = plain.indexOfFirst { "online" in it }
        if (statsRow >= 0) {
            val values = plain.drop(statsRow + 1).takeWhile { "points" !in it && "puncte" !in it && "how we" !in it }.take(3)
            for (v in values) {
                val m = duration.find(v)
                if (m != null && online == null) {
                    online = (m.groupValues[1].toIntOrNull() ?: 0) * 60 + m.groupValues[2].toInt()
                    trips = trips ?: v.substring(m.range.last + 1).trim().split(' ').firstOrNull()?.toIntOrNull()
                } else if (trips == null) {
                    trips = v.trim().toIntOrNull()
                }
            }
        }

        // Breakdown: a label on one row, its amount on the next ("Net Fare" / "RON 120.00"), up to "Total Earnings".
        val start = plain.indexOfFirst { it == "breakdown" || it == "defalcare" }
        val end = plain.indexOfFirst { it.startsWith("total earnings") || it.startsWith("castiguri totale") || it.startsWith("total castiguri") }
        if (end < 0) return null
        val total = ReportText.amountRow(rows[end])?.amountMinor ?: return null
        val lines = mutableListOf<ParsedLine>()
        if (start in 0 until end) {
            var label: String? = null
            for (row in rows.subList(start + 1, end)) {
                val amount = ReportText.amountRow(row)
                when {
                    amount != null && amount.label.isEmpty() && label != null -> {
                        lines += ParsedLine(kindOf(label), amount.amountMinor, false, label)
                        label = null
                    }
                    amount != null && amount.label.isNotEmpty() -> lines += ParsedLine(kindOf(amount.label), amount.amountMinor, false, amount.label)
                    amount == null -> label = row.trim()
                }
            }
        }
        return UberEarnings(week, day, total, lines, online, trips)
    }

    private fun kindOf(label: String): IncomeLineKind {
        val l = ReportText.plain(label)
        return when {
            "fare" in l || "tarif" in l -> IncomeLineKind.FARE
            "tip" in l || "bacsis" in l -> IncomeLineKind.TIP
            "quest" in l || "boost" in l || "promotion" in l || "promotie" in l || "incentive" in l || "surge" in l -> IncomeLineKind.BONUS
            "cancel" in l || "anulare" in l -> IncomeLineKind.CANCELLATION_FEE
            "toll" in l -> IncomeLineKind.TOLL
            "airport" in l || "aeroport" in l -> IncomeLineKind.AIRPORT_FEE
            else -> IncomeLineKind.OTHER
        }
    }
}

object UberPaymentsParser {

    /** The Payments screen, either part of it. */
    fun recognises(rows: List<String>): Boolean {
        val plain = rows.map(ReportText::plain)
        val labels = listOf("customer payments", "amount uber spent", "earnings from fares", "your total earnings", "total customer fare")
        return plain.any { "uber | payments" in it || "uber i payments" in it } || labels.count { l -> plain.any { it.startsWith(l) } } >= 2
    }

    fun parse(rows: List<String>, reference: LocalDate): UberPaymentsWeek? {
        if (!recognises(rows)) return null
        val week = rows.firstNotNullOfOrNull { UberWeekLabel.parse(it, reference) }
        var fare: Long? = null
        var fee: Long? = null
        var bonus: Long? = null
        var thirdParty: Long? = null
        var thirdPartyItems: Long? = null
        var tips: Long? = null
        var earnings: Long? = null
        // Under "Amount Uber spent": the fee, customer promotions paid back (they cancel the ones off the customer
        // fare) and Uber's own money (Quest, Boost…), which counts as bonus.
        var inUberSpent = false
        for (row in rows) {
            val amount = ReportText.amountRow(row) ?: continue
            val label = ReportText.plain(amount.label)
            when {
                label.startsWith("total customer fare") -> fare = abs(amount.amountMinor)
                label.startsWith("customer payments") || label.startsWith("earnings from fares") -> inUberSpent = false
                label.startsWith("third-party fees") || label.startsWith("third party fees") -> {
                    thirdParty = -abs(amount.amountMinor); inUberSpent = false
                }
                label.startsWith("airport") || "toll" in label -> thirdPartyItems = (thirdPartyItems ?: 0) - abs(amount.amountMinor)
                label.startsWith("amount uber spent") -> inUberSpent = true
                label.startsWith("service fee") -> fee = -abs(amount.amountMinor)
                label.startsWith("customer promotions") -> Unit
                label == "tips" || label == "tip" -> { tips = abs(amount.amountMinor); inUberSpent = false }
                label.startsWith("your total earnings") -> earnings = abs(amount.amountMinor)
                inUberSpent && label.isNotEmpty() -> bonus = (bonus ?: 0) + amount.amountMinor
            }
        }
        val result = UberPaymentsWeek(week, fare, fee, bonus, thirdParty ?: thirdPartyItems, tips, earnings)
        return result.takeIf { listOf(fare, fee, bonus, thirdParty, thirdPartyItems, tips, earnings).any { it != null } }
    }
}
