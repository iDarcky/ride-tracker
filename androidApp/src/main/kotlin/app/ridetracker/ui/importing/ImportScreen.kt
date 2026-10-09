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
import app.ridetracker.shared.domain.ImportChecklist
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import kotlin.time.Clock
import kotlinx.datetime.todayIn
import kotlinx.datetime.TimeZone
import app.ridetracker.shared.domain.importing.OnlineTime
import app.ridetracker.shared.domain.importing.ParsedDay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.Edit
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
private const val UBER_COLOR = 0xFF000000

/**
 * What the edit sheet shows ([report]) and how the corrected one goes back into the item: an Uber day is edited
 * with the day editor, then put back among the file's days.
 */
private class EditTarget(val uri: Uri, val report: ReadReport, val apply: (ReadReport) -> ReadReport = { it })

/** The sheet for an Uber report reuses Bolt's editors: a day, a period's totals, or hours. */
private fun uberEditTarget(uri: Uri, report: ReadReport, dayIndex: Int? = null): EditTarget? = when (report) {
    is ReadReport.UberDays -> dayIndex?.let { i ->
        EditTarget(uri, ReadReport.BoltDay(report.fileHash, report.payments.days[i])) { edited ->
            val day = (edited as ReadReport.BoltDay).day
            val days = report.payments.days.toMutableList().also { it[i] = day }.sortedBy { it.date }
            report.copy(payments = report.payments.copy(days = days))
        }
    }
    is ReadReport.UberTotals -> EditTarget(uri, ReadReport.BoltPeriod(report.fileHash, report.summary, monthly = true, addsUp = true)) {
        report.copy(summary = (it as ReadReport.BoltPeriod).summary)
    }
    is ReadReport.UberDay -> EditTarget(uri, ReadReport.BoltDay(report.fileHash, report.day)) {
        report.copy(day = (it as ReadReport.BoltDay).day)
    }
    is ReadReport.UberWeek -> EditTarget(uri, ReadReport.BoltPeriod(report.fileHash, report.summary, monthly = false, addsUp = report.addsUp)) {
        report.copy(summary = (it as ReadReport.BoltPeriod).summary)
    }
    is ReadReport.UberPaymentsScreen -> report.week.asSummary()?.let { summary ->
        EditTarget(uri, ReadReport.BoltPeriod(report.fileHash, summary, monthly = false, addsUp = report.week.addsUp)) {
            val s = (it as ReadReport.BoltPeriod).summary
            // What isn't a named part (airport and other third-party fees) is the rest of the total.
            val named = listOfNotNull(s.grossFareMinor, s.platformFeeMinor, s.bonusMinor, s.tipsMinor, s.cancellationMinor).sum()
            report.copy(
                week = report.week.copy(
                    customerFareMinor = s.grossFareMinor, serviceFeeMinor = s.platformFeeMinor, bonusMinor = s.bonusMinor,
                    tipsMinor = s.tipsMinor, earningsMinor = s.earningsMinor,
                    thirdPartyMinor = s.earningsMinor?.let { e -> (e - named).takeIf { d -> d != 0L } },
                ),
            )
        }
    }
    is ReadReport.UberHours -> EditTarget(uri, ReadReport.BoltActivity(report.fileHash, listOf(OnlineTime(report.time.period, report.time.onlineMinutes)))) {
        report.copy(time = report.time.copy(onlineMinutes = (it as ReadReport.BoltActivity).times.single().minutes))
    }
    else -> null
}

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
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    editing?.let { target ->
        ImportEditSheet(
            report = target.report,
            currency = state.currency,
            onSave = { viewModel.edit(target.uri, target.apply(it)); editing = null },
            onDismiss = { editing = null },
        )
    }

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
                EmptyState(state.checklists, state.uberChecklists, dates, onScreenshots = chooseScreenshots, onFiles = chooseFiles, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Files that aren't needed go last, so what will be saved comes first.
                val hasUberTotals = state.items.any { it.report is ReadReport.UberTotals && it.selected }
                items(state.items.sortedBy { it.report is ReadReport.UberNotNeeded }, key = { it.uri.toString() }) { item ->
                    ItemCard(
                        item, money, dates, hasUberTotals,
                        onToggle = { viewModel.toggle(item.uri) },
                        onRemove = { viewModel.remove(item.uri) },
                        onEdit = { editing = uberEditTarget(item.uri, it) ?: EditTarget(item.uri, it) },
                        onEditDay = { report, i -> editing = uberEditTarget(item.uri, report, i) },
                    )
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
private fun EmptyState(
    checklists: List<ImportChecklist>,
    uberChecklists: List<ImportChecklist>,
    dates: DateFormats,
    onScreenshots: () -> Unit,
    onFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
        if (checklists.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(R.string.import_whats_missing),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
            )
            checklists.forEach { ImportChecklistCard(it, dates, Modifier.padding(top = 12.dp)) }
        }
        if (uberChecklists.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(R.string.import_whats_missing_uber),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.checklist_uber_intro),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            uberChecklists.forEach { UberChecklistCard(it, dates, Modifier.padding(top = 12.dp)) }
        }
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
private fun ItemCard(
    item: ImportItem,
    money: MoneyFormat,
    dates: DateFormats,
    /** Uber's totals file is being imported too, so its fee needn't be asked for. */
    hasUberTotals: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onEdit: (ReadReport) -> Unit,
    onEditDay: (ReadReport.UberDays, Int) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val report = item.report
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isBolt = report is ReadReport.BoltDay || report is ReadReport.BoltTrips || report is ReadReport.BoltMonth ||
                    report is ReadReport.BoltPeriod || report is ReadReport.BoltActivity
                val isUber = report is ReadReport.UberDays || report is ReadReport.UberTrips || report is ReadReport.UberTotals ||
                    report is ReadReport.UberHours || report is ReadReport.UberNotNeeded || report is ReadReport.UberDay ||
                    report is ReadReport.UberWeek || report is ReadReport.UberPaymentsScreen || report is ReadReport.UberJoined
                if (isBolt) {
                    PlatformBadge("Bolt", BOLT_COLOR, size = 32.dp)
                } else if (isUber) {
                    PlatformBadge("Uber", UBER_COLOR, size = 32.dp)
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
                else -> Details(report, item.replaces, money, dates, hasUberTotals, onEditDay)
            }
            if (item.edited) Note(Icons.Outlined.Edit, stringResource(R.string.import_edited))
            if (report != null && !item.alreadyImported) EditActions(report, onEdit)
        }
    }
}

