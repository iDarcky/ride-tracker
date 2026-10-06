package app.ridetracker.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import app.ridetracker.RideTrackerApplication
import app.ridetracker.shared.domain.Country
import app.ridetracker.ui.common.CurrencyDialog
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.resolveCurrency
import kotlinx.coroutines.launch

/** First-launch screen: picks the country, which decides the currency. */
@Composable
fun WelcomeScreen() {
    val settingsRepository = (LocalContext.current.applicationContext as RideTrackerApplication).container.settingsRepository
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    var country by rememberSaveable { mutableStateOf<Country?>(null) }
    var otherCurrency by rememberSaveable { mutableStateOf(resolveCurrency(null).currencyCode) }
    var pickCurrency by rememberSaveable { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.welcome_country_question), style = MaterialTheme.typography.titleMedium)

            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CountryOption(
                    title = stringResource(R.string.country_romania),
                    detail = stringResource(R.string.country_romania_detail),
                    selected = country == Country.ROMANIA,
                    onClick = { country = Country.ROMANIA },
                )
                CountryOption(
                    title = stringResource(R.string.country_other),
                    detail = if (country == Country.OTHER) {
                        "${stringResource(R.string.currency)}: $otherCurrency – ${resolveCurrency(otherCurrency).getDisplayName(locale)}"
                    } else {
                        stringResource(R.string.country_other_detail)
                    },
                    selected = country == Country.OTHER,
                    onClick = {
                        country = Country.OTHER
                        pickCurrency = true
                    },
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val chosen = country ?: return@Button
                    scope.launch {
                        settingsRepository.setCountry(chosen, otherCurrency.takeIf { chosen == Country.OTHER })
                    }
                },
                enabled = country != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.welcome_continue))
            }
            Text(
                stringResource(R.string.local_first),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (pickCurrency) {
        CurrencyDialog(
            selected = resolveCurrency(otherCurrency),
            onDismiss = { pickCurrency = false },
            onSelect = {
                otherCurrency = it.currencyCode
                pickCurrency = false
            },
        )
    }
}

@Composable
private fun CountryOption(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            leadingContent = { RadioButton(selected = selected, onClick = null) },
            headlineContent = { Text(title) },
            supportingContent = { Text(detail) },
        )
    }
}
