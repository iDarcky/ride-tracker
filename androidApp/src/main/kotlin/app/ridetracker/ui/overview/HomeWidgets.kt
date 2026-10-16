package app.ridetracker.ui.overview

import app.ridetracker.ui.common.GlassDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.clickable
import app.ridetracker.ui.theme.positiveColor
import androidx.compose.material3.FilterChip
import app.ridetracker.shared.domain.ComparisonMetric
import app.ridetracker.shared.domain.ExpenseCategory
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import app.ridetracker.shared.domain.Comparison
import app.ridetracker.shared.domain.ComparisonRow
import app.ridetracker.shared.domain.PeriodType
import kotlinx.datetime.isoDayNumber
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.TextButton
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ridetracker.R
import app.ridetracker.shared.domain.DocumentKind
import app.ridetracker.ui.vehicle.documentName
import app.ridetracker.ui.vehicle.icon
import app.ridetracker.ui.vehicle.documentStatus
import app.ridetracker.ui.vehicle.documentStatusColor
import app.ridetracker.shared.data.PlatformEntity
import app.ridetracker.shared.domain.HomeWidget
import app.ridetracker.shared.domain.PendingExpense
import app.ridetracker.shared.domain.Period
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.ExpenseBadge
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.PlatformBadge
import app.ridetracker.ui.common.icon
import app.ridetracker.ui.common.label
import app.ridetracker.ui.common.tabular
import app.ridetracker.ui.money.PaymentSplitContent
import java.text.NumberFormat
import kotlinx.datetime.LocalDate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import app.ridetracker.ui.common.currentLocale

/** The heat map needs enough trips to say anything about when you earn. */
const val MIN_TRIPS_FOR_HEAT = 20

/** Name shown in Customise. */
val HomeWidget.title: Int
    get() = when (this) {
        HomeWidget.TARGET -> R.string.widget_target
        HomeWidget.COMPARISON -> R.string.widget_comparison
        HomeWidget.BREAKDOWN -> R.string.widget_breakdown
        HomeWidget.METRICS -> R.string.widget_metrics
        HomeWidget.SPLIT -> R.string.split_by_app
        HomeWidget.ACTIVITY -> R.string.daily_activity
        HomeWidget.WHEN_YOU_EARN -> R.string.best_time
        HomeWidget.EXPENSE_GROUPS -> R.string.expenses_by_group
        HomeWidget.NEEDS_ATTENTION -> R.string.needs_attention
        HomeWidget.CASH_CARD -> R.string.card_and_cash
        HomeWidget.WEEK_SCREENSHOTS -> R.string.week_title
        HomeWidget.WEEK_PAYOUT -> R.string.widget_week_payout
        HomeWidget.REAL_PER_HOUR -> R.string.widget_real_per_hour
        HomeWidget.MONTH_DETAIL -> R.string.widget_month_detail
    }

/** Whether the card has something to show for this period (empty cards are left out, except while customising). */
fun HomeWidget.hasContent(state: OverviewUiState, attention: Attention): Boolean {
    val stats = state.stats ?: return false
    return when (this) {
        HomeWidget.BREAKDOWN -> stats.grossMinor > 0
        HomeWidget.METRICS -> stats.incomePlatformIds.isNotEmpty() || stats.tripCount > 0
        HomeWidget.SPLIT -> state.totals.isNotEmpty()
        HomeWidget.ACTIVITY -> state.period !is Period.Day && stats.days.size > 1 &&
            stats.days.any { it.totalMinor > 0 || it.tripCount > 0 }
        HomeWidget.WHEN_YOU_EARN -> stats.tripCount >= MIN_TRIPS_FOR_HEAT
        HomeWidget.EXPENSE_GROUPS -> state.expenseGroups.isNotEmpty()
        HomeWidget.NEEDS_ATTENTION -> false // pinned at the top instead (see NeedsAttention)
        HomeWidget.TARGET -> false // inside the money-kept card instead (see TotalCard)
        HomeWidget.COMPARISON -> state.comparisonCard != null
        HomeWidget.CASH_CARD -> state.breakdown?.payment != null
        HomeWidget.WEEK_SCREENSHOTS -> state.week != null
        HomeWidget.WEEK_PAYOUT -> state.payout != null
        HomeWidget.REAL_PER_HOUR -> state.realHourly != null
        HomeWidget.MONTH_DETAIL -> state.totalMinor != 0L || stats.tripCount > 0
    }
}

