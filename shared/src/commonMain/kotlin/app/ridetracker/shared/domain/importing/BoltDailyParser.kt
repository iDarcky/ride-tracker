package app.ridetracker.shared.domain.importing

import app.ridetracker.shared.domain.IncomeLineKind
import kotlin.math.abs
import kotlinx.datetime.LocalDate

/** One line of an imported breakdown. Deductions are negative. */
data class ParsedLine(val kind: IncomeLineKind, val amountMinor: Long, val inCash: Boolean, val label: String)

/** A day of income for one app, read from a report. */
data class ParsedDay(
    val date: LocalDate,
    /** What the app says the driver earned (after commission). */
    val earningsMinor: Long,
    val cashCollectedMinor: Long?,
    val lines: List<ParsedLine>,
    /** True when every group adds up to its total and the totals add up to [earningsMinor]. */
    val addsUp: Boolean,
)

sealed interface DailyParseResult {
    data class Day(val day: ParsedDay) : DailyParseResult

    /** The screenshot shows the Weekly or Monthly tab. */
    data object NotADay : DailyParseResult

    /** Not a screen we know, or too much of it could not be read. */
    data object NotRecognised : DailyParseResult
}

/**
 * Reads Bolt Driver's "Earnings breakdown" screen (Daily tab) from OCR text rows, top to bottom,
 * one row per line on screen (label and amount joined).
 */
object BoltDailyParser {

    private enum class Section { NONE, IN_APP, CASH, FEES }

    fun parse(rows: List<String>, reference: LocalDate): DailyParseResult {
        val lower = rows.map { it.lowercase() }
        if (lower.none { "commission" in it || "comision" in it }) return DailyParseResult.NotRecognised
        if (lower.none { "earnings breakdown" in it || "your earnings" in it || "câștigurile" in it || "castigurile" in it }) {
            return DailyParseResult.NotRecognised
        }

        val dateRow = rows.indexOfFirst { ReportText.dayAndMonth(it) != null }
        if (dateRow < 0) {
            val range = rows.any { Regex("""\d{1,2}\s+\p{L}{3,}\.?\s*[-–]\s*\d{1,2}\s+\p{L}{3,}""").containsMatchIn(it) }
            return if (range) DailyParseResult.NotADay else DailyParseResult.NotRecognised
        }
        val (dayOfMonth, month) = ReportText.dayAndMonth(rows[dateRow])!!
        val date = ReportText.inferYear(dayOfMonth, month, reference) ?: return DailyParseResult.NotRecognised

        var section = Section.NONE
        var inAppTotal: Long? = null
        var cashTotal: Long? = null
        var feesTotal: Long? = null
        var commission: Long? = null
        var earnings: Long? = null
        var cashInHand: Long? = null
        val lines = mutableListOf<ParsedLine>()

        for (row in rows.drop(dateRow + 1)) {
            val parsed = ReportText.amountRow(row) ?: continue
            val label = parsed.label.lowercase()
            when {
                label.isIn("in-app income", "in app income", "venituri în aplicație", "venituri in aplicatie") -> {
                    section = Section.IN_APP; inAppTotal = parsed.amountMinor
                }
                label.isIn("cash income", "venituri în numerar", "venituri in numerar") -> {
                    section = Section.CASH; cashTotal = parsed.amountMinor
                }
                label.isIn("cost and fees", "costs and fees", "costuri și taxe", "costuri si taxe") -> {
                    section = Section.FEES; feesTotal = -abs(parsed.amountMinor)
                }
                "commission" in label || "comision" in label -> {
                    section = Section.NONE; commission = -abs(parsed.amountMinor)
                }
                label.isIn("your earnings", "câștigurile tale", "castigurile tale") -> {
                    section = Section.NONE; earnings = parsed.amountMinor
                }
                label.isIn("cash in hand", "numerar în mână", "numerar in mana") -> cashInHand = abs(parsed.amountMinor)
                section == Section.IN_APP || section == Section.CASH ->
                    lines += ParsedLine(kindOf(label), parsed.amountMinor, section == Section.CASH, parsed.label)
                section == Section.FEES ->
                    lines += ParsedLine(IncomeLineKind.OTHER_FEE, -abs(parsed.amountMinor), false, parsed.label)
            }
        }
        if (earnings == null) return DailyParseResult.NotRecognised

        // A group whose rows were not read still counts through its total, as one line.
        fun fillMissing(total: Long?, inCash: Boolean, fees: Boolean, label: String) {
            if (total == null || total == 0L) return
            val present = lines.any { it.inCash == inCash && (it.kind == IncomeLineKind.OTHER_FEE) == fees }
            if (!present) lines += ParsedLine(if (fees) IncomeLineKind.OTHER_FEE else IncomeLineKind.OTHER, total, inCash, label)
        }
        fillMissing(inAppTotal, inCash = false, fees = false, label = "In-app income")
        fillMissing(cashTotal, inCash = true, fees = false, label = "Cash income")
        fillMissing(feesTotal, inCash = false, fees = true, label = "Cost and fees")
        commission?.takeIf { it != 0L }?.let { lines += ParsedLine(IncomeLineKind.COMMISSION, it, false, "Bolt commission") }

        fun sum(inCash: Boolean) = lines.filter { it.inCash == inCash && it.kind != IncomeLineKind.OTHER_FEE && it.kind != IncomeLineKind.COMMISSION }
            .sumOf { it.amountMinor }
        val groupsMatch = (inAppTotal == null || sum(false) == inAppTotal) &&
            (cashTotal == null || sum(true) == cashTotal) &&
            (feesTotal == null || lines.filter { it.kind == IncomeLineKind.OTHER_FEE }.sumOf { it.amountMinor } == feesTotal)
        val addsUp = groupsMatch && inAppTotal != null && commission != null &&
            lines.sumOf { it.amountMinor } == earnings

        return DailyParseResult.Day(ParsedDay(date, earnings, cashInHand, lines, addsUp))
    }

    private fun String.isIn(vararg names: String) = names.any { this == it }

    private fun kindOf(label: String): IncomeLineKind = when {
        "ride payment" in label || "plăți curse" in label || "plati curse" in label -> IncomeLineKind.FARE
        "tip" in label || "bacșiș" in label || "bacsis" in label -> IncomeLineKind.TIP
        "campaign" in label || "campanii" in label || "bonus" in label -> IncomeLineKind.BONUS
        "promotion" in label || "rider credit" in label || "promoți" in label -> IncomeLineKind.PROMOTION
        "toll" in label || "taxă de drum" in label || "taxa de drum" in label -> IncomeLineKind.TOLL
        "airport" in label || "aeroport" in label -> IncomeLineKind.AIRPORT_FEE
        "cancel" in label || "anulare" in label -> IncomeLineKind.CANCELLATION_FEE
        else -> IncomeLineKind.OTHER
    }
}
