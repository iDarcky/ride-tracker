package app.ridetracker.ui.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import app.ridetracker.importing.ReadReport
import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.IncomeLineKind
import app.ridetracker.shared.domain.Money
import app.ridetracker.shared.domain.importing.OnlineTime
import app.ridetracker.shared.domain.importing.ParsedDay
import app.ridetracker.shared.domain.importing.ParsedLine
import app.ridetracker.shared.domain.importing.ParsedSummary
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.tabular
import app.ridetracker.ui.expense.ConnectedChoice
import app.ridetracker.ui.expense.DateField
import app.ridetracker.ui.money.incomeKindLabel
import java.util.Currency
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private val deductionKinds = setOf(IncomeLineKind.COMMISSION, IncomeLineKind.OTHER_FEE)

/** Lines a driver can add to a day, as Bolt groups them. */
private val addableLines: List<Pair<IncomeLineKind, Boolean>> = listOf(
    IncomeLineKind.FARE to false, IncomeLineKind.TIP to false, IncomeLineKind.BONUS to false,
    IncomeLineKind.CANCELLATION_FEE to false, IncomeLineKind.TOLL to false, IncomeLineKind.AIRPORT_FEE to false,
    IncomeLineKind.FARE to true, IncomeLineKind.PROMOTION to true,
    IncomeLineKind.COMMISSION to false, IncomeLineKind.OTHER_FEE to false,
)

/**
 * Correct what was read before saving: a day's lines, a week or month's totals, or online hours.
 * Deductions are typed as positive amounts and saved negative. Returns the corrected report.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportEditSheet(report: ReadReport, currency: Currency, onSave: (ReadReport) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Plain surface, not the default grey sheet colour.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (report) {
                is ReadReport.BoltDay -> DayEditor(report.day, currency, onDismiss) { onSave(report.copy(day = it)) }
                is ReadReport.BoltPeriod -> PeriodEditor(report.summary, withEarnings = true, currency, onDismiss) {
                    onSave(report.copy(summary = it))
                }
                is ReadReport.BoltMonth -> PeriodEditor(report.summary, withEarnings = false, currency, onDismiss) {
                    onSave(report.copy(summary = it))
                }
                is ReadReport.BoltActivity -> HoursEditor(report.times, onDismiss) { onSave(report.copy(times = it)) }
                else -> Unit
            }
        }
    }
}

/** An amount field holding text; [minor] is null while empty or not a number. */
private class AmountText(initial: Long?, private val digits: Int) {
    var text by mutableStateOf(initial?.let { Money.toPlainString(kotlin.math.abs(it), digits) }.orEmpty())
    val minor: Long? get() = Money.parseToMinor(text, digits)
}

