package app.ridetracker.ui.importing

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import app.ridetracker.shared.data.PlatformEntity
import app.ridetracker.shared.domain.MonthEndApp
import app.ridetracker.shared.domain.MonthEndStatus
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.PlatformBadge
import kotlin.math.abs

/** LATER: optional (an extra, not needed to finish the month). */
private enum class Status { DONE, MISSING, LATER }

/**
 * One app's month-end files (owner, 15 Oct 2026: two steps per app): its monthly total and the hours, each saying
 * where to find it and what it adds; then the difference between the monthly total and the month's days, added with
 * one tap.
 */
@Composable
fun MonthEndCard(
    app: MonthEndApp,
    platform: PlatformEntity,
    status: MonthEndStatus,
    money: MoneyFormat,
    dates: DateFormats,
    onAddDifference: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uber = platform.name.trim().equals("Uber", ignoreCase = true)
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PlatformBadge(platform.name, platform.colorArgb, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(if (uber) R.string.checklist_uber_title else R.string.checklist_title, dates.period(status.month)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                val done = listOf(app.hasMonthlyTotal, app.hasHours).count { it }
                Text(
                    stringResource(R.string.month_steps, done, 2),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (app.complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ChecklistRow(
                stringResource(if (uber) R.string.checklist_uber_totals else R.string.month_total),
                stringResource(if (app.hasMonthlyTotal) R.string.checklist_done else if (uber) R.string.month_uber_totals_adds else R.string.month_total_adds),
                if (app.hasMonthlyTotal) Status.DONE else Status.MISSING,
                stringResource(if (uber) R.string.month_uber_totals_how else R.string.checklist_monthly_how),
            )
            ChecklistRow(
                stringResource(if (uber) R.string.checklist_uber_hours else R.string.checklist_hours),
                stringResource(if (app.hasHours) R.string.checklist_done else if (uber) R.string.month_uber_hours_adds else R.string.month_hours_adds),
                if (app.hasHours) Status.DONE else Status.MISSING,
                stringResource(if (uber) R.string.month_uber_hours_how else R.string.checklist_hours_how),
            )
            app.differenceMinor?.let { difference ->
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            if (difference > 0) R.string.month_difference_more else R.string.month_difference_less,
                            platform.name, money.format(abs(difference)),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilledTonalButton(onClick = { onAddDifference(difference) }) {
                        Text(stringResource(if (difference > 0) R.string.month_difference_add else R.string.month_difference_match))
                    }
                }
            }
            if (app.addedMinor != 0L) {
                Text(
                    stringResource(R.string.month_difference_added, money.format(app.addedMinor)),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Files that aren't needed to finish a month, folded away: trips and Uber's other ways in. */
@Composable
fun MonthEndExtras(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.animateContentSize().padding(vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.month_extras), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.month_extras_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            }
            if (open) {
                ChecklistRow(stringResource(R.string.checklist_trips), stringResource(R.string.month_extras_bolt_trips), Status.LATER, stringResource(R.string.checklist_trips_how))
                ChecklistRow(stringResource(R.string.checklist_uber_payments), stringResource(R.string.month_extras_uber_payments), Status.LATER, stringResource(R.string.checklist_uber_payments_how))
                ChecklistRow(stringResource(R.string.checklist_uber_trips), stringResource(R.string.month_extras_uber_trips), Status.LATER, stringResource(R.string.checklist_uber_trips_how))
                ChecklistRow(stringResource(R.string.checklist_uber_screens), stringResource(R.string.checklist_uber_screens_status), Status.LATER, stringResource(R.string.checklist_uber_screens_how))
            }
        }
    }
}

@Composable
private fun ChecklistRow(title: String, status: String, state: Status, how: String) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val icon: ImageVector = when (state) {
        Status.DONE -> Icons.Filled.CheckCircle
        Status.LATER -> Icons.Outlined.RemoveCircleOutline
        Status.MISSING -> Icons.Outlined.RadioButtonUnchecked
    }
    Column(
        Modifier.fillMaxWidth().clickable { open = !open }.animateContentSize().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (state == Status.DONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = stringResource(R.string.checklist_how),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            Text(
                how,
                modifier = Modifier.padding(start = 36.dp, top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
