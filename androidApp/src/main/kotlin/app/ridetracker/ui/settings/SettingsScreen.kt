package app.ridetracker.ui.settings

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import app.ridetracker.shared.domain.TargetSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.FilterChip
import androidx.compose.ui.text.input.KeyboardType
import app.ridetracker.shared.domain.Money
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.TargetBasis
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.MoneyFormat
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.ridetracker.notifications.ZReportReminderScheduler
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.draw.clip
import androidx.core.content.ContextCompat
import app.ridetracker.ui.common.GLASS_DIALOG
import app.ridetracker.ui.common.glass
import app.ridetracker.ui.common.glassContainer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.BuildConfig
import app.ridetracker.R
import app.ridetracker.shared.domain.Country
import app.ridetracker.shared.domain.DrivingType
import app.ridetracker.shared.domain.ThemeMode
import app.ridetracker.ui.common.ConfirmDialog
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.common.selectableCurrencies
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

/** Settings sub-pages, each a full screen (Android/Google Health settings pattern). */
enum class SettingsPage(val route: String) {
    COUNTRY("settings/country"),
    CURRENCY("settings/currency"),
    DRIVING("settings/driving"),
    LANGUAGE("settings/language"),
    THEME("settings/theme"),
    WEEK_START("settings/week"),
    Z_REPORT("settings/z-report"),
    TARGET("settings/target"),
}

private val weekStartOptions = listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** In-app languages: empty tag = follow the system. Names are shown in their own language. */
val languageOptions = listOf("", "en", "ro")

@Composable
private fun settingsViewModel(): SettingsViewModel =
    viewModel { SettingsViewModel(container.settingsRepository, container.backupService) }

/** Shared frame: back arrow, large title that collapses into the bar on scroll, same colour throughout. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsFrame(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            content = content,
        )
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpen: (SettingsPage) -> Unit) {
    val viewModel = settingsViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val country = settings?.country
    val currency = resolveCurrency(settings?.currencyCode)
    val languageTag = currentLanguageTag()

    SettingsFrame(stringResource(R.string.nav_settings), onBack) {
        item { Section(stringResource(R.string.section_app_settings)) }
        item {
            Row(Icons.Outlined.Public, stringResource(R.string.country), "${countryName(country)} · ${currency.currencyCode}") {
                onOpen(SettingsPage.COUNTRY)
            }
        }
        if (country == Country.ROMANIA) {
            item {
                Row(
                    Icons.Outlined.Badge,
                    stringResource(R.string.driving_type),
                    settings?.drivingType?.let { drivingName(it) } ?: stringResource(R.string.not_set),
                ) { onOpen(SettingsPage.DRIVING) }
            }
        }
        if (country == Country.ROMANIA) {
            item {
                val z by viewModel.zReport.collectAsStateWithLifecycle()
                Row(
                    Icons.AutoMirrored.Outlined.ReceiptLong,
                    stringResource(R.string.z_setting),
                    if (z.enabled) stringResource(R.string.z_setting_on, timeText(z.hour, z.minute)) else stringResource(R.string.z_setting_off),
                ) { onOpen(SettingsPage.Z_REPORT) }
            }
        }
        item {
            val target by viewModel.target.collectAsStateWithLifecycle()
            val month = Period.Month.containing(Clock.System.todayIn(TimeZone.currentSystemDefault()))
            val money = MoneyFormat(currency, locale)
            Row(
                Icons.Outlined.Flag,
                stringResource(R.string.target_setting),
                target.targetFor(month)?.let { "${money.format(it)} · ${targetBasisName(target.basis)}" } ?: stringResource(R.string.not_set),
            ) { onOpen(SettingsPage.TARGET) }
        }
        item { Row(Icons.Outlined.Language, stringResource(R.string.language), languageName(languageTag)) { onOpen(SettingsPage.LANGUAGE) } }
        item {
            Row(Icons.Outlined.Contrast, stringResource(R.string.theme), themeName(settings?.themeMode ?: ThemeMode.SYSTEM)) {
                onOpen(SettingsPage.THEME)
            }
        }
        item {
            Row(
                Icons.Outlined.CalendarMonth,
                stringResource(R.string.first_day_of_week),
                dayName(settings?.firstDayOfWeek ?: DayOfWeek.MONDAY, locale),
            ) { onOpen(SettingsPage.WEEK_START) }
        }
    }
}

/** Single-choice page with the radio on the right, like Google Health's Theme page. */
@Composable
private fun <T> ChoicePage(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onBack: () -> Unit,
    description: (@Composable (T) -> String)? = null,
) {
    SettingsFrame(title, onBack) {
        items(options.size) { index ->
            val option = options[index]
            ListItem(
                modifier = Modifier.selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) },
                headlineContent = { Text(label(option), style = MaterialTheme.typography.titleLarge) },
                supportingContent = description?.let { { Text(it(option)) } },
                trailingContent = { RadioButton(selected = option == selected, onClick = null) },
            )
        }
    }
}

