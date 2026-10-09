package app.ridetracker.ui.vehicle

import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.AlertDialogDefaults
import app.ridetracker.ui.common.GLASS_DIALOG
import app.ridetracker.ui.common.glassContainer
import app.ridetracker.ui.common.glass
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.R
import app.ridetracker.ui.common.ScrollToTopOnReselect
import app.ridetracker.shared.data.OdometerReadingEntity
import app.ridetracker.shared.domain.FuelType
import app.ridetracker.shared.domain.Money
import app.ridetracker.ui.common.ConfirmDialog
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.ExpenseBadge
import app.ridetracker.ui.common.LocalBottomBarSpace
import app.ridetracker.ui.common.MenuButton
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.SectionHeader
import app.ridetracker.ui.common.ActionRow
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.icon
import app.ridetracker.ui.common.label
import app.ridetracker.ui.common.pickerMillisToLocalDate
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.common.tabular
import app.ridetracker.ui.common.toPickerMillis
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.text.NumberFormat
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(
    onEditVehicle: () -> Unit,
    viewModel: VehicleViewModel = viewModel {
        VehicleViewModel(container.vehicleRepository, container.expenseRepository, container.settingsRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val money = remember(state.currencyCode, locale) { MoneyFormat(resolveCurrency(state.currencyCode), locale) }
    val dates = remember(locale) { DateFormats(locale) }
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var updateOdometer by rememberSaveable { mutableStateOf(false) }
    var deleteReadingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val deletedText = stringResource(R.string.reading_deleted)
    val undoText = stringResource(R.string.undo)
    val bottomSpace = LocalBottomBarSpace.current

    fun deleteWithUndo(id: Long) {
        scope.launch {
            val deleted = viewModel.deleteReading(id) ?: return@launch
            val result = snackbar.showSnackbar(deletedText, undoText, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreReading(deleted)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_vehicle)) },
                actions = {
                    if (state.vehicle != null) {
                        IconButton(onClick = onEditVehicle) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit_vehicle)) }
                    }
                    MenuButton()
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomSpace)) },
    ) { padding ->
        val vehicle = state.vehicle
        if (!state.loading && vehicle == null) {
            NoVehicle(onEditVehicle, Modifier.padding(padding).padding(bottom = bottomSpace))
            return@Scaffold
        }
        if (vehicle == null) return@Scaffold
        val fuel = FuelType.fromId(vehicle.fuelType)
        val listState = rememberLazyListState()
        ScrollToTopOnReselect(listState)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = bottomSpace + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // Car card
                OutlinedCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    ListItem(
                        leadingContent = { ExpenseBadge(Icons.Outlined.DirectionsCar, size = 56.dp) },
                        headlineContent = { Text(vehicle.name, style = MaterialTheme.typography.titleLarge) },
                        supportingContent = {
                            val consumption = vehicle.consumptionCenti?.let {
                                // Stored ×100: 700 → "7", 650 → "6.5".
                                Money.toPlainString(it, 2).trimEnd('0').trimEnd('.').let { v -> "$v ${if (fuel.isElectric) "kWh" else "L"}/100 km" }
                            }
                            Text(listOfNotNull(vehicle.year?.toString(), fuelName(fuel), consumption).joinToString(" · "))
                        },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
            item {
                // Odometer card
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Text(stringResource(R.string.odometer), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        val latest = state.latest
                        Text(
                            latest?.let { stringResource(R.string.km_value, numbers.format(it.km)) } ?: stringResource(R.string.odometer_none),
                            style = MaterialTheme.typography.displaySmall.tabular(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        latest?.let {
                            Text(
                                stringResource(R.string.odometer_updated, dates.day(LocalDate.fromEpochDays(it.date))),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        // Filled primary on the light primary card: no grey tonal button.
                        Button(onClick = { updateOdometer = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Speed, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.update_odometer))
                        }
                    }
                }
            }
            item {
                // Month navigator
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::previousMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_period))
                    }
                    Text(
                        dates.period(state.month),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = viewModel::nextMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_period))
                    }
                }
            }
            item {
                OutlinedCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Metric(stringResource(R.string.km_driven), state.kmDriven?.let { stringResource(R.string.km_value, numbers.format(it)) })
                        if (state.kmDriven == null) {
                            Text(
                                stringResource(R.string.needs_two_readings),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Metric(stringResource(R.string.vehicle_costs), money.format(state.vehicleCostMinor))
                        Metric(stringResource(R.string.cost_per_km), state.costPerKmMinor?.let { money.format(it) })
                        Metric(stringResource(R.string.fuel_spent), money.format(state.fuelSpentMinor))
                        Metric(stringResource(R.string.fuel_estimate), state.fuelEstimateMinor?.let { money.format(it) })
                    }
                }
            }
            if (state.byCategory.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.vehicle_costs_by_category)) }
                items(state.byCategory, key = { "cat-${it.category.id}" }) { total ->
                    ListItem(
                        leadingContent = { ExpenseBadge(total.category.icon) },
                        headlineContent = { Text(stringResource(total.category.label)) },
                        trailingContent = { Text(money.format(total.totalMinor), style = MaterialTheme.typography.titleMedium.tabular()) },
                    )
                }
            }
            if (state.readings.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.odometer_history)) }
                items(state.readings, key = { "reading-${it.id}" }) { reading ->
                    ActionRow(
                        leading = { ExpenseBadge(Icons.Outlined.Speed) },
                        title = stringResource(R.string.km_value, numbers.format(reading.km)),
                        note = dates.day(LocalDate.fromEpochDays(reading.date)),
                        amount = "",
                        onClick = { deleteReadingId = reading.id },
                        onDelete = { deleteWithUndo(reading.id) },
                        deleteTitle = stringResource(R.string.delete_reading_title),
                        onEdit = null,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    deleteReadingId?.let { id ->
        val reading = state.readings.firstOrNull { it.id == id }
        ConfirmDialog(
            title = stringResource(R.string.delete_reading_title),
            // Show which reading; Undo is offered afterwards, so no "can't be undone" warning.
            body = reading?.let {
                stringResource(R.string.km_value, numbers.format(it.km)) + " · " + dates.day(LocalDate.fromEpochDays(it.date))
            } ?: "",
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onDismiss = { deleteReadingId = null },
            onConfirm = {
                deleteReadingId = null
                deleteWithUndo(id)
            },
        )
    }

    if (updateOdometer) {
        OdometerDialog(
            latest = state.latest,
            onDismiss = { updateOdometer = false },
            onSave = { date, km ->
                when (viewModel.addReading(date, km)) {
                    VehicleViewModel.ReadingResult.SAVED -> null.also { updateOdometer = false }
                    VehicleViewModel.ReadingResult.OUT_OF_ORDER -> R.string.odometer_out_of_order
                    VehicleViewModel.ReadingResult.IMPLAUSIBLE -> R.string.odometer_implausible
                }
            },
        )
    }
}

/** One line of the month summary; a missing value shows as a dash, as the brief requires. */
@Composable
private fun Metric(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value ?: "—", style = MaterialTheme.typography.titleMedium.tabular())
    }
}