/** One Home card's content. */
@Composable
fun WidgetBody(
    widget: HomeWidget,
    state: OverviewUiState,
    attention: Attention,
    money: MoneyFormat,
    dates: DateFormats,
    percent: NumberFormat,
    onOpenDay: (LocalDate) -> Unit,
    onOpenTarget: () -> Unit,
    onCompareWhole: (Boolean) -> Unit = {},
    onImport: () -> Unit = {},
    onImportMonth: () -> Unit = {},
    onToggleDayOff: (LocalDate) -> Unit = {},
    /** Money without cents, for the cards Home shows by default (owner, 16 Oct 2026); rates keep theirs. */
    whole: MoneyFormat = money,
    onSeeAll: () -> Unit = {},
) {
    val stats = state.stats ?: return
    when (widget) {
        HomeWidget.BREAKDOWN -> MoneyBreakdown(stats, state.expenseMinor, state.tracksExpenses, whole)
        HomeWidget.METRICS -> Metrics(stats, state.platforms, money)
        HomeWidget.SPLIT -> PlatformSplit(state.totals, money)
        HomeWidget.ACTIVITY -> DailyActivity(stats.days, state.platforms, money, dates, onOpenDay, monthly = stats.monthly)
        HomeWidget.WHEN_YOU_EARN -> BestTimeToDrive(stats.heat, money)
        HomeWidget.EXPENSE_GROUPS -> ExpenseGroups(state, money, percent)
        HomeWidget.CASH_CARD -> state.breakdown?.payment?.let { payment ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 12.dp)) {
                    CardTitle(stringResource(R.string.card_and_cash), Modifier.padding(horizontal = 16.dp), info = app.ridetracker.ui.money.paymentNote(payment))
                    PaymentSplitContent(payment, money, percent)
                }
            }
        }
        HomeWidget.TARGET -> Unit
        HomeWidget.COMPARISON -> state.comparisonCard?.let { ComparisonCardView(it, state, money, dates, percent, onCompareWhole) }
        HomeWidget.WEEK_SCREENSHOTS -> state.week?.let { week ->
            // Today's income once it's in, while the period shown includes today.
            val todayMinor = stats.days.firstOrNull { it.date == week.today }?.totalMinor?.takeIf { week.todayIn && it > 0 }
            WeekCard(week, dates, todayMinor?.let { whole.format(it) }, onImport, onImportMonth, onToggleDayOff, onOpenDay)
        }
        HomeWidget.WEEK_PAYOUT -> state.payout?.let { WeekPayoutCard(it, state.platforms, state.fleet, whole) }
        HomeWidget.REAL_PER_HOUR -> state.realHourly?.let { RealHourlyCard(it, money) }
        HomeWidget.MONTH_DETAIL -> MonthDetailCard(state, money, whole, dates, percent, onSeeAll)
        HomeWidget.NEEDS_ATTENTION -> Unit
    }
}

/**
 * Today and this week's screenshots in one row (owner, 16 Oct 2026): each day's letter beside its dot, the dot split
 * into one slice per app, each slice filled in its app's colour once that day is in. Days still to come are faint; a
 * day off is a dash. Above it, today: its button while it's missing, or its amount. Sits above money kept on Home.
 * Tapping a day that's in opens it; a past day with nothing in opens "Didn't drive" / Open day / Import.
 */
