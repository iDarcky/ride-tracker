package app.ridetracker.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import java.util.Currency

@Composable
fun CurrencyDialog(selected: Currency?, onDismiss: () -> Unit, onSelect: (Currency) -> Unit) {
    val locale = currentLocale()
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, locale) {
        selectableCurrencies.filter {
            query.isBlank() || it.currencyCode.contains(query, ignoreCase = true) ||
                it.getDisplayName(locale).contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.currency)) },
        text = {
            Column {
                Text(stringResource(R.string.currency_no_conversion), modifier = Modifier.padding(bottom = 12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.currencyCode }) { c ->
                        ListItem(
                            modifier = Modifier.selectable(selected = c == selected, role = Role.RadioButton) { onSelect(c) },
                            leadingContent = { RadioButton(selected = c == selected, onClick = null) },
                            headlineContent = { Text(c.currencyCode) },
                            supportingContent = { Text(c.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
