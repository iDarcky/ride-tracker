package app.ridetracker.ui.overview

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.rememberSwipeToDismissBoxState
import java.time.format.TextStyle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.platform.LocalContext
import app.ridetracker.notifications.ZReportReminderScheduler
import androidx.compose.material3.DatePickerDefaults
import app.ridetracker.ui.common.GLASS_DIALOG
import app.ridetracker.ui.common.glassContainer
import app.ridetracker.ui.common.glass
import androidx.compose.ui.draw.clip
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import app.ridetracker.shared.domain.HomeWidget
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import app.ridetracker.R
import app.ridetracker.ui.common.ScrollToTopOnReselect
import app.ridetracker.shared.data.EntryWithPlatform
import app.ridetracker.shared.domain.Comparison
import app.ridetracker.shared.domain.ExpenseCategory
import app.ridetracker.shared.domain.Frequency
import app.ridetracker.shared.domain.PendingExpense
import app.ridetracker.shared.domain.Comparisons
import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.PeriodType
import app.ridetracker.shared.domain.type
import app.ridetracker.ui.common.LocalBottomBarSpace
import app.ridetracker.ui.common.MonthTitle
import app.ridetracker.ui.common.MenuButton
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.SectionHeader
import app.ridetracker.ui.common.PlatformBadge
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.ExpenseBadge
import app.ridetracker.ui.common.icon
import app.ridetracker.ui.common.label
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.common.tabular
import app.ridetracker.ui.common.pickerMillisToLocalDate
import app.ridetracker.ui.common.toPickerMillis
import app.ridetracker.ui.expense.frequencyName
import app.ridetracker.ui.theme.positiveColor
import app.ridetracker.ui.common.InfoButton
import app.ridetracker.shared.domain.TargetProgress
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onImport: () -> Unit,
    onImportMonth: () -> Unit,
    onOpenSummary: (DateRange) -> Unit,
    onOpenZReport: () -> Unit,
    onOpenTarget: () -> Unit,
    onRenewDocument: (Long) -> Unit,
    onOpenWrapped: (DateRange, Boolean) -> Unit,
    viewModel: OverviewViewModel = viewModel {
        OverviewViewModel(
            container.incomeRepository,
            container.expenseRepository,
            container.importRepository,
            container.recurringRepository,
            container.settingsRepository,
            container.vehicleRepository,
            container.period,
        )
    },
) {
    val baseState by viewModel.uiState.collectAsStateWithLifecycle()
    val wrappedOffer by viewModel.wrappedOffer.collectAsStateWithLifecycle()
    val target by viewModel.target.collectAsStateWithLifecycle()
    val comparisonCard by viewModel.comparisonCard.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    val monthEnd by viewModel.monthEnd.collectAsStateWithLifecycle()
    val payout by viewModel.payout.collectAsStateWithLifecycle()
    val dismissedText = stringResource(R.string.offer_dismissed)
    val state = baseState.copy(target = target, comparisonCard = comparisonCard, week = week, payout = payout)
    val attention by viewModel.attention.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val months by viewModel.months.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val addedText = stringResource(R.string.expense_added)
    // The card just swiped away: say where it went (Menu → Summaries). Shown here, not in the card, which has
    // already left the list.
    var justDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(justDismissed) {
        if (!justDismissed) return@LaunchedEffect
        snackbar.showSnackbar(dismissedText, duration = SnackbarDuration.Short)
        justDismissed = false
    }
    val locale = currentLocale()
    val money = remember(state.currencyCode, locale) { MoneyFormat(resolveCurrency(state.currencyCode), locale) }
    // Home's own cards show whole amounts (owner, 16 Oct 2026: too much text); Details and rates keep the cents.
    val whole = remember(state.currencyCode, locale) { MoneyFormat(resolveCurrency(state.currencyCode), locale, wholeUnits = true) }
    val dates = remember(locale) { DateFormats(locale) }
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    var showRangePicker by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    // "See all" from In detail: the full cards, on Home's own list (no screen change, like Customise).
    var details by rememberSaveable { mutableStateOf(false) }
    var order by remember { mutableStateOf(state.widgets) }
    val listState = rememberLazyListState()
    // The month title is the first item: once more than half of it has scrolled up under the top bar, the bar shows
    // the period instead.
    val headerHidden by remember {
        derivedStateOf {
            val title = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
            title == null || title.offset + title.size / 2 <= 0
        }
    }
    ScrollToTopOnReselect(listState)
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val moving = (from.key as? String)?.let(HomeWidget::fromId) ?: return@rememberReorderableLazyListState
        val target = (to.key as? String)?.let(HomeWidget::fromId) ?: return@rememberReorderableLazyListState
        order = order.toMutableList().apply { add(indexOf(target), removeAt(indexOf(moving))) }
    }
    BackHandler(enabled = editing) { editing = false }
    // Opening or closing Details starts at the top (only then, not when coming back from another screen).
    fun showDetails(show: Boolean) {
        details = show
        scope.launch { listState.scrollToItem(0) }
    }
    BackHandler(enabled = details) { showDetails(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    // Once the month header scrolls away, the bar says which period the numbers are for; tapping it
                    // goes back to the top.
                    val period = state.period
                    val periodLabel = when (period) {
                        is Period.All -> stringResource(R.string.all_time)
                        is Period.Custom -> dates.range(period.range)
                        else -> dates.period(period)
                    }
                    val title = when {
                        editing -> stringResource(R.string.widget_customise)
                        details -> stringResource(R.string.details_title)
                        headerHidden -> periodLabel
                        else -> stringResource(R.string.nav_home)
                    }
                    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
                        label = "home title",
                    ) { text ->
                        Text(
                            text,
                            maxLines = 1,
                            modifier = if (headerHidden && !editing && !details) {
                                Modifier.clickable(onClickLabel = stringResource(R.string.back_to_top)) {
                                    scope.launch { listState.animateScrollToItem(0) }
                                }
                            } else {
                                Modifier
                            },
                        )
                    }
                },
                navigationIcon = {
                    if (details) {
                        IconButton(onClick = { showDetails(false) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (editing) {
                        // Always in reach while the cards are being moved, however long the list.
                        TextButton(onClick = { order = HomeWidget.DEFAULT }) { Text(stringResource(R.string.widget_reset)) }
                        Button(
                            onClick = {
                                viewModel.setWidgets(order)
                                editing = false
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text(stringResource(R.string.done)) }
                    } else {
                        MenuButton()
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = LocalBottomBarSpace.current)) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + LocalBottomBarSpace.current + 24.dp,
            ),
        ) {
            if (details) {
                item(key = "details-period") {
                    val period = state.period
                    Text(
                        when (period) {
                            is Period.All -> stringResource(R.string.all_time)
                            is Period.Custom -> dates.range(period.range)
                            else -> dates.period(period)
                        },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                items(HomeWidget.DETAILS.filter { it.hasContent(state, attention) }, key = { "details-${it.id}" }) { widget ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        WidgetBody(
                            widget, state, attention, money, dates, percent,
                            onOpenDay = viewModel::openDay,
                            onOpenTarget = onOpenTarget,
                            onCompareWhole = viewModel::setCompareWhole,
                        )
                    }
                }
                return@LazyColumn
            }
            if (editing) {
                item(key = "edit-hint") {
                    Text(
                        stringResource(R.string.widget_hint),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                val period = state.period
                MonthTitle(
                    title = when (period) {
                        is Period.All -> stringResource(R.string.all_time)
                        is Period.Custom -> dates.range(period.range)
                        else -> dates.period(Period.Month.containing(period.range.start))
                    },
                    months = months,
                    selected = (period as? Period.Month),
                    dates = dates,
                    onMonth = viewModel::selectMonth,
                    current = Period.Month.containing(state.today).takeIf { period is Period.Month },
                    onCustom = { showRangePicker = true },
                    onOpen = viewModel::refreshMonths,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                PeriodTypeSelector(
                    selected = state.period.type,
                    onSelect = viewModel::selectType,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (state.period is Period.Week || state.period is Period.Day) {
                item {
                    PeriodNavigator(
                        label = dates.period(state.period),
                        showToday = state.today !in state.period.range.start..state.period.range.endInclusive,
                        onPrevious = viewModel::previous,
                        onNext = viewModel::next,
                        onToday = viewModel::goToToday,
                    )
                }
            } else {
                item { Spacer(Modifier.height(12.dp)) }
            }
            wrappedOffer?.let { (range, yearly) ->
                // Keyed by its month: the list restores a card's saved swipe state, and next month's card must not
                // come back already swiped.
                item(key = "wrapped-${range.start}") {
                    // Swipe it away either way; it stays in Menu → Summaries.
                    val dismiss = rememberSwipeToDismissBoxState()
                    SwipeToDismissBox(
                        state = dismiss,
                        backgroundContent = {},
                        onDismiss = {
                            viewModel.dismissOffer(range)
                            justDismissed = true
                        },
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    ) {
                        WrappedCard(range, yearly, onClick = { if (yearly) onOpenWrapped(range, true) else onOpenSummary(range) })
                    }
                }
            }
            // Things waiting for the driver, pinned above everything else; only when there are some.
            if (!editing && !attention.isEmpty()) {
                item(key = "attention") {
                    NeedsAttention(
                        attention, state, money, dates,
                        onImport = onImport,
                        onAcceptDue = { item ->
                            viewModel.accept(item)
                            scope.launch { snackbar.showSnackbar(addedText, duration = SnackbarDuration.Short) }
                        },
                        onSkipDue = viewModel::skip,
                        onZReportDone = { day ->
                            viewModel.zReportDone(day)
                            ZReportReminderScheduler.cancelNotification(context)
                        },
                        onOpenZReport = onOpenZReport,
                        onDismissZReport = viewModel::dismissZReportSuggestion,
                        onOpenTarget = onOpenTarget,
                        onDismissTarget = viewModel::dismissTargetSuggestion,
                        onRenewDocument = onRenewDocument,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    )
                }
            }
            // Today's job first: this week's screenshots sit above money kept (still movable and removable in Customise).
            val pinnedWeek = !editing && HomeWidget.WEEK_SCREENSHOTS in state.widgets && HomeWidget.WEEK_SCREENSHOTS.hasContent(state, attention)
            if (pinnedWeek) {
                item(key = "week") {
                    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        WidgetBody(
                            HomeWidget.WEEK_SCREENSHOTS, state, attention, money, dates, percent,
                            onOpenDay = viewModel::openDay,
                            onOpenTarget = onOpenTarget,
                            onImport = onImport,
                            onImportMonth = onImportMonth,
                            onToggleDayOff = viewModel::toggleDayOff,
                            whole = whole,
                        )
                    }
                }
            }
            item {
                // Last month's numbers can still change until its month-end files are in.
                val mayChange = monthEnd?.let { !it.complete && state.period == it.month } == true
                TotalCard(state, whole, dates, mayChange, onImportMonth, onOpenTarget, Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp))
            }
            val shown = if (editing) order else state.widgets.filter { it.hasContent(state, attention) && !(pinnedWeek && it == HomeWidget.WEEK_SCREENSHOTS) }
            items(shown, key = { it.id }) { widget ->
                ReorderableItem(reorder, key = widget.id, enabled = editing) { dragging ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        val body: @Composable () -> Unit = {
                            if (widget.hasContent(state, attention)) {
                                WidgetBody(
                                    widget, state, attention, money, dates, percent,
                                    onOpenDay = viewModel::openDay,
                                    onOpenTarget = onOpenTarget,
                                    onCompareWhole = viewModel::setCompareWhole,
                                    onImport = onImport,
                                    onImportMonth = onImportMonth,
                                    onToggleDayOff = viewModel::toggleDayOff,
                                    whole = whole,
                                    onSeeAll = { showDetails(true) },
                                )
                            } else {
                                WidgetPlaceholder()
                            }
                        }
                        if (editing) {
                            EditFrame(widget, Modifier.draggableHandle(), dragging, onRemove = { order = order - widget }, content = body)
                        } else {
                            body()
                        }
                    }
                }
            }
            if (editing) {
                val hidden = HomeWidget.MOVABLE - order.toSet()
                if (hidden.isNotEmpty()) {
                    item(key = "add-header") { SectionHeader(stringResource(R.string.widget_add_title)) }
                    items(hidden, key = { "add-${it.id}" }) { widget -> AddWidgetRow(widget) { order = order + widget } }
                }
            }
            if (!editing && !state.loading && state.entryCount == 0 && state.expenseMinor == 0L) {
                item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.empty_period),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(onClick = onImport, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.import_title)) }
                    }
                }
            }
            if (!editing) {
                item(key = "customise") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        OutlinedButton(onClick = {
                            order = state.widgets
                            editing = true
                        }) {
                            Icon(Icons.Filled.Tune, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.widget_customise))
                        }
                    }
                }
            }
        }
    }

    if (showRangePicker) {
        RangePickerDialog(
            initial = state.period.range,
            onDismiss = { showRangePicker = false },
            onConfirm = {
                viewModel.setCustomRange(it)
                showRangePicker = false
            },
        )
    }
}

