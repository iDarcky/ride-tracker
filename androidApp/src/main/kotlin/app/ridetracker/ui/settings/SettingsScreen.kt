package app.ridetracker.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.BuildConfig
import app.ridetracker.R
import app.ridetracker.shared.domain.Country
import app.ridetracker.shared.domain.DrivingType
import app.ridetracker.shared.domain.ThemeMode
import app.ridetracker.ui.common.ChoiceDialog
import app.ridetracker.ui.common.ConfirmDialog
import app.ridetracker.ui.common.CurrencyDialog
import app.ridetracker.ui.common.LocalBottomBarSpace
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.resolveCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.toJavaDayOfWeek
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

private val weekStartOptions = listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** In-app languages: empty tag = follow the system. Names are shown in their own language. */
private val languageOptions = listOf("", "en", "ro")

private enum class Dialog { COUNTRY, CURRENCY, DRIVING, LANGUAGE, THEME, WEEK_START, ERASE }

/** Settings in the Google Health style: large title, coloured section headers, icon rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onManagePlatforms: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel(container.settingsRepository, container.backupService) },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }
    val country = settings?.country
    val currency = resolveCurrency(settings?.currencyCode)
    val weekStart = settings?.firstDayOfWeek ?: DayOfWeek.MONDAY
    val themeMode = settings?.themeMode ?: ThemeMode.SYSTEM
    val languageTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val text = viewModel.backupText(BuildConfig.VERSION_NAME)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }
            }.isSuccess
            viewModel.backupFinished(ok)
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            viewModel.onRestoreFileRead(text)
        }
    }

    val messageText = message?.let {
        stringResource(
            when (it) {
                SettingsMessage.BACKUP_SAVED -> R.string.backup_saved
                SettingsMessage.BACKUP_FAILED -> R.string.backup_failed
                SettingsMessage.RESTORE_DONE -> R.string.restore_done
                SettingsMessage.RESTORE_INVALID -> R.string.restore_invalid
                SettingsMessage.RESTORE_NEWER -> R.string.restore_newer
            },
        )
    }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            viewModel.messageShown()
            snackbar.showSnackbar(messageText)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeTopAppBar(title = { Text(stringResource(R.string.nav_settings)) }, scrollBehavior = scrollBehavior)
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = LocalBottomBarSpace.current)) },
    ) { padding ->
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = LocalBottomBarSpace.current + 24.dp,
            ),
        ) {
            item { Section(stringResource(R.string.section_app_settings)) }
            item {
                Row(Icons.Outlined.Public, stringResource(R.string.country), "${countryName(country)} · ${currency.currencyCode}") {
                    dialog = Dialog.COUNTRY
                }
            }
            if (country == Country.ROMANIA) {
                item {
                    Row(Icons.Outlined.Badge, stringResource(R.string.driving_type), settings?.drivingType?.let { drivingName(it) } ?: stringResource(R.string.not_set)) {
                        dialog = Dialog.DRIVING
                    }
                }
            }
            item { Row(Icons.Outlined.Apps, stringResource(R.string.apps), stringResource(R.string.apps_summary), onClick = onManagePlatforms) }
            item { Row(Icons.Outlined.Language, stringResource(R.string.language), languageName(languageTag)) { dialog = Dialog.LANGUAGE } }
            item { Row(Icons.Outlined.Contrast, stringResource(R.string.theme), themeName(themeMode)) { dialog = Dialog.THEME } }
            item {
                Row(Icons.Outlined.CalendarMonth, stringResource(R.string.first_day_of_week), dayName(weekStart, locale)) {
                    dialog = Dialog.WEEK_START
                }
            }

            item { Section(stringResource(R.string.section_data)) }
            item {
                Row(Icons.Outlined.Save, stringResource(R.string.backup), stringResource(R.string.backup_summary)) {
                    createBackup.launch("ridetracker-backup-${LocalDate.now()}.json")
                }
            }
            item {
                Row(Icons.Outlined.Restore, stringResource(R.string.restore), stringResource(R.string.restore_summary)) {
                    openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                }
            }
            item {
                Row(
                    Icons.Outlined.DeleteForever,
                    stringResource(R.string.erase),
                    stringResource(R.string.erase_summary),
                    tint = MaterialTheme.colorScheme.error,
                ) { dialog = Dialog.ERASE }
            }

            item { Section(stringResource(R.string.section_about)) }
            item { Row(Icons.Outlined.Shield, stringResource(R.string.local_first), null, onClick = null) }
            item { Row(Icons.Outlined.Info, stringResource(R.string.version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", onClick = null) }
        }
    }

    pendingRestore?.let { file ->
        val created = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(file.createdAtEpochMillis))
        val count = pluralStringResource(R.plurals.entry_count, file.entries.size, file.entries.size)
        ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            body = stringResource(R.string.restore_confirm_body, created, count),
            confirmLabel = stringResource(R.string.restore_action),
            onDismiss = viewModel::cancelRestore,
            onConfirm = viewModel::confirmRestore,
        )
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
        Dialog.DRIVING -> ChoiceDialog(
            title = stringResource(R.string.driving_type),
            options = DrivingType.entries,
            selected = settings?.drivingType,
            label = { drivingName(it) },
            description = { drivingDetail(it) },
            onDismiss = { dialog = null },
            onSelect = {
                viewModel.setDrivingType(it)
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
                setAppLanguage(it)
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
        Dialog.ERASE -> ConfirmDialog(
            title = stringResource(R.string.erase_confirm_title),
            body = stringResource(R.string.erase_confirm_body),
            confirmLabel = stringResource(R.string.erase_action),
            destructive = true,
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                // Language is stored by AppCompat, not in our settings: reset it too.
                viewModel.eraseAll { setAppLanguage("") }
            },
        )
        null -> Unit
    }
}

/** Recreates the activity in the new language; AppCompat stores the choice. */
fun setAppLanguage(tag: String) {
    AppCompatDelegate.setApplicationLocales(
        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
    )
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Row(
    icon: ImageVector,
    title: String,
    summary: String?,
    tint: Color = Color.Unspecified,
    onClick: (() -> Unit)?,
) {
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        leadingContent = { Icon(icon, contentDescription = null, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint) },
        headlineContent = {
            Text(title, style = MaterialTheme.typography.titleLarge, color = if (tint == Color.Unspecified) Color.Unspecified else tint)
        },
        supportingContent = summary?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
    )
}

@Composable
fun countryName(country: Country?): String = when (country) {
    Country.ROMANIA -> stringResource(R.string.country_romania)
    Country.OTHER, null -> stringResource(R.string.country_other)
}

@Composable
fun drivingName(type: DrivingType): String = stringResource(
    when (type) {
        DrivingType.PFA -> R.string.driving_pfa
        DrivingType.FLEET -> R.string.driving_fleet
    },
)

@Composable
fun drivingDetail(type: DrivingType): String = stringResource(
    when (type) {
        DrivingType.PFA -> R.string.driving_pfa_detail
        DrivingType.FLEET -> R.string.driving_fleet_detail
    },
)

@Composable
private fun themeName(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
fun languageName(tag: String): String =
    if (tag.isEmpty()) {
        stringResource(R.string.language_system)
    } else {
        Locale.forLanguageTag(tag).let { it.getDisplayName(it).replaceFirstChar { c -> c.titlecase(it) } }
    }

private fun dayName(day: DayOfWeek, locale: Locale): String =
    day.toJavaDayOfWeek().getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
