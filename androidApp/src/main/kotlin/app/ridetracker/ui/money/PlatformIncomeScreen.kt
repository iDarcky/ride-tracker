package app.ridetracker.ui.money

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.R
import app.ridetracker.shared.data.PlatformEntity
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.domain.ImportRepository
import app.ridetracker.shared.domain.IncomeBreakdown
import app.ridetracker.shared.domain.IncomeBreakdownCalculator
import app.ridetracker.shared.domain.IncomeRepository
import app.ridetracker.shared.domain.Period
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.PlatformBadge
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.common.tabular
import java.text.NumberFormat
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

data class PlatformIncomeState(
    val platform: PlatformEntity? = null,
    val breakdown: IncomeBreakdown? = null,
    val currencyCode: String? = null,
)

/** One platform's income in one month: what it's made of and how it was paid. */
class PlatformIncomeViewModel(
    platformId: Long,
    val month: Period.Month,
    incomeRepository: IncomeRepository,
    importRepository: ImportRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<PlatformIncomeState> = combine(
        combine(
            incomeRepository.observeEntryDetails(month.range),
            incomeRepository.observeLines(month.range),
            importRepository.observeTrips(month.range),
            importRepository.observeSummaries(month.range),
        ) { entries, lines, trips, summaries ->
            IncomeBreakdownCalculator.compute(month.range, entries, lines, trips, summaries, platformId)
        },
        incomeRepository.observePlatforms(),
        settingsRepository.settings,
    ) { breakdown, platforms, settings ->
        PlatformIncomeState(platforms.firstOrNull { it.id == platformId }, breakdown, settings.currencyCode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlatformIncomeState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformIncomeScreen(
    platformId: Long,
    monthStart: LocalDate,
    onBack: () -> Unit,
    viewModel: PlatformIncomeViewModel = viewModel {
        PlatformIncomeViewModel(
            platformId, Period.Month.containing(monthStart),
            container.incomeRepository, container.importRepository, container.settingsRepository,
        )
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val money = remember(state.currencyCode, locale) { MoneyFormat(resolveCurrency(state.currencyCode), locale) }
    val dates = remember(locale) { DateFormats(locale) }
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val platform = state.platform
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.platform_income_title, platform?.name.orEmpty())) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        val b = state.breakdown ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            OutlinedCard(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (platform != null) {
                        PlatformBadge(platform.name, platform.colorArgb)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column {
                        Text(dates.period(viewModel.month), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money.format(b.earnedMinor), style = MaterialTheme.typography.headlineMedium.tabular())
                        if (b.estimatedMinor != 0L) {
                            Text(
                                stringResource(R.string.includes_estimated, money.format(b.estimatedMinor)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (b.parts.isNotEmpty()) MadeOfCard(b, money)
            b.payment?.let { BreakdownCard(stringResource(R.string.card_and_cash)) { PaymentSplitContent(it, money, percent) } }
        }
    }
}