@Composable
private fun WeekCard(
    week: WeekLog,
    dates: DateFormats,
    today: String?,
    onImport: () -> Unit,
    onImportMonth: () -> Unit,
    onToggleDayOff: (LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val colours = week.apps.map { platformChartColor(it.colorArgb) }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // One row: today on the left; its button, amount or "Day off" on the right (owner, 16 Oct 2026: narrower).
            Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.week_today_title, dates.weekdayFull(week.today).lowercase(currentLocale())),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                when {
                    !week.todayIn -> FilledTonalButton(onClick = onImport) { Text(stringResource(R.string.week_add_today)) }
                    else -> Text(
                        when {
                            week.today in week.daysOff -> stringResource(R.string.week_today_off)
                            today != null -> today
                            else -> stringResource(R.string.week_today_in)
                        },
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.days.forEach { day ->
                    val future = day > week.today
                    val isToday = day == week.today
                    val off = day in week.daysOff
                    val inDay = week.logged[day].orEmpty()
                    val dayOffLabel = stringResource(R.string.day_off_label, dates.weekdayFull(day))
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .then(
                                    when {
                                        future -> Modifier
                                        inDay.isNotEmpty() -> Modifier.clickable(onClickLabel = stringResource(R.string.open_day)) { onOpenDay(day) }
                                        else -> Modifier.clickable { menu = true }
                                    },
                                )
                                .padding(horizontal = 2.dp, vertical = 6.dp)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = if (off) {
                                        dayOffLabel
                                    } else {
                                        dates.weekdayFull(day) + ": " +
                                            week.apps.joinToString { app -> app.name + " " + if (app.id in inDay) "✓" else "–" }
                                    }
                                },
                        ) {
                            Text(
                                dates.weekdayNarrow(day),
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                if (off && inDay.isEmpty()) {
                                    // A day off: a dash, so it reads as done, not missing.
                                    Box(Modifier.width(12.dp).height(2.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
                                } else {
                                    SplitDot(
                                        filled = week.apps.map { it.id in inDay },
                                        colours = colours,
                                        ring = when {
                                            future -> MaterialTheme.colorScheme.outlineVariant
                                            isToday -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.outline
                                        },
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                        GlassDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            Text(
                                dates.day(day),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (off) R.string.week_day_drove else R.string.week_day_off)) },
                                onClick = { menu = false; onToggleDayOff(day) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.open_day)) },
                                onClick = { menu = false; onOpenDay(day) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_title)) },
                                onClick = { menu = false; onImport() },
                            )
                        }
                    }
                }
            }
            // Early in a month: how last month's files are coming in, until every app has them.
            week.files?.let { files ->
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (files.complete) {
                            stringResource(R.string.week_month_files_done, dates.monthName(files.month))
                        } else {
                            pluralStringResource(R.plurals.week_month_files, files.apps.size, dates.monthName(files.month), files.appsDone, files.apps.size)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (!files.complete) {
                        TextButton(onClick = onImportMonth) { Text(stringResource(R.string.import_title)) }
                    }
                }
            }
        }
    }
}

/**
 * A day's dot split into one slice per app (left to right for two: halves), each slice filled in its app's colour
 * when [filled], with a thin [ring] around the whole dot and a gap between slices.
 */
@Composable
private fun SplitDot(filled: List<Boolean>, colours: List<Color>, ring: Color, modifier: Modifier = Modifier) {
    val gap = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val n = filled.size.coerceAtLeast(1)
        val sweep = 360f / n
        filled.forEachIndexed { i, on ->
            if (on) drawArc(colours[i], 90f + i * sweep, sweep, useCenter = true)
        }
        if (n > 1 && filled.any { it }) {
            for (i in 0 until n) {
                val angle = Math.toRadians((90.0 + i * sweep))
                val r = size.minDimension / 2
                drawLine(gap, center, center + Offset((r * kotlin.math.cos(angle)).toFloat(), (r * kotlin.math.sin(angle)).toFloat()), 1.5.dp.toPx())
            }
        }
        drawCircle(ring, style = Stroke(1.dp.toPx()), radius = size.minDimension / 2 - 0.5.dp.toPx())
    }
}

/**
 * This period against the previous one (the same days while it's running, or the whole one): each figure now, before
 * and the change, green when it's better and red when it's worse.
 */