@Composable
private fun AmountField(label: String, field: AmountText, symbol: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    OutlinedTextField(
        value = field.text,
        onValueChange = { field.text = it },
        label = { Text(label) },
        suffix = { Text(symbol) },
        isError = field.text.isNotBlank() && field.minor == null,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.tabular(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailingIcon = trailing,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun EditorTitle(text: String) = Text(text, style = MaterialTheme.typography.titleLarge)

@Composable
private fun EditorButtons(enabled: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        Button(onClick = onSave, enabled = enabled) { Text(stringResource(R.string.save)) }
    }
}

@Composable
private fun Check(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private class LineText(val kind: IncomeLineKind, val inCash: Boolean, val label: String, initial: Long?, digits: Int) {
    val amount = AmountText(initial, digits)
    fun signed(): Long? = amount.minor?.let { if (kind in deductionKinds) -it else it }
}

@Composable
private fun DayEditor(day: ParsedDay, currency: Currency, onCancel: () -> Unit, onSave: (ParsedDay) -> Unit) {
    val digits = currency.defaultFractionDigits.coerceAtLeast(0)
    val locale = currentLocale()
    val money = remember(currency, locale) { MoneyFormat(currency, locale) }
    val symbol = currency.getSymbol(locale)
    var date by remember { mutableStateOf(day.date) }
    val lines = remember { mutableStateListOf<LineText>().apply { day.lines.forEach { add(LineText(it.kind, it.inCash, it.label, it.amountMinor, digits)) } } }
    val earnings = remember { AmountText(day.earningsMinor.takeIf { it != 0L || day.lines.isNotEmpty() }, digits) }
    val cash = remember { AmountText(day.cashCollectedMinor, digits) }
    var adding by remember { mutableStateOf(false) }

    EditorTitle(stringResource(R.string.edit_day_title))
    DateField(stringResource(R.string.date), date) { date = it }
    lines.forEach { line ->
        val name = incomeKindLabel(line.kind)
        AmountField(
            if (line.kind in deductionKinds) stringResource(R.string.edit_deduction, name)
            else stringResource(if (line.inCash) R.string.line_in_cash else R.string.line_in_app, name),
            line.amount,
            symbol,
            trailing = { IconButton(onClick = { lines.remove(line) }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.import_remove)) } },
        )
    }
    Box {
        OutlinedButton(onClick = { adding = true }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.edit_add_line))
        }
        DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
            addableLines.forEach { (kind, inCash) ->
                val name = incomeKindLabel(kind)
                DropdownMenuItem(
                    text = {
                        Text(
                            if (kind in deductionKinds) name
                            else stringResource(if (inCash) R.string.line_in_cash else R.string.line_in_app, name),
                        )
                    },
                    onClick = { adding = false; lines += LineText(kind, inCash, name, null, digits) },
                )
            }
        }
    }
    AmountField(stringResource(R.string.import_your_earnings), earnings, symbol)
    AmountField(stringResource(R.string.import_cash_in_hand), cash, symbol)

    val sum = lines.sumOf { it.signed() ?: 0L }
    val earned = earnings.minor
    if (earned != null && lines.isNotEmpty()) {
        if (sum == earned) Check(true, stringResource(R.string.import_adds_up))
        else Check(false, stringResource(R.string.edit_lines_total, money.format(sum), money.format(earned)))
    }
    val valid = earned != null && lines.all { it.amount.minor != null } && (cash.text.isBlank() || cash.minor != null)
    EditorButtons(valid, onCancel) {
        val parsed = lines.map { ParsedLine(it.kind, it.signed() ?: 0L, it.inCash, it.label) }
        onSave(ParsedDay(date, earned ?: 0L, cash.minor, parsed, addsUp = false).rechecked())
    }
}

@Composable
private fun PeriodEditor(summary: ParsedSummary, withEarnings: Boolean, currency: Currency, onCancel: () -> Unit, onSave: (ParsedSummary) -> Unit) {
    val digits = currency.defaultFractionDigits.coerceAtLeast(0)
    val locale = currentLocale()
    val money = remember(currency, locale) { MoneyFormat(currency, locale) }
    val dates = remember(locale) { DateFormats(locale) }
    val symbol = currency.getSymbol(locale)
    val earnings = remember { AmountText(summary.earningsMinor, digits) }
    val fares = remember { AmountText(summary.grossFareMinor, digits) }
    val bonus = remember { AmountText(summary.bonusMinor, digits) }
    val tips = remember { AmountText(summary.tipsMinor, digits) }
    val cancellations = remember { AmountText(summary.cancellationMinor, digits) }
    val commission = remember { AmountText(summary.platformFeeMinor?.takeIf { it != 0L }, digits) }
    val km = remember { AmountText(summary.distanceMeters?.let { it / 10 }, 2) } // km with two decimals

    EditorTitle(stringResource(R.string.edit_period_title))
    Text(dates.range(DateRange(summary.periodStart, summary.periodEnd)), style = MaterialTheme.typography.bodyMedium)
    if (withEarnings) AmountField(stringResource(R.string.import_your_earnings), earnings, symbol)
    AmountField(stringResource(R.string.line_fare), fares, symbol)
    if (withEarnings) AmountField(stringResource(R.string.line_bonus), bonus, symbol)
    AmountField(stringResource(R.string.line_tip), tips, symbol)
    AmountField(stringResource(R.string.line_cancellation_fee), cancellations, symbol)
    AmountField(stringResource(R.string.edit_deduction, stringResource(R.string.line_commission)), commission, symbol)
    if (!withEarnings) AmountField(stringResource(R.string.import_distance), km, "km")

    val fields = listOf(earnings, fares, bonus, tips, cancellations, commission, km)
    if (withEarnings && earnings.minor != null) {
        val named = listOf(fares, bonus, tips, cancellations).sumOf { it.minor ?: 0L } - (commission.minor ?: 0L)
        val other = earnings.minor!! - named
        if (other == 0L) Check(true, stringResource(R.string.import_adds_up))
        else Check(other > 0, stringResource(R.string.edit_period_other, money.format(other)))
    }
    EditorButtons(fields.all { it.text.isBlank() || it.minor != null }, onCancel) {
        onSave(
            summary.copy(
                earningsMinor = if (withEarnings) earnings.minor else summary.earningsMinor,
                grossFareMinor = fares.minor,
                bonusMinor = if (withEarnings) bonus.minor else summary.bonusMinor,
                tipsMinor = tips.minor,
                cancellationMinor = cancellations.minor,
                platformFeeMinor = commission.minor?.let { -it },
                distanceMeters = if (withEarnings) summary.distanceMeters else km.minor?.let { it * 10 },
            ),
        )
    }
}

