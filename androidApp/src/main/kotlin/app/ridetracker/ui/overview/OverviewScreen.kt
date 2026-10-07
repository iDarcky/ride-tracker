package app.ridetracker.ui.overview

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.R
import app.ridetracker.shared.data.EntryWithPlatform
import app.ridetracker.shared.domain.Comparison
import app.ridetracker.shared.domain.ExpenseCategory
import app.ridetracker.shared.domain.Frequency
import app.ridetracker.shared.domain.PendingExpense
import app.ridetracker.shared.domain.Comparisons
import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.PeriodType
import app.ridetracker.shared.domain.type
import app.ridetracker.ui.common.LocalBottomBarSpace
import app.ridetracker.ui.common.MenuButton
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.SectionHeader
import app.ridetracker.ui.common.PlatformBadge
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.ExpenseBadge
import app.ridetracker.ui.common.icon
import app.ridetracker.ui.common.label
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.common.tabular
import app.ridetracker.ui.common.pickerMillisToLocalDate
import app.ridetracker.ui.common.toPickerMillis
import app.ridetracker.ui.expense.frequencyName
import app.ridetracker.ui.theme.positiveColor
import kotlinx.coroutines.launch
import kotlinx.datetime.isoDayNumber
import java.text.NumberFormat
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onAddEntry: () -> Unit,
    onAddExpense: () -> Unit,
    viewModel: OverviewViewModel = viewModel {
        OverviewViewModel(container.incomeRepository, container.expenseRepository, container.recurringRepository, container.settingsRepository)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val addedText = stringResource(R.string.expense_added)
    val locale = currentLocale()
    val money = remember(state.currencyCode, locale) { MoneyFormat(resolveCurrency(state.currencyCode), locale) }
    val dates = remember(locale) { DateFormats(locale) }
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    var showRangePicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_home)) }, actions = { MenuButton() }) },
        floatingActionButton = {
            AddMenu(onAddIncome = onAddEntry, onAddExpense = onAddExpense, Modifier.padding(bottom = LocalBottomBarSpace.current))
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = LocalBottomBarSpace.current)) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + LocalBottomBarSpace.current + 96.dp,
            ),
        ) {
            if (pending.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.needs_attention)) }
                items(pending, key = { "due-${it.rule.id}-${it.dueDate}" }) { item ->
                    DueExpenseCard(
                        item = item,
                        money = money,
                        dates = dates,
                        onAdd = {
                            viewModel.accept(item)
                            scope.launch { snackbar.showSnackbar(addedText, duration = SnackbarDuration.Short) }
                        },
                        onSkip = { viewModel.skip(item) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
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
            if (state.expenseGroups.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.expenses_by_group)) }
                items(state.expenseGroups, key = { "group-${it.group}" }) { group ->
                    val share = if (state.expenseMinor > 0) group.totalMinor.toFloat() / state.expenseMinor else 0f
                    ListItem(
                        leadingContent = { ExpenseBadge(group.group.icon) },
                        headlineContent = { Text(stringResource(group.group.label)) },
                        supportingContent = {
                            LinearProgressIndicator(
                                progress = { share },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money.format(group.totalMinor), style = MaterialTheme.typography.titleMedium.tabular())
                                Text(percent.format(share), style = MaterialTheme.typography.bodySmall)
                            }
                        },
                    )
                }
            }
            if (!state.loading && state.entryCount == 0 && state.expenseMinor == 0L) {
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
            Text(
                stringResource(if (state.tracksExpenses) R.string.money_kept else R.string.total_income),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                money.format(if (state.tracksExpenses) state.keptMinor else state.totalMinor),
                style = MaterialTheme.typography.displayMedium.tabular(),
                fontWeight = FontWeight.SemiBold,
            )
            val previous = state.previousHeadlineMinor
            val comparison = state.comparison
            if (previous != null && comparison != null) {
                ComparisonLine(state.headlineMinor, previous, comparison, money)
            }
            Spacer(Modifier.height(4.dp))
            if (state.tracksExpenses) {
                // Income minus expenses, spelled out so "money kept" is never a mystery number.
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Figure(stringResource(R.string.income), money.format(state.totalMinor))
                    Figure(stringResource(R.string.nav_expenses), money.format(-state.expenseMinor))
                }
            } else {
                Text(
                    pluralStringResource(R.plurals.entry_count, state.entryCount, state.entryCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** "▲ 12.4% (+RON 50.00) vs the same days last month": change in colour, comparison in plain text. */
@Composable
private fun ComparisonLine(current: Long, previous: Long, comparison: Comparison, money: MoneyFormat) {
    val locale = currentLocale()
    val diff = current - previous
    val up = diff >= 0
    val color = if (up) positiveColor() else MaterialTheme.colorScheme.error
    val percent = Comparisons.percentChange(current, previous)?.let {
        NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }.format(abs(it))
    }
    val amount = (if (up) "+" else "−") + money.format(abs(diff))
    val change = if (percent != null) "$percent ($amount)" else amount
    val label = comparisonLabel(comparison)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(
            if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
            contentDescription = stringResource(if (up) R.string.change_up else R.string.change_down),
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium)) { append(change) }
                append(" ")
                append(label)
            },
            style = MaterialTheme.typography.bodyMedium.tabular(),
        )
    }
}

