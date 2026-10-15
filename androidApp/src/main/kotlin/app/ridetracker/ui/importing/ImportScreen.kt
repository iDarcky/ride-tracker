package app.ridetracker.ui.importing

import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.CompositionLocalProvider
import app.ridetracker.ui.common.GlassDropdownMenu
import app.ridetracker.ui.common.LocalHaze
import app.ridetracker.ui.common.glass
import app.ridetracker.ui.common.glassContainer
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import app.ridetracker.ui.common.distanceFormat
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import app.ridetracker.shared.domain.importing.UnrecognisedReason
import app.ridetracker.ui.expense.ConnectedChoice
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
    onOpenReminder: () -> Unit,
    sharedFiles: MutableStateFlow<List<Uri>>,
    /** Open on "End of month" (from Home's files line or the reminder on the 1st). */
    startOnMonth: Boolean = false,
    /** A finished month's summary (End of month, once the month is complete). */
    onOpenSummary: (DateRange) -> Unit = {},
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

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val undoText = stringResource(R.string.undo)
    // "Day off": the day leaves the menu and the week strip shows a dash; Undo puts it back.
    val markDayOff: (LocalDate) -> Unit = { day ->
        viewModel.setDayOff(day, true)
        scope.launch {
            val text = resources.getString(R.string.day_marked_off, dates.shortWeekday(day))
            if (snackbar.showSnackbar(text, actionLabel = undoText, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                viewModel.setDayOff(day, false)
            }
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = 80.dp)) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
                },
                // Read on the phone, and sharing from the gallery: behind ⓘ instead of two lines on the page.
                actions = {
                    app.ridetracker.ui.common.InfoButton(
                        stringResource(R.string.import_empty_body) + "\n\n" + stringResource(R.string.import_share_hint),
                        Modifier.padding(end = 8.dp),
                    )
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
                EmptyState(
                    state, money, dates, startOnMonth,
                    onScreenshots = chooseScreenshots, onFiles = chooseFiles, onOpenReminder = onOpenReminder,
                    onToggleDayOff = markDayOff, onAddDifference = viewModel::addMonthDifference, onOpenSummary = onOpenSummary,
                    modifier = Modifier.padding(padding),
                )
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
                        onImportAgain = { viewModel.importAgain(item.uri) },
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

/** What the start page shows: the day's screenshots, or the month's files. */
private enum class ImportGuide { TODAY, MONTH }

/**
 * The start page: "Today" (one screenshot per app after each driving day, with the last week's days ticked) or
 * "End of month" (what a finished month needs from each app, ticked as it comes in). Screenshots come first (owner,
 * 13 Oct 2026): it always opens on Today; the month is there when the driver wants it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyState(
    state: ImportUiState,
    money: MoneyFormat,
    dates: DateFormats,
    startOnMonth: Boolean,
    onScreenshots: () -> Unit,
    onFiles: () -> Unit,
    onOpenReminder: () -> Unit,
    onToggleDayOff: (LocalDate) -> Unit,
    onAddDifference: (Long, Long) -> Unit,
    onOpenSummary: (DateRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    var guide by rememberSaveable { mutableStateOf(if (startOnMonth) ImportGuide.MONTH else ImportGuide.TODAY) }
    // The page scrolls under the floating toolbar, which blurs it like the navigation bar does.
    val haze = rememberHazeState()
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().hazeSource(haze).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 112.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ConnectedChoice(ImportGuide.entries, guide, { guide = it }) {
                Text(stringResource(if (it == ImportGuide.TODAY) R.string.import_tab_today else R.string.import_tab_month))
            }
            Spacer(Modifier.height(16.dp))
            when (guide) {
                ImportGuide.TODAY -> TodayGuide(state, dates, onOpenReminder)
                ImportGuide.MONTH -> MonthGuide(state, money, dates, onAddDifference, onOpenSummary)
            }
        }
        CompositionLocalProvider(LocalHaze provides haze) {
            ImportToolbar(
                state, dates,
                onScreenshots = onScreenshots,
                onFiles = onFiles,
                onToggleDayOff = onToggleDayOff,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
            )
        }
    }
}

/**
 * The start page's actions floating at the bottom, frosted glass like the navigation bar: screenshots, files, and
 * "Day off", whose menu lists the last week's days with nothing in.
 */
@Composable
private fun ImportToolbar(
    state: ImportUiState,
    dates: DateFormats,
    onScreenshots: () -> Unit,
    onFiles: () -> Unit,
    onToggleDayOff: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dayOffMenu by remember { mutableStateOf(false) }
    // Days with nothing in and not already marked: a marked day leaves the menu (Undo is in the snackbar).
    val emptyDays = state.recentDays.filter { it !in state.boltDays && it !in state.uberDays && it.toEpochDays() !in state.daysOff }.reversed()
    // Three equal frosted-glass buttons (owner, 15 Oct 2026: none coloured, so none looks like a selected tab).
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolbarButton(Icons.Outlined.Image, stringResource(R.string.toolbar_screenshots), onScreenshots)
        ToolbarButton(Icons.Outlined.Description, stringResource(R.string.toolbar_files), onFiles)
        if (emptyDays.isNotEmpty()) {
            Box {
                ToolbarButton(Icons.Outlined.EventBusy, stringResource(R.string.toolbar_day_off)) { dayOffMenu = true }
                GlassDropdownMenu(expanded = dayOffMenu, onDismissRequest = { dayOffMenu = false }) {
                    Text(
                        stringResource(R.string.days_off_body),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 260.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    emptyDays.forEach { day ->
                        DropdownMenuItem(
                            text = { Text(dates.shortWeekday(day)) },
                            onClick = {
                                dayOffMenu = false
                                onToggleDayOff(day)
                            },
                            leadingIcon = { Icon(Icons.Outlined.EventBusy, contentDescription = null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayGuide(state: ImportUiState, dates: DateFormats, onOpenReminder: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.import_today_intro), style = MaterialTheme.typography.bodyLarge)
        GuideCard("Bolt", BOLT_COLOR, stringResource(R.string.import_today_bolt_how)) {
            if (state.recentDays.isNotEmpty()) WeekStrip(state.recentDays, state.boltDays, state.daysOff, dates)
        }
        GuideCard("Uber", UBER_COLOR, stringResource(R.string.import_today_uber_how)) {
            if (state.recentDays.isNotEmpty()) WeekStrip(state.recentDays, state.uberDays, state.daysOff, dates)
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenReminder),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = {
                    Text(
                        if (state.reminder.enabled) {
                            stringResource(R.string.import_reminder_row_on, reminderTime(state.reminder.hour, state.reminder.minute))
                        } else {
                            stringResource(R.string.import_reminder_row)
                        },
                    )
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            )
        }
    }
}

/** One of the floating actions: an outlined pill in frosted glass, all alike. */
@Composable
private fun ToolbarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(48.dp).clip(CircleShape).glass(),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = glassContainer()),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Icon(icon, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label, maxLines = 1)
    }
}

/**
 * One app's screenshots, compact: its name and the last week's days; where to find the screenshot opens with the
 * arrow (owner, 15 Oct 2026: smaller cards, the how-to hidden).
 */
@Composable
private fun GuideCard(name: String, color: Long, how: String, progress: @Composable () -> Unit) {
    var open by rememberSaveable(name) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlatformBadge(name, color, size = 28.dp)
                Spacer(Modifier.width(12.dp))
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.import_today_week),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(R.string.checklist_how),
                    modifier = Modifier.padding(start = 8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (open) Text(how, style = MaterialTheme.typography.bodyMedium)
            progress()
        }
    }
}

/** The last 7 days as weekday letters, each ticked when that day is imported. */
@Composable
private fun WeekStrip(days: List<LocalDate>, done: Set<LocalDate>, daysOff: Set<Long>, dates: DateFormats) {
    val locale = currentLocale()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            val imported = day in done
            val off = !imported && day.toEpochDays() in daysOff
            val label = if (off) {
                stringResource(R.string.day_off_label, dates.day(day))
            } else {
                stringResource(if (imported) R.string.import_day_done else R.string.import_day_missing, dates.day(day))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label },
            ) {
                Text(
                    day.toJavaLocalDate().dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW_STANDALONE, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Icon(
                    when {
                        imported -> Icons.Filled.CheckCircle
                        off -> Icons.Outlined.RemoveCircleOutline
                        else -> Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = if (imported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                Text(day.day.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MonthGuide(
    state: ImportUiState,
    money: MoneyFormat,
    dates: DateFormats,
    onAddDifference: (Long, Long) -> Unit,
    onOpenSummary: (DateRange) -> Unit,
) {
    val status = state.monthEnd ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.month_intro), style = MaterialTheme.typography.bodyLarge)
        when {
            status.apps.isEmpty() -> Text(
                stringResource(R.string.month_nothing, dates.monthName(status.month)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            status.complete -> OutlinedCard(Modifier.fillMaxWidth()) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(stringResource(R.string.month_complete, dates.monthName(status.month))) },
                    supportingContent = { Text(stringResource(R.string.month_complete_body)) },
                )
                Button(
                    onClick = { onOpenSummary(status.month.range) },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                ) { Text(stringResource(R.string.month_see, dates.monthName(status.month))) }
            }
        }
        status.apps.forEach { app ->
            val platform = state.platforms.firstOrNull { it.id == app.platformId } ?: return@forEach
            MonthEndCard(app, platform, status, money, dates, onAddDifference = { onAddDifference(app.platformId, it) })
        }
        MonthEndExtras()
    }
}

/** "21:30" or "9:30 PM", as the phone shows times. */
@Composable
private fun reminderTime(hour: Int, minute: Int): String {
    val context = LocalContext.current
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return java.time.LocalTime.of(hour, minute).format(java.time.format.DateTimeFormatter.ofPattern(pattern, currentLocale()))
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
    onImportAgain: () -> Unit,
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
                item.alreadyImported -> {
                    Note(Icons.Outlined.Info, stringResource(R.string.import_already))
                    // A misread or deleted import can be saved again; it replaces what this file saved before.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onImportAgain) { Text(stringResource(R.string.import_again)) }
                    }
                }
                else -> Details(report, item.replaces, money, dates, hasUberTotals, onEditDay)
            }
            if (item.edited) Note(Icons.Outlined.Edit, stringResource(R.string.import_edited))
            if (item.reimport) Note(Icons.Outlined.Info, stringResource(R.string.import_again_note))
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
            // More than 5,000 (lei, euro…) in a day is almost always a misread total.
            if (day.earningsMinor > UNUSUALLY_HIGH_DAY_MINOR) {
                Note(Icons.Outlined.WarningAmber, stringResource(R.string.import_unusually_high), warning = true)
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
            s.distanceMeters?.let { Figure(stringResource(R.string.import_distance), distanceFormat().meters(it, decimals = 1)) }
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
                Figure(stringResource(R.string.import_distance), distanceFormat().meters(it, decimals = 1))
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
                Figure(stringResource(R.string.import_distance_driven), distanceFormat().meters(t.distanceMeters, decimals = 1))
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
        is ReadReport.Unknown -> Note(Icons.Outlined.WarningAmber, reasonText(report.reason), warning = true)
    }
}

/** What went wrong with a file and what to do instead. */
@Composable
private fun reasonText(reason: UnrecognisedReason): String = stringResource(
    when (reason) {
        UnrecognisedReason.NO_TEXT -> R.string.import_reason_no_text
        UnrecognisedReason.UBER_NO_WEEK -> R.string.import_reason_uber_no_week
        UnrecognisedReason.UBER_CROPPED -> R.string.import_reason_uber_cropped
        UnrecognisedReason.UBER_OTHER_SCREEN -> R.string.import_reason_uber_other
        UnrecognisedReason.BOLT_NO_DATE -> R.string.import_reason_bolt_no_date
        UnrecognisedReason.BOLT_CROPPED -> R.string.import_reason_bolt_cropped
        UnrecognisedReason.BOLT_OTHER_SCREEN -> R.string.import_reason_bolt_other
        UnrecognisedReason.OTHER_IMAGE -> R.string.import_reason_other_image
        UnrecognisedReason.PDF_NOT_BOLT_MONTHLY -> R.string.import_reason_pdf
        UnrecognisedReason.UBER_RENAMED -> R.string.import_reason_uber_renamed
        UnrecognisedReason.CSV_UNKNOWN -> R.string.import_reason_csv
    },
)

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

/** A day's earnings above this (5,000 in the app currency) get a "check the total" warning on import. */
private const val UNUSUALLY_HIGH_DAY_MINOR = 500_000L
