package app.ridetracker.shared.domain.importing

import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.IncomeLineKind
import app.ridetracker.shared.domain.PaymentMethod
import kotlinx.datetime.LocalDate

/**
 * The CSV reports Uber's Supplier portal (supplier.uber.com → Reports) gives for a period, by the key in their
 * file names ("20260901-20260930-payments_order-<name>.csv"). The names stay English whatever the language of the
 * columns. Only [needed] ones are read; the rest repeat them or hold personal data (driver_status has the phone
 * number and email) and are never read.
 */
enum class UberReport(val key: String, val needed: Boolean) {
    PAYMENTS_ORDER("payments_order", true),
    PAYMENTS_ORGANIZATION("payments_organization", true),
    TRIP_ACTIVITY("trip_activity", true),
    DRIVER_TIME_AND_DISTANCE("driver_time_and_distance", true),
    PAYMENTS_DRIVER("payments_driver", false),
    DRIVER_ACTIVITY("driver_activity", false),
    DRIVER_QUALITY("driver_quality", false),
    DRIVER_PERFORMANCE("driver_performance", false),
    DRIVER_STATUS("driver_status", false),
    VEHICLE_TIME_AND_DISTANCE("vehicle_time_and_distance", false),
    VEHICLE_PERFORMANCE("vehicle_performance", false),
    ;

    companion object {
        val neededReports: List<UberReport> get() = entries.filter { it.needed }
    }
}

/** What an Uber file's name says: which report, and the period ("20260901-20260930-…"). */
object UberFileName {

    fun report(fileName: String): UberReport? {
        val name = fileName.lowercase()
        // Longest keys first: "vehicle_time_and_distance" also ends in "time_and_distance".
        return UberReport.entries.sortedByDescending { it.key.length }.firstOrNull { r ->
            Regex("""(^|[^a-z_])${r.key}([^a-z_]|$)""").containsMatchIn(name)
        }
    }

    fun period(fileName: String): DateRange? {
        val m = Regex("""(\d{4})(\d{2})(\d{2})-(\d{4})(\d{2})(\d{2})""").find(fileName) ?: return null
        val v = m.groupValues.drop(1).map { it.toInt() }
        val start = runCatching { LocalDate(v[0], v[1], v[2]) }.getOrNull() ?: return null
        val end = runCatching { LocalDate(v[3], v[4], v[5]) }.getOrNull() ?: return null
        return if (start <= end) DateRange(start, end) else null
    }
}

/** A trip's earnings from Uber's payments report, to put a fare on the trip. */
data class UberTripFare(val tripId: String, val date: LocalDate, val minute: Int, val fareMinor: Long, val inCash: Boolean)

/** Uber's payments per transaction ("payments_order"): days of income, and each trip's earnings. */
data class UberPayments(val period: DateRange, val days: List<ParsedDay>, val tripFares: List<UberTripFare>)

/** Uber's time online and km for a period ("driver_time_and_distance"). */
data class UberTimeAndDistance(val period: DateRange, val onlineMinutes: Int, val distanceMeters: Long)

/**
 * Reads Uber's CSV reports, Romanian or English columns. Only amounts, dates, trip ids, km and how a trip was
 * paid are read: names, addresses, phone numbers and emails in the files never enter the app.
 *
 * Uber's money columns are paths: "Paid to you : Your earnings : Fare : Fare". Leaves are the parts; a parent
 * column is their total. Everything under "Your earnings" is income; under "Trip balance", refunds (airport fee,
 * tolls) are income too, while cash collected and bank transfers only say how it was paid.
 */
object UberReports {

