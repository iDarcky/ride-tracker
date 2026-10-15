package app.ridetracker.ui.summary

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
    range: DateRange,
    summaries: SummaryRepository,
    income: IncomeRepository,
    imports: ImportRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SummaryUiState())
    val state: StateFlow<SummaryUiState> = _state

    init {
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
    val locale = currentLocale()
    val money = remember(state.currencyCode, locale) { MoneyFormat(resolveCurrency(state.currencyCode), locale) }
    val dates = remember(locale) { DateFormats(locale) }
    val monthly = state.summary?.monthly ?: (Period.Month.containing(range.start).range == range)
    val month = Period.Month.containing(range.start)
    val title = if (monthly) dates.period(month) else stringResource(R.string.summary_week_title, dates.range(range))
    SettingsFrame(title, onBack) {
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
            item {
                Row(gap, verticalAlignment = Alignment.CenterVertically) {
                    val days = summary.missingDays.take(5).joinToString { dates.shortWeekday(it) } + if (summary.missingDays.size > 5) "…" else ""
                    Text(
                        pluralStringResource(R.plurals.summary_missing, summary.missingDays.size, days),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onImport) { Text(stringResource(R.string.import_title)) }
                }
            }
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