@Composable
fun SettingsChoicePage(page: SettingsPage, onBack: () -> Unit, onOpen: (SettingsPage) -> Unit) {
    val viewModel = settingsViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val locale = currentLocale()
    when (page) {
        SettingsPage.COUNTRY -> ChoicePage(
            title = stringResource(R.string.country),
            options = Country.entries,
            selected = settings?.country,
            label = { countryName(it) },
            description = {
                when (it) {
                    Country.ROMANIA -> stringResource(R.string.country_romania_detail)
                    Country.OTHER -> if (settings?.country == Country.OTHER) {
                        resolveCurrency(settings?.currencyCode).let { c -> "${c.currencyCode} – ${c.getDisplayName(locale)}" }
                    } else {
                        stringResource(R.string.country_other_detail)
                    }
                }
            },
            onSelect = { if (it == Country.OTHER) onOpen(SettingsPage.CURRENCY) else viewModel.setCountry(it) },
            onBack = onBack,
        )
        SettingsPage.CURRENCY -> CurrencyPage(
            selected = if (settings?.country == Country.OTHER) settings?.currencyCode else null,
            onSelect = {
                viewModel.setCountry(Country.OTHER, it)
                onBack()
            },
            onBack = onBack,
        )
        SettingsPage.DRIVING -> ChoicePage(
            title = stringResource(R.string.driving_type),
            options = DrivingType.entries,
            selected = settings?.drivingType,
            label = { drivingName(it) },
            description = { drivingDetail(it) },
            onSelect = viewModel::setDrivingType,
            onBack = onBack,
        )
        SettingsPage.LANGUAGE -> ChoicePage(
            title = stringResource(R.string.language),
            options = languageOptions,
            selected = currentLanguageTag(),
            label = { languageName(it) },
            onSelect = ::setAppLanguage,
            onBack = onBack,
        )
        SettingsPage.THEME -> ChoicePage(
            title = stringResource(R.string.theme),
            options = ThemeMode.entries,
            selected = settings?.themeMode,
            label = { themeName(it) },
            onSelect = viewModel::setThemeMode,
            onBack = onBack,
        )
        SettingsPage.Z_REPORT -> ZReportPage(viewModel, onBack)
        SettingsPage.TARGET -> TargetPage(viewModel, resolveCurrency(settings?.currencyCode), onBack)
        SettingsPage.WEEK_START -> ChoicePage(
            title = stringResource(R.string.first_day_of_week),
            options = weekStartOptions,
            selected = settings?.firstDayOfWeek,
            label = { dayName(it, locale) },
            onSelect = viewModel::setFirstDayOfWeek,
            onBack = onBack,
        )
    }
}

@Composable
fun targetBasisName(basis: TargetBasis): String = stringResource(
    when (basis) {
        TargetBasis.INCOME -> R.string.target_basis_income
        TargetBasis.KEPT -> R.string.target_basis_kept
    },
)

