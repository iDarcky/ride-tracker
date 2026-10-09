package app.ridetracker.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import app.ridetracker.shared.domain.Period

/**
 * The period as a large title ("October 2026 ▾"). Tapping it lists the months, newest first, and, when
 * [onCustom] is given, "Custom range…" at the end. [selected] gets a check mark.
 */
@Composable
fun MonthTitle(
    title: String,
    months: List<Period.Month>,
    selected: Period.Month?,
    dates: DateFormats,
    onMonth: (Period.Month) -> Unit,
    modifier: Modifier = Modifier,
    onCustom: (() -> Unit)? = null,
    onOpen: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(
            onClick = { onOpen(); open = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.choose_month))
        }
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            months.forEach { m ->
                DropdownMenuItem(
                    text = { Text(dates.period(m)) },
                    onClick = { open = false; onMonth(m) },
                    trailingIcon = if (m == selected) { { Icon(Icons.Filled.Check, contentDescription = null) } } else null,
                )
            }
            if (onCustom != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.custom_range)) },
                    leadingIcon = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
                    onClick = { open = false; onCustom() },
                )
            }
        }
    }
}
