package app.ridetracker.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import app.ridetracker.shared.domain.IncomeBreakdown
import app.ridetracker.shared.domain.IncomeLineKind
import app.ridetracker.shared.domain.PaymentSplit
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.tabular
import java.text.NumberFormat

/** Our name for a kind of income or deduction. */
@Composable
fun incomeKindLabel(kind: IncomeLineKind): String = stringResource(
    when (kind) {
        IncomeLineKind.FARE -> R.string.line_fare
        IncomeLineKind.TIP -> R.string.line_tip
        IncomeLineKind.BONUS -> R.string.line_bonus
        IncomeLineKind.PROMOTION -> R.string.line_promotion
        IncomeLineKind.TOLL -> R.string.line_toll
        IncomeLineKind.AIRPORT_FEE -> R.string.line_airport_fee
        IncomeLineKind.CANCELLATION_FEE -> R.string.line_cancellation_fee
        IncomeLineKind.COMMISSION -> R.string.line_commission
        IncomeLineKind.OTHER_FEE -> R.string.line_other_fee
        IncomeLineKind.OTHER -> R.string.line_other
    },
)

/** Rides, tips, bonuses… minus commission = what the platforms paid. */
@Composable
fun MadeOfCard(b: IncomeBreakdown, money: MoneyFormat) {
    BreakdownCard(stringResource(R.string.made_of)) {
        b.incomeParts.forEach { TypeRow(incomeKindLabel(it.kind), money.format(it.amountMinor)) }
        if (b.withoutBreakdownMinor != 0L) TypeRow(stringResource(R.string.without_breakdown), money.format(b.withoutBreakdownMinor))
        b.deductions.forEach { TypeRow(incomeKindLabel(it.kind), money.format(it.amountMinor)) }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        TypeRow(stringResource(R.string.paid_out), money.format(b.earnedMinor), strong = true)
        if (b.withoutBreakdownMinor != 0L) Note(stringResource(R.string.without_breakdown_note))
    }
}

/** In the app vs cash: a two-part bar with the parts named under it, then cash in hand, paid to the bank and trips. */
@Composable
fun PaymentSplitContent(p: PaymentSplit, money: MoneyFormat, percent: NumberFormat) {
    val inAppColor = MaterialTheme.colorScheme.primary
    val cashColor = MaterialTheme.colorScheme.tertiary
    if (p.hasMoney) {
        val total = (p.inAppMinor + p.cashMinor).coerceAtLeast(1)
        val inAppShare = p.inAppMinor.toFloat() / total
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (p.inAppMinor > 0) Segment(inAppShare, inAppColor)
                if (p.cashMinor > 0) Segment(1f - inAppShare, cashColor)
            }
            Row {
                Part(stringResource(R.string.paid_in_app), percent.format(inAppShare), money.format(p.inAppMinor), inAppColor, Modifier.weight(1f))
                Part(stringResource(R.string.paid_cash), percent.format(1f - inAppShare), money.format(p.cashMinor), cashColor, Modifier.weight(1f))
            }
        }
        p.cashInHandMinor?.let { TypeRow(stringResource(R.string.cash_in_hand), money.format(it)) }
        p.toBankMinor?.let { TypeRow(stringResource(R.string.to_bank), money.format(it), strong = true) }
        Note(stringResource(if (p.partial) R.string.payment_note_partial else R.string.payment_note))
    }
    if (p.hasTrips) {
        if (p.hasMoney) HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        TypeRow(
            pluralStringResource(R.plurals.trips_paid_in_app, p.inAppTrips, p.inAppTrips),
            money.format(p.inAppFaresMinor),
        )
        TypeRow(
            pluralStringResource(R.plurals.trips_paid_cash, p.cashTrips, p.cashTrips),
            money.format(p.cashFaresMinor),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Segment(weight: Float, color: Color) {
    Box(Modifier.weight(weight.coerceAtLeast(0.02f)).height(12.dp).background(color, RoundedCornerShape(4.dp)))
}

@Composable
private fun Part(label: String, share: String, amount: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier) {
        Box(Modifier.width(3.dp).height(40.dp).background(color))
        Column(Modifier.padding(start = 8.dp)) {
            Text("$label · $share", style = MaterialTheme.typography.labelLarge)
            Text(amount, style = MaterialTheme.typography.titleSmall.tabular())
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
