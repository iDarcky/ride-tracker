package app.ridetracker.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import app.ridetracker.shared.data.PlatformEntity
import app.ridetracker.shared.domain.RealHourly
import app.ridetracker.shared.domain.WeekPayout
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.PlatformBadge
import app.ridetracker.ui.common.tabular
import kotlin.math.abs

/**
 * This week so far, app by app: what it paid, the cash already in hand, and what's coming to the account (or owed
 * back, when the cash is more than earned). The week, whatever period Home shows.
 */
@Composable
fun WeekPayoutCard(
    payout: WeekPayout,
    platforms: List<PlatformEntity>,
    fleet: Boolean,
    money: MoneyFormat,
    title: String = stringResource(R.string.widget_week_payout),
    /** "So far this week…": off for a finished week (Summary). */
    showNote: Boolean = true,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            payout.apps.forEach { app ->
                val platform = platforms.firstOrNull { it.id == app.platformId } ?: return@forEach
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlatformBadge(platform.name, platform.colorArgb, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(platform.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(
                                stringResource(R.string.payout_earned, money.format(app.earnedMinor)),
                                app.cashInHandMinor?.let { stringResource(R.string.payout_cash, money.format(it)) },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            money.format(abs(app.toAccountMinor)),
                            style = MaterialTheme.typography.titleMedium.tabular(),
                            fontWeight = FontWeight.SemiBold,
                            color = if (app.toAccountMinor < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (app.toAccountMinor < 0) stringResource(R.string.payout_owed, platform.name) else stringResource(R.string.payout_to_account),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (payout.apps.size > 1) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.payout_total), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        money.format(payout.toAccountMinor),
                        style = MaterialTheme.typography.titleLarge.tabular(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (showNote) {
                Text(
                    stringResource(if (fleet) R.string.payout_note_fleet else R.string.payout_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Money per hour online, then after fuel and after every expense, each as a big figure with its label. */
@Composable
fun RealHourlyCard(real: RealHourly, money: MoneyFormat) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.widget_real_per_hour), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            HourlyRow(stringResource(R.string.real_from_apps), money.format(real.perHourMinor), money)
            HourlyRow(
                stringResource(R.string.real_after_fuel),
                real.afterFuelPerHourMinor?.let { money.format(it) },
                money,
                missing = stringResource(R.string.real_no_fuel),
                strong = true,
            )
            real.afterExpensesPerHourMinor?.let { HourlyRow(stringResource(R.string.real_after_expenses), money.format(it), money) }
            Text(
                listOfNotNull(
                    stringResource(R.string.real_hours, stringResource(R.string.hours_minutes, real.onlineMinutes / 60, real.onlineMinutes % 60)),
                    if (real.partial) stringResource(R.string.real_partial) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HourlyRow(label: String, value: String?, money: MoneyFormat, missing: String? = null, strong: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                stringResource(R.string.per_hour_value, value),
                style = (if (strong) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium).tabular(),
                fontWeight = FontWeight.SemiBold,
            )
        } else if (missing != null) {
            Text(missing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