/** Edit what was read, or type in a screenshot that wasn't recognised (a day's breakdown or hours). */
@Composable
private fun EditActions(report: ReadReport, onEdit: (ReadReport) -> Unit) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        when (report) {
            is ReadReport.BoltDay, is ReadReport.BoltPeriod, is ReadReport.BoltMonth, is ReadReport.BoltActivity,
            is ReadReport.UberTotals, is ReadReport.UberHours, is ReadReport.UberDay, is ReadReport.UberWeek ->
                TextButton(onClick = { onEdit(report) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_edit))
                }
            is ReadReport.Unknown -> {
                OutlinedButton(onClick = {
                    onEdit(ReadReport.BoltDay(report.fileHash, ParsedDay(today, 0, null, emptyList(), addsUp = false)))
                }) { Text(stringResource(R.string.import_type_day)) }
                OutlinedButton(onClick = {
                    onEdit(ReadReport.BoltActivity(report.fileHash, listOf(OnlineTime(DateRange(today, today), 0))))
                }) { Text(stringResource(R.string.import_type_hours)) }
            }
            // Uber's days are edited one by one in the list of days.
            // Half of the Payments screen is edited once joined.
            is ReadReport.UberPaymentsScreen -> if (report.week.complete) {
                TextButton(onClick = { onEdit(report) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_edit))
                }
            }
            is ReadReport.BoltTrips, is ReadReport.UberDays, is ReadReport.UberTrips, is ReadReport.UberNotNeeded,
            is ReadReport.UberJoined -> Unit
        }
    }
}

