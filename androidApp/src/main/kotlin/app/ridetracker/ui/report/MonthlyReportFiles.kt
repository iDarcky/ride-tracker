package app.ridetracker.ui.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import app.ridetracker.AppContainer
import app.ridetracker.R
import app.ridetracker.shared.domain.ExpenseCategory
import app.ridetracker.shared.domain.MonthlyReport
import app.ridetracker.shared.domain.MonthlyReports
import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.Period
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.label
import app.ridetracker.ui.common.resolveCurrency
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.math.abs
import kotlin.time.Clock

/** A fresh file in the cache's share folder; only the latest shared file is kept. */
internal fun sharedFile(context: Context, fileName: String): File {
    val dir = File(context.cacheDir, "reports").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    return File(dir, fileName)
}

/** Hands [file] to the share sheet (WhatsApp, Threads, email, Files…). */
internal fun openShareSheet(context: Context, file: File, mimeType: String, name: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TITLE, name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(name, uri) // lets the share sheet show a preview
    context.startActivity(Intent.createChooser(send, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Saves [bitmap] as a PNG and opens the share sheet (Wrapped's summary card). */
suspend fun shareImage(context: Context, bitmap: Bitmap, name: String) {
    val file = withContext(Dispatchers.IO) {
        sharedFile(context, "$name.png").also { file -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    openShareSheet(context, file, "image/png", name)
}

/** PDF or image of the month: the same drawing, so both look alike. */
enum class ReportFormat(val mimeType: String, val extension: String) { PDF("application/pdf", "pdf"), IMAGE("image/png", "png") }

/**
 * The monthly report (owner, 13 Oct 2026): income, expenses and profit, income by app, expenses by category. No name,
 * no day-by-day table. Drawn on a plain light page (it's for sharing and printing, whatever the app's theme), written
 * to the cache and handed to the share sheet.
 */
object MonthlyReportFiles {

    suspend fun build(container: AppContainer, month: Period.Month): MonthlyReport {
        val range = DateRange((month.previous() as Period.Month).range.start, month.range.endInclusive)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        return MonthlyReports.build(month, container.incomeRepository.getEntries(range), container.expenseRepository.getInRange(range), today)
    }

    /** Writes the report and opens the share sheet. */
    suspend fun share(context: Context, container: AppContainer, month: Period.Month, format: ReportFormat, locale: Locale) {
        val report = build(container, month)
        val currency = resolveCurrency(container.settingsRepository.settings.first().currencyCode)
        val page = ReportPage(context, report, MoneyFormat(currency, locale), DateFormats(locale), locale)
        val name = context.getString(R.string.report_file, DateFormats(locale).period(month))
        val file = withContext(Dispatchers.IO) {
            sharedFile(context, "$name.${format.extension}").also { file ->
                when (format) {
                    ReportFormat.PDF -> writePdf(page, file)
                    ReportFormat.IMAGE -> writeImage(page, file)
                }
            }
        }
        openShareSheet(context, file, format.mimeType, name)
    }

    private fun writePdf(page: ReportPage, file: File) {
        val pdf = PdfDocument()
        try {
            val p = pdf.startPage(PdfDocument.PageInfo.Builder(ReportPage.WIDTH.toInt(), ReportPage.A4_HEIGHT.toInt(), 1).create())
            page.draw(p.canvas, minHeight = ReportPage.A4_HEIGHT)
            pdf.finishPage(p)
            file.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    /** Twice the PDF's size (1190 px wide), as tall as the content: sharp in chats without a large file. */
    private fun writeImage(page: ReportPage, file: File) {
        val scale = 2f
        val height = page.draw(null)
        val bitmap = createBitmap((ReportPage.WIDTH * scale).toInt(), (height * scale).toInt())
        try {
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            page.draw(canvas)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }
}

/** Lays out and draws the report in PDF points (1/72 inch); A4 is 595 × 842. */
private class ReportPage(
    private val context: Context,
    private val report: MonthlyReport,
    private val money: MoneyFormat,
    private val dates: DateFormats,
    locale: Locale,
) {
    private val percent = NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 0 }

    private fun text(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    // Wrapped's look (owner, 15 Oct 2026): white on the brand teal, see-through cards, an Expressive cookie shape.
    private val brand = text(11f, MUTED, bold = true)
    private val title = text(30f, ON, bold = true)
    private val caption = text(10f, MUTED)
    private val section = text(13f, ON, bold = true)
    private val body = text(11f, ON)
    private val small = text(9f, MUTED)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = LINE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Draws on [canvas] (or only measures when it's null) and returns the height used, at least [minHeight]. */
    fun draw(canvas: Canvas?, minHeight: Float = 0f): Float {
        val right = WIDTH - MARGIN
        canvas?.drawColor(BG)
        canvas?.let { cookie(it, WIDTH - 30f, 60f, 170f) }
        var y = MARGIN + 11f
        canvas?.drawText(context.getString(R.string.report_brand), MARGIN, y, brand)
        y += 38f
        canvas?.drawText(dates.period(report.month), MARGIN, y, title)
        y += 24f

        // Income · Expenses · Profit
        val gap = 10f
        val cardWidth = (right - MARGIN - 2 * gap) / 3
        val cards = listOf(
            Triple(R.string.report_income, report.incomeMinor, POSITIVE),
            Triple(R.string.report_expenses, report.expensesMinor, NEGATIVE),
            Triple(R.string.report_profit, report.profitMinor, ON),
        )
        cards.forEachIndexed { i, (name, amount, color) ->
            val left = MARGIN + i * (cardWidth + gap)
            fill.color = CARD
            canvas?.drawRoundRect(RectF(left, y, left + cardWidth, y + CARD_HEIGHT), 16f, 16f, fill)
            canvas?.drawText(context.getString(name), left + 12f, y + 22f, caption)
            val value = text(19f, color, bold = true).also { fit(it, money.format(amount), cardWidth - 24f) }
            canvas?.drawText(money.format(amount), left + 12f, y + 50f, value)
        }
        y += CARD_HEIGHT
        report.profitChange?.let { change ->
            y += 18f
            val sign = if (change >= 0) "+" else "−"
            val line = context.getString(R.string.report_change, sign + percent.format(abs(change)), dates.period(report.month.previous()))
            canvas?.drawText(line, right - small.measureText(line), y, text(9f, if (change >= 0) POSITIVE else NEGATIVE))
        }

        // Income by app: name, share and amount, with a bar of its share.
        y += 36f
        canvas?.drawText(context.getString(R.string.report_by_app), MARGIN, y, section)
        y += 10f
        if (report.platforms.isEmpty()) {
            y += 20f
            canvas?.drawText(context.getString(R.string.report_no_income), MARGIN, y, caption)
        }
        report.platforms.forEach { p ->
            y += 22f
            fill.color = ON
            canvas?.drawCircle(MARGIN + 5f, y - 4f, 6f, fill) // a white ring keeps dark app colours visible
            fill.color = (p.colorArgb and 0xFFFFFFFF).toInt()
            canvas?.drawCircle(MARGIN + 5f, y - 4f, 5f, fill)
            val amount = money.format(p.totalMinor)
            val share = percent.format(p.share)
            val amountX = right - body.measureText(amount)
            val shareX = amountX - 16f - caption.measureText(share)
            canvas?.drawText(ellipsize(p.name, body, shareX - MARGIN - 28f), MARGIN + 18f, y, body)
            canvas?.drawText(share, shareX, y, caption)
            canvas?.drawText(amount, amountX, y, body)
            y += 8f
            fill.color = TRACK
            canvas?.drawRoundRect(RectF(MARGIN + 18f, y, right, y + 4f), 2f, 2f, fill)
            fill.color = ON
            canvas?.drawRoundRect(RectF(MARGIN + 18f, y, MARGIN + 18f + (right - MARGIN - 18f) * p.share.toFloat(), y + 4f), 2f, 2f, fill)
            y += 4f
        }

        // Expenses by category, biggest first.
        y += 36f
        canvas?.drawText(context.getString(R.string.report_by_category), MARGIN, y, section)
        y += 10f
        if (report.categories.isEmpty()) {
            y += 20f
            canvas?.drawText(context.getString(R.string.report_no_expenses), MARGIN, y, caption)
        }
        report.categories.forEachIndexed { i, c ->
            if (i > 0) canvas?.drawLine(MARGIN, y + 7f, right, y + 7f, stroke)
            y += 22f
            val amount = money.format(c.totalMinor)
            val amountX = right - body.measureText(amount)
            canvas?.drawText(ellipsize(categoryName(c.category), body, amountX - MARGIN - 16f), MARGIN, y, body)
            canvas?.drawText(amount, amountX, y, body)
        }

        // Footer: how complete the month is, and when this was made; at the bottom of a PDF page.
        y += 40f
        val footerY = maxOf(y, minHeight - MARGIN)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val days = context.getString(R.string.report_days, report.daysWithIncome, report.daysSoFar)
        val made = context.getString(R.string.report_made, dates.day(today))
        canvas?.drawText("$days · $made", MARGIN, footerY, small)
        return maxOf(footerY + MARGIN, minHeight)
    }

    /** A soft nine-scallop "cookie" (Material 3 Expressive's Cookie9Sided), see-through white. */
    private fun cookie(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val path = android.graphics.Path()
        val steps = 180
        for (i in 0..steps) {
            val a = 2 * Math.PI * i / steps
            val r = radius * (1 + 0.07 * kotlin.math.cos(9 * a))
            val x = (cx + r * kotlin.math.cos(a)).toFloat()
            val yy = (cy + r * kotlin.math.sin(a)).toFloat()
            if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
        }
        path.close()
        fill.color = SHAPE
        canvas.drawPath(path, fill)
    }

    private fun categoryName(category: ExpenseCategory) = context.getString(category.label)

    private fun ellipsize(text: String, paint: TextPaint, width: Float) =
        TextUtils.ellipsize(text, paint, width.coerceAtLeast(10f), TextUtils.TruncateAt.END).toString()

    /** Shrinks [paint] until [text] fits in [width] (very large amounts). */
    private fun fit(paint: TextPaint, text: String, width: Float) {
        while (paint.measureText(text) > width && paint.textSize > 9f) paint.textSize -= 0.5f
    }

    companion object {
        const val WIDTH = 595f
        const val A4_HEIGHT = 842f
        private const val MARGIN = 40f
        private const val CARD_HEIGHT = 66f

        // Wrapped's first palette: the brand teal (primary) with white, and light green / red for money in and out.
        private const val BG = 0xFF136682.toInt() // primary
        private const val ON = 0xFFFFFFFF.toInt()
        private const val MUTED = 0xCCFFFFFF.toInt()
        private const val POSITIVE = 0xFFA6F4C0.toInt() // positive container
        private const val NEGATIVE = 0xFFFFB4AB.toInt() // error (dark theme)
        private const val CARD = 0x1FFFFFFF
        private const val LINE = 0x33FFFFFF
        private const val TRACK = 0x40FFFFFF
        private const val SHAPE = 0x14FFFFFF
    }
}