@Composable
private fun ComparisonCardView(
    card: ComparisonCard,
    state: OverviewUiState,
    money: MoneyFormat,
    dates: DateFormats,
    percent: NumberFormat,
    onCompareWhole: (Boolean) -> Unit,
) {
    val previousLabel = card.comparison.range.let { if (it.start == it.endInclusive) dates.day(it.start) else dates.range(it) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            CardTitle(stringResource(R.string.comparison_title, previousLabel), modifier = Modifier.padding(horizontal = 16.dp))
            if (card.canCompareWhole) {
                val whole = stringResource(if (state.period is Period.Week) R.string.comparison_whole_week else R.string.comparison_whole_month)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !card.whole, onClick = { onCompareWhole(false) }, label = { Text(stringResource(R.string.comparison_same_days)) })
                    FilterChip(selected = card.whole, onClick = { onCompareWhole(true) }, label = { Text(whole) })
                }
            }
            card.rows.forEach { row ->
                val label = when (row.metric) {
                    ComparisonMetric.INCOME -> stringResource(R.string.comparison_income)
                    ComparisonMetric.KEPT -> stringResource(R.string.money_kept)
                    ComparisonMetric.EXPENSES -> stringResource(R.string.nav_expenses)
                    ComparisonMetric.HOURS -> stringResource(R.string.comparison_hours)
                    ComparisonMetric.PER_HOUR -> stringResource(R.string.comparison_per_hour)
                    ComparisonMetric.TRIPS -> stringResource(R.string.nav_trips)
                    ComparisonMetric.PLATFORM -> state.platforms.firstOrNull { it.id == row.platformId }?.name.orEmpty()
                }
                @Composable
                fun value(v: Long?): String = when {
                    v == null -> "—"
                    row.metric == ComparisonMetric.HOURS -> stringResource(R.string.hours_minutes, (v / 60).toInt(), (v % 60).toInt())
                    row.metric == ComparisonMetric.TRIPS -> v.toString()
                    else -> money.format(v)
                }
                // Just the change; tap a row for what it was before.
                var showWas by rememberSaveable(row.metric, row.platformId) { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().clickable { showWas = !showWas }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value(row.current), style = MaterialTheme.typography.titleMedium.tabular())
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        ChangeText(row, percent)
                        if (showWas || row.change == null) {
                            Text(
                                stringResource(R.string.comparison_was, value(row.previous)),
                                style = MaterialTheme.typography.bodySmall.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Needs attention", pinned at the top of Home (not a movable card): one compact row per thing waiting, each with
 * its button; the first three, then "N more". Setup suggestions (Raportul Z, target) come last as chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NeedsAttention(
    attention: Attention,
    state: OverviewUiState,
    money: MoneyFormat,
    dates: DateFormats,
    onImport: () -> Unit,
    onAcceptDue: (PendingExpense) -> Unit,
    onSkipDue: (PendingExpense) -> Unit,
    onZReportDone: (LocalDate) -> Unit,
    onOpenZReport: () -> Unit,
    onDismissZReport: () -> Unit,
    onOpenTarget: () -> Unit,
    onDismissTarget: () -> Unit,
    onRenewDocument: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = buildList<@Composable () -> Unit> {
        attention.zReportDay?.let { day ->
            add {
                AttentionRow(
                    leading = { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.z_notification_title),
                    detail = stringResource(R.string.z_waiting, dates.day(day)),
                ) { FilledTonalButton(onClick = { onZReportDone(day) }) { Text(stringResource(R.string.z_done)) } }
            }
        }
        attention.documents.forEach { document ->
            add {
                AttentionRow(
                    leading = { ExpenseBadge(DocumentKind.fromId(document.kind).icon, size = 32.dp) },
                    title = documentName(document),
                    detail = documentStatus(document, state.today),
                    detailColor = documentStatusColor(document, state.today),
                ) { FilledTonalButton(onClick = { onRenewDocument(document.id) }) { Text(stringResource(R.string.document_renew)) } }
            }
        }
        attention.pending.forEach { item ->
            add {
                val category = ExpenseCategory.fromId(item.rule.category)
                AttentionRow(
                    leading = { ExpenseBadge(category.icon, size = 32.dp) },
                    title = item.rule.note ?: stringResource(category.label),
                    detail = money.format(-item.rule.amountMinor) + " · " + stringResource(R.string.due_on, dates.day(item.dueDate)),
                ) {
                    TextButton(onClick = { onSkipDue(item) }) { Text(stringResource(R.string.skip)) }
                    FilledTonalButton(onClick = { onAcceptDue(item) }) { Text(stringResource(R.string.add)) }
                }
            }
        }
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
            Text(
                stringResource(R.string.needs_attention),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            (if (expanded) rows else rows.take(VISIBLE_ATTENTION_ROWS)).forEach { it() }
            if (rows.size > VISIBLE_ATTENTION_ROWS) {
                val more = rows.size - VISIBLE_ATTENTION_ROWS
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(start = 8.dp)) {
                    Text(if (expanded) stringResource(R.string.show_less) else pluralStringResource(R.plurals.attention_more, more, more))
                }
            }
            if (attention.suggestZReport || attention.suggestTarget) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (attention.suggestTarget) {
                        Suggestion(Icons.Outlined.Flag, stringResource(R.string.target_suggest), onOpenTarget, onDismissTarget)
                    }
                    if (attention.suggestZReport) {
                        Suggestion(Icons.AutoMirrored.Outlined.ReceiptLong, stringResource(R.string.z_suggest), onOpenZReport, onDismissZReport)
                    }
                }
            }
        }
    }
}

