package app.ridetracker.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.R
import app.ridetracker.ui.common.GlassDropdownMenu
import app.ridetracker.ui.common.ScrollToTopOnReselect
import app.ridetracker.shared.data.TripWithPlatform
import app.ridetracker.shared.domain.PaymentMethod
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.LocalBottomBarSpace
import app.ridetracker.ui.common.MenuButton
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.PlatformBadge
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.common.tabular

/** Day groups show this many trips until "Show all" is tapped. */
private const val COLLAPSED_TRIPS = 5

/** Imported trips by month: app and payment filters, a summary, then one card per day. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TripsScreen(
    onOpenTrip: (Long) -> Unit,
    onImport: () -> Unit,
    viewModel: TripsViewModel = viewModel { TripsViewModel(container.importRepository, container.settingsRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val money = remember(state.currencyCode, locale) { MoneyFormat(resolveCurrency(state.currencyCode), locale) }
    val dates = remember(locale) { DateFormats(locale) }
    var expandedDays by rememberSaveable { mutableStateOf(setOf<Long>()) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_trips)) }, actions = { MenuButton() }) },
    ) { padding ->
        if (!state.loading && !state.hasAnyTrips) {
            EmptyTrips(onImport, Modifier.padding(padding).padding(bottom = LocalBottomBarSpace.current))
            return@Scaffold
        }
        val listState = rememberLazyListState()
        ScrollToTopOnReselect(listState)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + LocalBottomBarSpace.current + 24.dp,
            ),
        ) {
            if (state.platforms.size >= 2) {
                item { PlatformSelector(state, viewModel::selectPlatform) }
            }
            item { FilterRow(state, dates, viewModel::selectMonth, viewModel::selectPayment) }
            item { Summary(state, money) }
            state.days.forEach { day ->
                val key = day.date.toEpochDays()
                stickyHeader(key = "h$key") { DayHeader(day, money, dates) }
                item(key = "d$key") {
                    val expanded = key in expandedDays
                    DayCard(
                        day = day,
                        expanded = expanded,
                        money = money,
                        onOpenTrip = onOpenTrip,
                        onShowAll = { expandedDays = expandedDays + key },
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.trips_footnote),
                    modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlatformSelector(state: TripsUiState, onSelect: (Long?) -> Unit) {
    val options: List<Pair<Long?, String>> = listOf(null to stringResource(R.string.trips_all)) + state.platforms.map { it.id to it.name }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (id, label) ->
            ToggleButton(
                checked = state.platformId == id,
                onCheckedChange = { onSelect(id) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun FilterRow(
    state: TripsUiState,
    dates: DateFormats,
    onMonth: (app.ridetracker.shared.domain.Period.Month) -> Unit,
    onPayment: (PaymentFilter) -> Unit,
) {
    var monthMenu by remember { mutableStateOf(false) }
    var paymentMenu by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            FilterChip(
                selected = false,
                onClick = { monthMenu = true },
                label = { Text(state.month?.let { dates.period(it) }.orEmpty()) },
                leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null, Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            )
            GlassDropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }) {
                state.months.forEach { month ->
                    DropdownMenuItem(
                        text = { Text(dates.period(month)) },
                        onClick = { monthMenu = false; onMonth(month) },
                        trailingIcon = if (month == state.month) { { Icon(Icons.Filled.Check, contentDescription = null) } } else null,
                    )
                }
            }
        }
        Box {
            FilterChip(
                selected = state.payment != PaymentFilter.ALL,
                onClick = { paymentMenu = true },
                label = { Text(stringResource(paymentLabel(state.payment))) },
                leadingIcon = { Icon(Icons.Outlined.FilterList, contentDescription = null, Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            )
            GlassDropdownMenu(expanded = paymentMenu, onDismissRequest = { paymentMenu = false }) {
                PaymentFilter.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(if (option == PaymentFilter.ALL) R.string.trips_all_payments else paymentLabel(option))) },
                        onClick = { paymentMenu = false; onPayment(option) },
                        trailingIcon = if (option == state.payment) { { Icon(Icons.Filled.Check, contentDescription = null) } } else null,
                    )
                }
            }
        }
    }
}

private fun paymentLabel(filter: PaymentFilter): Int = when (filter) {
    PaymentFilter.ALL -> R.string.trips_filters
    PaymentFilter.CASH -> R.string.payment_cash
    PaymentFilter.IN_APP -> R.string.payment_in_app
}

@Composable
private fun Summary(state: TripsUiState, money: MoneyFormat) {
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(R.plurals.trip_count, state.tripCount, state.tripCount),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall.tabular(),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(money.format(state.totalMinor), style = MaterialTheme.typography.titleMedium.tabular())
                Text(
                    stringResource(R.string.trips_total_fares),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DayHeader(day: TripDay, money: MoneyFormat, dates: DateFormats) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(top = 12.dp, bottom = 6.dp, start = 4.dp, end = 4.dp),
    ) {
        Text(
            dates.day(day.date),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            money.format(day.totalMinor) + " · " + pluralStringResource(R.plurals.trip_count, day.trips.size, day.trips.size),
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayCard(day: TripDay, expanded: Boolean, money: MoneyFormat, onOpenTrip: (Long) -> Unit, onShowAll: () -> Unit) {
    val shown = if (expanded) day.trips else day.trips.take(COLLAPSED_TRIPS)
    OutlinedCard(Modifier.fillMaxWidth()) {
        shown.forEachIndexed { index, trip ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 72.dp))
            TripRow(trip, money, onClick = { onOpenTrip(trip.id) })
        }
        if (shown.size < day.trips.size) {
            HorizontalDivider()
            TextButton(onClick = onShowAll, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(pluralStringResource(R.plurals.trips_show_all, day.trips.size, day.trips.size))
            }
        }
    }
}

@Composable
private fun TripRow(trip: TripWithPlatform, money: MoneyFormat, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformBadge(trip.platformName, trip.platformColorArgb)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(trip.platformName, style = MaterialTheme.typography.bodyLarge)
            Text(
                timeOf(trip.startMinute) + " · " + stringResource(paymentName(trip.paymentMethod)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money.format(trip.fareMinor), style = MaterialTheme.typography.titleMedium.tabular())
            Text(
                distanceAndDuration(trip),
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyTrips(onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Route, contentDescription = null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.trips_empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.trips_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Outlined.UploadFile, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.import_title))
        }
    }
}

/** 20:11 */
fun timeOf(startMinute: Int): String =
    (startMinute / 60).toString().padStart(2, '0') + ":" + (startMinute % 60).toString().padStart(2, '0')

fun paymentName(id: String): Int = when (PaymentMethod.fromId(id)) {
    PaymentMethod.CASH -> R.string.payment_cash
    PaymentMethod.IN_APP -> R.string.payment_in_app
    PaymentMethod.BUSINESS -> R.string.payment_business
}

/** "6.0 km · 30 min", with dashes for what the report does not have. */
@Composable
fun distanceAndDuration(trip: TripWithPlatform): String {
    val locale = currentLocale()
    val km = trip.distanceMeters?.let { "%.1f".format(locale, it / 1000.0) } ?: "—"
    val min = trip.durationSeconds?.let { (it / 60).toString() } ?: "—"
    return stringResource(R.string.trip_km_min, km, min)
}
