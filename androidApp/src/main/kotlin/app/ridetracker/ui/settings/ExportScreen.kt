package app.ridetracker.ui.settings

import androidx.compose.material3.DatePickerDefaults
import app.ridetracker.ui.common.GLASS_DIALOG
import app.ridetracker.ui.common.glassContainer
import app.ridetracker.ui.common.glass
import androidx.compose.ui.draw.clip
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.R
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.domain.CsvExport
import app.ridetracker.shared.domain.CsvLabels
import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.ExpenseRepository
import app.ridetracker.shared.domain.IncomeRepository
import app.ridetracker.shared.domain.Period
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.label
import app.ridetracker.ui.common.pickerMillisToLocalDate
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.common.toPickerMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

class ExportViewModel(
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    suspend fun csv(range: DateRange, labels: CsvLabels): String {
        val currency = resolveCurrency(settingsRepository.settings.first().currencyCode)
        return CsvExport.build(
            income = incomeRepository.getEntries(range),
            expenses = expenseRepository.getInRange(range),
            currencyCode = currency.currencyCode,
            fractionDigits = currency.defaultFractionDigits.coerceAtLeast(0),
            labels = labels,
        )
    }
}

private enum class ExportChoice { THIS_MONTH, LAST_MONTH, THIS_YEAR, ALL_TIME, CUSTOM }

/** Pick a period, then a file name; writes one CSV with income and expenses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = viewModel {
        ExportViewModel(container.incomeRepository, container.expenseRepository, container.settingsRepository)
    },
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val locale = currentLocale()
    val dates = remember(locale) { DateFormats(locale) }
    var pendingRange by remember { mutableStateOf<DateRange?>(null) }
    var pickCustom by rememberSaveable { mutableStateOf(false) }
    val savedText = stringResource(R.string.export_saved)
    val failedText = stringResource(R.string.export_failed)

    val labels = CsvLabels(
        date = stringResource(R.string.csv_date),
        type = stringResource(R.string.csv_type),
        item = stringResource(R.string.csv_item),
        group = stringResource(R.string.csv_group),
        amount = stringResource(R.string.csv_amount),
        currency = stringResource(R.string.csv_currency),
        note = stringResource(R.string.csv_note),
        income = stringResource(R.string.csv_income),
        expense = stringResource(R.string.csv_expense),
        category = { resources.getString(it.label) },
        groupName = { resources.getString(it.label) },
    )

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val range = pendingRange
        if (uri == null || range == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val text = viewModel.csv(range, labels)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use {
                        // UTF-8 byte-order mark so Excel shows ă, ș, ț correctly.
                        it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                        it.write(text.toByteArray())
                    }
                }
            }.isSuccess
            snackbar.showSnackbar(if (ok) savedText else failedText)
        }
    }

    fun export(range: DateRange, fileLabel: String) {
        pendingRange = range
        createFile.launch("ridetracker-$fileLabel.csv")
    }

    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val thisMonth = Period.Month.containing(today)
    val lastMonth = thisMonth.previous()

    SettingsFrame(stringResource(R.string.export_csv), onBack, snackbar) {
        item {
            Text(
                stringResource(R.string.export_csv_summary),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ExportChoice.entries.forEach { choice ->
            item {
                val (title, detail) = when (choice) {
                    ExportChoice.THIS_MONTH -> stringResource(R.string.this_month) to dates.period(thisMonth)
                    ExportChoice.LAST_MONTH -> stringResource(R.string.last_month) to dates.period(lastMonth)
                    ExportChoice.THIS_YEAR -> stringResource(R.string.this_year) to today.year.toString()
                    ExportChoice.ALL_TIME -> stringResource(R.string.all_time) to null
                    ExportChoice.CUSTOM -> stringResource(R.string.custom_range) to null
                }
                ListItem(
                    modifier = Modifier.clickable {
                        when (choice) {
                            ExportChoice.THIS_MONTH -> export(thisMonth.range, thisMonth.range.start.toString().take(7))
                            ExportChoice.LAST_MONTH -> export(lastMonth.range, lastMonth.range.start.toString().take(7))
                            ExportChoice.THIS_YEAR ->
                                export(DateRange(LocalDate(today.year, 1, 1), LocalDate(today.year, 12, 31)), today.year.toString())
                            ExportChoice.ALL_TIME -> export(DateRange(LocalDate(1970, 1, 1), LocalDate(2999, 12, 31)), "all")
                            ExportChoice.CUSTOM -> pickCustom = true
                        }
                    },
                    headlineContent = { Text(title, style = MaterialTheme.typography.titleLarge) },
                    supportingContent = detail?.let { { Text(it) } },
                )
            }
        }
    }

    if (pickCustom) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = thisMonth.range.start.toPickerMillis(),
            initialSelectedEndDateMillis = today.toPickerMillis(),
        )
        DatePickerDialog(
            modifier = Modifier.clip(DatePickerDefaults.shape).glass(GLASS_DIALOG),
            colors = DatePickerDefaults.colors(containerColor = glassContainer()),
            onDismissRequest = { pickCustom = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        pickCustom = false
                        val start = pickerState.selectedStartDateMillis!!.pickerMillisToLocalDate()
                        val end = pickerState.selectedEndDateMillis?.pickerMillisToLocalDate() ?: start
                        export(DateRange(start, end), "${start}_$end")
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickCustom = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DateRangePicker(colors = DatePickerDefaults.colors(containerColor = glassContainer()), state = pickerState, modifier = Modifier.weight(1f))
        }
    }
}
