package app.ridetracker.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.BuildConfig
import app.ridetracker.R
import app.ridetracker.shared.domain.Country
import app.ridetracker.shared.domain.ThemeMode
import app.ridetracker.ui.common.CurrencyDialog
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.resolveCurrency
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.toJavaDayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private val weekStartOptions = listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** In-app languages: empty tag = follow the system. Names are shown in their own language. */
private val languageOptions = listOf("", "en", "ro")

private enum class Dialog { COUNTRY, CURRENCY, LANGUAGE, THEME, WEEK_START }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onManagePlatforms: () -> Unit,
    viewModel: MoreViewModel = viewModel { MoreViewModel(container.settingsRepository) },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val locale = currentLocale()
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }
    val country = settings?.country
    val currency = resolveCurrency(settings?.currencyCode)
    val weekStart = settings?.firstDayOfWeek ?: DayOfWeek.MONDAY
    val themeMode = settings?.themeMode ?: ThemeMode.SYSTEM
    val languageTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_more)) }) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Row(Icons.Outlined.Apps, stringResource(R.string.apps), stringResource(R.string.apps_summary), onManagePlatforms)
            Row(
                Icons.Outlined.Public,
                stringResource(R.string.country),
                "${countryName(country)} · ${currency.currencyCode}",
            ) { dialog = Dialog.COUNTRY }
            Row(Icons.Outlined.Language, stringResource(R.string.language), languageName(languageTag)) {
                dialog = Dialog.LANGUAGE
            }
            Row(Icons.Outlined.DarkMode, stringResource(R.string.theme), themeName(themeMode)) {
                dialog = Dialog.THEME
            }
            Row(Icons.Outlined.CalendarMonth, stringResource(R.string.first_day_of_week), dayName(weekStart, locale)) {
                dialog = Dialog.WEEK_START
            }
            Row(Icons.Outlined.Shield, stringResource(R.string.local_first), null, null)
            Row(Icons.Outlined.Info, stringResource(R.string.version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", null)
        }
    }

    when (dialog) {
        Dialog.COUNTRY -> ChoiceDialog(
            title = stringResource(R.string.country),
            options = Country.entries,
            selected = country,
            label = { countryName(it) },
            onDismiss = { dialog = null },
            onSelect = {
                if (it == Country.OTHER) {
                    dialog = Dialog.CURRENCY
                } else {
                    viewModel.setCountry(it)
                    dialog = null
                }
            },
        )
        Dialog.CURRENCY -> CurrencyDialog(
            selected = if (country == Country.OTHER) currency else null,
            onDismiss = { dialog = null },
            onSelect = {
                viewModel.setCountry(Country.OTHER, it.currencyCode)
                dialog = null
            },
        )
        Dialog.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.language),
            options = languageOptions,
            selected = languageTag,
            label = { languageName(it) },
            onDismiss = { dialog = null },
            onSelect = {
                dialog = null
                // Recreates the activity in the new language; the choice is stored by AppCompat.
                AppCompatDelegate.setApplicationLocales(
                    if (it.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(it),
                )
            },
        )
        Dialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.theme),
            options = ThemeMode.entries,
            selected = themeMode,
            label = { themeName(it) },
            onDismiss = { dialog = null },
            onSelect = {
                dialog = null
                viewModel.setThemeMode(it)
            },
        )
        Dialog.WEEK_START -> ChoiceDialog(
            title = stringResource(R.string.first_day_of_week),
            options = weekStartOptions,
            selected = weekStart,
            label = { dayName(it, locale) },
            onDismiss = { dialog = null },
            onSelect = {
                viewModel.setFirstDayOfWeek(it)
                dialog = null
            },
        )
        null -> Unit
    }
}

@Composable
private fun countryName(country: Country?): String = when (country) {
    Country.ROMANIA -> stringResource(R.string.country_romania)
    Country.OTHER, null -> stringResource(R.string.country_other)
}

@Composable
private fun themeName(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun languageName(tag: String): String =
    if (tag.isEmpty()) {
        stringResource(R.string.language_system)
    } else {
        Locale.forLanguageTag(tag).let { it.getDisplayName(it).replaceFirstChar { c -> c.titlecase(it) } }
    }

private fun dayName(day: DayOfWeek, locale: Locale): String =
    day.toJavaDayOfWeek().getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }

@Composable
private fun Row(icon: ImageVector, title: String, summary: String?, onClick: (() -> Unit)?) {
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = onClick?.let {
            { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
        },
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    ListItem(
                        modifier = Modifier.selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) },
                        leadingContent = { RadioButton(selected = option == selected, onClick = null) },
                        headlineContent = { Text(label(option), style = MaterialTheme.typography.bodyLarge) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        modifier = Modifier.padding(vertical = 24.dp),
    )
}
