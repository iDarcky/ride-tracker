package app.ridetracker.ui.common

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
    SwipeToDeleteRow(
        leading = { PlatformBadge(entry.platformName, entry.platformColorArgb) },
        title = entry.platformName,
        note = entry.note,
        amount = money.format(entry.amountMinor),
        onClick = onClick,
        onDelete = onDelete,
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
    SwipeToDeleteRow(
        leading = { ExpenseBadge(category.icon) },
        title = stringResource(category.label),
        note = expense.note,
        amount = money.format(-expense.amountMinor),
        onClick = onClick,
        onDelete = onDelete,
        modifier = modifier,
    )
}

/** List row that deletes on swipe left (the screen shows an Undo snackbar). */
@Composable
fun SwipeToDeleteRow(
    leading: @Composable () -> Unit,
    title: String,
    note: String?,
    amount: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    // The list keeps each row's swipe state by key. After Undo the row comes back with that saved
    // "swiped away" state: put it back in place instead of deleting the entry a second time.
    val cameBackSwiped = remember { dismissState.currentValue == SwipeToDismissBoxValue.EndToStart }
    // React to where the swipe ends up, so both slow drags and quick flings delete.
    LaunchedEffect(dismissState.currentValue) {
        when {
            dismissState.currentValue != SwipeToDismissBoxValue.EndToStart -> Unit
            cameBackSwiped -> dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            else -> onDelete()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
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
            leadingContent = leading,
            headlineContent = { Text(title) },
            supportingContent = note?.let { { Text(it, maxLines = 2) } },
            trailingContent = { Text(amount, style = MaterialTheme.typography.titleMedium.tabular()) },
        )
    }
}

