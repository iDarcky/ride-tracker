package app.ridetracker.importing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import app.ridetracker.shared.domain.importing.ReportText
import app.ridetracker.shared.domain.importing.UberEarningsParser
import app.ridetracker.shared.domain.importing.UberPaymentsParser
import app.ridetracker.shared.domain.importing.UberPaymentsWeek
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import app.ridetracker.shared.domain.importing.BoltActivityParser
import app.ridetracker.shared.domain.importing.BoltDailyParser
import app.ridetracker.shared.domain.importing.OnlineTime
import app.ridetracker.shared.domain.importing.TextBox
import app.ridetracker.shared.domain.importing.BoltMonthlySummaryParser
import app.ridetracker.shared.domain.importing.BoltRiderInvoicesParser
import app.ridetracker.shared.domain.importing.DailyParseResult
import app.ridetracker.shared.domain.importing.ParsedDay
import app.ridetracker.shared.domain.importing.ParsedSummary
import app.ridetracker.shared.domain.importing.ParsedTrip
import app.ridetracker.shared.domain.importing.Csv
import app.ridetracker.shared.domain.importing.UberFileName
import app.ridetracker.shared.domain.importing.UberPayments
import app.ridetracker.shared.domain.importing.UberReport
import app.ridetracker.shared.domain.importing.UberReports
import app.ridetracker.shared.domain.importing.UberTimeAndDistance
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock
import kotlin.time.Instant

/** What a picked or shared file turned out to be. */
sealed interface ReadReport {
    val fileHash: String

    data class BoltDay(override val fileHash: String, val day: ParsedDay) : ReadReport
    data class BoltTrips(override val fileHash: String, val trips: List<ParsedTrip>) : ReadReport
    data class BoltMonth(override val fileHash: String, val summary: ParsedSummary) : ReadReport

    /** Bolt's Activity screen: online time for a month, or a week and a day. */
    data class BoltActivity(override val fileHash: String, val times: List<OnlineTime>) : ReadReport

    /** A Bolt breakdown on the Weekly or Monthly tab: Bolt's totals for that period. */
    data class BoltPeriod(override val fileHash: String, val summary: ParsedSummary, val monthly: Boolean, val addsUp: Boolean) : ReadReport
    /** Uber's payments per transaction: days of income. */
    data class UberDays(override val fileHash: String, val payments: UberPayments) : ReadReport
    data class UberTrips(override val fileHash: String, val trips: List<ParsedTrip>) : ReadReport

    /** Uber's totals for the period, with its service fee. */
    data class UberTotals(override val fileHash: String, val summary: ParsedSummary) : ReadReport
    data class UberHours(override val fileHash: String, val time: UberTimeAndDistance) : ReadReport

    /** Uber's Earnings screen with a day tapped: that day, its hours and trips. */
    data class UberDay(override val fileHash: String, val day: ParsedDay, val onlineMinutes: Int?, val trips: Int?) : ReadReport

    /** Uber's Earnings screen with no day tapped: the week's total and hours. */
    data class UberWeek(override val fileHash: String, val summary: ParsedSummary, val onlineMinutes: Int?, val addsUp: Boolean) : ReadReport

    /**
     * Uber's Payments screen for a week (with Uber's fee). [joined] holds the hashes of other screenshots joined
     * into this one (the second part of the screen).
     */
    data class UberPaymentsScreen(override val fileHash: String, val week: UberPaymentsWeek, val joined: List<String> = emptyList()) : ReadReport

    /** The second part of a Payments screenshot, joined into the one with the week. */
    data class UberJoined(override val fileHash: String, val into: String) : ReadReport

    /** One of Uber's other reports: not read (it repeats the needed ones or holds personal data). */
    data class UberNotNeeded(override val fileHash: String, val report: UberReport) : ReadReport
    data class Unknown(override val fileHash: String) : ReadReport
}

/**
 * Reads screenshots (on-device text recognition), PDFs (rendered, then recognised) and CSVs.
 * Nothing leaves the phone.
 */
