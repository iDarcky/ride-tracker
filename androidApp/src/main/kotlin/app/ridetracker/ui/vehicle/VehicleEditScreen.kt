package app.ridetracker.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import app.ridetracker.RideTrackerApplication
import app.ridetracker.shared.data.VehicleEntity
import app.ridetracker.shared.domain.FuelType
import app.ridetracker.shared.domain.Money
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.resolveCurrency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Add or edit the vehicle. Consumption and fuel price are optional; they only drive the estimate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEditScreen(onDone: () -> Unit) {
    val container = (LocalContext.current.applicationContext as RideTrackerApplication).container
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    var loaded by rememberSaveable { mutableStateOf(false) }
    var id by rememberSaveable { mutableLongStateOf(0L) }
    var name by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var fuel by rememberSaveable { mutableStateOf(FuelType.DIESEL) }
    var consumption by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var currencyCode by rememberSaveable { mutableStateOf<String?>(null) }
    var invalid by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect
        currencyCode = container.settingsRepository.settings.first().currencyCode
        val digits = resolveCurrency(currencyCode).defaultFractionDigits.coerceAtLeast(0)
        container.vehicleRepository.observeVehicle().first()?.let { v ->
            id = v.id
            name = v.name
            year = v.year?.toString() ?: ""
            fuel = FuelType.fromId(v.fuelType)
            consumption = v.consumptionCenti?.let { Money.toPlainString(it, 2) } ?: ""
            price = v.fuelPriceMinor?.let { Money.toPlainString(it, digits) } ?: ""
        }
        loaded = true
    }

    fun save() {
        val digits = resolveCurrency(currencyCode).defaultFractionDigits.coerceAtLeast(0)
        val yearValue = year.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        val consumptionValue = consumption.takeIf { it.isNotBlank() }?.let { Money.parseToMinor(it, 2) }
        val priceValue = price.takeIf { it.isNotBlank() }?.let { Money.parseToMinor(it, digits) }
        val bad = name.isBlank() ||
            (year.isNotBlank() && (yearValue == null || yearValue !in 1950..2100)) ||
            (consumption.isNotBlank() && consumptionValue == null) ||
            (price.isNotBlank() && priceValue == null)
        if (bad) {
            invalid = true
            return
        }
        scope.launch {
            container.vehicleRepository.save(
                VehicleEntity(id = id, name = name.trim(), year = yearValue, fuelType = fuel.id, consumptionCenti = consumptionValue, fuelPriceMinor = priceValue),
            )
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (id == 0L) R.string.add_vehicle else R.string.edit_vehicle)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; invalid = false },
                label = { Text(stringResource(R.string.vehicle_name)) },
                singleLine = true,
                isError = invalid && name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = year,
                onValueChange = { year = it.filter(Char::isDigit).take(4); invalid = false },
                label = { Text(stringResource(R.string.vehicle_year)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.fuel_type), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FuelType.entries.forEach { type ->
                    FilterChip(selected = fuel == type, onClick = { fuel = type }, label = { Text(fuelName(type)) })
                }
            }
            OutlinedTextField(
                value = consumption,
                onValueChange = { consumption = it; invalid = false },
                label = { Text(stringResource(if (fuel.isElectric) R.string.consumption_kwh else R.string.consumption_litres)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = price,
                onValueChange = { price = it; invalid = false },
                label = { Text(stringResource(if (fuel.isElectric) R.string.fuel_price_kwh else R.string.fuel_price_litre)) },
                suffix = { Text(resolveCurrency(currencyCode).getSymbol(locale)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (invalid) Text(stringResource(R.string.invalid_number), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::save, enabled = loaded, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
fun fuelName(type: FuelType): String = stringResource(
    when (type) {
        FuelType.DIESEL -> R.string.fuel_diesel
        FuelType.PETROL -> R.string.fuel_petrol
        FuelType.HYBRID -> R.string.fuel_hybrid
        FuelType.LPG -> R.string.fuel_lpg
        FuelType.ELECTRIC -> R.string.fuel_electric
    },
)
