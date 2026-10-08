package app.ridetracker.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import app.ridetracker.shared.data.PlatformEntity
import app.ridetracker.shared.domain.HomeWidget
import app.ridetracker.shared.domain.MissingMonthlyTotal
import app.ridetracker.shared.domain.PendingExpense
import app.ridetracker.shared.domain.Period
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.ExpenseBadge
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.PlatformBadge
import app.ridetracker.ui.common.icon
import app.ridetracker.ui.common.label
import app.ridetracker.ui.common.tabular
import app.ridetracker.ui.money.PaymentSplitContent
import java.text.NumberFormat
import kotlinx.datetime.LocalDate

/** The heat map needs enough trips to say anything about when you earn. */
const val MIN_TRIPS_FOR_HEAT = 20

/** Name shown in Customise. */
val HomeWidget.title: Int
    get() = when (this) {
        HomeWidget.BREAKDOWN -> R.string.widget_breakdown
        HomeWidget.METRICS -> R.string.widget_metrics
        HomeWidget.SPLIT -> R.string.split_by_app
        HomeWidget.ACTIVITY -> R.string.daily_activity
        HomeWidget.WHEN_YOU_EARN -> R.string.best_time
        HomeWidget.EXPENSE_GROUPS -> R.string.expenses_by_group
        HomeWidget.NEEDS_ATTENTION -> R.string.needs_attention
        HomeWidget.CASH_CARD -> R.string.card_and_cash
    }

/** Whether the card has something to show for this period (empty cards are left out, except while customising). */
fun HomeWidget.hasContent(state: OverviewUiState, attention: Attention): Boolean {
    val stats = state.stats ?: return false
    return when (this) {
        HomeWidget.BREAKDOWN -> stats.grossMinor > 0
        HomeWidget.METRICS -> stats.incomePlatformIds.isNotEmpty() || stats.tripCount > 0
        HomeWidget.SPLIT -> state.totals.isNotEmpty()
        HomeWidget.ACTIVITY -> state.period !is Period.Day && stats.days.size > 1 &&
            stats.days.any { it.totalMinor > 0 || it.tripCount > 0 }
        HomeWidget.WHEN_YOU_EARN -> stats.tripCount >= MIN_TRIPS_FOR_HEAT
        HomeWidget.EXPENSE_GROUPS -> state.expenseGroups.isNotEmpty()
        HomeWidget.NEEDS_ATTENTION -> !attention.isEmpty()
        HomeWidget.CASH_CARD -> state.breakdown?.payment != null
    }
}

/** One Home card's content. */
@Composable
fun WidgetBody(
    widget: HomeWidget,
    state: OverviewUiState,
    attention: Attention,
    money: MoneyFormat,
    dates: DateFormats,
    percent: NumberFormat,
    onOpenDay: (LocalDate) -> Unit,
    onImport: () -> Unit,
    onAcceptDue: (PendingExpense) -> Unit,
    onSkipDue: (PendingExpense) -> Unit,
) {
    val stats = state.stats ?: return
    when (widget) {
        HomeWidget.BREAKDOWN -> MoneyBreakdown(stats, state.expenseMinor, state.tracksExpenses, money)
        HomeWidget.METRICS -> Metrics(stats, state.platforms, money)
        HomeWidget.SPLIT -> PlatformSplit(state.totals, money)
        HomeWidget.ACTIVITY -> DailyActivity(stats.days, state.platforms, money, dates, onOpenDay, monthly = stats.monthly)
        HomeWidget.WHEN_YOU_EARN -> BestTimeToDrive(stats.heat, money)
        HomeWidget.EXPENSE_GROUPS -> ExpenseGroups(state, money, percent)
        HomeWidget.CASH_CARD -> state.breakdown?.payment?.let { payment ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.card_and_cash),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    PaymentSplitContent(payment, money, percent)
                }
            }
        }
        HomeWidget.NEEDS_ATTENTION -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.needs_attention), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            attention.missingMonthly.forEach { missing ->
                MissingMonthlyCard(missing, state.platforms.firstOrNull { it.id == missing.platformId }, dates, onImport)
            }
            attention.pending.forEach { item ->
                DueExpenseCard(item = item, money = money, dates = dates, onAdd = { onAcceptDue(item) }, onSkip = { onSkipDue(item) })
            }
        }
    }
}

/** A finished month estimated without the platform's monthly total: ask for that screenshot. */
@Composable
private fun MissingMonthlyCard(missing: MissingMonthlyTotal, platform: PlatformEntity?, dates: DateFormats, onImport: () -> Unit) {
    val name = platform?.name.orEmpty()
    OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { if (platform != null) PlatformBadge(platform.name, platform.colorArgb) },
            headlineContent = { Text(stringResource(R.string.missing_monthly_title, name, dates.period(missing.month))) },
            supportingContent = { Text(stringResource(R.string.missing_monthly_body, name)) },
        )
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.End) {
            FilledTonalButton(onClick = onImport) { Text(stringResource(R.string.import_title)) }
        }
    }
}

@Composable
private fun ExpenseGroups(state: OverviewUiState, money: MoneyFormat, percent: NumberFormat) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                stringResource(R.string.expenses_by_group),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            state.expenseGroups.forEach { group ->
                val share = if (state.expenseMinor > 0) group.totalMinor.toFloat() / state.expenseMinor else 0f
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    leadingContent = { ExpenseBadge(group.group.icon) },
                    headlineContent = { Text(stringResource(group.group.label)) },
                    supportingContent = {
                        LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = MaterialTheme.colorScheme.tertiary)
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
    }
}

/**
 * A card while customising: a header row (drag handle, name, remove) above the card itself, no extra border.
 * While dragging, the whole item lifts on a surface so it reads as picked up. [handle] is the drag-handle modifier.
 */
@Suppress("ModifierParameter") // [handle] goes on the drag handle, not on the frame.
@Composable
fun EditFrame(widget: HomeWidget, handle: Modifier, dragging: Boolean, onRemove: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (dragging) 3.dp else 0.dp,
        shadowElevation = if (dragging) 8.dp else 0.dp,
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}, modifier = handle) {
                    Icon(Icons.Filled.DragIndicator, contentDescription = stringResource(R.string.widget_drag, stringResource(widget.title)))
                }
                Text(stringResource(widget.title), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.widget_remove, stringResource(widget.title)))
                }
            }
            content()
        }
    }
}

/** Shown inside a frame when the card has nothing for this period. */
@Composable
fun WidgetPlaceholder() {
    Text(
        stringResource(R.string.widget_empty),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A hidden widget under "Add widget". */
@Composable
fun AddWidgetRow(widget: HomeWidget, onAdd: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = { Text(stringResource(widget.title)) },
        trailingContent = {
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.widget_add, stringResource(widget.title)))
            }
        },
    )
}
