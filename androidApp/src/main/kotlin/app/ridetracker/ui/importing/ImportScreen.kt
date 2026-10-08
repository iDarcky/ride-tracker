package app.ridetracker.ui.importing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.R
import app.ridetracker.importing.ReadReport
import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.IncomeLineKind
import app.ridetracker.shared.domain.PaymentMethod
import app.ridetracker.shared.domain.importing.ParsedLine
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.PlatformBadge
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.tabular
import kotlinx.coroutines.flow.MutableStateFlow

private const val BOLT_COLOR = 0xFF34D186

/**
 * Import Bolt screenshots and reports: pick or share files, check what was read, save.
 * Everything is read on the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onDone: () -> Unit,
    sharedFiles: MutableStateFlow<List<Uri>>,
    viewModel: ImportViewModel = viewModel {
        ImportViewModel(container.reportReader, container.importRepository, container.incomeRepository, container.settingsRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shared by sharedFiles.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        if (shared.isNotEmpty()) {
            viewModel.add(shared)
            sharedFiles.value = emptyList()
        }
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { viewModel.add(it) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.add(it) }
    val chooseScreenshots = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    // Any file: phones label CSVs in many ways (even as Excel); unknown files show as "Not recognised".
    val chooseFiles = { pickFiles.launch(arrayOf("*/*")) }

    val locale = currentLocale()
    val money = remember(state.currency, locale) { MoneyFormat(state.currency, locale) }
    val dates = remember(locale) { DateFormats(locale) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
                },
            )
        },
        bottomBar = {
            if (state.items.isNotEmpty()) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.reading && !state.saving && state.toSave.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(
                            if (state.reading) {
                                stringResource(R.string.import_reading)
                            } else if (state.toSave.isEmpty()) {
                                stringResource(R.string.save)
                            } else {
                                pluralStringResource(R.plurals.import_save_count, state.toSave.size, state.toSave.size)
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.items.isEmpty() && state.savedCount != null ->
                DoneState(state.savedCount!!, onMore = viewModel::startOver, onDone = onDone, modifier = Modifier.padding(padding))
            state.items.isEmpty() ->
                EmptyState(onScreenshots = chooseScreenshots, onFiles = chooseFiles, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items, key = { it.uri.toString() }) { item ->
                    ItemCard(item, money, dates, onToggle = { viewModel.toggle(item.uri) }, onRemove = { viewModel.remove(item.uri) })
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = chooseScreenshots) { Text(stringResource(R.string.import_add_screenshots)) }
                        OutlinedButton(onClick = chooseFiles) { Text(stringResource(R.string.import_add_files)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onScreenshots: () -> Unit, onFiles: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.import_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.import_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onScreenshots, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Outlined.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.import_choose_screenshots))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onFiles, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Outlined.Description, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.import_choose_files))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.import_share_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DoneState(count: Int, onMore: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(pluralStringResource(R.plurals.import_saved, count, count), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.done)) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.import_more)) }
    }
}

@Composable
private fun ItemCard(item: ImportItem, money: MoneyFormat, dates: DateFormats, onToggle: () -> Unit, onRemove: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val report = item.report
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isBolt = report is ReadReport.BoltDay || report is ReadReport.BoltTrips || report is ReadReport.BoltMonth ||
                    report is ReadReport.BoltPeriod
                if (isBolt) {
                    PlatformBadge("Bolt", BOLT_COLOR, size = 32.dp)
                } else {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title(report, item.failed), style = MaterialTheme.typography.titleMedium)
                    subtitle(report, dates)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (item.canSave) {
                    Checkbox(checked = item.selected, onCheckedChange = { onToggle() })
                } else if (report != null || item.failed) {
                    IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.import_remove)) }
                }
            }
            when {
                item.failed -> Note(Icons.Outlined.WarningAmber, stringResource(R.string.import_failed), warning = true)
                report == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                item.alreadyImported -> Note(Icons.Outlined.Info, stringResource(R.string.import_already))
                else -> Details(report, item.replaces, money)
            }
        }
    }
}

@Composable
private fun title(report: ReadReport?, failed: Boolean): String = when {
    failed -> stringResource(R.string.import_unknown_file)
    report == null -> stringResource(R.string.import_reading)
    report is ReadReport.BoltPeriod -> stringResource(if (report.monthly) R.string.import_bolt_month_screen else R.string.import_bolt_week)
    report is ReadReport.BoltDay -> stringResource(R.string.import_bolt_day)
    report is ReadReport.BoltTrips -> stringResource(R.string.import_bolt_invoices)
    report is ReadReport.BoltMonth -> stringResource(R.string.import_bolt_month)
    else -> stringResource(R.string.import_unknown_file)
}

private fun subtitle(report: ReadReport?, dates: DateFormats): String? = when (report) {
    is ReadReport.BoltDay -> dates.day(report.day.date)
    is ReadReport.BoltTrips -> dates.range(DateRange(report.trips.minOf { it.date }, report.trips.maxOf { it.date }))
    is ReadReport.BoltMonth -> dates.range(DateRange(report.summary.periodStart, report.summary.periodEnd))
    is ReadReport.BoltPeriod -> dates.range(DateRange(report.summary.periodStart, report.summary.periodEnd))
    else -> null
}

