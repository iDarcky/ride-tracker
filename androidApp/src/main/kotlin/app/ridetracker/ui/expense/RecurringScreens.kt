package app.ridetracker.ui.expense

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ridetracker.R
import app.ridetracker.RideTrackerApplication
import app.ridetracker.shared.data.RecurringExpenseEntity
import app.ridetracker.shared.domain.ExpenseCategory
import app.ridetracker.shared.domain.Frequency
import app.ridetracker.shared.domain.Money
import app.ridetracker.ui.common.ConfirmDialog
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.ExpenseBadge
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.icon
import app.ridetracker.ui.common.label
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.common.tabular
import app.ridetracker.ui.settings.SettingsFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** All recurring expenses with their next due date; tap one to edit or stop it. */
@Composable
fun RecurringListScreen(onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val container = (LocalContext.current.applicationContext as RideTrackerApplication).container
    val rules by container.recurringRepository.observeAll().collectAsStateWithLifecycle(initialValue = null)
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val locale = currentLocale()
    val money = remember(settings?.currencyCode, locale) { MoneyFormat(resolveCurrency(settings?.currencyCode), locale) }
    val dates = remember(locale) { DateFormats(locale) }
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    SettingsFrame(stringResource(R.string.recurring_expenses), onBack) {
        val list = rules ?: return@SettingsFrame
        if (list.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_recurring),
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        list.forEach { rule ->
            item(key = rule.id) {
                val category = ExpenseCategory.fromId(rule.category)
                val next = LocalDate.fromEpochDays(rule.nextDueDate)
                val ended = rule.endDate?.let { next > LocalDate.fromEpochDays(it) } ?: false
                ListItem(
                    modifier = Modifier.clickable { onEdit(rule.id) },
                    leadingContent = { ExpenseBadge(category.icon) },
                    headlineContent = { Text(rule.note ?: stringResource(category.label)) },
                    supportingContent = {
                        Text(
                            frequencyName(Frequency.fromId(rule.frequency)) + " · " +
                                if (ended) stringResource(R.string.recurring_ended) else stringResource(R.string.next_due, dates.day(next)),
                            color = if (!ended && next <= today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = { Text(money.format(-rule.amountMinor), style = MaterialTheme.typography.titleMedium.tabular()) },
                )
            }
        }
    }
}

/** Edit amount, category, frequency, next date and end of a recurring expense, or stop it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringEditScreen(ruleId: Long, onDone: () -> Unit) {
    val container = (LocalContext.current.applicationContext as RideTrackerApplication).container
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    var rule by remember { mutableStateOf<RecurringExpenseEntity?>(null) }
    var currencyCode by remember { mutableStateOf<String?>(null) }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(ExpenseCategory.OTHER) }
    var note by rememberSaveable { mutableStateOf("") }
    var frequency by rememberSaveable { mutableStateOf(Frequency.MONTHLY) }
    var nextDue by remember { mutableStateOf(Clock.System.todayIn(TimeZone.currentSystemDefault())) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(ruleId) {
        currencyCode = container.settingsRepository.settings.first().currencyCode
        val loaded = container.recurringRepository.get(ruleId) ?: return@LaunchedEffect onDone()
        val digits = resolveCurrency(currencyCode).defaultFractionDigits.coerceAtLeast(0)
        rule = loaded
        amount = Money.toPlainString(loaded.amountMinor, digits)
        category = ExpenseCategory.fromId(loaded.category)
        note = loaded.note ?: ""
        frequency = Frequency.fromId(loaded.frequency)
        nextDue = LocalDate.fromEpochDays(loaded.nextDueDate)
        endDate = loaded.endDate?.let { LocalDate.fromEpochDays(it) }
    }

    fun save() {
        val current = rule ?: return
        val digits = resolveCurrency(currencyCode).defaultFractionDigits.coerceAtLeast(0)
        val minor = Money.parseToMinor(amount, digits)
        if (minor == null || minor == 0L) {
            invalid = true
            return
        }
        val dateOrFrequencyChanged = nextDue.toEpochDays() != current.nextDueDate || frequency.id != current.frequency
        scope.launch {
            container.recurringRepository.update(
                current.copy(
                    amountMinor = minor,
                    category = category.id,
                    note = note.trim().ifEmpty { null },
                    frequency = frequency.id,
                    // A new date or rhythm restarts the series from the chosen next date.
                    anchorDate = if (dateOrFrequencyChanged) nextDue.toEpochDays() else current.anchorDate,
                    nextDueDate = nextDue.toEpochDays(),
                    endDate = endDate?.toEpochDays(),
                    notifiedDueDate = if (dateOrFrequencyChanged) null else current.notifiedDueDate,
                ),
            )
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_recurring)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_recurring))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; invalid = false },
                label = { Text(stringResource(R.string.amount)) },
                suffix = { Text(resolveCurrency(currencyCode).getSymbol(locale)) },
                isError = invalid,
                supportingText = if (invalid) {
                    { Text(stringResource(R.string.invalid_amount)) }
                } else {
                    null
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.tabular(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            CategoryPicker(
                group = category.group,
                category = category,
                onGroup = { if (it != category.group) category = ExpenseCategory.inGroup(it).first() },
                onCategory = { category = it },
            )
            Text(stringResource(R.string.repeat), style = MaterialTheme.typography.titleSmall)
            FrequencyPicker(frequency) { frequency = it }
            DateField(stringResource(R.string.next_due_date), nextDue) { nextDue = it }
            EndsPicker(endDate, { endDate = it }, earliest = nextDue)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.note_optional)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::save, enabled = rule != null, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.save))
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_recurring_title),
            body = stringResource(R.string.delete_recurring_body),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    container.recurringRepository.delete(ruleId)
                    onDone()
                }
            },
        )
    }
}