class ReportReader(private val context: Context) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /**
     * Makes sure Google Play services has the text recognition model (downloaded once, then offline).
     * Returns false if it cannot be installed, e.g. without Play services.
     */
    suspend fun read(uri: Uri): ReadReport = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot open $uri")
        val hash = sha256(bytes)
        val type = context.contentResolver.getType(uri).orEmpty()
        val name = displayName(uri).orEmpty().lowercase()
        // Phones label files inconsistently (a .csv can arrive as "Excel" or "octet-stream"), so look at the bytes too.
        when {
            bytes.startsWith("%PDF") || type == "application/pdf" || name.endsWith(".pdf") -> readPdf(bytes, hash)
            type.startsWith("image/") || bytes.looksLikeImage() -> readScreenshot(uri, bytes, hash)
            else -> readCsv(bytes, hash, displayName(uri).orEmpty())
        }
    }

    private fun ByteArray.startsWith(prefix: String): Boolean =
        size >= prefix.length && prefix.indices.all { this[it] == prefix[it].code.toByte() }

    /** PNG, JPEG or WebP signatures. */
    private fun ByteArray.looksLikeImage(): Boolean =
        (size > 3 && this[0] == 0x89.toByte() && this[1] == 'P'.code.toByte()) ||
            (size > 2 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte()) ||
            (size > 12 && startsWith("RIFF") && this[8] == 'W'.code.toByte())

    private suspend fun readScreenshot(uri: Uri, bytes: ByteArray, hash: String): ReadReport {
        // Decoded once: text recognition reads it, and Uber's screens need its colours too.
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val image = if (bitmap != null) InputImage.fromBitmap(bitmap, 0) else InputImage.fromFilePath(context, uri)
        val text = recognize(image)
        val reference = takenOn(uri)
        val rows = rows(text)
        if (UberPaymentsParser.recognises(rows)) {
            return UberPaymentsParser.parse(rows, reference)?.let { ReadReport.UberPaymentsScreen(hash, it) } ?: ReadReport.Unknown(hash)
        }
        if (UberEarningsParser.recognises(rows)) {
            val earnings = UberEarningsParser.parse(rows, reference, bitmap?.let { selectedUberDay(text, it) })
            val day = earnings?.asDay()
            return when {
                earnings == null -> ReadReport.Unknown(hash)
                day != null -> ReadReport.UberDay(hash, day, earnings.onlineMinutes, earnings.trips)
                else -> ReadReport.UberWeek(hash, earnings.asSummary(), earnings.onlineMinutes, earnings.addsUp)
            }
        }
        return when (val result = BoltDailyParser.parse(rows, reference)) {
            is DailyParseResult.Day -> ReadReport.BoltDay(hash, result.day)
            is DailyParseResult.Period -> ReadReport.BoltPeriod(hash, result.summary, result.monthly, result.addsUp)
            DailyParseResult.NotRecognised -> {
                // Not an earnings breakdown: maybe the Activity screen, read from word positions.
                val words = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { e ->
                    e.boundingBox?.let { TextBox(e.text, it.left, it.top, it.right, it.bottom) }
                }
                BoltActivityParser.parse(words, image.width, reference)?.takeIf { it.isNotEmpty() }
                    ?.let { ReadReport.BoltActivity(hash, it) }
                    ?: ReadReport.Unknown(hash)
            }
        }
    }

    /**
     * On Uber's Earnings screen a tapped day's label (its date and weekday under the bars) turns blue; with none
     * tapped they're all grey and the screen shows the week. Returns the tapped day of the month, or null.
     */
    private fun selectedUberDay(text: Text, bitmap: Bitmap): Int? {
        val words = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }.filter { it.boundingBox != null }
        val weekdays = words.filter { ReportText.plain(it.text).trimEnd('.').take(3) in WEEKDAYS }
        // The row of weekday labels: the one with most of them on the same line.
        val row = weekdays.map { w ->
            val box = w.boundingBox!!
            weekdays.filter { kotlin.math.abs(it.boundingBox!!.centerY() - box.centerY()) < box.height() / 2 }
        }.maxByOrNull { it.size }?.takeIf { it.size >= 5 } ?: return null
        val columns = row.mapNotNull { weekday ->
            val box = weekday.boundingBox!!
            // The date sits just above its weekday, in the same column.
            val number = words.filter { w ->
                val b = w.boundingBox!!
                w.text.trim().toIntOrNull() in 1..31 && b.bottom <= box.top + box.height() / 2 &&
                    box.top - b.bottom < box.height() * 2 && b.centerX() in box.left - box.width()..box.right + box.width()
            }.minByOrNull { kotlin.math.abs(it.boundingBox!!.centerX() - box.centerX()) } ?: return@mapNotNull null
            number.text.trim().toInt() to maxOf(blueShare(bitmap, box), blueShare(bitmap, number.boundingBox!!))
        }
        val best = columns.maxByOrNull { it.second } ?: return null
        val others = columns.filter { it !== best }.maxOfOrNull { it.second } ?: 0f
        return best.first.takeIf { best.second > 0.04f && best.second > others * 3 }
    }

    /** Share of a box's pixels that are Uber's blue (selected text), not grey or black. */
    private fun blueShare(bitmap: Bitmap, box: Rect): Float {
        val left = box.left.coerceIn(0, bitmap.width - 1)
        val right = box.right.coerceIn(left + 1, bitmap.width)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height)
        var blue = 0
        var all = 0
        for (y in top until bottom step 2) for (x in left until right step 2) {
            val c = bitmap[x, y]
            val r = AndroidColor.red(c)
            val g = AndroidColor.green(c)
            val b = AndroidColor.blue(c)
            all++
            if (b > 150 && b - r > 90 && b - g > 40) blue++
        }
        return if (all == 0) 0f else blue.toFloat() / all
    }

    private suspend fun readPdf(bytes: ByteArray, hash: String): ReadReport {
        val rows = mutableListOf<String>()
        // PdfRenderer needs a seekable file; files from Gmail or Drive arrive as streams, so copy to a temporary file.
        val file = File.createTempFile("import", ".pdf", context.cacheDir)
        try {
            file.writeBytes(bytes)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { pdf ->
                    for (i in 0 until minOf(pdf.pageCount, 3)) {
                        pdf.openPage(i).use { page ->
                            // ~200 dpi: PDF points are 1/72 inch.
                            val scale = 200f / 72f
                            val bitmap = createBitmap((page.width * scale).toInt(), (page.height * scale).toInt())
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            rows += rows(recognize(InputImage.fromBitmap(bitmap, 0)))
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            file.delete()
        }
        return BoltMonthlySummaryParser.parse(rows)?.let { ReadReport.BoltMonth(hash, it) } ?: ReadReport.Unknown(hash)
    }

    /** Uber's reports are told apart by the names Uber gives them; the ones not needed are never read. */
    private fun readCsv(bytes: ByteArray, hash: String, fileName: String): ReadReport {
        UberFileName.report(fileName)?.takeIf { !it.needed }?.let { return ReadReport.UberNotNeeded(hash, it) }
        val text = decodeText(bytes)
        val header = Csv.parse(text.substringBefore('\n')).firstOrNull().orEmpty()
        val period = UberFileName.period(fileName)
        val uber = when (UberReports.detect(fileName, header)) {
            UberReport.PAYMENTS_ORDER -> UberReports.parsePayments(text, period)?.let { ReadReport.UberDays(hash, it) }
            UberReport.TRIP_ACTIVITY -> UberReports.parseTrips(text)?.let { ReadReport.UberTrips(hash, it) }
            // These two have no dates inside: the period is only in the file name Uber gave them.
            UberReport.PAYMENTS_ORGANIZATION -> period?.let { UberReports.parseTotals(text, it) }?.let { ReadReport.UberTotals(hash, it) }
            UberReport.DRIVER_TIME_AND_DISTANCE -> period?.let { UberReports.parseTimeAndDistance(text, it) }?.let { ReadReport.UberHours(hash, it) }
            else -> null
        }
        if (uber != null) return uber
        val trips = BoltRiderInvoicesParser.parse(text)
        return trips?.let { ReadReport.BoltTrips(hash, it) } ?: ReadReport.Unknown(hash)
    }

    private suspend fun recognize(image: InputImage): Text = recognizer.process(image).await()

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

    /**
     * Joins recognised lines that sit on the same row on screen (a label on the left, its amount on
     * the right), top to bottom.
     */
    private fun rows(text: Text): List<String> {
        val lines = text.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }
        val rows = mutableListOf<MutableList<Text.Line>>()
        for (line in lines.sortedBy { it.boundingBox!!.centerY() }) {
            val box = line.boundingBox!!
            val row = rows.lastOrNull()
            val rowBox = row?.first()?.boundingBox
            if (row != null && rowBox != null && kotlin.math.abs(rowBox.centerY() - box.centerY()) < minOf(rowBox.height(), box.height()) * 0.6) {
                row += line
            } else {
                rows += mutableListOf(line)
            }
        }
        return rows.map { row -> row.sortedBy { it.boundingBox!!.left }.joinToString(" ") { it.text } }
    }

    /** When the screenshot was taken, so "8 Oct" gets the right year; today if unknown. */
    private fun takenOn(uri: Uri): LocalDate {
        val zone = TimeZone.currentSystemDefault()
        val millis = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val taken = c.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN).takeIf { it >= 0 }?.let { c.getLong(it) }
                val modified = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED).takeIf { it >= 0 }?.let { c.getLong(it) * 1000 }
                taken?.takeIf { it > 0 } ?: modified?.takeIf { it > 0 }
            }
        }.getOrNull()
        val instant = millis?.let { Instant.fromEpochMilliseconds(it) } ?: Clock.System.now()
        return instant.toLocalDateTime(zone).date
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** UTF-8 by default; UTF-16 when the file starts with its byte-order mark (some spreadsheet exports). */
    private fun decodeText(bytes: ByteArray): String = when {
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> String(bytes, Charsets.UTF_16LE).removePrefix("\uFEFF")
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, Charsets.UTF_16BE).removePrefix("\uFEFF")
        else -> bytes.decodeToString()
    }

    private companion object {
        val WEEKDAYS = setOf("mon", "tue", "wed", "thu", "fri", "sat", "sun", "lun", "mar", "mie", "joi", "vin", "sam", "dum")
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
