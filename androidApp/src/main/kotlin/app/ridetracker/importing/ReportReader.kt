package app.ridetracker.importing

import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.graphics.createBitmap
import app.ridetracker.shared.domain.importing.BoltDailyParser
import app.ridetracker.shared.domain.importing.BoltMonthlySummaryParser
import app.ridetracker.shared.domain.importing.BoltRiderInvoicesParser
import app.ridetracker.shared.domain.importing.DailyParseResult
import app.ridetracker.shared.domain.importing.ParsedDay
import app.ridetracker.shared.domain.importing.ParsedSummary
import app.ridetracker.shared.domain.importing.ParsedTrip
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

    /** A Bolt breakdown on the Weekly or Monthly tab: Bolt's totals for that period. */
    data class BoltPeriod(override val fileHash: String, val summary: ParsedSummary, val monthly: Boolean, val addsUp: Boolean) : ReadReport
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
            type.startsWith("image/") || bytes.looksLikeImage() -> readScreenshot(uri, hash)
            else -> readCsv(bytes, hash)
        }
    }

    private fun ByteArray.startsWith(prefix: String): Boolean =
        size >= prefix.length && prefix.indices.all { this[it] == prefix[it].code.toByte() }

    /** PNG, JPEG or WebP signatures. */
    private fun ByteArray.looksLikeImage(): Boolean =
        (size > 3 && this[0] == 0x89.toByte() && this[1] == 'P'.code.toByte()) ||
            (size > 2 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte()) ||
            (size > 12 && startsWith("RIFF") && this[8] == 'W'.code.toByte())

    private suspend fun readScreenshot(uri: Uri, hash: String): ReadReport {
        val rows = rows(recognize(InputImage.fromFilePath(context, uri)))
        return when (val result = BoltDailyParser.parse(rows, takenOn(uri))) {
            is DailyParseResult.Day -> ReadReport.BoltDay(hash, result.day)
            is DailyParseResult.Period -> ReadReport.BoltPeriod(hash, result.summary, result.monthly, result.addsUp)
            DailyParseResult.NotRecognised -> ReadReport.Unknown(hash)
        }
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

    private fun readCsv(bytes: ByteArray, hash: String): ReadReport {
        val trips = BoltRiderInvoicesParser.parse(decodeText(bytes))
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

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
