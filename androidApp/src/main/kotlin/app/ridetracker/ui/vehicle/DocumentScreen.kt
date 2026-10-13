package app.ridetracker.ui.vehicle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.R
import app.ridetracker.shared.data.ExpenseEntity
import app.ridetracker.shared.data.SettingsRepository
import app.ridetracker.shared.data.VehicleDocumentEntity
import app.ridetracker.shared.domain.Country
import app.ridetracker.shared.domain.DocumentKind
import app.ridetracker.shared.domain.DrivingType
import app.ridetracker.shared.domain.DocumentRenewal
import app.ridetracker.shared.domain.ExpenseRepository
import app.ridetracker.shared.domain.Money
import app.ridetracker.shared.domain.VehicleRepository
import app.ridetracker.ui.common.ConfirmDialog
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.ExpenseBadge
import app.ridetracker.ui.common.GLASS_DIALOG
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.SectionHeader
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.glass
import app.ridetracker.ui.common.glassContainer
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.common.tabular
import app.ridetracker.ui.expense.DateField
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class DocumentUiState(
    val document: VehicleDocumentEntity? = null,
    /** Expenses linked to this document, newest first. */
    val payments: List<ExpenseEntity> = emptyList(),
    val currencyCode: String? = null,
    /** PFA drivers in Romania can mark the payment tax deductible. */
    val offerTaxDeductible: Boolean = false,
    val loading: Boolean = true,
)