    /** The report a CSV is, from its file name, else from its columns (in case the phone renamed it). */
    fun detect(fileName: String, header: List<String>): UberReport? {
        UberFileName.report(fileName)?.let { return it }
        val h = header.map(ReportText::plain)
        fun has(vararg words: String) = h.any { col -> words.any { it in col } }
        return when {
            has("tranzactie", "transaction") && has("castigurile tale", "your earnings") -> UberReport.PAYMENTS_ORDER
            has("soldul la inceputul", "start of period", "opening balance") -> UberReport.PAYMENTS_ORGANIZATION
            has("adresa de preluare", "pickup address") && has("starea cursei", "trip status") -> UberReport.TRIP_ACTIVITY
            has("timp in cursa", "time on trip") && !has("vehiculului", "vehicle") && !has("conectarii", "online") ->
                UberReport.DRIVER_TIME_AND_DISTANCE
            else -> null
        }
    }

    // ---- Money columns ----

    private enum class Branch { EARNINGS, REFUNDS, CASH, PAYOUT, OTHER }

    private class MoneyColumn(val index: Int, val path: List<String>, val label: String) {
        val branch: Branch = run {
            val p = path.map(ReportText::plain)
            when {
                p.any { "numerar" in it || "cash" in it } -> Branch.CASH
                p.any { "transferat" in it || "transferred" in it || "payout" in it || "bank" in it } -> Branch.PAYOUT
                p.any { "rambursar" in it || "refund" in it || "reimburse" in it } -> Branch.REFUNDS
                p.size >= 2 && (p[1] == "castigurile tale" || p[1] == "your earnings") -> Branch.EARNINGS
                else -> Branch.OTHER
            }
        }

        val kind: IncomeLineKind = kindOf(path.drop(1).map(ReportText::plain))
    }

    /** What a leaf under "Your earnings" or refunds is, from its path (lower case, no accents). */
    private fun kindOf(path: List<String>): IncomeLineKind {
        val leaf = path.lastOrNull().orEmpty()
        val all = path.joinToString(":")
        return when {
            "aeroport" in leaf || "airport" in leaf -> IncomeLineKind.AIRPORT_FEE
            "trecere" in leaf || "toll" in leaf || "pod" == leaf || "rovinieta" in leaf -> IncomeLineKind.TOLL
            "anulare" in leaf || "cancel" in leaf -> IncomeLineKind.CANCELLATION_FEE
            "bacsis" in leaf || leaf == "tip" || leaf == "tips" -> IncomeLineKind.TIP
            "taxa de servicii" in leaf || "service fee" in leaf -> IncomeLineKind.COMMISSION
            "promotie" in all || "promotion" in all || "quest" in leaf || "boost" in leaf || "recompens" in leaf ||
                "reward" in leaf || "incentive" in leaf || "bonus" in leaf -> IncomeLineKind.BONUS
            path.size >= 2 && (path[1] == "tarif" || path[1] == "fare") -> IncomeLineKind.FARE
            else -> IncomeLineKind.OTHER
        }
    }

