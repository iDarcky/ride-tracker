package app.ridetracker.shared.domain.importing

import app.ridetracker.shared.domain.DateRange
import kotlin.math.abs
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/** A recognised word or line with its position on the screenshot (pixels). */
data class TextBox(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

/** Online time Bolt reports for a period (a month, a week or a day). */
data class OnlineTime(val range: DateRange, val minutes: Int)

/**
 * Reads Bolt Driver's "Activity" screen ("Activitate"), Online hours tab, from positioned OCR text.
 * The period tab that is selected sits in the middle of the tab row; the selected bar's bubble sits under its
 * label. Gives the selected month (Last 3 months), or the week's total and the selected day (week tabs).
 */
object BoltActivityParser {

    private val duration = Regex("""(\d+)\s*(?:ore|oră|ora|h|hours?|hrs?)\s*(\d+)\s*min""")
    private val minutesOnly = Regex("""^(\d+)\s*min$""")
    private val weekRange = Regex("""(\d{1,2})\.(\d{1,2})\s*[-–]\s*(\d{1,2})\.(\d{1,2})""")
    private val weekdays = mapOf(
        "lun" to DayOfWeek.MONDAY, "mar" to DayOfWeek.TUESDAY, "mie" to DayOfWeek.WEDNESDAY, "joi" to DayOfWeek.THURSDAY,
        "vin" to DayOfWeek.FRIDAY, "sam" to DayOfWeek.SATURDAY, "dum" to DayOfWeek.SUNDAY,
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
    )

    /** [words] are single words or short lines; [width] is the screenshot's width. */
    fun parse(words: List<TextBox>, width: Int, reference: LocalDate): List<OnlineTime>? {
        val plain = words.map { ReportText.plain(it.text) }
        val all = plain.joinToString(" ")
        if (!("activitate" in all || "activity" in all)) return null
        if (!("ore online" in all || "online hours" in all || "hours online" in all || "ore conduse" in all)) return null

        // Durations, joined from neighbouring words on the same line ("42ore" "55min").
        val durations = lines(words).mapNotNull { line -> minutesOf(ReportText.plain(line.text))?.let { line to it } }
        if (durations.isEmpty()) return null
        val total = durations.minBy { it.first.top }
        val selected = durations.filter { it !== total }.minByOrNull { it.first.top }

        // Labels under the bars: months or weekdays.
        val monthLabels = words.mapNotNull { w -> monthOf(ReportText.plain(w.text))?.let { w to it } }
        val dayLabels = words.mapNotNull { w -> weekdays[ReportText.plain(w.text).trimEnd('.').take(3)]?.let { w to it } }
        val tabs = words.filter { ReportText.plain(it.text).let { t -> "saptamana" in t || "ultimele" in t || "week" in t || "last" in t || weekRange.containsMatchIn(t) } }
        val selectedTab = tabs.minByOrNull { abs(it.centerX - width / 2) }?.let { ReportText.plain(it.text) }

        return when {
            monthLabels.size >= 2 -> {
                val (sel, minutes) = selected ?: return null
                val (_, month) = monthLabels.minBy { abs(it.first.centerX - sel.centerX) }
                val year = if (month.number > reference.month.number) reference.year - 1 else reference.year
                val first = LocalDate(year, month.number, 1)
                listOf(OnlineTime(DateRange(first, first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))), minutes))
            }
            dayLabels.size >= 5 -> {
                val week = weekOf(selectedTab, reference) ?: return null
                buildList {
                    add(OnlineTime(week, total.second))
                    selected?.let { (sel, minutes) ->
                        val (_, day) = dayLabels.minBy { abs(it.first.centerX - sel.centerX) }
                        val date = week.start.plus(DatePeriod(days = day.isoDayNumber - 1))
                        add(OnlineTime(DateRange(date, date), minutes))
                    }
                }
            }
            else -> null
        }
    }

    /** Letters OCR reads instead of digits in the small bubbles ("Gore 20min" is "6ore 20min"). */
    private val lookalikes = mapOf('g' to '6', 'b' to '6', 'o' to '0', 'l' to '1', 'i' to '1', '|' to '1', 's' to '5', 'z' to '2')
    private val digitsBeforeUnit = Regex("""(?<![\p{L}\d])([\dgbolisz|]{1,3})(?=\s*(?:ore|ora|h\b|min))""")

    private fun fixDigits(text: String): String =
        digitsBeforeUnit.replace(text) { m -> m.value.map { lookalikes[it] ?: it }.joinToString("") }

    /** "12ore 30min" -> 750; "45min" -> 45. Text is already plain (lower case). */
    private fun minutesOf(raw: String): Int? {
        val text = fixDigits(raw)
        duration.find(text)?.let { return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        minutesOnly.find(text.trim())?.let { return it.groupValues[1].toInt() }
        return null
    }

    private fun monthOf(text: String): Month? {
        val t = text.trimEnd('.')
        if (t.length < 3 || t.length > 5 || !t.all { it.isLetter() }) return null
        return ReportText.dayAndMonth("1 $t")?.second
    }

    /** "Săptămâna în curs" = the week of [reference]; "28.09-04.10" = that week. Weeks start on Monday. */
    private fun weekOf(tab: String?, reference: LocalDate): DateRange? {
        val m = tab?.let { weekRange.find(it) }
        val start = if (m != null) {
            val month = m.groupValues[2].toInt()
            val year = if (month > reference.month.number) reference.year - 1 else reference.year
            runCatching { LocalDate(year, month, m.groupValues[1].toInt()) }.getOrNull() ?: return null
        } else {
            reference.minus(DatePeriod(days = reference.dayOfWeek.isoDayNumber - 1))
        }
        return DateRange(start, start.plus(DatePeriod(days = 6)))
    }

    /** Words on the same line, left to right, joined (bubbles are recognised as "42ore" + "55min"). */
    private fun lines(words: List<TextBox>): List<TextBox> {
        val rows = mutableListOf<MutableList<TextBox>>()
        for (w in words.sortedBy { it.centerY }) {
            val row = rows.lastOrNull()
            if (row != null && abs(row.first().centerY - w.centerY) < (w.bottom - w.top) * 0.6 && w.left - row.maxOf { it.right } < (w.bottom - w.top) * 2) {
                row += w
            } else {
                rows += mutableListOf(w)
            }
        }
        return rows.map { row ->
            val sorted = row.sortedBy { it.left }
            TextBox(sorted.joinToString(" ") { it.text }, sorted.minOf { it.left }, sorted.minOf { it.top }, sorted.maxOf { it.right }, sorted.maxOf { it.bottom })
        }
    }
}