class DocumentViewModel(
    private val documentId: Long,
    private val vehicleRepository: VehicleRepository,
    private val expenseRepository: ExpenseRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<DocumentUiState> = combine(
        vehicleRepository.observeAllDocuments(),
        expenseRepository.observeForDocument(documentId),
        settingsRepository.settings,
    ) { documents, payments, settings ->
        DocumentUiState(
            documents.firstOrNull { it.id == documentId }, payments, settings.currencyCode,
            offerTaxDeductible = settings.country == Country.ROMANIA && settings.drivingType == DrivingType.PFA,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentUiState())

    /** Moves the expiry on and, when [costMinor] is given and [addExpense], adds the payment as a linked expense dated today. */
    fun renew(newExpiry: LocalDate, costMinor: Long?, addExpense: Boolean, taxDeductible: Boolean, expenseNote: String) {
        val document = state.value.document ?: return
        viewModelScope.launch {
            vehicleRepository.saveDocument(document.copy(expiryDate = newExpiry.toEpochDays(), costMinor = costMinor ?: document.costMinor))
            if (addExpense && costMinor != null && costMinor > 0) {
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                val category = DocumentKind.fromId(document.kind).expenseCategory
                expenseRepository.save(
                    null, costMinor, today, category, expenseNote, Clock.System.now().toEpochMilliseconds(),
                    taxDeductible = taxDeductible, documentId = document.id,
                )
            }
        }
    }

    /** The screen closes itself once the document is gone. */
    fun delete() {
        viewModelScope.launch { vehicleRepository.deleteDocument(documentId) }
    }
}

/**
 * One car document: how long it's still valid, what it last cost, Renew (new date and payment in one go) and
 * the payments linked to it. Edit and Delete are in the top bar. [startRenewing] opens Renew straight away
 * (from Home's "Renew").
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun DocumentScreen(
    documentId: Long,
    startRenewing: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    viewModel: DocumentViewModel = viewModel {
        DocumentViewModel(documentId, container.vehicleRepository, container.expenseRepository, container.settingsRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val money = remember(state.currencyCode, locale) { MoneyFormat(resolveCurrency(state.currencyCode), locale) }
    val dates = remember(locale) { DateFormats(locale) }
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var renewing by rememberSaveable { mutableStateOf(startRenewing) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val document = state.document

    // Deleted elsewhere (or never existed): nothing to show.
    LaunchedEffect(state.loading, document) { if (!state.loading && document == null) onBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(document?.let { documentName(it) }.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = onEdit, enabled = document != null) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit_document))
                    }
                    IconButton(onClick = { confirmDelete = true }, enabled = document != null) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
                    }
                },
            )
        },
    ) { padding ->
        if (document == null) return@Scaffold
        val kind = DocumentKind.fromId(document.kind)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExpenseBadge(kind.icon, size = 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(documentName(document), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            documentStatus(document, today),
                            style = MaterialTheme.typography.titleMedium,
                            color = documentStatusColor(document, today),
                        )
                    }
                }
            }
            item {
                OutlinedCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    InfoRow(stringResource(R.string.document_expires), dates.day(LocalDate.fromEpochDays(document.expiryDate)))
                    document.costMinor?.let {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        InfoRow(stringResource(R.string.document_last_cost), money.format(it))
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    InfoRow(stringResource(R.string.document_reminders), stringResource(R.string.document_reminders_value))
                }
            }
            item {
                Button(
                    onClick = { renewing = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                ) {
                    Icon(Icons.Filled.Autorenew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.document_renew))
                }
            }
            item { SectionHeader(stringResource(R.string.document_payments)) }
            if (state.payments.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.document_payments_empty),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.payments, key = { it.id }) { payment ->
                ListItem(
                    modifier = Modifier.clickable { onOpenExpense(payment.id) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    headlineContent = { Text(dates.day(LocalDate.fromEpochDays(payment.date))) },
                    supportingContent = payment.note?.let { { Text(it, maxLines = 1) } },
                    trailingContent = {
                        Text(money.format(-payment.amountMinor), style = MaterialTheme.typography.titleMedium.tabular())
                    },
                )
            }
        }
    }

    if (renewing && document != null) {
        val name = documentName(document)
        val renewedText = stringResource(R.string.document_renewed, "%s")
        RenewSheet(
            document = document,
            title = stringResource(R.string.document_renew_title, name),
            currencyCode = state.currencyCode,
            offerTaxDeductible = state.offerTaxDeductible,
            today = today,
            onDismiss = { renewing = false },
            onRenew = { expiry, cost, addExpense, deductible ->
                renewing = false
                viewModel.renew(expiry, cost, addExpense, deductible, name)
                scope.launch { snackbar.showSnackbar(renewedText.format(dates.day(expiry))) }
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_document_title),
            body = stringResource(R.string.delete_entry_body),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        overlineContent = { Text(label) },
        headlineContent = { Text(value) },
    )
}

/** New last valid day (a year on by default), what was paid (last cost by default) and whether it goes to expenses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenewSheet(
    document: VehicleDocumentEntity,
    title: String,
    currencyCode: String?,
    offerTaxDeductible: Boolean,
    today: LocalDate,
    onDismiss: () -> Unit,
    onRenew: (LocalDate, Long?, Boolean, Boolean) -> Unit,
) {
    val locale = currentLocale()
    val currency = resolveCurrency(currencyCode)
    val digits = currency.defaultFractionDigits.coerceAtLeast(0)
    var expiry by rememberSaveable { mutableLongStateOf(DocumentRenewal.suggestedExpiry(document.expiryDate, today).toEpochDays()) }
    var cost by rememberSaveable { mutableStateOf(document.costMinor?.let { Money.toPlainString(it, digits) }.orEmpty()) }
    var addExpense by rememberSaveable { mutableStateOf(true) }
    var taxDeductible by rememberSaveable { mutableStateOf(false) }
    var costInvalid by rememberSaveable { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Frosted glass on the content, like ImportEditSheet.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = glassContainer(), dragHandle = null) {
        Column(Modifier.fillMaxWidth().clip(BottomSheetDefaults.ExpandedShape).glass(GLASS_DIALOG)) {
            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                    .padding(horizontal = 16.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                DateField(stringResource(R.string.document_expires), LocalDate.fromEpochDays(expiry)) { expiry = it.toEpochDays() }
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it; costInvalid = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.document_renew_cost)) },
                    suffix = { Text(currency.getSymbol(locale)) },
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
                    if (addExpense && offerTaxDeductible) {
                        Row(
                            Modifier.fillMaxWidth().toggleable(value = taxDeductible, role = Role.Checkbox, onValueChange = { taxDeductible = it }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.tax_deductible), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Checkbox(checked = taxDeductible, onCheckedChange = null)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val costMinor = cost.trim().takeIf { it.isNotEmpty() }?.let { Money.parseToMinor(it, digits) }
                        costInvalid = cost.isNotBlank() && (costMinor == null || costMinor < 0)
                        if (!costInvalid) onRenew(LocalDate.fromEpochDays(expiry), costMinor, addExpense, offerTaxDeductible && taxDeductible)
                    }) { Text(stringResource(R.string.document_renew)) }
                }
            }
        }
    }
}
