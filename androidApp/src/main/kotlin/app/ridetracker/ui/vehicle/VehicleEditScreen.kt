package app.ridetracker.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.ToggleButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
        val vehicle = buildVehicle(id, name, year, fuel, consumption, price, resolveCurrency(currencyCode).defaultFractionDigits.coerceAtLeast(0))
        if (vehicle == null) {
            invalid = true
            return
        }
        scope.launch {
            container.vehicleRepository.save(vehicle)
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
            VehicleFields(
                name = name, onName = { name = it; invalid = false },
                year = year, onYear = { year = it; invalid = false },
                fuel = fuel, onFuel = { fuel = it },
                consumption = consumption, onConsumption = { consumption = it; invalid = false },
                price = price, onPrice = { price = it; invalid = false },
                currencySymbol = resolveCurrency(currencyCode).getSymbol(locale),
                invalid = invalid,
            )
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
        FuelType.HYBRID -> R.string.fuel_hybrid_petrol
        FuelType.HYBRID_DIESEL -> R.string.fuel_hybrid_diesel
        FuelType.PLUG_IN_HYBRID -> R.string.fuel_plug_in_petrol
        FuelType.PLUG_IN_HYBRID_DIESEL -> R.string.fuel_plug_in_diesel
        FuelType.LPG -> R.string.fuel_lpg
        FuelType.ELECTRIC -> R.string.fuel_electric
    },
)