@Composable
private fun title(report: ReadReport?, failed: Boolean): String = when {
    failed -> stringResource(R.string.import_unknown_file)
    report == null -> stringResource(R.string.import_reading)
    report is ReadReport.BoltPeriod -> stringResource(if (report.monthly) R.string.import_bolt_month_screen else R.string.import_bolt_week)
    report is ReadReport.BoltActivity -> stringResource(R.string.import_bolt_activity)
    report is ReadReport.BoltDay -> stringResource(R.string.import_bolt_day)
    report is ReadReport.BoltTrips -> stringResource(R.string.import_bolt_invoices)
    report is ReadReport.BoltMonth -> stringResource(R.string.import_bolt_month)
    report is ReadReport.UberDays -> stringResource(R.string.import_uber_days)
    report is ReadReport.UberTrips -> stringResource(R.string.import_uber_trips)
    report is ReadReport.UberTotals -> stringResource(R.string.import_uber_totals)
    report is ReadReport.UberHours -> stringResource(R.string.import_uber_hours)
    report is ReadReport.UberNotNeeded -> stringResource(R.string.import_uber_not_needed)
    report is ReadReport.UberDay -> stringResource(R.string.import_uber_day)
    report is ReadReport.UberWeek -> stringResource(R.string.import_uber_week)
    report is ReadReport.UberPaymentsScreen -> stringResource(R.string.import_uber_payments_week)
    report is ReadReport.UberJoined -> stringResource(R.string.import_uber_joined)
    else -> stringResource(R.string.import_unknown_file)
}

private fun subtitle(report: ReadReport?, dates: DateFormats): String? = when (report) {
    is ReadReport.BoltDay -> dates.day(report.day.date)
    is ReadReport.BoltTrips -> dates.range(DateRange(report.trips.minOf { it.date }, report.trips.maxOf { it.date }))
    is ReadReport.BoltMonth -> dates.range(DateRange(report.summary.periodStart, report.summary.periodEnd))
    is ReadReport.BoltPeriod -> dates.range(DateRange(report.summary.periodStart, report.summary.periodEnd))
    is ReadReport.BoltActivity -> null
    is ReadReport.UberDays -> dates.range(report.payments.period)
    is ReadReport.UberTrips -> dates.range(DateRange(report.trips.minOf { it.date }, report.trips.maxOf { it.date }))
    is ReadReport.UberTotals -> dates.range(DateRange(report.summary.periodStart, report.summary.periodEnd))
    is ReadReport.UberHours -> dates.range(report.time.period)
    is ReadReport.UberNotNeeded -> report.report.key
    is ReadReport.UberDay -> dates.day(report.day.date)
    is ReadReport.UberWeek -> dates.range(DateRange(report.summary.periodStart, report.summary.periodEnd))
    is ReadReport.UberPaymentsScreen -> report.week.week?.let { dates.range(it) }
    else -> null
}

