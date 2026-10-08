package app.ridetracker.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import app.ridetracker.shared.data.VehicleEntity
import app.ridetracker.shared.domain.FuelType
import app.ridetracker.shared.domain.Money

/** The car's fields, shared by the Vehicle screen and onboarding. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VehicleFields(
    name: String,
    onName: (String) -> Unit,
    year: String,
    onYear: (String) -> Unit,
    fuel: FuelType,
    onFuel: (FuelType) -> Unit,
    consumption: String,
    onConsumption: (String) -> Unit,
    price: String,
    onPrice: (String) -> Unit,
    currencySymbol: String,
    invalid: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                label = { Text(stringResource(R.string.vehicle_name)) },
                singleLine = true,
                isError = invalid && name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = year,
                onValueChange = { onYear(it.filter(Char::isDigit).take(4)) },
                label = { Text(stringResource(R.string.vehicle_year)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.fuel_type), style = MaterialTheme.typography.titleSmall)
            // Main kinds as chips; a hybrid then asks for its engine and whether it plugs in.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(FuelType.DIESEL, FuelType.PETROL, FuelType.HYBRID, FuelType.LPG, FuelType.ELECTRIC).forEach { type ->
                    val selected = if (type == FuelType.HYBRID) fuel.isHybrid else fuel == type
                    FilterChip(
                        selected = selected,
                        onClick = { if (!(type == FuelType.HYBRID && fuel.isHybrid)) onFuel(type) },
                        label = { Text(if (type == FuelType.HYBRID) stringResource(R.string.fuel_hybrid) else fuelName(type)) },
                    )
                }
            }
            if (fuel.isHybrid) {
                Text(stringResource(R.string.hybrid_engine), style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    listOf(false to R.string.fuel_petrol, true to R.string.fuel_diesel).forEachIndexed { index, (diesel, label) ->
                        ToggleButton(
                            checked = fuel.isDieselHybrid == diesel,
                            onCheckedChange = { onFuel(FuelType.hybrid(diesel = diesel, plugIn = fuel.isPlugIn)) },
                            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                            shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        ) { Text(stringResource(label)) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.plug_in), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = fuel.isPlugIn,
                        onCheckedChange = { onFuel(FuelType.hybrid(diesel = fuel.isDieselHybrid, plugIn = it)) },
                    )
                }
            }
            OutlinedTextField(
                value = consumption,
                onValueChange = onConsumption,
                label = { Text(stringResource(if (fuel.isElectric) R.string.consumption_kwh else R.string.consumption_litres)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = price,
                onValueChange = onPrice,
                label = { Text(stringResource(if (fuel.isElectric) R.string.fuel_price_kwh else R.string.fuel_price_litre)) },
                suffix = { Text(currencySymbol) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        if (invalid) Text(stringResource(R.string.invalid_number), color = MaterialTheme.colorScheme.error)
    }
}

/** The vehicle from the form's text, or null when something is missing or not a number. */
fun buildVehicle(id: Long, name: String, year: String, fuel: FuelType, consumption: String, price: String, priceDigits: Int): VehicleEntity? {
    val yearValue = year.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val consumptionValue = consumption.takeIf { it.isNotBlank() }?.let { Money.parseToMinor(it, 2) }
    val priceValue = price.takeIf { it.isNotBlank() }?.let { Money.parseToMinor(it, priceDigits) }
    val bad = name.isBlank() ||
        (year.isNotBlank() && (yearValue == null || yearValue !in 1950..2100)) ||
        (consumption.isNotBlank() && consumptionValue == null) ||
        (price.isNotBlank() && priceValue == null)
    if (bad) return null
    return VehicleEntity(
        id = id, name = name.trim(), year = yearValue, fuelType = fuel.id,
        consumptionCenti = consumptionValue, fuelPriceMinor = priceValue,
    )
}
