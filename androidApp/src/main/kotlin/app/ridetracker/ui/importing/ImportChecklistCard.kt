package app.ridetracker.ui.importing

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import app.ridetracker.shared.domain.ImportChecklist
import app.ridetracker.ui.common.DateFormats

/** LATER: not needed yet (month still running) or optional (the monthly total covers it). */
private enum class Status { DONE, MISSING, LATER }

/**
 * What a month has from Bolt and what's still missing; each row opens to say where to find it in the Bolt app.
 */
@Composable
fun ImportChecklistCard(c: ImportChecklist, dates: DateFormats, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                stringResource(R.string.checklist_title, dates.period(c.month)),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            ChecklistRow(
                stringResource(R.string.checklist_monthly),
                when {
                    c.hasMonthlyTotal -> stringResource(R.string.checklist_done)
                    !c.monthOver -> stringResource(R.string.checklist_monthly_later)
                    else -> stringResource(R.string.checklist_monthly_missing)
                },
                when {
                    c.hasMonthlyTotal -> Status.DONE
                    !c.monthOver -> Status.LATER
                    else -> Status.MISSING
                },
                stringResource(R.string.checklist_monthly_how),
            )
            ChecklistRow(
                stringResource(R.string.checklist_daily),
                if (c.exactDays == 0 && c.hasMonthlyTotal) {
                    // The month's total is exact already; daily screenshots only add card and cash per day.
                    stringResource(R.string.checklist_daily_optional)
                } else {
                    buildString {
                        append(pluralStringResource(R.plurals.checklist_days_with, c.exactDays, c.exactDays))
                        if (c.daysWithoutScreenshot > 0) {
                            append(" · ")
                            append(pluralStringResource(R.plurals.checklist_days_without, c.daysWithoutScreenshot, c.daysWithoutScreenshot))
                        }
                    }
                },
                when {
                    c.exactDays > 0 && (c.daysWithoutScreenshot == 0 || c.hasMonthlyTotal) -> Status.DONE
                    c.hasMonthlyTotal -> Status.LATER
                    else -> Status.MISSING
                },
                stringResource(R.string.checklist_daily_how),
            )
            ChecklistRow(
                stringResource(R.string.checklist_trips),
                stringResource(if (c.hasTrips) R.string.checklist_done else R.string.checklist_trips_missing),
                if (c.hasTrips) Status.DONE else Status.MISSING,
                stringResource(R.string.checklist_trips_how),
            )
            ChecklistRow(
                stringResource(R.string.checklist_hours),
                stringResource(if (c.hasHours) R.string.checklist_done else R.string.checklist_hours_missing),
                if (c.hasHours) Status.DONE else Status.MISSING,
                stringResource(R.string.checklist_hours_how),
            )
        }
    }
}

/** The same for Uber: the four Supplier portal files it needs, each saying which file it is. */
@Composable
fun UberChecklistCard(c: ImportChecklist, dates: DateFormats, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                stringResource(R.string.checklist_uber_title, dates.period(c.month)),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            ChecklistRow(
                stringResource(R.string.checklist_uber_payments),
                if (c.exactDays > 0) pluralStringResource(R.plurals.checklist_uber_days, c.exactDays, c.exactDays)
                else stringResource(R.string.checklist_uber_payments_missing),
                if (c.exactDays > 0) Status.DONE else Status.MISSING,
                stringResource(R.string.checklist_uber_payments_how),
            )
            ChecklistRow(
                stringResource(R.string.checklist_uber_totals),
                when {
                    c.hasMonthlyTotal -> stringResource(R.string.checklist_done)
                    !c.monthOver -> stringResource(R.string.checklist_uber_totals_later)
                    else -> stringResource(R.string.checklist_uber_totals_missing)
                },
                when {
                    c.hasMonthlyTotal -> Status.DONE
                    !c.monthOver -> Status.LATER
                    else -> Status.MISSING
                },
                stringResource(R.string.checklist_uber_totals_how),
            )
            ChecklistRow(
                stringResource(R.string.checklist_uber_trips),
                stringResource(if (c.hasTrips) R.string.checklist_done else R.string.checklist_uber_trips_missing),
                if (c.hasTrips) Status.DONE else Status.MISSING,
                stringResource(R.string.checklist_uber_trips_how),
            )
            ChecklistRow(
                stringResource(R.string.checklist_uber_hours),
                stringResource(if (c.hasHours) R.string.checklist_done else R.string.checklist_uber_hours_missing),
                if (c.hasHours) Status.DONE else Status.MISSING,
                stringResource(R.string.checklist_uber_hours_how),
            )
            ChecklistRow(
                stringResource(R.string.checklist_uber_screens),
                stringResource(R.string.checklist_uber_screens_status),
                Status.LATER,
                stringResource(R.string.checklist_uber_screens_how),
            )
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
                modifier = Modifier.padding(start = 36.dp, top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
