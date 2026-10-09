package app.ridetracker.ui.common

import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.ridetracker.R

/** Single-choice dialog with radio buttons; picking an option confirms it. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    description: (@Composable (T) -> String?)? = null,
) {
    AlertDialog(
        modifier = Modifier.clip(AlertDialogDefaults.shape).glass(GLASS_DIALOG),
        containerColor = glassContainer(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    ListItem(
                        modifier = Modifier.selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        leadingContent = { RadioButton(selected = option == selected, onClick = null) },
                        headlineContent = { Text(label(option), style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = description?.invoke(option)?.let { { Text(it) } },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Yes/no confirmation; [destructive] colours the confirm button as an error. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        modifier = Modifier.clip(AlertDialogDefaults.shape).glass(GLASS_DIALOG),
        containerColor = glassContainer(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
