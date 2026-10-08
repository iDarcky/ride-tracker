package app.ridetracker.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CarRental
import androidx.compose.material.icons.outlined.CarRepair
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.LocalCarWash
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import app.ridetracker.shared.domain.ExpenseCategory
import app.ridetracker.shared.domain.ExpenseGroup

val ExpenseCategory.icon: ImageVector
    get() = when (this) {
        ExpenseCategory.FUEL -> Icons.Outlined.LocalGasStation
        ExpenseCategory.CHARGING -> Icons.Outlined.EvStation
        ExpenseCategory.MAINTENANCE -> Icons.Outlined.Build
        ExpenseCategory.REPAIRS -> Icons.Outlined.CarRepair
        ExpenseCategory.INSURANCE -> Icons.Outlined.VerifiedUser
        ExpenseCategory.INSPECTION -> Icons.Outlined.Checklist
        ExpenseCategory.ROAD_TAX -> Icons.Outlined.Toll
        ExpenseCategory.PARKING -> Icons.Outlined.LocalParking
        ExpenseCategory.CAR_WASH -> Icons.Outlined.LocalCarWash
        ExpenseCategory.RENT -> Icons.Outlined.CarRental
        ExpenseCategory.ACCOUNTANT -> Icons.Outlined.Calculate
        ExpenseCategory.BANK_FEES -> Icons.Outlined.AccountBalance
        ExpenseCategory.PHONE -> Icons.Outlined.Smartphone
        ExpenseCategory.FLEET_FEES -> Icons.Outlined.Handshake
        ExpenseCategory.INTRA_EU_VAT -> Icons.Outlined.Percent
        ExpenseCategory.OTHER_BUSINESS -> Icons.Outlined.BusinessCenter
        ExpenseCategory.OTHER -> Icons.Outlined.MoreHoriz
    }

@get:StringRes
val ExpenseCategory.label: Int
    get() = when (this) {
        ExpenseCategory.FUEL -> R.string.cat_fuel
        ExpenseCategory.CHARGING -> R.string.cat_charging
        ExpenseCategory.MAINTENANCE -> R.string.cat_maintenance
        ExpenseCategory.REPAIRS -> R.string.cat_repairs
        ExpenseCategory.INSURANCE -> R.string.cat_insurance
        ExpenseCategory.INSPECTION -> R.string.cat_inspection
        ExpenseCategory.ROAD_TAX -> R.string.cat_road_tax
        ExpenseCategory.PARKING -> R.string.cat_parking
        ExpenseCategory.CAR_WASH -> R.string.cat_car_wash
        ExpenseCategory.RENT -> R.string.cat_rent
        ExpenseCategory.ACCOUNTANT -> R.string.cat_accountant
        ExpenseCategory.BANK_FEES -> R.string.cat_bank_fees
        ExpenseCategory.PHONE -> R.string.cat_phone
        ExpenseCategory.FLEET_FEES -> R.string.cat_fleet_fees
        ExpenseCategory.INTRA_EU_VAT -> R.string.cat_intra_eu_vat
        ExpenseCategory.OTHER_BUSINESS -> R.string.cat_other_business
        ExpenseCategory.OTHER -> R.string.cat_other
    }

val ExpenseGroup.icon: ImageVector
    get() = when (this) {
        ExpenseGroup.VEHICLE -> Icons.Outlined.DirectionsCar
        ExpenseGroup.BUSINESS -> Icons.Outlined.BusinessCenter
        ExpenseGroup.OTHER -> Icons.Outlined.Category
    }

@get:StringRes
val ExpenseGroup.label: Int
    get() = when (this) {
        ExpenseGroup.VEHICLE -> R.string.group_vehicle
        ExpenseGroup.BUSINESS -> R.string.group_business
        ExpenseGroup.OTHER -> R.string.group_other
    }

/** Rounded-square icon badge for expenses, the same shape as app badges. */
@Composable
fun ExpenseBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier.size(size).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(size / 4)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(size * 0.55f))
    }
}