private enum class HoursSpan(val label: Int) { DAY(R.string.period_day), WEEK(R.string.period_week), MONTH(R.string.period_month) }

private class HoursText(val time: OnlineTime) {
    var hours by mutableStateOf((time.minutes / 60).takeIf { time.minutes > 0 }?.toString().orEmpty())
    var minutes by mutableStateOf((time.minutes % 60).takeIf { time.minutes > 0 }?.toString().orEmpty())
    val total: Int? get() {
        val h = hours.ifBlank { "0" }.toIntOrNull() ?: return null
        val m = minutes.ifBlank { "0" }.toIntOrNull() ?: return null
        return if (m in 0..59 && h >= 0) h * 60 + m else null
    }
}

/** Hours online per period. A single empty item (typed in by hand) also asks which day, week or month. */
@Composable
private fun HoursEditor(times: List<OnlineTime>, onCancel: () -> Unit, onSave: (List<OnlineTime>) -> Unit) {
    val locale = currentLocale()
    val dates = remember(locale) { DateFormats(locale) }
    val rows = remember { times.map { HoursText(it) } }
    val typedIn = times.size == 1 && times[0].minutes == 0
    var span by remember { mutableStateOf(HoursSpan.MONTH) }
    var anchor by remember { mutableStateOf(times[0].range.start) }

    EditorTitle(stringResource(R.string.edit_hours_title))
    if (typedIn) {
        ConnectedChoice(HoursSpan.entries, span, { span = it }) { Text(stringResource(it.label), maxLines = 1) }
        DateField(stringResource(R.string.date), anchor) { anchor = it }
    }
    rows.forEach { row ->
        if (!typedIn) {
            val r = row.time.range
            Text(if (r.start == r.endInclusive) dates.day(r.start) else dates.range(r), style = MaterialTheme.typography.titleSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = row.hours, onValueChange = { row.hours = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.edit_hours)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = row.minutes, onValueChange = { row.minutes = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.edit_minutes)) }, singleLine = true, isError = row.total == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
            )
        }
    }
    EditorButtons(rows.all { (it.total ?: 0) > 0 }, onCancel) {
        if (typedIn) {
            val range = when (span) {
                HoursSpan.DAY -> DateRange(anchor, anchor)
                HoursSpan.WEEK -> anchor.minus(DatePeriod(days = anchor.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber))
                    .let { DateRange(it, it.plus(DatePeriod(days = 6))) }
                HoursSpan.MONTH -> LocalDate(anchor.year, anchor.month, 1)
                    .let { DateRange(it, it.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))) }
            }
            onSave(listOf(OnlineTime(range, rows[0].total ?: 0)))
        } else {
            onSave(rows.map { OnlineTime(it.time.range, it.total ?: 0) })
        }
    }
}
