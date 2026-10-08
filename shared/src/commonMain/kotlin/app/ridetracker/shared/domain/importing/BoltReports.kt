package app.ridetracker.shared.domain.importing

import app.ridetracker.shared.domain.PaymentMethod
import kotlin.math.abs
import kotlinx.datetime.LocalDate

/** A platform's own totals for a period. Nulls are figures the report does not have. */
data class ParsedSummary(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val grossFareMinor: Long? = null,
    val cancellationMinor: Long? = null,
    val tipsMinor: Long? = null,
    val bonusMinor: Long? = null,
    val platformFeeMinor: Long? = null,
    val earningsMinor: Long? = null,
    val distanceMeters: Long? = null,
)

/** Reads Bolt's "Monthly summary" PDF from its text, one line per row. The driver's name is ignored. */
object BoltMonthlySummaryParser {

    fun parse(rows: List<String>): ParsedSummary? {
        if (rows.none { it.contains("monthly summary", ignoreCase = true) || it.contains("rezumat lunar", ignoreCase = true) }) return null
        val period = rows.firstNotNullOfOrNull { row ->
            Regex("""(\d{1,2}\.\d{1,2}\.\d{4})\s*[-–]\s*(\d{1,2}\.\d{1,2}\.\d{4})""").find(row)
        } ?: return null
        val start = ReportText.dottedDate(period.groupValues[1]) ?: return null
        val end = ReportText.dottedDate(period.groupValues[2]) ?: return null

        var gross: Long? = null
        var cancellation: Long? = null
        var tips: Long? = null
        var distance: Long? = null
        for (row in rows.flatMap(ReportText::splitColumns)) {
            val label = row.lowercase()
            if ("mileage" in label || "kilometraj" in label) {
                val km = Regex("""(\d[\d.,]*)\s*km""").find(row)?.groupValues?.get(1)
                distance = km?.let { ReportText.toMinor(it) }?.times(10) // km with 2 decimals -> metres
                continue
            }
            val amount = ReportText.amountRow(row) ?: continue
            val name = amount.label.lowercase()
            when {
                name.startsWith("gross fare") || name.startsWith("tarif brut") -> gross = amount.amountMinor
                name.startsWith("cancellation") || name.startsWith("taxă de anulare") || name.startsWith("taxa de anulare") ->
                    cancellation = amount.amountMinor
                name == "tip" || name == "tips" || name.startsWith("bacșiș") || name.startsWith("bacsis") -> tips = amount.amountMinor
                // "Bolt Fee" here is Bolt's other costs and fees ("Costuri și taxe"), not the commission, which
                // this summary does not include.
                name.startsWith("bolt fee") || name.startsWith("taxa bolt") || name.startsWith("taxă bolt") -> Unit
            }
        }
        if (gross == null) return null
        return ParsedSummary(start, end, gross, cancellation, tips, distanceMeters = distance)
    }
}

/** One ride from a per-trip report. */
data class ParsedTrip(
    val externalId: String,
    val date: LocalDate,
    val startMinute: Int,
    val fareMinor: Long,
    val paymentMethod: PaymentMethod,
)

/**
 * Reads Bolt's "Rider invoices" CSV. Keeps only invoice number, ride time, payment method and price:
 * rider names and pickup addresses in the file are never read into the app.
 */
object BoltRiderInvoicesParser {

    fun parse(text: String): List<ParsedTrip>? {
        val table = Csv.parse(text)
        if (table.size < 2) return null
        val header = table.first().map { it.trim().lowercase() }
        fun column(vararg names: String, fallback: Int): Int =
            names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: fallback.takeIf { header.size == 16 } ?: -1
        val invoice = column("invoice number", "număr factură", "numar factura", fallback = 0)
        val method = column("payment method", "metodă de plată", "metoda de plata", fallback = 3)
        val rideDate = column("date of ride", "data cursei", fallback = 4)
        val total = column("price total", "preț total", "pret total", fallback = 15)
        if (listOf(invoice, method, rideDate, total).any { it < 0 }) return null

        return table.drop(1).filter { it.size > maxOf(invoice, method, rideDate, total) }.mapNotNull { row ->
            val dateTime = Regex("""(\d{1,2}\.\d{1,2}\.\d{4})\s+(\d{1,2}):(\d{2})""").find(row[rideDate]) ?: return@mapNotNull null
            val date = ReportText.dottedDate(dateTime.groupValues[1]) ?: return@mapNotNull null
            val fare = ReportText.toMinor(row[total]) ?: return@mapNotNull null
            ParsedTrip(
                externalId = row[invoice].trim(),
                date = date,
                startMinute = dateTime.groupValues[2].toInt() * 60 + dateTime.groupValues[3].toInt(),
                fareMinor = fare,
                paymentMethod = paymentMethod(row[method]),
            )
        }.takeIf { it.isNotEmpty() }
    }

    private fun paymentMethod(text: String): PaymentMethod {
        val t = text.lowercase()
        return when {
            "cash" in t || "numerar" in t -> PaymentMethod.CASH
            "business" in t || "firm" in t -> PaymentMethod.BUSINESS
            else -> PaymentMethod.IN_APP
        }
    }
}

/** Minimal RFC 4180 reader: quoted fields, doubled quotes, commas and newlines inside quotes. */
object Csv {
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        val t = text.removePrefix("﻿")
        while (i < t.length) {
            val c = t[i]
            when {
                quoted && c == '"' && i + 1 < t.length && t[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < t.length && t[i + 1] == '\n') i++
                    row += field.toString(); field.clear()
                    if (row.any { it.isNotEmpty() }) rows += row
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            if (row.any { it.isNotEmpty() }) rows += row
        }
        return rows
    }
}
