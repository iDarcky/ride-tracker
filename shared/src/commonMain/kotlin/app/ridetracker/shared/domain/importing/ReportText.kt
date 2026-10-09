package app.ridetracker.shared.domain.importing

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.number

/** Helpers for reading amounts and dates out of report text (OCR rows, PDF text, CSV cells). */
object ReportText {

    private val number = Regex("""\d(?:[\d.,   ]*\d)?""")
    private val minusSigns = setOf('-', '−', '–', '—')
    private val currencyWords = Regex("""(?i)\s*(lei|ron)\s*$""")

    /** A label and the signed amount at the end of a row, e.g. "Bolt commission -lei 20.15". */
    data class AmountRow(val label: String, val amountMinor: Long)

    /**
     * Splits a row into its label and the amount at its end, in minor units (2 decimals).
     * Returns null when the row does not end with an amount.
     */
    fun amountRow(row: String): AmountRow? {
        // Rows that open a detail page end with an arrow ("Cash in hand +lei 42.00 >"), which OCR may read.
        val text = row.trim().trimEnd('>', '›', '»', '〉', ')', ' ')
        val match = number.findAll(text).lastOrNull() ?: return null
        val after = text.substring(match.range.last + 1).trim()
        if (after.isNotEmpty() && !after.equals("lei", ignoreCase = true) && !after.equals("ron", ignoreCase = true)) return null
        val minor = toMinor(match.value) ?: return null
        var prefix = text.substring(0, match.range.first).trimEnd()
        var negative = false
        repeat(2) { // sign and currency can come in either order: "-lei 2", "lei -2", "+RON 3"
            prefix = prefix.replace(currencyWords, "").trimEnd()
            val last = prefix.lastOrNull()
            if (last != null && (last in minusSigns || last == '+')) {
                negative = negative || last in minusSigns
                prefix = prefix.dropLast(1).trimEnd()
            }
        }
        val label = prefix.trimEnd('.', '…', '·', ' ', ':').trim()
        return AmountRow(label, if (negative) -minor else minor)
    }

    /**
     * Two-column layouts come out of text recognition as one row: "Gross Fare lei 3,141.60 Tip lei 26.00".
     * Splits after each amount that is followed by a new capitalised label.
     */
    fun splitColumns(row: String): List<String> =
        row.split(Regex("""(?<=\d)\s+(?=\p{Lu})""")).map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * "3,141.60", "3.141,60", "14.90", "14,90", "1 234,5" -> minor units. The last separator is the decimal
     * point when one or two digits follow it; otherwise all separators group thousands.
     */
    fun toMinor(text: String): Long? {
        val clean = text.filter { it.isDigit() || it == '.' || it == ',' }
        if (clean.isEmpty()) return null
        val lastSep = clean.indexOfLast { it == '.' || it == ',' }
        val decimals = if (lastSep >= 0) clean.length - lastSep - 1 else 0
        val (whole, fraction) = if (lastSep >= 0 && decimals in 1..2) {
            clean.substring(0, lastSep).filter(Char::isDigit) to clean.substring(lastSep + 1).padEnd(2, '0')
        } else {
            clean.filter(Char::isDigit) to "00"
        }
        if (whole.length > 15) return null
        return (whole.ifEmpty { "0" }.toLong()) * 100 + fraction.toLong()
    }

    private val monthNames: Map<String, Month> = buildMap {
        val en = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        val ro = listOf("ian", "feb", "mar", "apr", "mai", "iun", "iul", "aug", "sep", "oct", "noi", "dec")
        Month.entries.forEachIndexed { i, month ->
            put(en[i], month)
            put(ro[i], month)
        }
    }

    private val accents: Map<Char, Char> = buildMap {
        // OCR sometimes reads "ă" as "ắ" or "ș" as "š": strip any accent it might produce.
        "àáâãäåăắằẳẵặấầẩẫậạả".forEach { put(it, 'a') }
        "èéêëěẹẻẽếềểễệ".forEach { put(it, 'e') }
        "ìíîïĩịỉ".forEach { put(it, 'i') }
        "òóôõöọỏốồổỗộ".forEach { put(it, 'o') }
        "ùúûüũụủ".forEach { put(it, 'u') }
        "șşšś".forEach { put(it, 's') }
        "țţť".forEach { put(it, 't') }
    }

    /** Lower case without diacritics, so labels match however OCR reads "ș" or "ă". */
    fun plain(text: String): String = text.trim().lowercase().map { accents[it] ?: it }.joinToString("")

    /** "oct", "octombrie", "0ct" (OCR reads the O of "Oct" as a zero) -> the month. */
    private fun monthOf(name: String): Month? = monthNames[name.replace('0', 'o').take(3)]

    /** "sept. 2026", "September 2026", "oct 2026" -> first day of that month. */
    fun monthAndYear(text: String): LocalDate? {
        val m = Regex("""^([a-z0][a-z]{2,})\.?\s+(\d{4})$""").find(plain(text)) ?: return null
        val month = monthOf(m.groupValues[1]) ?: return null
        return LocalDate(m.groupValues[2].toInt(), month.number, 1)
    }

    /** "8 Oct", "8 oct.", "08 octombrie", "Oct 8", "1oct." (OCR can drop the space) -> day and month. */
    fun dayAndMonth(text: String): Pair<Int, Month>? {
        val t = plain(text)
        val dayFirst = Regex("""^(\d{1,2})\s*([a-z0][a-z]{2,})\.?$""").find(t)
            ?: Regex("""^([li])\s+([a-z0][a-z]{2,})\.?$""").find(t)
        val monthFirst = Regex("""^([a-z0][a-z]{2,})\.?\s+(\d{1,2}|[li])$""").find(t)
        val (day, name) = when {
            dayFirst != null -> dayFirst.groupValues[1] to dayFirst.groupValues[2]
            monthFirst != null -> monthFirst.groupValues[2] to monthFirst.groupValues[1]
            else -> return null
        }
        val month = monthOf(name) ?: return null
        val d = day.toIntOrNull() ?: 1 // OCR reads a lone "1" as "l" or "i"
        return if (d in 1..31) d to month else null
    }

    /** The latest date with this day and month that is not after [reference] (reports never show the future). */
    fun inferYear(day: Int, month: Month, reference: LocalDate): LocalDate? {
        for (year in reference.year downTo reference.year - 1) {
            val date = runCatching { LocalDate(year, month.number, day) }.getOrNull() ?: continue
            if (date <= reference) return date
        }
        return null
    }

    /** "30.09.2026" -> date. */
    fun dottedDate(text: String): LocalDate? {
        val m = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""").find(text) ?: return null
        return runCatching { LocalDate(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
    }
}
