package app.ridetracker.ui.overview

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.R
import app.ridetracker.shared.data.EntryWithPlatform
import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.PeriodType
import app.ridetracker.shared.domain.type
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.PlatformBadge
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.common.tabular
import app.ridetracker.ui.common.pickerMillisToLocalDate
import app.ridetracker.ui.common.toPickerMillis
import kotlinx.coroutines.launch
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onAddEntry: () -> Unit,
    onEditEntry: (Long) -> Unit,
    viewModel: OverviewViewModel = viewModel {
        OverviewViewModel(container.incomeRepository, container.settingsRepository)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val money = remember(state.currencyCode, locale) { MoneyFormat(resolveCurrency(state.currencyCode), locale) }
    val dates = remember(locale) { DateFormats(locale) }
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val deletedMessage = stringResource(R.string.entry_deleted)
    val undoLabel = stringResource(R.string.undo)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showRangePicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_overview)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddEntry,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_income)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            item {
                PeriodTypeSelector(
                    selected = state.period.type,
                    onSelect = { type ->
                        viewModel.selectType(type)
                        if (type == PeriodType.CUSTOM && state.period.type != PeriodType.CUSTOM) showRangePicker = true
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                PeriodNavigator(
                    label = dates.period(state.period),
                    period = state.period,
                    showToday = state.period !is Period.Custom &&
                        state.today !in state.period.range.start..state.period.range.endInclusive,
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                    onToday = viewModel::goToToday,
                    onPickRange = { showRangePicker = true },
                )
            }
            item {
                TotalCard(state, money, Modifier.padding(horizontal = 16.dp))
            }
            if (state.totals.size > 1 || (state.totals.size == 1 && state.entryCount > 0)) {
                item { SectionHeader(stringResource(R.string.by_app)) }
                items(state.totals, key = { "total-${it.platformId}" }) { total ->
                    val share = if (state.totalMinor > 0) total.totalMinor.toFloat() / state.totalMinor else 0f
                    ListItem(
                        leadingContent = { PlatformBadge(total.name, total.colorArgb) },
                        headlineContent = { Text(total.name) },
                        supportingContent = {
                            LinearProgressIndicator(
                                progress = { share },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money.format(total.totalMinor), style = MaterialTheme.typography.titleMedium.tabular())
                                Text(percent.format(share), style = MaterialTheme.typography.bodySmall)
                            }
                        },
                    )
                }
            }
            if (!state.loading && state.entryCount == 0) {
                item {
                    Text(
                        stringResource(R.string.empty_period),
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.days.forEach { day ->
                item(key = "day-${day.date}") {
                    SectionHeader(dates.day(day.date), trailing = money.format(day.totalMinor))
                }
                items(day.entries, key = { "entry-${it.id}" }) { entry ->
                    EntryRow(
                        entry = entry,
                        money = money,
                        onClick = { onEditEntry(entry.id) },
                        onDelete = {
                            scope.launch {
                                val deleted = viewModel.delete(entry.id) ?: return@launch
                                val result = snackbar.showSnackbar(
                                    message = deletedMessage,
                                    actionLabel = undoLabel,
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) viewModel.restore(deleted)
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (showRangePicker) {
        RangePickerDialog(
            initial = state.period.range,
            onDismiss = { showRangePicker = false },
            onConfirm = {
                viewModel.setCustomRange(it)
                showRangePicker = false
            },
        )
    }
}

// Order follows the design system: Month first.
private val periodLabels = listOf(
    PeriodType.MONTH to R.string.period_month,
    PeriodType.WEEK to R.string.period_week,
    PeriodType.DAY to R.string.period_day,
    PeriodType.CUSTOM to R.string.period_custom,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PeriodTypeSelector(selected: PeriodType, onSelect: (PeriodType) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        periodLabels.forEachIndexed { index, (type, label) ->
            ToggleButton(
                checked = selected == type,
                onCheckedChange = { onSelect(type) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    periodLabels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(stringResource(label), maxLines = 1)
            }
        }
    }
}

@Composable
private fun PeriodNavigator(
    label: String,
    period: Period,
    showToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onPickRange: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_period))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (period is Period.Custom) {
                TextButton(onClick = onPickRange) {
                    Icon(Icons.Filled.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    if (showToday) {
                        TextButton(onClick = onToday) { Text(stringResource(R.string.back_to_today)) }
                    }
                }
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_period))
        }
    }
}

@Composable
private fun TotalCard(state: OverviewUiState, money: MoneyFormat, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        // Hide the figures until real data arrives, so a placeholder "0" never flashes.
        Column(Modifier.padding(24.dp).alpha(if (state.loading) 0f else 1f)) {
            Text(stringResource(R.string.total_income), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                money.format(state.totalMinor),
                style = MaterialTheme.typography.displayMedium.tabular(),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                pluralStringResource(R.plurals.entry_count, state.entryCount, state.entryCount),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EntryRow(
    entry: EntryWithPlatform,
    money: MoneyFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        ListItem(
            modifier = Modifier.clickable(onClick = onClick),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            leadingContent = { PlatformBadge(entry.platformName, entry.platformColorArgb) },
            headlineContent = { Text(entry.platformName) },
            supportingContent = entry.note?.let { note -> { Text(note, maxLines = 2) } },
            trailingContent = { Text(money.format(entry.amountMinor), style = MaterialTheme.typography.titleMedium.tabular()) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(initial: DateRange, onDismiss: () -> Unit, onConfirm: (DateRange) -> Unit) {
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial.start.toPickerMillis(),
        initialSelectedEndDateMillis = initial.endInclusive.toPickerMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedStartDateMillis != null,
                onClick = {
                    val start = pickerState.selectedStartDateMillis!!.pickerMillisToLocalDate()
                    val end = pickerState.selectedEndDateMillis?.pickerMillisToLocalDate() ?: start
                    onConfirm(DateRange(start, end))
                },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DateRangePicker(state = pickerState, modifier = Modifier.weight(1f))
    }
}