// Order follows the design system: Month first. Custom lives in the title's month list.
private val periodLabels = listOf(
    PeriodType.MONTH to R.string.period_month,
    PeriodType.WEEK to R.string.period_week,
    PeriodType.DAY to R.string.period_day,
    PeriodType.ALL to R.string.period_all,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PeriodTypeSelector(selected: PeriodType, onSelect: (PeriodType) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        periodLabels.forEachIndexed { index, (type, label) ->
            ToggleButton(
                checked = selected == type,
                onCheckedChange = { onSelect(type) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    periodLabels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                // Whole words even with large text: shrink to fit instead of "Mont".
                Text(stringResource(label), maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = LocalTextStyle.current.fontSize))
            }
        }
    }
}

/** Week and Day only: previous / next and "Back to today". */
@Composable
private fun PeriodNavigator(
    label: String,
    showToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_period))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (showToday) {
                TextButton(onClick = onToday) { Text(stringResource(R.string.back_to_today)) }
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_period))
        }
    }
}

/**
 * Money kept (or income) for the period, big, with its change as one short line. In a month with a target, the
 * target sits in the same card: a ring with the share done, what's left per driving day and the pace (tap for its
 * settings). Explanations are behind ⓘ.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TotalCard(
    state: OverviewUiState,
    money: MoneyFormat,
    dates: DateFormats,
    mayChange: Boolean,
    onImportMonth: () -> Unit,
    onOpenTarget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = state.target?.takeIf { state.period is Period.Month && it.month == state.period }
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    val shape = MaterialTheme.shapes.extraLarge
    val content: @Composable () -> Unit = {
        // Hide the figures until real data arrives, so a placeholder "0" never flashes.
        Column(Modifier.padding(24.dp).alpha(if (state.loading) 0f else 1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(if (state.tracksExpenses) R.string.money_kept else R.string.total_income),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        InfoButton(stringResource(if (state.tracksExpenses) R.string.kept_info else R.string.income_info))
                    }
                    Text(
                        money.format(state.headlineMinor),
                        style = MaterialTheme.typography.displayMedium.tabular(),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = MaterialTheme.typography.displayMedium.fontSize),
                    )
                    val previous = state.previousHeadlineMinor
                    val comparison = state.comparison
                    if (previous != null && comparison != null) {
                        ComparisonLine(state.headlineMinor, previous, comparison, money, dates)
                    }
                }
                if (target != null) {
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(progress = { target.fraction }, modifier = Modifier.size(88.dp))
                        Text(
                            NumberFormat.getPercentInstance(currentLocale()).format(target.fraction.toDouble()),
                            style = MaterialTheme.typography.titleMedium.tabular(),
                        )
                    }
                } else {
                    state.stats?.let { Sparkline(it.days, LocalContentColor.current.copy(alpha = 0.55f), Modifier.padding(start = 16.dp).width(96.dp).height(40.dp)) }
                }
            }
            target?.let { TargetLines(it, money) }
            state.stats?.estimatedMinor?.takeIf { it > 0 }?.let {
                Text(stringResource(R.string.includes_estimated, money.format(it)), style = MaterialTheme.typography.bodySmall)
            }
            if (mayChange) MayStillChangeChip(onImportMonth, Modifier.padding(top = 8.dp))
        }
    }
    val sized = modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
    if (target != null) {
        Card(onClick = onOpenTarget, modifier = sized, colors = colors, shape = shape) { content() }
    } else {
        Card(modifier = sized, colors = colors, shape = shape) { content() }
    }
}

/** The target in two short lines: "Target: 1,810 of 3,000", then per day and days left (or reached / missed) and the pace. */
@Composable
private fun TargetLines(p: TargetProgress, money: MoneyFormat) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.target_short, money.format(p.achievedMinor), money.format(p.targetMinor)),
            style = MaterialTheme.typography.bodyLarge.tabular(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val first = when {
                p.reached && p.achievedMinor > p.targetMinor -> stringResource(R.string.target_reached, money.format(p.achievedMinor - p.targetMinor))
                p.reached -> stringResource(R.string.target_reached_exact)
                p.finished -> stringResource(R.string.target_missed, money.format(p.remainingMinor))
                else -> p.perDayMinor?.let { pluralStringResource(R.plurals.target_per_day_short, p.drivingDaysLeft, money.format(it), p.drivingDaysLeft) }
            }
            first?.let { Text(it, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium.tabular()) }
            val pace = p.paceMinor.takeIf { !p.reached }
            if (pace != null) {
                if (first != null) Text("  ·  ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    when {
                        pace > 0 -> stringResource(R.string.target_ahead_short, money.format(pace))
                        pace < 0 -> stringResource(R.string.target_behind_short, money.format(-pace))
                        else -> stringResource(R.string.target_on_pace)
                    },
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                    fontWeight = FontWeight.Medium,
                    color = if (pace < 0) MaterialTheme.colorScheme.error else positiveColor(),
                )
            }
        }
    }
}

