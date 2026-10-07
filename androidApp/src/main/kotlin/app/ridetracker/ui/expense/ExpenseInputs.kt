package app.ridetracker.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import app.ridetracker.shared.domain.ExpenseCategory
import app.ridetracker.shared.domain.ExpenseGroup
import app.ridetracker.shared.domain.Frequency
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.icon
import app.ridetracker.ui.common.label
import app.ridetracker.ui.common.pickerMillisToLocalDate
import app.ridetracker.ui.common.toPickerMillis
import kotlinx.datetime.LocalDate

/** Connected M3 Expressive toggle buttons, one per option. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedChoice(options: List<T>, selected: T, onSelect: (T) -> Unit, content: @Composable (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { content(option) }
        }
    }
}

/** Group as connected buttons, then that group's categories as chips. */
@Composable
fun CategoryPicker(group: ExpenseGroup, category: ExpenseCategory, onGroup: (ExpenseGroup) -> Unit, onCategory: (ExpenseCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.category), style = MaterialTheme.typography.titleSmall)
        ConnectedChoice(ExpenseGroup.entries, group, onGroup) {
            Icon(it.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(it.label), maxLines = 1)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpenseCategory.inGroup(group).forEach { c ->
                FilterChip(
                    selected = c == category,
                    onClick = { onCategory(c) },
                    label = { Text(stringResource(c.label)) },
                    leadingIcon = { Icon(c.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
fun frequencyName(frequency: Frequency): String = stringResource(
    when (frequency) {
        Frequency.WEEKLY -> R.string.repeat_weekly
        Frequency.MONTHLY -> R.string.repeat_monthly
        Frequency.YEARLY -> R.string.repeat_yearly
    },
)

@Composable
fun FrequencyPicker(frequency: Frequency, onFrequency: (Frequency) -> Unit) {
    ConnectedChoice(Frequency.entries, frequency, onFrequency) { Text(frequencyName(it), maxLines = 1) }
}

/** "Ends": never, or on a chosen date. */
@Composable
fun EndsPicker(endDate: LocalDate?, onEndDate: (LocalDate?) -> Unit, earliest: LocalDate) {
    val locale = currentLocale()
    val dates = remember(locale) { DateFormats(locale) }
    var pick by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.repeat_ends), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = endDate == null, onClick = { onEndDate(null) }, label = { Text(stringResource(R.string.repeat_never)) })
            FilterChip(
                selected = endDate != null,
                onClick = { pick = true },
                label = { Text(endDate?.let { stringResource(R.string.repeat_until, dates.day(it)) } ?: stringResource(R.string.repeat_on_date)) },
            )
        }
    }
    if (pick) DatePickDialog(endDate ?: earliest, onDismiss = { pick = false }, onPick = { onEndDate(maxOf(it, earliest)); pick = false })
}

/** Outlined button showing a date; opens the Material date picker. */
@Composable
fun DateField(label: String, date: LocalDate, onDate: (LocalDate) -> Unit) {
    val locale = currentLocale()
    val dates = remember(locale) { DateFormats(locale) }
    var pick by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { pick = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.DateRange, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(dates.day(date))
        }
    }
    if (pick) DatePickDialog(date, onDismiss = { pick = false }, onPick = { onDate(it); pick = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(it.pickerMillisToLocalDate()) } ?: onDismiss() }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state = state) }
}
