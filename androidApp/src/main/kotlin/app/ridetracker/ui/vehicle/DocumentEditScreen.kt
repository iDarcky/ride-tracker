package app.ridetracker.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import app.ridetracker.RideTrackerApplication
import app.ridetracker.shared.data.VehicleDocumentEntity
import app.ridetracker.shared.domain.DocumentKind
import app.ridetracker.shared.domain.Money
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.expense.DateField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Add or edit a car document: what it is, the last day it's valid and, optionally, what it cost. Renewing is
 * editing: a new date starts the reminders again, and the cost can go to expenses (dated today).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditScreen(documentId: Long, onDone: () -> Unit) {
    val container = (LocalContext.current.applicationContext as RideTrackerApplication).container
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    var loaded by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf(DocumentKind.RCA) }
    var name by rememberSaveable { mutableStateOf("") }
    var expiryDay by rememberSaveable { mutableLongStateOf(today.plus(DatePeriod(years = 1)).toEpochDays()) }
    var cost by rememberSaveable { mutableStateOf("") }
    var addExpense by rememberSaveable { mutableStateOf(false) }
    var currencyCode by rememberSaveable { mutableStateOf<String?>(null) }
    var nameMissing by rememberSaveable { mutableStateOf(false) }
    var costInvalid by rememberSaveable { mutableStateOf(false) }
    val digits = resolveCurrency(currencyCode).defaultFractionDigits.coerceAtLeast(0)
    val kindNames = DocumentKind.entries.associateWith { stringResource(it.label) }

    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect
        currencyCode = container.settingsRepository.settings.first().currencyCode
        val d = resolveCurrency(currencyCode).defaultFractionDigits.coerceAtLeast(0)
        val existing = documentId.takeIf { it != 0L }?.let { container.vehicleRepository.getDocument(it) }
        if (existing != null) {
            kind = DocumentKind.fromId(existing.kind)
            name = existing.name.orEmpty()
            expiryDay = existing.expiryDate
            cost = existing.costMinor?.let { Money.toPlainString(it, d) }.orEmpty()
        } else {
            // A new one starts on the first usual document the car doesn't have yet.
            val used = container.vehicleRepository.getAllDocuments().map { DocumentKind.fromId(it.kind) }.toSet()
            kind = (DocumentKind.entries - DocumentKind.OTHER).firstOrNull { it !in used } ?: DocumentKind.OTHER
        }
        loaded = true
    }

    fun save() {
        val costMinor = cost.trim().takeIf { it.isNotEmpty() }?.let { Money.parseToMinor(it, digits) }
        costInvalid = cost.isNotBlank() && (costMinor == null || costMinor < 0)
        nameMissing = kind == DocumentKind.OTHER && name.isBlank()
        if (costInvalid || nameMissing) return
        scope.launch {
            val vehicle = container.vehicleRepository.observeVehicle().first() ?: return@launch onDone()
            val now = Clock.System.now().toEpochMilliseconds()
            val existing = documentId.takeIf { it != 0L }?.let { container.vehicleRepository.getDocument(it) }
            val document = (existing ?: VehicleDocumentEntity(vehicleId = vehicle.id, kind = kind.id, expiryDate = expiryDay, createdAt = now)).copy(
                kind = kind.id,
                name = name.trim().takeIf { kind == DocumentKind.OTHER && it.isNotEmpty() },
                expiryDate = expiryDay,
                costMinor = costMinor,
            )
            container.vehicleRepository.saveDocument(document)
            if (addExpense && costMinor != null && costMinor > 0) {
                val label = if (kind == DocumentKind.OTHER) name.trim() else kindNames.getValue(kind)
                container.expenseRepository.save(null, costMinor, today, kind.expenseCategory, label, now)
            }
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (documentId == 0L) R.string.add_document else R.string.edit_document)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.document_kind), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DocumentKind.entries.forEach { k ->
                    FilterChip(
                        selected = k == kind,
                        onClick = { kind = k; nameMissing = false },
                        label = { Text(kindNames.getValue(k)) },
                        leadingIcon = { Icon(k.icon, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
                    )
                }
            }
            if (kind == DocumentKind.OTHER) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40); nameMissing = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.document_name)) },
                    isError = nameMissing,
                    supportingText = if (nameMissing) {
                        { Text(stringResource(R.string.document_name_missing)) }
                    } else {
                        null
                    },
                    singleLine = true,
                )
            }
            DateField(stringResource(R.string.document_expires), LocalDate.fromEpochDays(expiryDay)) { expiryDay = it.toEpochDays() }
            OutlinedTextField(
                value = cost,
                onValueChange = { cost = it; costInvalid = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.document_cost)) },
                suffix = { Text(resolveCurrency(currencyCode).getSymbol(locale)) },
                isError = costInvalid,
                supportingText = if (costInvalid) {
                    { Text(stringResource(R.string.invalid_amount)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (cost.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().toggleable(value = addExpense, role = Role.Checkbox, onValueChange = { addExpense = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.document_add_expense), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Checkbox(checked = addExpense, onCheckedChange = null)
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::save, enabled = loaded, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
