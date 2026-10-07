package app.ridetracker.ui.money

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.R
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.EntryRow
import app.ridetracker.ui.common.ExpenseRow
import app.ridetracker.ui.common.LocalBottomBarSpace
import app.ridetracker.ui.common.MenuButton
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.SectionHeader
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.resolveCurrency
import kotlinx.coroutines.launch

/** Income and expenses in one tab, split by a tab row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyScreen(
    onAddIncome: () -> Unit,
    onEditIncome: (Long) -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (Long) -> Unit,
    viewModel: IncomeListViewModel = viewModel { IncomeListViewModel(container.incomeRepository, container.settingsRepository) },
    expenseViewModel: ExpenseListViewModel = viewModel { ExpenseListViewModel(container.expenseRepository, container.settingsRepository) },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = if (tab == 0) onAddIncome else onAddExpense,
                modifier = Modifier.padding(bottom = bottomSpace),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(if (tab == 0) R.string.add_income else R.string.add_expense)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomSpace)) },
    ) { padding ->
        when (tab) {
            0 -> IncomeList(viewModel, snackbar, onEditIncome, Modifier.padding(top = padding.calculateTopPadding()))
            else -> ExpenseList(expenseViewModel, snackbar, onEditExpense, Modifier.padding(top = padding.calculateTopPadding()))
        }
    }
}

@Composable
private fun IncomeList(
    viewModel: IncomeListViewModel,
    snackbar: SnackbarHostState,
    onEditIncome: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val money = remember(state.currencyCode, locale) { MoneyFormat(resolveCurrency(state.currencyCode), locale) }
    val dates = remember(locale) { DateFormats(locale) }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.entry_deleted)
    val undoLabel = stringResource(R.string.undo)

    if (!state.loading && state.days.isEmpty()) {
        Text(
            stringResource(R.string.no_income_yet),
            modifier = modifier.fillMaxWidth().padding(32.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalBottomBarSpace.current + 96.dp),
    ) {
        state.days.forEach { day ->
            item(key = "day-${day.date}") {
                SectionHeader(dates.day(day.date), trailing = money.format(day.totalMinor))
            }
            items(day.entries, key = { "entry-${it.id}" }) { entry ->
                EntryRow(
                    entry = entry,
                    money = money,
                    onClick = { onEditIncome(entry.id) },
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
    }
}

@Composable
private fun ExpenseList(
    viewModel: ExpenseListViewModel,
    snackbar: SnackbarHostState,
    onEditExpense: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val money = remember(state.currencyCode, locale) { MoneyFormat(resolveCurrency(state.currencyCode), locale) }
    val dates = remember(locale) { DateFormats(locale) }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.expense_deleted)
    val undoLabel = stringResource(R.string.undo)

    if (!state.loading && state.days.isEmpty()) {
        Text(
            stringResource(R.string.no_expenses_yet),
            modifier = modifier.fillMaxWidth().padding(32.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalBottomBarSpace.current + 96.dp),
    ) {
        state.days.forEach { day ->
            item(key = "day-${day.date}") {
                SectionHeader(dates.day(day.date), trailing = money.format(-day.totalMinor))
            }
            items(day.expenses, key = { "expense-${it.id}" }) { expense ->
                ExpenseRow(
                    expense = expense,
                    money = money,
                    onClick = { onEditExpense(expense.id) },
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
    }
}
