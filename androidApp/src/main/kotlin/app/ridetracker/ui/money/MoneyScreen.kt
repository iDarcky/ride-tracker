package app.ridetracker.ui.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.R
import app.ridetracker.ui.common.MonthTitle
import app.ridetracker.ui.common.ScrollToTopOnReselect
import app.ridetracker.shared.domain.Comparisons
import app.ridetracker.shared.domain.Period
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.EntryRow
import app.ridetracker.ui.common.ExpenseBadge
import app.ridetracker.ui.common.ExpenseRow
import app.ridetracker.ui.common.LocalBottomBarSpace
import app.ridetracker.ui.common.MenuButton
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.PlatformBadge
import app.ridetracker.ui.common.SectionHeader
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.icon
import app.ridetracker.ui.common.label
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.common.tabular
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.abs

/** Recent entries shown before "See all". */
private const val RECENT = 5

/**
 * Income and expenses, one month at a time, like the cockpit design: month chip, total with the change
 * from the previous month, breakdown (by platform and type / by category), then recent entries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyScreen(
    onAddIncome: () -> Unit,
    onEditIncome: (Long) -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenPlatform: (Long, Period.Month) -> Unit,
    viewModel: IncomeListViewModel = viewModel {
        IncomeListViewModel(container.incomeRepository, container.importRepository, container.settingsRepository)
    },
    expenseViewModel: ExpenseListViewModel = viewModel {
        ExpenseListViewModel(container.expenseRepository, container.recurringRepository, container.settingsRepository)
    },
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val bottomSpace = LocalBottomBarSpace.current

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(title = { Text(stringResource(R.string.nav_money)) }, actions = { MenuButton() })
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.income)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.nav_expenses)) })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomSpace)) },
    ) { padding ->
        val modifier = Modifier.padding(top = padding.calculateTopPadding())
        when (tab) {
            0 -> IncomeTab(viewModel, snackbar, onAddIncome, onEditIncome, onOpenPlatform, modifier)
            else -> ExpenseTab(expenseViewModel, snackbar, onAddExpense, onEditExpense, onOpenRecurring, modifier)
        }
    }
}

@Composable
private fun IncomeTab(
    viewModel: IncomeListViewModel,
    snackbar: SnackbarHostState,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenPlatform: (Long, Period.Month) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val money = remember(state.currencyCode, locale) { MoneyFormat(resolveCurrency(state.currencyCode), locale) }
    val dates = remember(locale) { DateFormats(locale) }
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.entry_deleted)
    val undoLabel = stringResource(R.string.undo)
    var showAll by rememberSaveable(state.month) { mutableStateOf(false) }

    val listState = rememberLazyListState()
    ScrollToTopOnReselect(listState)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = LocalBottomBarSpace.current + 24.dp),
    ) {
        item {
            MonthTitle(
                title = state.month?.let { dates.period(it) }.orEmpty(),
                months = state.months,
                selected = state.month,
                dates = dates,
                onMonth = viewModel::selectMonth,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        item {
            TotalCard(
                label = stringResource(R.string.total_income),
                totalMinor = state.totalMinor,
                previousMinor = state.previousMinor,
                previousMonth = state.month?.previous() as? Period.Month,
                money = money,
                dates = dates,
                addLabel = stringResource(R.string.add_income),
                onAdd = onAdd,
            )
        }
        if (state.platforms.isNotEmpty()) {
            item {
                BreakdownCard(stringResource(R.string.by_platform)) {
                    state.platforms.forEach { p ->
                        ShareRow(
                            leading = { PlatformBadge(p.name, p.colorArgb, size = 36.dp) },
                            title = p.name,
                            amount = money.format(p.totalMinor),
                            share = if (state.totalMinor > 0) p.totalMinor.toFloat() / state.totalMinor else 0f,
                            percent = percent,
                            onClick = state.month?.let { m -> { onOpenPlatform(p.platformId, m) } },
                        )
                    }
                }
            }
        }
        val breakdown = state.breakdown
        if (breakdown != null && breakdown.parts.isNotEmpty()) {
            // Only meaningful once some income has an imported breakdown.
            item { MadeOfCard(breakdown, money) }
        }
        breakdown?.payment?.let { payment ->
            item { BreakdownCard(stringResource(R.string.card_and_cash)) { PaymentSplitContent(payment, money, percent) } }
        }
        val entries = state.days.flatMap { it.entries }
        if (!state.loading && entries.isEmpty()) {
            item { EmptyText(stringResource(R.string.no_income_this_month)) }
        } else {
            recentHeader(entries.size, showAll)
            val days = if (showAll) state.days else state.days.limitTo(RECENT) { it.entries.size }
            days.forEach { day ->
                item(key = "day-${day.date}") { SectionHeader(dates.day(day.date), trailing = money.format(day.totalMinor)) }
                val list = if (showAll) day.entries else day.entries.take(RECENT - days.takeWhile { it != day }.sumOf { it.entries.size })
                items(list, key = { "entry-${it.id}" }) { entry ->
                    EntryRow(
                        entry = entry,
                        money = money,
                        onClick = { onEdit(entry.id) },
                        onDelete = {
                            scope.launch {
                                val deleted = viewModel.delete(entry.id) ?: return@launch
                                val result = snackbar.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Short)
                                if (result == SnackbarResult.ActionPerformed) viewModel.restore(deleted)
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            seeAll(entries.size, showAll) { showAll = !showAll }
        }
    }
}

@Composable
private fun ExpenseTab(
    viewModel: ExpenseListViewModel,
    snackbar: SnackbarHostState,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenRecurring: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recurringCount by viewModel.recurringCount.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val money = remember(state.currencyCode, locale) { MoneyFormat(resolveCurrency(state.currencyCode), locale) }
    val dates = remember(locale) { DateFormats(locale) }
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.expense_deleted)
    val undoLabel = stringResource(R.string.undo)
    var showAll by rememberSaveable(state.month, state.category) { mutableStateOf(false) }

    val listState = rememberLazyListState()
    ScrollToTopOnReselect(listState)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = LocalBottomBarSpace.current + 24.dp),
    ) {
        item {
            MonthTitle(
                title = state.month?.let { dates.period(it) }.orEmpty(),
                months = state.months,
                selected = state.month,
                dates = dates,
                onMonth = viewModel::selectMonth,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        item {
            TotalCard(
                label = stringResource(R.string.total_expenses),
                totalMinor = state.totalMinor,
                previousMinor = state.previousMinor,
                previousMonth = state.month?.previous() as? Period.Month,
                money = money,
                dates = dates,
                addLabel = stringResource(R.string.add_expense),
                onAdd = onAdd,
            )
        }
        if (state.categories.isNotEmpty()) {
            item {
                BreakdownCard(stringResource(R.string.by_category)) {
                    state.categories.forEach { c ->
                        ShareRow(
                            leading = { ExpenseBadge(c.category.icon, size = 36.dp) },
                            title = stringResource(c.category.label),
                            amount = money.format(c.totalMinor),
                            share = if (state.totalMinor > 0) c.totalMinor.toFloat() / state.totalMinor else 0f,
                            percent = percent,
                            selected = state.category == c.category,
                            onClick = { viewModel.toggleCategory(c.category) },
                        )
                    }
                }
            }
        }
        item(key = "recurring") {
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenRecurring),
                leadingContent = { Icon(Icons.Filled.Repeat, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.recurring_expenses)) },
                supportingContent = { Text(pluralStringResource(R.plurals.recurring_count, recurringCount, recurringCount)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            )
        }
        val expenses = state.days.flatMap { it.expenses }
        state.category?.let { category ->
            item {
                InputChip(
                    selected = true,
                    onClick = { viewModel.toggleCategory(null) },
                    label = { Text(stringResource(category.label)) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_filter), Modifier.size(18.dp)) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        if (!state.loading && expenses.isEmpty()) {
            item { EmptyText(stringResource(R.string.no_expenses_this_month)) }
        } else {
            recentHeader(expenses.size, showAll)
            val days = if (showAll) state.days else state.days.limitTo(RECENT) { it.expenses.size }
            days.forEach { day ->
                item(key = "day-${day.date}") { SectionHeader(dates.day(day.date), trailing = money.format(-day.totalMinor)) }
                val list = if (showAll) day.expenses else day.expenses.take(RECENT - days.takeWhile { it != day }.sumOf { it.expenses.size })
                items(list, key = { "expense-${it.id}" }) { expense ->
                    ExpenseRow(
                        expense = expense,
                        money = money,
                        onClick = { onEdit(expense.id) },
                        onDelete = {
                            scope.launch {
                                val deleted = viewModel.delete(expense.id) ?: return@launch
                                val result = snackbar.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Short)
                                if (result == SnackbarResult.ActionPerformed) viewModel.restore(deleted)
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            seeAll(expenses.size, showAll) { showAll = !showAll }
        }
    }
}

/** The first days whose items add up to [count] (the last one may be cut). */
private fun <T> List<T>.limitTo(count: Int, size: (T) -> Int): List<T> {
    val out = mutableListOf<T>()
    var left = count
    for (item in this) {
        if (left <= 0) break
        out += item
        left -= size(item)
    }
    return out
}

