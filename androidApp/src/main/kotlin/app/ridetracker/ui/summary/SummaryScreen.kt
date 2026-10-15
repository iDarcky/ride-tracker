package app.ridetracker.ui.summary

import androidx.compose.ui.platform.LocalResources
import androidx.compose.material3.AssistChip
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import kotlinx.datetime.LocalDate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.R
import app.ridetracker.shared.data.PlatformEntity
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.DrivingType
import app.ridetracker.shared.domain.ImportRepository
import app.ridetracker.shared.domain.IncomeRepository
import app.ridetracker.shared.domain.MonthEndStatus
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.Summary
import app.ridetracker.shared.domain.SummaryRepository
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.PlatformBadge
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.common.tabular
import app.ridetracker.ui.overview.MayStillChangeChip
import app.ridetracker.ui.overview.RealHourlyCard
import app.ridetracker.ui.overview.WeekPayoutCard
import app.ridetracker.ui.settings.SettingsFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.math.abs
import kotlin.time.Clock

data class SummaryUiState(
    val summary: Summary? = null,
    val platforms: List<PlatformEntity> = emptyList(),
    val currencyCode: String? = null,
    val fleet: Boolean = false,
    /** The month's files (monthly only): "May still change" until they're in. */
    val monthEnd: MonthEndStatus? = null,
)

class SummaryViewModel(
    private val range: DateRange,
    private val summaries: SummaryRepository,
    private val income: IncomeRepository,
    private val imports: ImportRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SummaryUiState())
    val state: StateFlow<SummaryUiState> = _state

    init {
        load()
    }

    /** Marks a missing day as "Didn't drive" (or undoes it); the summary is worked out again. */
    fun setDayOff(day: LocalDate, off: Boolean) {
        viewModelScope.launch {
            imports.setDayOff(day, off)
            load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val s = settings.settings.first()
            val summary = summaries.summary(range, today)
            _state.value = SummaryUiState(
                summary = summary,
                platforms = income.observePlatforms().first(),
                currencyCode = s.currencyCode,
                fleet = s.drivingType == DrivingType.FLEET,
                monthEnd = if (summary.monthly) imports.observeMonthEnd(Period.Month.containing(range.start)).first() else null,
            )
            if (summary.monthly) settings.setSummarySeenMonth(range.start.toEpochDays())
        }
    }
}

/**
 * A finished week or month to read and check (owner, 15 Oct 2026): money kept and the change, money per hour after
 * fuel, days driven and off, the best day, each app, and the week's payout. A month also opens its Wrapped.
 */
@Composable
fun SummaryScreen(
    range: DateRange,
    onBack: () -> Unit,
    onOpenWrapped: (DateRange) -> Unit,
    onImport: () -> Unit,
    onImportMonth: () -> Unit,
    viewModel: SummaryViewModel = viewModel(key = range.toString()) {
        SummaryViewModel(
            range, SummaryRepository(container.incomeRepository, container.expenseRepository, container.importRepository),
            container.incomeRepository, container.importRepository, container.settingsRepository,
        )
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoText = stringResource(R.string.undo)
    val resources = LocalResources.current
    val locale = currentLocale()
    val money = remember(state.currencyCode, locale) { MoneyFormat(resolveCurrency(state.currencyCode), locale) }
    val dates = remember(locale) { DateFormats(locale) }
    val monthly = state.summary?.monthly ?: (Period.Month.containing(range.start).range == range)
    val month = Period.Month.containing(range.start)
    val title = if (monthly) dates.period(month) else stringResource(R.string.summary_week_title, dates.range(range))
    // A day marked as a day off leaves the missing list; Undo puts it back.
    val markOff: (LocalDate) -> Unit = { day ->
        viewModel.setDayOff(day, true)
        scope.launch {
            val text = resources.getString(R.string.day_marked_off, dates.shortWeekday(day))
            if (snackbar.showSnackbar(text, actionLabel = undoText, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                viewModel.setDayOff(day, false)
            }
        }
    }
    SettingsFrame(title, onBack, snackbar) {
        val summary = state.summary ?: return@SettingsFrame
        val gap = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        if (monthly) {
            item {
                Column(gap, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.monthEnd?.let { !it.complete && it.apps.isNotEmpty() } == true) MayStillChangeChip(onImportMonth)
                    Button(onClick = { onOpenWrapped(range) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.summary_wrapped))
                    }
                }
            }
        }
        item { KeptCard(summary, money, dates, month, gap) }
        if (summary.missingDays.isNotEmpty()) {
            item { MissingDays(summary, dates, onImport, markOff, gap) }
        }
        summary.real?.let { real -> item { Column(gap) { RealHourlyCard(real, money) } } }
        item {
            OutlinedCard(gap.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.summary_days), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        listOfNotNull(
                            pluralStringResource(R.plurals.summary_days_driven, summary.daysDriven, summary.daysDriven),
                            summary.daysOff.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.summary_days_off, it, it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    summary.bestDay?.let { best ->
                        Text(
                            stringResource(R.string.summary_best_day, dates.shortWeekday(best.date), money.format(best.totalMinor)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (summary.apps.isNotEmpty()) {
            item {
                OutlinedCard(gap.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.summary_by_app), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        summary.apps.forEach { app ->
                            val platform = state.platforms.firstOrNull { it.id == app.platformId } ?: return@forEach
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlatformBadge(platform.name, platform.colorArgb, size = 28.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(platform.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(money.format(app.incomeMinor), style = MaterialTheme.typography.titleMedium.tabular(), fontWeight = FontWeight.SemiBold)
                                    app.perHourMinor?.let {
                                        Text(
                                            stringResource(R.string.per_hour_value, money.format(it)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        if (summary.monthDifferenceMinor != 0L) {
                            HorizontalDivider()
                            Text(
                                stringResource(R.string.month_difference_added, money.format(summary.monthDifferenceMinor)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        summary.payout?.let { payout ->
            item {
                Column(gap) {
                    WeekPayoutCard(payout, state.platforms, state.fleet, money, title = stringResource(R.string.summary_payout), showNote = false)
                }
            }
        }
    }
}

/** Money kept, big, with the change from the week or month before. */
@Composable
private fun KeptCard(summary: Summary, money: MoneyFormat, dates: DateFormats, month: Period.Month, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(stringResource(R.string.money_kept), style = MaterialTheme.typography.labelLarge)
            Text(
                money.format(summary.keptMinor),
                style = MaterialTheme.typography.displaySmall.tabular(),
                fontWeight = FontWeight.SemiBold,
            )
            summary.previousKeptMinor?.let { previous ->
                val change = summary.keptMinor - previous
                val sign = if (change >= 0) "+" else "−"
                val versus = if (summary.monthly) stringResource(R.string.summary_vs_month, dates.monthName(month.previous())) else stringResource(R.string.summary_vs_week)
                Text(
                    "$sign${money.format(abs(change))} $versus",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * The days with nothing in, in red, each a chip: tap one to mark it "Didn't drive" and it leaves the list (owner,
 * 15 Oct 2026: a ticked day shouldn't stay); the snackbar's Undo brings it back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissingDays(summary: Summary, dates: DateFormats, onImport: () -> Unit, onMarkOff: (LocalDate) -> Unit, modifier: Modifier) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.summary_missing_count, summary.missingDays.size, summary.missingDays.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onImport) { Text(stringResource(R.string.import_title)) }
            }
            Text(stringResource(R.string.summary_mark_days_off), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.missingDays.forEach { day ->
                    AssistChip(onClick = { onMarkOff(day) }, label = { Text(dates.shortWeekday(day)) })
                }
            }
        }
    }
}