private const val VISIBLE_ATTENTION_ROWS = 3

@Composable
private fun AttentionRow(
    leading: @Composable () -> Unit,
    title: String,
    detail: String?,
    detailColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = detailColor) }
        }
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** A setup suggestion: the chip opens its settings, "Not now" hides it for good. */
@Composable
private fun Suggestion(icon: ImageVector, label: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = onOpen,
            label = { Text(label) },
            leadingIcon = { Icon(icon, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
        )
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.z_not_now)) }
    }
}

/** "▲ 7 %", green when better and red when worse; nothing when there's no change to show. */
@Composable
fun ChangeText(row: ComparisonRow, percent: NumberFormat) {
    val change = row.change
    if (change == null || change == 0.0) return
    val better = (change > 0) == row.higherIsBetter
    Text(
        (if (change > 0) "▲ " else "▼ ") + percent.format(kotlin.math.abs(change)),
        style = MaterialTheme.typography.labelLarge.tabular(),
        color = if (better) positiveColor() else MaterialTheme.colorScheme.error,
    )
}

/**
 * "In detail": one line from each detail card (income and per hour with their change, hours · trips · distance, the
 * apps' shares) and "See all" for the full cards.
 */
@Composable
private fun MonthDetailCard(
    state: OverviewUiState,
    money: MoneyFormat,
    whole: MoneyFormat,
    dates: DateFormats,
    percent: NumberFormat,
    onSeeAll: () -> Unit,
) {
    val stats = state.stats ?: return
    val rows = state.comparisonCard?.rows.orEmpty()
    fun row(metric: ComparisonMetric) = rows.firstOrNull { it.metric == metric }
    val vs = state.comparisonCard?.comparison?.let { comparisonShort(it, dates) }
    OutlinedCard(onClick = onSeeAll, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardTitle(listOfNotNull(stringResource(R.string.widget_month_detail), vs).joinToString(" · "))
            @Composable
            fun Line(label: String, value: String, change: ComparisonRow? = null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.titleMedium.tabular())
                    if (change?.change != null && change.change != 0.0) {
                        Spacer(Modifier.width(8.dp))
                        ChangeText(change, percent)
                    }
                }
            }
            Line(stringResource(R.string.comparison_income), whole.format(state.totalMinor), row(ComparisonMetric.INCOME))
            stats.perHourMinor?.let { Line(stringResource(R.string.comparison_per_hour), money.format(it), row(ComparisonMetric.PER_HOUR)) }
            val distance = app.ridetracker.ui.common.distanceFormat()
            val activity = listOfNotNull(
                stats.onlineMinutes?.let { stringResource(R.string.hours_minutes, it / 60, it % 60) },
                stats.tripCount.takeIf { it > 0 }?.toString(),
                stats.distanceMeters?.let { distance.meters(it) },
            )
            if (activity.isNotEmpty()) Line(stringResource(R.string.detail_activity), activity.joinToString(" · "))
            val sum = state.totals.sumOf { it.totalMinor }
            if (state.totals.size > 1 && sum > 0) {
                Line(
                    stringResource(R.string.detail_split),
                    state.totals.joinToString(" · ") { it.name + " " + percent.format(it.totalMinor.toDouble() / sum) },
                )
            }
            // On the left, so the floating + button never covers it.
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.detail_see_all)) }
        }
    }
}