@Composable
private fun Details(report: ReadReport, replaces: Int, money: MoneyFormat) {
    when (report) {
        is ReadReport.BoltDay -> {
            val day = report.day
            Figure(stringResource(R.string.import_your_earnings), money.format(day.earningsMinor), big = true)
            HorizontalDivider()
            day.lines.forEach { line -> Figure(lineLabel(line), money.format(line.amountMinor)) }
            day.cashCollectedMinor?.let { Figure(stringResource(R.string.import_cash_in_hand), money.format(it)) }
            if (day.addsUp) {
                Note(Icons.Outlined.CheckCircle, stringResource(R.string.import_adds_up))
            } else {
                Note(Icons.Outlined.WarningAmber, stringResource(R.string.import_does_not_add_up), warning = true)
            }
            if (replaces > 0) Note(Icons.Outlined.Info, pluralStringResource(R.plurals.import_replaces, replaces, replaces))
        }
        is ReadReport.BoltTrips -> {
            val trips = report.trips
            Figure(pluralStringResource(R.plurals.import_rides, trips.size, trips.size), money.format(trips.sumOf { it.fareMinor }), big = true)
            HorizontalDivider()
            val cash = trips.filter { it.paymentMethod == PaymentMethod.CASH }
            Figure(stringResource(R.string.import_paid_in_app), money.format(trips.sumOf { it.fareMinor } - cash.sumOf { it.fareMinor }))
            Figure(stringResource(R.string.import_paid_cash), money.format(cash.sumOf { it.fareMinor }))
            Note(Icons.Outlined.Info, stringResource(R.string.import_no_personal_data))
            Note(Icons.Outlined.Info, stringResource(R.string.import_trips_not_income))
        }
        is ReadReport.BoltMonth -> {
            val s = report.summary
            s.grossFareMinor?.let { Figure(stringResource(R.string.import_gross_fares), money.format(it), big = true) }
            HorizontalDivider()
            s.cancellationMinor?.let { Figure(stringResource(R.string.line_cancellation_fee), money.format(it)) }
            s.tipsMinor?.let { Figure(stringResource(R.string.line_tip), money.format(it)) }
            s.platformFeeMinor?.let { Figure(stringResource(R.string.line_commission), money.format(it)) }
            s.distanceMeters?.let { Figure(stringResource(R.string.import_distance), stringResource(R.string.km_value, "%.1f".format(currentLocale(), it / 1000.0))) }
            Note(Icons.Outlined.Info, stringResource(R.string.import_summary_not_income))
        }
        is ReadReport.BoltPeriod -> {
            val s = report.summary
            s.earningsMinor?.let { Figure(stringResource(R.string.import_your_earnings), money.format(it), big = true) }
            HorizontalDivider()
            s.grossFareMinor?.let { Figure(stringResource(R.string.line_fare), money.format(it)) }
            s.bonusMinor?.let { Figure(stringResource(R.string.line_bonus), money.format(it)) }
            s.tipsMinor?.let { Figure(stringResource(R.string.line_tip), money.format(it)) }
            s.cancellationMinor?.let { Figure(stringResource(R.string.line_cancellation_fee), money.format(it)) }
            s.platformFeeMinor?.let { Figure(stringResource(R.string.line_commission), money.format(it)) }
            if (report.addsUp) {
                Note(Icons.Outlined.CheckCircle, stringResource(R.string.import_adds_up))
            } else {
                Note(Icons.Outlined.WarningAmber, stringResource(R.string.import_does_not_add_up), warning = true)
            }
            Note(Icons.Outlined.Info, stringResource(R.string.import_period_not_income))
        }
        is ReadReport.Unknown -> Note(Icons.Outlined.WarningAmber, stringResource(R.string.import_not_recognised), warning = true)
    }
}

/** Our own name for a known line; the report's label for anything else. */
@Composable
fun lineLabel(line: ParsedLine): String {
    val name = when (line.kind) {
        IncomeLineKind.FARE -> stringResource(R.string.line_fare)
        IncomeLineKind.TIP -> stringResource(R.string.line_tip)
        IncomeLineKind.BONUS -> stringResource(R.string.line_bonus)
        IncomeLineKind.PROMOTION -> stringResource(R.string.line_promotion)
        IncomeLineKind.TOLL -> stringResource(R.string.line_toll)
        IncomeLineKind.AIRPORT_FEE -> stringResource(R.string.line_airport_fee)
        IncomeLineKind.CANCELLATION_FEE -> stringResource(R.string.line_cancellation_fee)
        IncomeLineKind.COMMISSION -> stringResource(R.string.line_commission)
        IncomeLineKind.OTHER_FEE, IncomeLineKind.OTHER -> line.label
    }
    val isTotal = line.kind == IncomeLineKind.COMMISSION || line.kind == IncomeLineKind.OTHER_FEE
    return if (isTotal) name else stringResource(if (line.inCash) R.string.line_in_cash else R.string.line_in_app, name)
}

@Composable
private fun Figure(label: String, value: String, big: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (big) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = (if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium).tabular())
    }
}

@Composable
private fun Note(icon: ImageVector, text: String, warning: Boolean = false) {
    val color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