@Composable
private fun NoVehicle(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.DirectionsCar, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(
            stringResource(R.string.no_vehicle_body),
            modifier = Modifier.padding(vertical = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAdd) { Text(stringResource(R.string.add_vehicle)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OdometerDialog(latest: OdometerReadingEntity?, onDismiss: () -> Unit, onSave: suspend (LocalDate, Long) -> Int?) {
    val locale = currentLocale()
    val dates = remember(locale) { DateFormats(locale) }
    val scope = rememberCoroutineScope()
    // Pre-filled with the last reading, cursor at the end, so usually only the last digits change.
    var field by remember {
        val start = latest?.km?.toString() ?: ""
        mutableStateOf(TextFieldValue(start, selection = TextRange(start.length)))
    }
    var date by remember { mutableStateOf(Clock.System.todayIn(TimeZone.currentSystemDefault())) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickDate by rememberSaveable { mutableStateOf(false) }

    AlertDialog(

        modifier = Modifier.clip(AlertDialogDefaults.shape).glass(GLASS_DIALOG),

        containerColor = glassContainer(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_odometer)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = field,
                    onValueChange = {
                        val digits = it.text.filter(Char::isDigit).take(7)
                        field = it.copy(text = digits, selection = TextRange(minOf(it.selection.end, digits.length)))
                        error = null
                    },
                    label = { Text(stringResource(R.string.odometer_reading_km)) },
                    suffix = { Text("km") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(stringResource(it)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedButton(onClick = { pickDate = true }) {
                    Icon(Icons.Filled.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(dates.day(date))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val km = field.text.toLongOrNull()
                if (km == null) {
                    error = R.string.invalid_number
                    return@TextButton
                }
                scope.launch { error = onSave(date, km) }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (pickDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toPickerMillis())
        DatePickerDialog(
            modifier = Modifier.clip(DatePickerDefaults.shape).glass(GLASS_DIALOG),
            colors = DatePickerDefaults.colors(containerColor = glassContainer()),
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = it.pickerMillisToLocalDate() }
                    pickDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(colors = DatePickerDefaults.colors(containerColor = glassContainer()), state = pickerState) }
    }
}
