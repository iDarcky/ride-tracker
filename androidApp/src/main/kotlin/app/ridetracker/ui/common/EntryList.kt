package app.ridetracker.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.R
import app.ridetracker.shared.domain.IncomeSource
import app.ridetracker.shared.data.EntryWithPlatform
import app.ridetracker.shared.data.ExpenseEntity
import app.ridetracker.shared.domain.ExpenseCategory
import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.PeriodType
import app.ridetracker.shared.domain.type
import kotlinx.coroutines.launch
import java.text.NumberFormat

@Composable
fun SectionHeader(title: String, trailing: String? = null) {
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
fun EntryRow(
    entry: EntryWithPlatform,
    money: MoneyFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionRow(
        leading = { PlatformBadge(entry.platformName, entry.platformColorArgb) },
        title = entry.platformName,
        note = if (entry.source == IncomeSource.ESTIMATE.id) {
            listOfNotNull(stringResource(R.string.estimated), entry.note).joinToString(" · ")
        } else {
            entry.note
        },
        amount = money.format(entry.amountMinor),
        onClick = onClick,
        onDelete = onDelete,
        deleteTitle = stringResource(R.string.delete_entry_title),
        modifier = modifier,
    )
}

@Composable
fun ExpenseRow(
    expense: ExpenseEntity,
    money: MoneyFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = ExpenseCategory.fromId(expense.category)
    ActionRow(
        leading = { ExpenseBadge(category.icon) },
        title = stringResource(category.label),
        note = expense.note,
        amount = money.format(-expense.amountMinor),
        onClick = onClick,
        onDelete = onDelete,
        deleteTitle = stringResource(R.string.delete_expense_title),
        modifier = modifier,
    )
}

/**
 * List row: tap opens it; long press opens a menu with Edit and Delete (Delete asks first; the screen then offers
 * Undo). No swipe, so a row can't be deleted by accident while scrolling.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionRow(
    leading: @Composable () -> Unit,
    title: String,
    note: String?,
    amount: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    deleteTitle: String,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = onClick,
) {
    var menu by remember { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(modifier) {
        ListItem(
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu = true
                },
                onLongClickLabel = stringResource(R.string.more_actions),
            ),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            leadingContent = leading,
            headlineContent = { Text(title) },
            supportingContent = note?.let { { Text(it, maxLines = 2) } },
            trailingContent = { Text(amount, style = MaterialTheme.typography.titleMedium.tabular()) },
        )
        GlassDropdownMenu(expanded = menu, onDismissRequest = { menu = false }, offset = DpOffset(16.dp, 0.dp)) {
            if (onEdit != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = { menu = false; onEdit() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = { menu = false; confirm = true },
            )
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = deleteTitle,
            body = listOfNotNull(title, amount.takeIf { it.isNotEmpty() }, note).joinToString(" · "),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onDismiss = { confirm = false },
            onConfirm = { confirm = false; onDelete() },
        )
    }
}