private fun LazyListScope.recentHeader(count: Int, showAll: Boolean) {
    item(key = "recent") {
        Text(
            stringResource(if (showAll || count <= RECENT) R.string.all_entries else R.string.recent),
            modifier = Modifier.padding(start = 16.dp, top = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private fun LazyListScope.seeAll(count: Int, showAll: Boolean, onToggle: () -> Unit) {
    if (count <= RECENT) return
    item(key = "see-all") {
        TextButton(onClick = onToggle, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(if (showAll) stringResource(R.string.show_fewer) else pluralStringResource(R.plurals.see_all, count, count))
        }
    }
}

/** Month total with the change from the whole previous month, and the Add button (as in the mockup). */
@Composable
private fun TotalCard(
    label: String,
    totalMinor: Long,
    previousMinor: Long?,
    previousMonth: Period.Month?,
    money: MoneyFormat,
    dates: DateFormats,
    addLabel: String,
    onAdd: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money.format(totalMinor), style = MaterialTheme.typography.headlineMedium.tabular())
                if (previousMinor != null && previousMonth != null) {
                    val diff = totalMinor - previousMinor
                    val change = Comparisons.percentChange(totalMinor, previousMinor)
                        ?.let { NumberFormat.getPercentInstance(currentLocale()).apply { maximumFractionDigits = 0 }.format(abs(it)) }
                        ?: money.format(abs(diff))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (diff >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = stringResource(if (diff >= 0) R.string.change_up else R.string.change_down),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.change_vs, change, dates.period(previousMonth)),
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(addLabel)
            }
        }
    }
}

@Composable
internal fun BreakdownCard(title: String, content: @Composable () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                title,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

/** Icon, name and a share bar; amount and % on the right. Tappable when [onClick] is set. */
@Composable
internal fun ShareRow(
    leading: @Composable () -> Unit,
    title: String,
    amount: String,
    share: Float,
    percent: NumberFormat,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 12.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(amount, style = MaterialTheme.typography.titleSmall.tabular())
            Text(percent.format(share), style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun TypeRow(label: String, amount: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            label,
            Modifier.weight(1f),
            style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        )
        Text(amount, style = (if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium).tabular())
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