/**
 * The monthly target: this month's amount (each month keeps its own; a new month starts with the last one), what it
 * counts, and the days the driver drives (for "per day" and the pace). Changes are kept on the page until Save.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetPage(viewModel: SettingsViewModel, currency: java.util.Currency, onBack: () -> Unit) {
    val saved by viewModel.targetLoaded.collectAsStateWithLifecycle()
    val loaded = saved ?: return
    val locale = currentLocale()
    val digits = currency.defaultFractionDigits.coerceAtLeast(0)
    val month = remember { Period.Month.containing(Clock.System.todayIn(TimeZone.currentSystemDefault())) }
    val dates = remember(locale) { DateFormats(locale) }
    val focus = LocalFocusManager.current
    var amount by rememberSaveable { mutableStateOf(loaded.targetFor(month)?.let { Money.toPlainString(it, digits) }.orEmpty()) }
    var basis by rememberSaveable { mutableStateOf(loaded.basis) }
    var days by rememberSaveable { mutableStateOf(TargetSettings.formatDays(loaded.drivingDays)) }
    val drivingDays = TargetSettings.parseDays(days)
    val minor = if (amount.isBlank()) 0L else Money.parseToMinor(amount, digits)
    SettingsFrame(stringResource(R.string.target_setting), onBack) {
        item {
            Text(
                stringResource(R.string.target_intro),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(stringResource(R.string.target_for_month, dates.period(month))) },
                suffix = { Text(currency.getSymbol(locale)) },
                isError = minor == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                supportingText = { Text(stringResource(R.string.target_each_month)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        item { Section(stringResource(R.string.target_counts)) }
        TargetBasis.entries.forEach { option ->
            item {
                ListItem(
                    modifier = Modifier.selectable(selected = basis == option, role = Role.RadioButton) { basis = option },
                    headlineContent = { Text(targetBasisName(option), style = MaterialTheme.typography.titleLarge) },
                    supportingContent = {
                        Text(stringResource(if (option == TargetBasis.INCOME) R.string.target_basis_income_detail else R.string.target_basis_kept_detail))
                    },
                    trailingContent = { RadioButton(selected = basis == option, onClick = null) },
                )
            }
        }
        item { Section(stringResource(R.string.target_driving_days)) }
        item {
            Text(
                stringResource(R.string.target_driving_days_detail),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DayOfWeek.entries.forEach { day ->
                    val on = day in drivingDays
                    FilterChip(
                        selected = on,
                        onClick = {
                            val next = if (on) drivingDays - day else drivingDays + day
                            if (next.isNotEmpty()) days = TargetSettings.formatDays(next)
                        },
                        label = { Text(day.toJavaDayOfWeek().getDisplayName(TextStyle.SHORT, locale)) },
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    viewModel.saveTarget(month, minor ?: 0L, basis, drivingDays)
                    onBack()
                },
                enabled = minor != null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).height(56.dp),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

/** "9:00 PM" or "21:00", as the phone shows times. */
@Composable
fun timeText(hour: Int, minute: Int): String {
    val context = LocalContext.current
    val locale = currentLocale()
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return java.time.LocalTime.of(hour, minute).format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
}

