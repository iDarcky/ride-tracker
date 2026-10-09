package app.ridetracker.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.R
import app.ridetracker.shared.domain.ImportKind
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.PlatformBadge
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.common.tabular
import kotlinx.datetime.LocalDate

private const val DASH = "—"

/** One imported trip: what the report says, with a dash for everything it does not include. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailsScreen(
    tripId: Long,
    onBack: () -> Unit,
    viewModel: TripDetailsViewModel = viewModel { TripDetailsViewModel(tripId, container.importRepository, container.settingsRepository) },
) {
    val (trip, currencyCode) = viewModel.state.collectAsStateWithLifecycle().value
    val locale = currentLocale()
    val money = remember(currencyCode, locale) { MoneyFormat(resolveCurrency(currencyCode), locale) }
    val dates = remember(locale) { DateFormats(locale) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trip_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        if (trip == null) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformBadge(trip.platformName, trip.platformColorArgb)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(trip.platformName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        dates.day(LocalDate.fromEpochDays(trip.date)) + " · " + timeOf(trip.startMinute),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(money.format(trip.fareMinor), Modifier.weight(1f), style = MaterialTheme.typography.displaySmall.tabular())
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.trip_completed)) },
                    leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null, Modifier.size(18.dp)) },
                )
            }

            // Uber's reports give what the driver earned on a trip (after Uber's fee), Bolt's what the rider paid.
            val afterFee = trip.importKind in uberTripKinds
            Card(stringResource(R.string.trip_breakdown)) {
                Field(stringResource(R.string.trip_fare), if (afterFee) DASH else money.format(trip.fareMinor))
                Field(stringResource(R.string.trip_platform_fee), DASH)
                Field(stringResource(R.string.trip_driver_earnings), if (afterFee) money.format(trip.fareMinor) else DASH)
                Field(stringResource(R.string.line_tip), DASH)
                Field(stringResource(R.string.line_bonus), DASH)
                Text(
                    stringResource(R.string.trip_dash_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Card(stringResource(R.string.trip_info)) {
                Field(stringResource(R.string.trip_distance), trip.distanceMeters?.let { stringResource(R.string.km_value, "%.1f".format(locale, it / 1000.0)) } ?: DASH)
                Field(stringResource(R.string.trip_duration), trip.durationSeconds?.let { stringResource(R.string.trip_minutes, (it / 60).toInt()) } ?: DASH)
                Field(stringResource(R.string.trip_payment), stringResource(paymentName(trip.paymentMethod)))
                Field(stringResource(R.string.trip_source), stringResource(sourceName(trip.importKind)), divider = false)
            }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.trip_privacy), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private val uberTripKinds = setOf(ImportKind.UBER_PAYMENTS_CSV.id, ImportKind.UBER_TRIPS_CSV.id)

private fun sourceName(kind: String): Int = when (ImportKind.fromId(kind)) {
    ImportKind.BOLT_RIDER_INVOICES_CSV -> R.string.import_bolt_invoices
    ImportKind.UBER_PAYMENTS_CSV -> R.string.import_uber_days
    ImportKind.UBER_TRIPS_CSV -> R.string.import_uber_trips
    else -> R.string.import_title
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Field(label: String, value: String, divider: Boolean = true) {
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = if (divider) 10.dp else 0.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = if (value == DASH) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (divider) HorizontalDivider()
    }
}