@Composable
private fun comparisonLabel(comparison: Comparison): String = when (comparison) {
    is Comparison.SameDaysPrevious -> stringResource(
        if (comparison.type == PeriodType.MONTH) R.string.compare_same_days_last_month else R.string.compare_same_days_last_week,
    )
    is Comparison.WholePrevious -> stringResource(
        if (comparison.type == PeriodType.MONTH) R.string.compare_previous_month else R.string.compare_previous_week,
    )
    is Comparison.SameWeekdayLastWeek -> stringResource(
        when (comparison.range.start.dayOfWeek.isoDayNumber) {
            1 -> R.string.compare_last_weekday_1
            2 -> R.string.compare_last_weekday_2
            3 -> R.string.compare_last_weekday_3
            4 -> R.string.compare_last_weekday_4
            5 -> R.string.compare_last_weekday_5
            6 -> R.string.compare_last_weekday_6
            else -> R.string.compare_last_weekday_7
        },
    )
    is Comparison.PreviousDays -> pluralStringResource(R.plurals.compare_previous_days, comparison.days, comparison.days)
}

/** A due recurring expense: what, when, how much, and Add / Skip. */
@Composable
private fun DueExpenseCard(
    item: PendingExpense,
    money: MoneyFormat,
    dates: DateFormats,
    onAdd: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = ExpenseCategory.fromId(item.rule.category)
    OutlinedCard(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            leadingContent = { ExpenseBadge(category.icon) },
            headlineContent = { Text(item.rule.note ?: stringResource(category.label)) },
            supportingContent = {
                Text(stringResource(R.string.due_on, dates.day(item.dueDate)) + " · " + frequencyName(Frequency.fromId(item.rule.frequency)))
            },
            trailingContent = { Text(money.format(-item.rule.amountMinor), style = MaterialTheme.typography.titleMedium.tabular()) },
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.skip)) }
            FilledTonalButton(onClick = onAdd) { Text(stringResource(R.string.add)) }
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.titleMedium.tabular())
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

/** M3 Expressive FAB menu: one button, two actions (add income, add expense). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddMenu(onAddIncome: () -> Unit, onAddExpense: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(checked = expanded, onCheckedChange = { expanded = it }) {
                Icon(
                    if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = stringResource(if (expanded) R.string.close else R.string.add_income),
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                onAddExpense()
            },
            icon = { Icon(Icons.Filled.Remove, contentDescription = null) },
            text = { Text(stringResource(R.string.add_expense)) },
        )
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                onAddIncome()
            },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.add_income)) },
        )
    }
}
