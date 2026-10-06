package app.ridetracker.ui.welcome

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.ridetracker.shared.domain.DrivingType
import app.ridetracker.ui.common.ChoiceDialog
import app.ridetracker.ui.common.CurrencyDialog
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.settings.drivingDetail
import app.ridetracker.ui.settings.drivingName
import app.ridetracker.ui.settings.languageName
import app.ridetracker.ui.settings.setAppLanguage
import kotlinx.coroutines.launch

private enum class Step { COUNTRY, DRIVING }

/** First-launch flow: country (decides currency), then for Romania how the driver works. */
@Composable
fun WelcomeScreen() {
    val settingsRepository = (LocalContext.current.applicationContext as RideTrackerApplication).container.settingsRepository
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    var step by rememberSaveable { mutableStateOf(Step.COUNTRY) }
    var country by rememberSaveable { mutableStateOf<Country?>(null) }
    var drivingType by rememberSaveable { mutableStateOf<DrivingType?>(null) }
    var otherCurrency by rememberSaveable { mutableStateOf(resolveCurrency(null).currencyCode) }
    var pickCurrency by rememberSaveable { mutableStateOf(false) }
    var pickLanguage by rememberSaveable { mutableStateOf(false) }
    val languageTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')

    fun finish() {
        val chosen = country ?: return
        scope.launch {
            settingsRepository.setCountry(
                chosen,
                otherCurrencyCode = otherCurrency.takeIf { chosen == Country.OTHER },
                drivingType = drivingType.takeIf { chosen == Country.ROMANIA },
            )
        }
    }

    BackHandler(enabled = step == Step.DRIVING) { step = Step.COUNTRY }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { pickLanguage = true }) {
                    Icon(Icons.Outlined.Language, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(languageName(languageTag))
                }
            }
            AnimatedContent(targetState = step, label = "welcome-step") { current ->
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (current) {
                        Step.COUNTRY -> {
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
                                Option(
                                    title = stringResource(R.string.country_romania),
                                    detail = stringResource(R.string.country_romania_detail),
                                    selected = country == Country.ROMANIA,
                                    onClick = { country = Country.ROMANIA },
                                )
                                Option(
                                    title = stringResource(R.string.country_other),
                                    detail = if (country == Country.OTHER) {
                                        "${stringResource(R.string.currency)}: $otherCurrency – " +
                                            resolveCurrency(otherCurrency).getDisplayName(locale)
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
                                onClick = { if (country == Country.ROMANIA) step = Step.DRIVING else finish() },
                                enabled = country != null,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) { Text(stringResource(R.string.welcome_continue)) }
                            Text(
                                stringResource(R.string.local_first),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Step.DRIVING -> {
                            Text(stringResource(R.string.welcome_driving_question), style = MaterialTheme.typography.headlineMedium)
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                DrivingType.entries.forEach { type ->
                                    Option(
                                        title = drivingName(type),
                                        detail = drivingDetail(type),
                                        selected = drivingType == type,
                                        onClick = { drivingType = type },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = ::finish,
                                enabled = drivingType != null,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) { Text(stringResource(R.string.welcome_continue)) }
                            OutlinedButton(onClick = { step = Step.COUNTRY }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                Text(stringResource(R.string.back))
                            }
                        }
                    }
                }
            }
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
    if (pickLanguage) {
        ChoiceDialog(
            title = stringResource(R.string.language),
            options = listOf("", "en", "ro"),
            selected = languageTag,
            label = { languageName(it) },
            onDismiss = { pickLanguage = false },
            onSelect = {
                pickLanguage = false
                setAppLanguage(it)
            },
        )
    }
}

@Composable
private fun Option(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
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