    private fun moneyColumns(header: List<String>): List<MoneyColumn> = header.mapIndexedNotNull { i, raw ->
        val parts = raw.split(':').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) null else MoneyColumn(i, parts, parts.last())
    }

    private fun MoneyColumn.isParentOf(other: MoneyColumn): Boolean =
        other.path.size > path.size && other.path.subList(0, path.size).map(ReportText::plain) == path.map(ReportText::plain)

    /** "-17.04", "0", "" -> signed minor units (2 decimals). */
    private fun signed(cell: String?): Long {
        val t = cell?.trim().orEmpty()
        if (t.isEmpty()) return 0
        val minor = ReportText.toMinor(t) ?: return 0
        return if (t.startsWith('-') || t.startsWith('−')) -minor else minor
    }

    /** One row's income parts: leaves, plus whatever a parent column has beyond its leaves. */
    private class RowMoney(val parts: List<Pair<MoneyColumn, Long>>, val cashMinor: Long)

    private fun rowMoney(row: List<String>, columns: List<MoneyColumn>): RowMoney {
        val income = columns.filter { it.branch == Branch.EARNINGS || it.branch == Branch.REFUNDS }
        val parts = mutableListOf<Pair<MoneyColumn, Long>>()
        // Deepest first: a parent's own value counts only for what its children don't explain.
        for (col in income.sortedByDescending { it.path.size }) {
            val children = childrenOf(col, income)
            val value = signed(row.getOrNull(col.index))
            if (children.isEmpty()) {
                if (value != 0L) parts += col to value
            } else {
                val explained = children.sumOf { child -> subtotal(child, row, income) }
                if (value != 0L && value != explained) parts += col to (value - explained)
            }
        }
        val cash = columns.filter { it.branch == Branch.CASH }.sumOf { kotlin.math.abs(signed(row.getOrNull(it.index))) }
        return RowMoney(parts, cash)
    }

    /** A column's value, or the sum of its children when the column is empty. */
    private fun subtotal(col: MoneyColumn, row: List<String>, all: List<MoneyColumn>): Long {
        val own = row.getOrNull(col.index)?.trim().orEmpty()
        if (own.isNotEmpty()) return signed(own)
        return childrenOf(col, all).sumOf { subtotal(it, row, all) }
    }

    /**
     * The nearest columns under [col]: "Your earnings : Promotion : Quest" is a child of "Your earnings" when the
     * report has no "Your earnings : Promotion" column.
     */
    private fun childrenOf(col: MoneyColumn, all: List<MoneyColumn>): List<MoneyColumn> {
        val below = all.filter { col.isParentOf(it) }
        return below.filter { d -> below.none { it.isParentOf(d) } }
    }

    /** The "Your earnings" column itself (path of two), when the report has it. */
    private fun earningsColumn(columns: List<MoneyColumn>): MoneyColumn? =
        columns.firstOrNull { it.branch == Branch.EARNINGS && it.path.size == 2 }

    private val timestamp = Regex("""^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})""")

    private fun dateAndMinute(cell: String): Pair<LocalDate, Int>? {
        val m = timestamp.find(cell.trim()) ?: return null
        val v = m.groupValues.drop(1).map { it.toInt() }
        val date = runCatching { LocalDate(v[0], v[1], v[2]) }.getOrNull() ?: return null
        return date to v[3] * 60 + v[4]
    }

    /** The column whose cells are date-times in most rows (Uber's header for it is odd: "față de raportare"). */
    private fun timestampColumn(rows: List<List<String>>, width: Int): Int? =
        (0 until width).maxByOrNull { i -> rows.count { r -> r.getOrNull(i)?.let { timestamp.containsMatchIn(it.trim()) } == true } }
            ?.takeIf { i -> rows.any { r -> r.getOrNull(i)?.let { timestamp.containsMatchIn(it.trim()) } == true } }

    // ---- payments_order ----

    /** Days of income from "payments_order". [period] comes from the file name; else the first and last day. */
    fun parsePayments(text: String, period: DateRange?): UberPayments? {
        val table = Csv.parse(text)
        if (table.size < 2) return null
        val header = table.first()
        val rows = table.drop(1)
        val columns = moneyColumns(header)
        if (columns.none { it.branch == Branch.EARNINGS }) return null
        val time = timestampColumn(rows, header.size) ?: return null
        val plainHeader = header.map(ReportText::plain)
        val tripColumn = plainHeader.indexOfFirst { ("uuid" in it || " id" in it) && ("cursei" in it || "trip" in it) }
        val earnings = earningsColumn(columns)

        class Row(val date: LocalDate, val minute: Int, val tripId: String?, val earningsMinor: Long, val money: RowMoney)

        val parsed = rows.mapNotNull { row ->
            val (date, minute) = dateAndMinute(row.getOrNull(time).orEmpty()) ?: return@mapNotNull null
            val money = rowMoney(row, columns)
            val refunds = money.parts.filter { it.first.branch == Branch.REFUNDS }.sumOf { it.second }
            val earned = (earnings?.let { signed(row.getOrNull(it.index)) } ?: money.parts.filter { it.first.branch == Branch.EARNINGS }.sumOf { it.second }) + refunds
            if (earned == 0L && money.cashMinor == 0L && money.parts.isEmpty()) return@mapNotNull null // payouts
            Row(date, minute, row.getOrNull(tripColumn)?.trim()?.takeIf { tripColumn >= 0 && it.isNotEmpty() }, earned, money)
        }
        if (parsed.isEmpty()) return null

        val days = parsed.groupBy { it.date }.toSortedMap().map { (date, list) ->
            // Fares of trips the rider paid in cash go in the cash group, as Bolt shows them.
            val lines = list.flatMap { r ->
                r.money.parts.map { (col, amount) -> Triple(col, amount, r.money.cashMinor != 0L && col.kind == IncomeLineKind.FARE) }
            }
                .groupBy { (col, _, inCash) -> Triple(col.kind, inCash, if (col.kind == IncomeLineKind.OTHER) col.label else "") }
                .map { (key, parts) ->
                    ParsedLine(key.first, parts.sumOf { it.second }, key.second, parts.first().first.label)
                }
                .filter { it.amountMinor != 0L }
                .sortedWith(compareBy<ParsedLine> { it.kind == IncomeLineKind.COMMISSION }.thenBy { it.inCash }.thenBy { it.kind.ordinal })
            val earned = list.sumOf { it.earningsMinor }
            ParsedDay(date, earned, list.sumOf { it.money.cashMinor }, lines, addsUp = false).copy(
                addsUp = lines.isNotEmpty() && lines.sumOf { it.amountMinor } == earned,
            )
        }

        val fares = parsed.filter { it.tripId != null }.groupBy { it.tripId!! }.mapNotNull { (id, list) ->
            val fare = list.sumOf { r ->
                r.money.parts.filter { it.first.kind == IncomeLineKind.FARE || it.first.kind == IncomeLineKind.TIP }.sumOf { it.second }
            }
            val first = list.minWith(compareBy<Row> { it.date }.thenBy { it.minute })
            UberTripFare(id, first.date, first.minute, fare, list.any { it.money.cashMinor != 0L }).takeIf { fare > 0 }
        }
        val range = period ?: DateRange(days.first().date, days.last().date)
        return UberPayments(range, days, fares)
    }

    // ---- payments_organization ----

    /** The period's totals from "payments_organization", with Uber's service fee. The period is in the file name only. */
    fun parseTotals(text: String, period: DateRange): ParsedSummary? {
        val table = Csv.parse(text)
        if (table.size < 2) return null
        val header = table.first()
        val columns = moneyColumns(header)
        if (columns.none { it.branch == Branch.EARNINGS }) return null
        val earningsCol = earningsColumn(columns)
        // One row per driver of the organisation; a driver on their own has one.
        var fare = 0L
        var cancellation = 0L
        var tips = 0L
        var bonus = 0L
        var fee = 0L
        var earnings = 0L
        for (row in table.drop(1)) {
            val money = rowMoney(row, columns)
            for ((col, amount) in money.parts) when (col.kind) {
                IncomeLineKind.FARE -> fare += amount
                IncomeLineKind.CANCELLATION_FEE -> cancellation += amount
                IncomeLineKind.TIP -> tips += amount
                IncomeLineKind.BONUS, IncomeLineKind.PROMOTION -> bonus += amount
                IncomeLineKind.COMMISSION -> fee += amount
                else -> Unit
            }
            val refunds = money.parts.filter { it.first.branch == Branch.REFUNDS }.sumOf { it.second }
            earnings += (earningsCol?.let { signed(row.getOrNull(it.index)) } ?: money.parts.filter { it.first.branch == Branch.EARNINGS }.sumOf { it.second }) + refunds
        }
        if (earnings == 0L && fare == 0L) return null
        return ParsedSummary(
            periodStart = period.start,
            periodEnd = period.endInclusive,
            grossFareMinor = fare,
            cancellationMinor = cancellation.takeIf { it != 0L },
            tipsMinor = tips.takeIf { it != 0L },
            bonusMinor = bonus.takeIf { it != 0L },
            platformFeeMinor = fee.takeIf { it != 0L },
            earningsMinor = earnings,
        )
    }

    // ---- trip_activity ----

    /**
     * Completed trips from "trip_activity": id, when it was requested, km, how long, how it was paid. Uber puts no
     * money in this file, so the fare comes from the payments report (0 until it is imported).
     */
    fun parseTrips(text: String): List<ParsedTrip>? {
        val table = Csv.parse(text)
        if (table.size < 2) return null
        val h = table.first().map(ReportText::plain)
        fun col(vararg words: String) = h.indexOfFirst { c -> words.any { it in c } }
        val id = h.indexOfFirst { ("uuid" in it || " id" in it) && ("cursei" in it || "trip" in it) && "sofer" !in it && "driver" !in it }
        val requested = col("comandat", "request")
        val arrived = col("sosirii", "dropoff", "drop-off", "arrival")
        val distance = col("distanta", "distance")
        val status = col("starea", "status")
        val payment = col("tip de plata", "payment")
        if (id < 0 || requested < 0) return null
        return table.drop(1).mapNotNull { row ->
            val state = row.getOrNull(status)?.trim()?.lowercase().orEmpty()
            if (status >= 0 && state.isNotEmpty() && state != "completed") return@mapNotNull null
            val tripId = row.getOrNull(id)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val (date, minute) = dateAndMinute(row.getOrNull(requested).orEmpty()) ?: return@mapNotNull null
            val end = row.getOrNull(arrived)?.let(::dateAndMinute)
            val duration = end?.let { (d, m) -> ((d.toEpochDays() - date.toEpochDays()) * 1440 + m - minute) * 60 }?.takeIf { it > 0 }
            val km = row.getOrNull(distance)?.trim()?.takeIf { it.isNotEmpty() }?.let(ReportText::toMinor)
            ParsedTrip(
                externalId = tripId,
                date = date,
                startMinute = minute,
                fareMinor = 0,
                paymentMethod = if (row.getOrNull(payment)?.trim()?.lowercase() == "cash") PaymentMethod.CASH else PaymentMethod.IN_APP,
                distanceMeters = km?.times(10), // km with 2 decimals -> metres
                durationSeconds = duration?.toLong(),
            )
        }.takeIf { it.isNotEmpty() }
    }

    // ---- driver_time_and_distance ----

    /**
     * Online time and km from "driver_time_and_distance": open (waiting), en route to pickup and on trip. Times are
     * days:hours:minutes ("01:12:42" = 1 day 12 h 42 min); km have two decimals.
     */
    fun parseTimeAndDistance(text: String, period: DateRange): UberTimeAndDistance? {
        val table = Csv.parse(text)
        if (table.size < 2) return null
        val h = table.first().map(ReportText::plain)
        fun cols(vararg words: String) = h.indices.filter { i -> words.any { it in h[i] } }
        // Unavailable time isn't online.
        val times = cols("timp", "time").filter { "indisponibil" !in h[it] && "unavailable" !in h[it] }
        val distances = cols("distanta", "distance").filter { "indisponibil" !in h[it] && "unavailable" !in h[it] }
        if (times.isEmpty()) return null
        var minutes = 0
        var meters = 0L
        for (row in table.drop(1)) {
            minutes += times.sumOf { daysHoursMinutes(row.getOrNull(it).orEmpty()) ?: 0 }
            meters += distances.sumOf { i -> row.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }?.let(ReportText::toMinor)?.times(10) ?: 0L }
        }
        if (minutes == 0) return null
        return UberTimeAndDistance(period, minutes, meters)
    }

    /** "02:09:16" -> 2 days 9 h 16 min in minutes. */
    fun daysHoursMinutes(text: String): Int? {
        val m = Regex("""^(\d+):(\d{1,2}):(\d{1,2})$""").find(text.trim()) ?: return null
        val (d, hr, min) = m.destructured
        return d.toInt() * 1440 + hr.toInt() * 60 + min.toInt()
    }
}
