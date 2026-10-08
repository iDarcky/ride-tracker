package app.ridetracker.shared.domain.importing

import app.ridetracker.shared.domain.IncomeLineKind
import kotlin.math.abs
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

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

    /** The Weekly or Monthly tab: Bolt's own totals for that period, kept for checking. */
    data class Period(val summary: ParsedSummary, val monthly: Boolean, val addsUp: Boolean) : DailyParseResult

    /** Not a screen we know, or too much of it could not be read. */
    data object NotRecognised : DailyParseResult
}

/**
 * Reads Bolt Driver's "Earnings breakdown" screen ("Defalcarea câștigurilor") from OCR text rows,
 * top to bottom, one row per line on screen (label and amount joined). English and Romanian.
 * The Daily tab gives a day of income; the Weekly and Monthly tabs give period totals.
 */
object BoltDailyParser {

    private enum class Section { NONE, IN_APP, CASH, FEES }

    private data class Breakdown(
        val lines: List<ParsedLine>,
        val earnings: Long,
        val cashInHand: Long?,
        val addsUp: Boolean,
    )

    fun parse(rows: List<String>, reference: LocalDate): DailyParseResult {
        val plain = rows.map(ReportText::plain)
        if (plain.none { "commission" in it || "comision" in it }) return DailyParseResult.NotRecognised
        if (plain.none { "earnings breakdown" in it || "your earnings" in it || "defalcarea" in it || "castigurile tale" in it }) {
            return DailyParseResult.NotRecognised
        }

        // The period line is the first row that reads as a day, a range of days or a month.
        for ((index, row) in rows.withIndex()) {
            ReportText.dayAndMonth(row)?.let { (day, month) ->
                val date = ReportText.inferYear(day, month, reference) ?: return DailyParseResult.NotRecognised
                val b = breakdown(rows.drop(index + 1)) ?: return DailyParseResult.NotRecognised
                return DailyParseResult.Day(ParsedDay(date, b.earnings, b.cashInHand, b.lines, b.addsUp))
            }
            dayRange(row, reference)?.let { (start, end) ->
                val b = breakdown(rows.drop(index + 1)) ?: return DailyParseResult.NotRecognised
                return DailyParseResult.Period(summary(start, end, b), monthly = false, addsUp = b.addsUp)
            }
            ReportText.monthAndYear(row)?.let { first ->
                val last = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
                val b = breakdown(rows.drop(index + 1)) ?: return DailyParseResult.NotRecognised
                return DailyParseResult.Period(summary(first, last, b), monthly = true, addsUp = b.addsUp)
            }
        }
        return DailyParseResult.NotRecognised
    }

    /** "28 sept. – 4 oct.", "29 Sep - 5 Oct": the range ends on or before [reference]. */
    private fun dayRange(row: String, reference: LocalDate): Pair<LocalDate, LocalDate>? {
        val parts = row.split('-', '–', '—').map { it.trim() }
        if (parts.size != 2) return null
        val (startDay, startMonth) = ReportText.dayAndMonth(parts[0]) ?: return null
        val (endDay, endMonth) = ReportText.dayAndMonth(parts[1]) ?: return null
        val end = ReportText.inferYear(endDay, endMonth, reference) ?: return null
        val start = ReportText.inferYear(startDay, startMonth, end) ?: return null
        return start to end
    }

    private fun summary(start: LocalDate, end: LocalDate, b: Breakdown): ParsedSummary {
        fun sum(kind: IncomeLineKind) = b.lines.filter { it.kind == kind }.takeIf { it.isNotEmpty() }?.sumOf { it.amountMinor }
        return ParsedSummary(
            periodStart = start,
            periodEnd = end,
            grossFareMinor = sum(IncomeLineKind.FARE),
            cancellationMinor = sum(IncomeLineKind.CANCELLATION_FEE),
            tipsMinor = sum(IncomeLineKind.TIP),
            bonusMinor = sum(IncomeLineKind.BONUS),
            platformFeeMinor = sum(IncomeLineKind.COMMISSION) ?: 0,
            earningsMinor = b.earnings,
        )
    }

    private fun breakdown(rows: List<String>): Breakdown? {
        var section = Section.NONE
        var inAppTotal: Long? = null
        var cashTotal: Long? = null
        var feesTotal: Long? = null
        var commission: Long? = null
        var earnings: Long? = null
        var cashInHand: Long? = null
        val lines = mutableListOf<ParsedLine>()

        for (row in rows) {
            val parsed = ReportText.amountRow(row) ?: continue
            val label = ReportText.plain(parsed.label)
            when {
                label.isIn("in-app income", "in app income", "venituri in aplicatie") -> {
                    section = Section.IN_APP; inAppTotal = parsed.amountMinor
                }
                label.isIn("cash income", "venituri in numerar") -> {
                    section = Section.CASH; cashTotal = parsed.amountMinor
                }
                label.isIn("cost and fees", "costs and fees", "costuri si taxe") -> {
                    section = Section.FEES; feesTotal = -abs(parsed.amountMinor)
                }
                "commission" in label || "comision" in label -> {
                    section = Section.NONE; commission = -abs(parsed.amountMinor)
                }
                label.isIn("your earnings", "castigurile tale") -> {
                    section = Section.NONE; earnings = parsed.amountMinor
                }
                label.isIn("cash in hand", "numerar in mana") -> cashInHand = abs(parsed.amountMinor)
                section == Section.IN_APP || section == Section.CASH ->
                    lines += ParsedLine(lineKind(parsed.label), parsed.amountMinor, section == Section.CASH, parsed.label)
                section == Section.FEES ->
                    lines += ParsedLine(IncomeLineKind.OTHER_FEE, -abs(parsed.amountMinor), false, parsed.label)
            }
        }
        if (earnings == null) return null

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
        val addsUp = groupsMatch && inAppTotal != null && commission != null && lines.sumOf { it.amountMinor } == earnings
        return Breakdown(lines, earnings, cashInHand, addsUp)
    }

    private fun String.isIn(vararg names: String) = names.any { this == it }

    /** What a breakdown row is, from its label as the report wrote it (English or Romanian, any accents). */
    fun lineKind(rawLabel: String): IncomeLineKind = kindOf(ReportText.plain(rawLabel))

    /** [label] is already lower case without diacritics. */
    private fun kindOf(label: String): IncomeLineKind = when {
        "ride payment" in label || "plati pentru curse" in label || "plati curse" in label -> IncomeLineKind.FARE
        "tip" in label || "bacsis" in label -> IncomeLineKind.TIP
        "campaign" in label || "campani" in label || "bonus" in label -> IncomeLineKind.BONUS
        "promotion" in label || "rider credit" in label || "promotii" in label || "credite" in label -> IncomeLineKind.PROMOTION
        "toll" in label || "drum cu tax" in label || "taxa de drum" in label -> IncomeLineKind.TOLL
        "airport" in label || "aeroport" in label -> IncomeLineKind.AIRPORT_FEE
        "cancel" in label || "anulare" in label -> IncomeLineKind.CANCELLATION_FEE
        else -> IncomeLineKind.OTHER
    }
}