/** "vs Sept", "vs last week", "vs last Friday": the short form for chips and titles. */
@Composable
fun comparisonShort(comparison: Comparison, dates: DateFormats): String = when (comparison) {
    is Comparison.SameDaysPrevious, is Comparison.WholePrevious -> {
        val type = if (comparison is Comparison.SameDaysPrevious) comparison.type else (comparison as Comparison.WholePrevious).type
        if (type == PeriodType.MONTH) {
            stringResource(R.string.chip_vs_month, dates.monthShort(Period.Month.containing(comparison.range.start)))
        } else {
            stringResource(R.string.chip_vs_last_week)
        }
    }
    is Comparison.SameWeekdayLastWeek -> stringResource(
        when (comparison.range.start.dayOfWeek.isoDayNumber) {
            1 -> R.string.compare_last_weekday_1
            2 -> R.string.compare_last_weekday_2
            3 -> R.string.compare_last_weekday_3
            4 -> R.string.compare_last_weekday_4
            5 -> R.string.compare_last_weekday_5
            6 -> R.string.compare_last_weekday_6
            else -> R.string.compare_last_weekday_7
        },
    )
    is Comparison.PreviousDays -> pluralStringResource(R.plurals.compare_previous_days, comparison.days, comparison.days)
}

@Composable
private fun ExpenseGroups(state: OverviewUiState, money: MoneyFormat, percent: NumberFormat) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            CardTitle(stringResource(R.string.expenses_by_group), modifier = Modifier.padding(horizontal = 16.dp))
            state.expenseGroups.forEach { group ->
                val share = if (state.expenseMinor > 0) group.totalMinor.toFloat() / state.expenseMinor else 0f
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    leadingContent = { ExpenseBadge(group.group.icon) },
                    headlineContent = { Text(stringResource(group.group.label)) },
                    supportingContent = {
                        LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = MaterialTheme.colorScheme.tertiary)
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money.format(group.totalMinor), style = MaterialTheme.typography.titleMedium.tabular())
                            Text(percent.format(share), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                )
            }
        }
    }
}

/**
 * A card while customising: a header row (drag handle, name, remove) above the card itself, no extra border.
 * While dragging, the whole item lifts on a surface so it reads as picked up. [handle] is the drag-handle modifier.
 */
@Suppress("ModifierParameter") // [handle] goes on the drag handle, not on the frame.
@Composable
fun EditFrame(widget: HomeWidget, handle: Modifier, dragging: Boolean, onRemove: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (dragging) 3.dp else 0.dp,
        shadowElevation = if (dragging) 8.dp else 0.dp,
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}, modifier = handle) {
                    Icon(Icons.Filled.DragIndicator, contentDescription = stringResource(R.string.widget_drag, stringResource(widget.title)))
                }
                Text(stringResource(widget.title), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.widget_remove, stringResource(widget.title)))
                }
            }
            content()
        }
    }
}

/** Shown inside a frame when the card has nothing for this period. */
@Composable
fun WidgetPlaceholder() {
    Text(
        stringResource(R.string.widget_empty),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A hidden widget under "Add widget". */
@Composable
fun AddWidgetRow(widget: HomeWidget, onAdd: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = { Text(stringResource(widget.title)) },
        trailingContent = {
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.widget_add, stringResource(widget.title)))
            }
        },
    )
}
