package app.ridetracker.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.R
import app.ridetracker.ui.importing.lineLabel
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.shared.domain.importing.ParsedLine
import app.ridetracker.shared.domain.IncomeSource
import app.ridetracker.shared.domain.IncomeLineKind
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedCard
import app.ridetracker.ui.common.PlatformBadge
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.tabular
import app.ridetracker.ui.common.pickerMillisToLocalDate
import app.ridetracker.ui.common.toPickerMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    onDone: () -> Unit,
    onManagePlatforms: () -> Unit,
    viewModel: EntryViewModel = viewModel {
        EntryViewModel(createSavedStateHandle(), container.incomeRepository, container.importRepository, container.settingsRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val dates = remember(locale) { DateFormats(locale) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.done) { if (state.done) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isEdit) R.string.edit_income else R.string.add_income)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (state.isEdit) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_entry))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::setAmount,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.amount)) },
                suffix = { Text(state.currency.getSymbol(locale)) },
                isError = state.amountInvalid,
                supportingText = if (state.amountInvalid) {
                    { Text(stringResource(R.string.invalid_amount)) }
                } else {
                    null
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.tabular(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                // The keyboard's ✓ saves directly, so the Save button never has to be uncovered first.
                keyboardActions = KeyboardActions(onDone = { viewModel.save() }),
            )

            Text(stringResource(R.string.app), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.platforms.forEach { platform ->
                    FilterChip(
                        selected = platform.id == state.platformId,
                        onClick = { viewModel.setPlatform(platform.id) },
                        label = { Text(platform.name) },
                        leadingIcon = { PlatformBadge(platform.name, platform.colorArgb, size = 20.dp) },
                    )
                }
                TextButton(onClick = onManagePlatforms) { Text(stringResource(R.string.manage_apps)) }
            }

            Text(stringResource(R.string.date), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(dates.day(state.date))
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.note_optional)) },
                minLines = 2,
            )

            if (state.source == IncomeSource.ESTIMATE) {
                Text(
                    stringResource(R.string.estimate_entry_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.lines.isNotEmpty()) Breakdown(state)

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.loading && state.platformId != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.date.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.setDate(it.pickerMillisToLocalDate()) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_entry_title)) },
            text = { Text(stringResource(R.string.delete_entry_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Read-only breakdown of an imported entry, as the report gave it. */
@Composable
private fun Breakdown(state: EntryUiState) {
    val locale = currentLocale()
    val money = remember(state.currency, locale) { MoneyFormat(state.currency, locale) }
    Text(stringResource(R.string.breakdown), style = MaterialTheme.typography.titleSmall)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.lines.forEach { line ->
                val parsed = ParsedLine(IncomeLineKind.fromId(line.kind), line.amountMinor, line.inCash, line.label.orEmpty())
                BreakdownRow(lineLabel(parsed), money.format(line.amountMinor))
            }
            state.cashCollectedMinor?.let { BreakdownRow(stringResource(R.string.import_cash_in_hand), money.format(it)) }
            if (state.source != IncomeSource.MANUAL) {
                Text(
                    stringResource(R.string.from_import),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.tabular())
    }
}
