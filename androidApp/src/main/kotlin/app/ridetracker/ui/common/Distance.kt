package app.ridetracker.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.ridetracker.R
import app.ridetracker.shared.domain.DistanceUnit
import java.text.NumberFormat
import java.util.Locale

/** The driver's distance unit (Settings → Distance), provided at the top of the app. */
val LocalDistanceUnit = compositionLocalOf { DistanceUnit.KM }

/** Shows km (as stored) in the chosen unit: "1,234 km" or "767 mi", in the UI locale's number style. */
class DistanceFormat(val unit: DistanceUnit, locale: Locale, private val unitLabel: String) {
    private val whole = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 0 }
    private val oneDecimal = NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }

    /** [km] in the chosen unit, whole or with one decimal. */
    fun km(km: Double, decimals: Int = 0): String =
        (if (decimals == 0) whole else oneDecimal).format(unit.fromKm(km)) + " " + unitLabel

    fun meters(meters: Long, decimals: Int = 0): String = km(meters / 1000.0, decimals)

    /** A typed value in the chosen unit, as whole km for storage. */
    fun toKm(value: Long): Long = Math.round(unit.toKm(value.toDouble()))

    /** Stored km as a whole number in the chosen unit (to pre-fill a field). */
    fun fromKm(km: Long): Long = Math.round(unit.fromKm(km.toDouble()))
}

@Composable
fun distanceFormat(): DistanceFormat {
    val unit = LocalDistanceUnit.current
    val locale = currentLocale()
    val label = stringResource(if (unit == DistanceUnit.MILES) R.string.mi_unit else R.string.km_unit)
    return remember(unit, locale, label) { DistanceFormat(unit, locale, label) }
}

/** Picks the km or miles version of a label ("Km driven" / "Miles driven"). */
@Composable
fun byUnit(km: Int, miles: Int): Int = if (LocalDistanceUnit.current == DistanceUnit.MILES) miles else km