/** Raportul Z: on/off and one time for every day. Turning it on asks for notifications (Android 13+). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZReportPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val z by viewModel.zReport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    // Checked again each time the page comes back (after the driver allows it in Settings).
    var exact by remember { mutableStateOf(ZReportReminderScheduler.canBeExact(context)) }
    LifecycleResumeEffect(Unit) {
        exact = ZReportReminderScheduler.canBeExact(context)
        onPauseOrDispose { }
    }
    if (pickingTime) {
        val state = rememberTimePickerState(
            initialHour = z.hour, initialMinute = z.minute,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
        )
        TimePickerDialog(
            onDismissRequest = { pickingTime = false },
            modifier = Modifier.clip(TimePickerDialogDefaults.shape).glass(GLASS_DIALOG),
            containerColor = glassContainer(),
            title = { Text(stringResource(R.string.z_time)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setZReportTime(state.hour, state.minute)
                    pickingTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            // The app's light blue instead of the default grey dial and boxes.
            val tint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            TimePicker(
                state,
                colors = TimePickerDefaults.colors(
                    containerColor = Color.Transparent,
                    clockDialColor = tint,
                    timeSelectorContainerColor = tint,
                    periodSelectorContainerColor = Color.Transparent,
                ),
            )
        }
    }
    SettingsFrame(stringResource(R.string.z_setting), onBack) {
        item {
            Text(
                stringResource(R.string.z_page_intro),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            ListItem(
                modifier = Modifier.toggleable(value = z.enabled, role = Role.Switch) { on ->
                    if (on && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    viewModel.setZReportEnabled(on)
                },
                headlineContent = { Text(stringResource(R.string.z_remind), style = MaterialTheme.typography.titleLarge) },
                trailingContent = { Switch(checked = z.enabled, onCheckedChange = null) },
            )
        }
        item {
            ListItem(
                modifier = Modifier.clickable(enabled = z.enabled) { pickingTime = true },
                headlineContent = { Text(stringResource(R.string.z_time), style = MaterialTheme.typography.titleLarge) },
                supportingContent = { Text(timeText(z.hour, z.minute)) },
            )
        }
        if (z.enabled && !exact && Build.VERSION.SDK_INT >= 31) {
            item {
                // Without "Alarms & reminders", Android may deliver the reminder up to an hour late.
                ListItem(
                    headlineContent = { Text(stringResource(R.string.z_exact_title)) },
                    supportingContent = { Text(stringResource(R.string.z_exact_body)) },
                    trailingContent = {
                        FilledTonalButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                            )
                        }) { Text(stringResource(R.string.z_exact_allow)) }
                    },
                )
            }
        }
    }
}

@Composable
private fun CurrencyPage(selected: String?, onSelect: (String) -> Unit, onBack: () -> Unit) {
    val locale = currentLocale()
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, locale) {
        selectableCurrencies.filter {
            query.isBlank() || it.currencyCode.contains(query, ignoreCase = true) ||
                it.getDisplayName(locale).contains(query, ignoreCase = true)
        }
    }
    SettingsFrame(stringResource(R.string.currency), onBack) {
        item {
            Text(
                stringResource(R.string.currency_no_conversion),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.search_currency)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
        items(filtered, key = { it.currencyCode }) { c ->
            ListItem(
                modifier = Modifier.selectable(selected = c.currencyCode == selected, role = Role.RadioButton) { onSelect(c.currencyCode) },
                headlineContent = { Text(c.currencyCode, style = MaterialTheme.typography.titleLarge) },
                supportingContent = { Text(c.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }) },
                trailingContent = { RadioButton(selected = c.currencyCode == selected, onClick = null) },
            )
        }
    }
}

/** Back up, restore and erase, on one page. */
@Composable
fun YourDataScreen(onBack: () -> Unit, onExport: () -> Unit) {
    val viewModel = settingsViewModel()
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmErase by rememberSaveable { mutableStateOf(false) }

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

    SettingsFrame(stringResource(R.string.section_data), onBack, snackbar) {
        item {
            Row(Icons.Outlined.Save, stringResource(R.string.backup), stringResource(R.string.backup_summary)) {
                createBackup.launch("ridetracker-backup-${LocalDate.now()}.json")
            }
        }
        item {
            Row(Icons.Outlined.TableView, stringResource(R.string.export_csv), stringResource(R.string.export_csv_summary), onClick = onExport)
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
            ) { confirmErase = true }
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
    if (confirmErase) {
        ConfirmDialog(
            title = stringResource(R.string.erase_confirm_title),
            body = stringResource(R.string.erase_confirm_body),
            confirmLabel = stringResource(R.string.erase_action),
            destructive = true,
            onDismiss = { confirmErase = false },
            onConfirm = {
                confirmErase = false
                // Language is stored by AppCompat, not in our settings: reset it too.
                viewModel.eraseAll { setAppLanguage("") }
            },
        )
    }
}

fun currentLanguageTag(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')

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
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
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
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(icon, contentDescription = null, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint)
        },
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