/** "▼ 5.5 % vs Sept": the change in colour, then what it's compared with, short. */
@Composable
private fun ComparisonLine(current: Long, previous: Long, comparison: Comparison, money: MoneyFormat, dates: DateFormats) {
    val locale = currentLocale()
    val diff = current - previous
    val up = diff >= 0
    val color = if (up) positiveColor() else MaterialTheme.colorScheme.error
    val change = Comparisons.percentChange(current, previous)?.let {
        (if (up) "▲ " else "▼ ") + NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }.format(abs(it))
    } ?: ((if (up) "+" else "−") + money.format(abs(diff)))
    val vs = comparisonShort(comparison, dates)
    // TalkBack says "up" / "down" instead of the triangle.
    val spoken = stringResource(if (up) R.string.change_up else R.string.change_down) + " " + change.removePrefix("▲ ").removePrefix("▼ ") + " " + vs
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium)) { append(change) }
            append(" ")
            append(vs)
        },
        modifier = Modifier.padding(top = 4.dp).semantics { contentDescription = spoken },
        style = MaterialTheme.typography.bodyMedium.tabular(),
    )
}

/** A due recurring expense: what, when, how much, and Add / Skip. */
@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.titleMedium.tabular())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(initial: DateRange, onDismiss: () -> Unit, onConfirm: (DateRange) -> Unit) {
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial.start.toPickerMillis(),
        initialSelectedEndDateMillis = initial.endInclusive.toPickerMillis(),
    )
    DatePickerDialog(
        modifier = Modifier.clip(DatePickerDefaults.shape).glass(GLASS_DIALOG),
        colors = DatePickerDefaults.colors(containerColor = glassContainer()),
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedStartDateMillis != null,
                onClick = {
                    val start = pickerState.selectedStartDateMillis!!.pickerMillisToLocalDate()
                    val end = pickerState.selectedEndDateMillis?.pickerMillisToLocalDate() ?: start
                    onConfirm(DateRange(start, end))
                },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DateRangePicker(colors = DatePickerDefaults.colors(containerColor = glassContainer()), state = pickerState, modifier = Modifier.weight(1f))
    }
}

/** "Your September, wrapped": opens the story (Home offers it early in a month, and in December for the year). */
@Composable
private fun WrappedCard(range: DateRange, yearly: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val name = if (yearly) range.start.year.toString()
    else java.time.Month.of(range.start.month.ordinal + 1).getDisplayName(TextStyle.FULL_STANDALONE, locale)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                // The year opens its Wrapped; a month opens its summary, with its Wrapped inside.
                Text(
                    stringResource(if (yearly) R.string.wrapped_ready else R.string.summary_month_ready, name).replaceFirstChar { it.titlecase(locale) },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(if (yearly) R.string.wrapped_ready_body else R.string.summary_month_ready_body), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** "May still change: month-end files missing", on last month's numbers until its files are in; opens End of month. */
@Composable
fun MayStillChangeChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.may_still_change)) },
        leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
        colors = AssistChipDefaults.assistChipColors(labelColor = LocalContentColor.current, leadingIconContentColor = LocalContentColor.current),
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = LocalContentColor.current.copy(alpha = 0.4f)),
        modifier = modifier,
    )
}
