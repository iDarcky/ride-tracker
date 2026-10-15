package app.ridetracker.ui.summary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import app.ridetracker.RideTrackerApplication
import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.SummaryRepository
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.settings.SettingsFrame
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Every finished month and the last 12 weeks that have income, newest first; each opens its summary. */
@Composable
fun SummariesScreen(onBack: () -> Unit, onOpen: (DateRange) -> Unit) {
    val container = (LocalContext.current.applicationContext as RideTrackerApplication).container
    val dates = DateFormats(currentLocale())
    var lists by remember { mutableStateOf<Pair<List<Period.Week>, List<Period.Month>>?>(null) }
    LaunchedEffect(Unit) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val firstDay = container.settingsRepository.settings.first().firstDayOfWeek
        lists = SummaryRepository(container.incomeRepository, container.expenseRepository, container.importRepository)
            .available(today, firstDay)
    }
    SettingsFrame(stringResource(R.string.summaries), onBack) {
        val (weeks, months) = lists ?: return@SettingsFrame
        if (weeks.isEmpty() && months.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.summaries_empty),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (months.isNotEmpty()) {
            item { Header(stringResource(R.string.summaries_months)) }
            months.forEach { month -> item { Entry(dates.period(month)) { onOpen(month.range) } } }
        }
        if (weeks.isNotEmpty()) {
            item { Header(stringResource(R.string.summaries_weeks)) }
            weeks.forEach { week -> item { Entry(dates.range(week.range)) { onOpen(week.range) } } }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Entry(title: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
    )
}
