package app.ridetracker.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.Period
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** The app's current UI locale (follows the in-app language choice). */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** Tabular numerals so amounts line up in lists (design system rule). */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/** Currencies offered for "Other country" (excludes pseudo-currencies like gold with no minor units). */
val selectableCurrencies: List<Currency> by lazy {
    Currency.getAvailableCurrencies().filter { it.defaultFractionDigits >= 0 }.sortedBy { it.currencyCode }
}

fun resolveCurrency(code: String?): Currency =
    code?.let { runCatching { Currency.getInstance(it) }.getOrNull() }
        ?: runCatching { Currency.getInstance(Locale.getDefault()) }.getOrNull()
        ?: Currency.getInstance("EUR")

/** Formats minor-unit amounts in the app currency, using the UI locale's number style. */
class MoneyFormat(val currency: Currency, locale: Locale) {
    val fractionDigits: Int = currency.defaultFractionDigits.coerceAtLeast(0)
    val symbol: String = currency.getSymbol(locale)

    private val formatter = NumberFormat.getCurrencyInstance(locale).apply {
        currency = this@MoneyFormat.currency
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }

    fun format(minor: Long): String {
        val text = formatter.format(BigDecimal.valueOf(minor, fractionDigits))
        // Some locales glue a letter code to the number ("RON367.75"); keep them apart with a no-break space.
        val i = text.indexOf(symbol)
        val end = i + symbol.length
        return if (i >= 0 && symbol.last().isLetter() && end < text.length && text[end].isDigit()) {
            text.substring(0, end) + '\u00A0' + text.substring(end)
        } else {
            text
        }
    }
}

/** Date and period labels in a given locale. */
class DateFormats(private val locale: Locale) {
    private val day = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale)
    private val shortDay = DateTimeFormatter.ofPattern("d MMM", locale)
    private val month = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    private val medium = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    fun day(date: LocalDate): String = date.toJavaLocalDate().format(day).capitalized()

    fun range(range: DateRange): String = with(range) {
        if (start.year == endInclusive.year) {
            "${start.toJavaLocalDate().format(shortDay)} – ${endInclusive.toJavaLocalDate().format(shortDay)} ${start.year}"
        } else {
            "${start.toJavaLocalDate().format(medium)} – ${endInclusive.toJavaLocalDate().format(medium)}"
        }
    }

    fun period(period: Period): String = when (period) {
        is Period.Day -> day(period.date)
        is Period.Week -> range(period.range)
        is Period.Month -> period.range.start.toJavaLocalDate().format(month).capitalized()
        is Period.Custom -> range(period.range)
    }

    private fun String.capitalized() = replaceFirstChar { it.titlecase(locale) }
}

private const val MILLIS_PER_DAY = 86_400_000L

/** Material date pickers use UTC midnight milliseconds. */
fun LocalDate.toPickerMillis(): Long = toEpochDays() * MILLIS_PER_DAY
fun Long.pickerMillisToLocalDate(): LocalDate = LocalDate.fromEpochDays(Math.floorDiv(this, MILLIS_PER_DAY))