@Composable
private fun Details(
    report: ReadReport,
    replaces: Int,
    money: MoneyFormat,
    dates: DateFormats,
    hasUberTotals: Boolean,
    onEditDay: (ReadReport.UberDays, Int) -> Unit,
) {
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
        is ReadReport.BoltActivity -> {
            report.times.forEach { t ->
                val label = if (t.range.start == t.range.endInclusive) dates.day(t.range.start) else dates.range(t.range)
                Figure(label, stringResource(R.string.hours_minutes, t.minutes / 60, t.minutes % 60))
            }
            Note(Icons.Outlined.Info, stringResource(R.string.import_activity_note))
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
        is ReadReport.UberDays -> UberDaysDetails(report, replaces, money, dates, hasUberTotals, onEditDay)
        is ReadReport.UberTrips -> {
            val trips = report.trips
            val cash = trips.count { it.paymentMethod == PaymentMethod.CASH }
            Figure(pluralStringResource(R.plurals.import_rides, trips.size, trips.size), pluralStringResource(R.plurals.import_cash_trips, cash, cash), big = true)
            trips.sumOf { it.distanceMeters ?: 0 }.takeIf { it > 0 }?.let {
                Figure(stringResource(R.string.import_distance), stringResource(R.string.km_value, "%.1f".format(currentLocale(), it / 1000.0)))
            }
            Note(Icons.Outlined.Info, stringResource(R.string.import_no_personal_data))
            Note(Icons.Outlined.Info, stringResource(R.string.import_uber_trips_note))
        }
        is ReadReport.UberTotals -> {
            val s = report.summary
            s.earningsMinor?.let { Figure(stringResource(R.string.import_your_earnings), money.format(it), big = true) }
            HorizontalDivider()
            s.grossFareMinor?.let { Figure(stringResource(R.string.import_gross_fares), money.format(it)) }
            s.bonusMinor?.let { Figure(stringResource(R.string.line_bonus), money.format(it)) }
            s.tipsMinor?.let { Figure(stringResource(R.string.line_tip), money.format(it)) }
            s.cancellationMinor?.let { Figure(stringResource(R.string.line_cancellation_fee), money.format(it)) }
            s.platformFeeMinor?.let { Figure(stringResource(R.string.import_service_fee), money.format(it)) }
            Note(Icons.Outlined.Info, stringResource(R.string.import_uber_totals_note))
        }
        is ReadReport.UberHours -> {
            val t = report.time
            Figure(stringResource(R.string.import_online), stringResource(R.string.hours_minutes, t.onlineMinutes / 60, t.onlineMinutes % 60), big = true)
            if (t.distanceMeters > 0) {
                Figure(stringResource(R.string.import_distance_driven), stringResource(R.string.km_value, "%.1f".format(currentLocale(), t.distanceMeters / 1000.0)))
            }
            Note(Icons.Outlined.Info, stringResource(R.string.import_uber_hours_note))
        }
        is ReadReport.UberNotNeeded -> Note(Icons.Outlined.Info, stringResource(R.string.import_uber_not_needed_note))
        is ReadReport.UberDay -> {
            val day = report.day
            Figure(stringResource(R.string.import_your_earnings), money.format(day.earningsMinor), big = true)
            HorizontalDivider()
            day.lines.forEach { line -> Figure(lineLabel(line), money.format(line.amountMinor)) }
            UberStats(report.onlineMinutes, report.trips)
            if (day.addsUp) Note(Icons.Outlined.CheckCircle, stringResource(R.string.import_adds_up))
            else Note(Icons.Outlined.WarningAmber, stringResource(R.string.import_does_not_add_up), warning = true)
            if (replaces > 0) Note(Icons.Outlined.Info, pluralStringResource(R.plurals.import_replaces, replaces, replaces))
        }
        is ReadReport.UberWeek -> {
            val s = report.summary
            s.earningsMinor?.let { Figure(stringResource(R.string.import_your_earnings), money.format(it), big = true) }
            HorizontalDivider()
            s.tipsMinor?.let { Figure(stringResource(R.string.line_tip), money.format(it)) }
            s.bonusMinor?.let { Figure(stringResource(R.string.line_bonus), money.format(it)) }
            UberStats(report.onlineMinutes, trips = null)
            Note(Icons.Outlined.Info, stringResource(R.string.import_uber_week_note))
        }
        is ReadReport.UberPaymentsScreen -> {
            val w = report.week
            w.earningsMinor?.let { Figure(stringResource(R.string.import_your_earnings), money.format(it), big = true) }
            HorizontalDivider()
            w.customerFareMinor?.let { Figure(stringResource(R.string.import_customer_fares), money.format(it)) }
            w.bonusMinor?.let { Figure(stringResource(R.string.line_bonus), money.format(it)) }
            w.tipsMinor?.let { Figure(stringResource(R.string.line_tip), money.format(it)) }
            w.thirdPartyMinor?.let { Figure(stringResource(R.string.import_third_party_fees), money.format(it)) }
            w.serviceFeeMinor?.let { Figure(stringResource(R.string.import_service_fee), money.format(it)) }
            when {
                !w.complete -> Note(Icons.Outlined.WarningAmber, stringResource(R.string.import_uber_payments_part_note), warning = true)
                w.addsUp -> Note(Icons.Outlined.CheckCircle, stringResource(R.string.import_adds_up))
                else -> Note(Icons.Outlined.WarningAmber, stringResource(R.string.import_does_not_add_up), warning = true)
            }
            if (w.complete) Note(Icons.Outlined.Info, stringResource(R.string.import_uber_week_note))
        }
        is ReadReport.UberJoined -> Note(Icons.Outlined.Info, stringResource(R.string.import_uber_joined_note))
        is ReadReport.Unknown -> Note(Icons.Outlined.WarningAmber, stringResource(R.string.import_not_recognised), warning = true)
    }
}

/** Hours online and trips from Uber's Earnings screen. */
@Composable
private fun UberStats(onlineMinutes: Int?, trips: Int?) {
    onlineMinutes?.let { Figure(stringResource(R.string.import_online), stringResource(R.string.hours_minutes, it / 60, it % 60)) }
    trips?.let { Figure(stringResource(R.string.nav_trips), it.toString()) }
}

/** Uber's payments: the period's earnings and what they're made of, then each day (tap one to correct it). */
@Composable
private fun UberDaysDetails(
    report: ReadReport.UberDays,
    replaces: Int,
    money: MoneyFormat,
    dates: DateFormats,
    hasUberTotals: Boolean,
    onEditDay: (ReadReport.UberDays, Int) -> Unit,
) {
    val days = report.payments.days
    val lines = days.flatMap { it.lines }
    Figure(stringResource(R.string.import_your_earnings), money.format(days.sumOf { it.earningsMinor }), big = true)
    HorizontalDivider()
    lines.groupBy { Triple(it.kind, it.inCash, if (it.kind == IncomeLineKind.OTHER) it.label else "") }
        .forEach { (_, group) -> Figure(lineLabel(group.first()), money.format(group.sumOf { it.amountMinor })) }
    Figure(stringResource(R.string.import_cash_in_hand), money.format(days.sumOf { it.cashCollectedMinor ?: 0 }))
    if (days.all { it.addsUp }) {
        Note(Icons.Outlined.CheckCircle, stringResource(R.string.import_adds_up))
    } else {
        Note(Icons.Outlined.WarningAmber, stringResource(R.string.import_does_not_add_up), warning = true)
    }
    Note(Icons.Outlined.Info, stringResource(R.string.import_uber_days_note))
    if (!hasUberTotals && lines.none { it.kind == IncomeLineKind.COMMISSION }) Note(Icons.Outlined.Info, stringResource(R.string.import_uber_days_fee_note))
    if (replaces > 0) Note(Icons.Outlined.Info, pluralStringResource(R.plurals.import_replaces, replaces, replaces))

    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) {
        Text(
            if (open) stringResource(R.string.import_hide_days)
            else stringResource(R.string.import_show_days) + " · " + pluralStringResource(R.plurals.import_days, days.size, days.size),
        )
    }
    if (open) {
        days.forEachIndexed { i, day ->
            val label = dates.day(day.date)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (!day.addsUp) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(money.format(day.earningsMinor), style = MaterialTheme.typography.bodyMedium.tabular())
                IconButton(onClick = { onEditDay(report, i) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.import_edit_day, label), Modifier.size(18.dp))
                }
            }
        }
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
