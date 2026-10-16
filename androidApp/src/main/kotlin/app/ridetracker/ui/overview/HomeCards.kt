package app.ridetracker.ui.overview

import app.ridetracker.ui.common.byUnit
import app.ridetracker.ui.common.distanceFormat
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.ridetracker.ui.common.isLargeText
import androidx.compose.foundation.layout.fillMaxSize
import app.ridetracker.ui.theme.positiveColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.pluralStringResource
import kotlinx.datetime.isoDayNumber
import app.ridetracker.R
import kotlinx.datetime.toJavaLocalDate
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.data.PlatformEntity
import app.ridetracker.shared.data.PlatformTotal
import app.ridetracker.shared.domain.DayIncome
import app.ridetracker.shared.domain.HomeStats
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.tabular
import app.ridetracker.ui.common.InfoButton
import androidx.compose.foundation.layout.heightIn
import kotlinx.datetime.LocalDate
import java.text.NumberFormat
import java.time.format.TextStyle
import kotlin.math.abs

/**
 * Chart colours. Money parts use a colour-blind-checked trio (validated light and dark: worst adjacent
 * CVD ΔE 9.2 / 9.4); apps use their badge colour so the chart matches the badges, with a legend always.
 */
private object ChartColors {
    val keptLight = Color(0xFF1BAF7A)
    val keptDark = Color(0xFF199E70)
    val feesLight = Color(0xFFEB6834)
    val feesDark = Color(0xFFD95926)
    val expensesLight = Color(0xFF2A78D6)
    val expensesDark = Color(0xFF3987E5)

    /** Sequential blue ramp, near-zero to busiest (light), for the heat map. */
    val heatLow = Color(0xFFCDE2FB)
    val heatHigh = Color(0xFF104281)
    val heatLowDark = Color(0xFF184F95)
    val heatHighDark = Color(0xFFCDE2FB)
}

@Composable
private fun isDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

@Composable
private fun keptColor() = if (isDark()) ChartColors.keptDark else ChartColors.keptLight

@Composable
private fun feesColor() = if (isDark()) ChartColors.feesDark else ChartColors.feesLight

@Composable
private fun expensesColor() = if (isDark()) ChartColors.expensesDark else ChartColors.expensesLight

/** An app's badge colour, or the text colour when the badge would vanish on the background (Uber black in dark mode). */
@Composable
fun platformChartColor(argb: Long): Color {
    val color = Color(argb)
    val surface = MaterialTheme.colorScheme.surface
    return if (abs(color.luminance() - surface.luminance()) < 0.2f) MaterialTheme.colorScheme.onSurface else color
}

/**
 * Gross split into kept, platform fees and expenses (owner, 16 Oct 2026: option 3a): a ring with the share kept in the
 * middle, and beside it the gross, then kept, fees and expenses, each with its colour.
 * What the gross is made of is behind ⓘ.
 */
@Composable
fun MoneyBreakdown(stats: HomeStats, expenseMinor: Long, tracksExpenses: Boolean, money: MoneyFormat, modifier: Modifier = Modifier) {
    val fees = -stats.feesMinor
    val kept = stats.grossMinor - fees - expenseMinor
    val percent = NumberFormat.getPercentInstance(currentLocale())
    val gross = buildList {
        add(stringResource(R.string.gross_fares, money.format(stats.faresMinor)))
        if (stats.bonusesAndTipsMinor != 0L) add(stringResource(R.string.gross_bonuses, money.format(stats.bonusesAndTipsMinor)))
        if (stats.otherIncomeMinor != 0L) add(stringResource(R.string.gross_other, money.format(stats.otherIncomeMinor)))
    }
    val info = stringResource(R.string.of_gross, money.format(stats.grossMinor), gross.joinToString(" + ")) +
        if (stats.feesKnownForAll) "" else "\n" + stringResource(R.string.fees_partial)
    val keptColor = keptColor()
    val feesColor = feesColor()
    val expensesColor = expensesColor()
    val ring = stats.grossMinor > 0 && kept >= 0
    OutlinedCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (ring) {
                val keptLabel = stringResource(if (tracksExpenses) R.string.kept else R.string.earned)
                val keptShare = percent.format(kept.toDouble() / stats.grossMinor)
                Box(
                    Modifier.size(104.dp).semantics(mergeDescendants = true) { contentDescription = "$keptLabel $keptShare" },
                    contentAlignment = Alignment.Center,
                ) {
                    val parts = listOf(kept to keptColor, fees to feesColor, expenseMinor to expensesColor).filter { it.first > 0 }
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 12.dp.toPx()
                        val inset = stroke / 2
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        val gap = if (parts.size > 1) 2f else 0f
                        var start = -90f
                        parts.forEach { (amount, color) ->
                            val sweep = 360f * amount / stats.grossMinor
                            drawArc(color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                            start += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(keptShare, style = MaterialTheme.typography.titleLarge.tabular(), fontWeight = FontWeight.SemiBold, color = positiveColor())
                        Text(keptLabel.lowercase(currentLocale()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.gross_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    InfoButton(info)
                }
                // One big number per screen (owner, 16 Oct 2026): money kept above; the gross is a figure, not a headline.
                Text(
                    money.format(stats.grossMinor),
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                // Every colour on the ring has its line, kept included (owner, 16 Oct 2026).
                PartRow(stringResource(if (tracksExpenses) R.string.kept else R.string.earned), money.format(kept), keptColor)
                if (fees > 0) PartRow(stringResource(R.string.fees), money.format(fees), feesColor)
                if (expenseMinor > 0) PartRow(stringResource(R.string.nav_expenses), money.format(expenseMinor), expensesColor)
            }
        }
    }
}

/** "● Fees      346 EUR": a dot in the part's colour, its name and amount. */
@Composable
private fun PartRow(label: String, value: String, color: Color) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall.tabular())
    }
}

/** One metric tile: value (or a dash), label, and either what it covers ("Bolt only") or what would fill it. */
private data class Tile(val value: String?, val label: String, val note: String?)

/**
 * Trips, paid km, hours online, money per hour and per paid km. Always all five, so drivers learn what each
 * report adds: a missing figure shows a dash and a hint, a partial one names the platforms it covers.
 */
@Composable
fun Metrics(stats: HomeStats, platforms: List<PlatformEntity>, money: MoneyFormat, modifier: Modifier = Modifier) {
    if (stats.incomePlatformIds.isEmpty() && stats.tripCount == 0) return
    val locale = currentLocale()
    val names = platforms.associate { it.id to it.name }
    val active = stats.incomePlatformIds + stats.tripPlatformIds
    @Composable
    fun coverage(covered: Set<Long>, missingHint: Int): String? = when {
        covered.isEmpty() -> stringResource(missingHint)
        (active - covered).isNotEmpty() -> stringResource(R.string.only_platforms, covered.mapNotNull { names[it] }.sorted().joinToString(", "))
        else -> null
    }
    val hours = stats.onlineMinutes?.let { stringResource(R.string.hours_minutes, it / 60, it % 60) }
    val distance = distanceFormat()
    val km = stats.distanceMeters?.let { distance.meters(it) }
    val tiles = listOf(
        Tile(stats.tripCount.takeIf { it > 0 }?.toString(), stringResource(R.string.metric_trips), coverage(stats.tripPlatformIds, R.string.hint_trips)),
        Tile(km, stringResource(byUnit(R.string.metric_km, R.string.metric_miles)), coverage(stats.kmPlatformIds, byUnit(R.string.hint_km, R.string.hint_miles))),
        Tile(hours, stringResource(R.string.metric_online), coverage(stats.hourPlatformIds, R.string.hint_hours)),
        Tile(stats.perHourMinor?.let { money.format(it) }, stringResource(R.string.metric_per_hour), coverage(stats.hourPlatformIds, R.string.hint_hours)),
        Tile(
            stats.perKmMinor?.let { money.format(distance.unit.perUnit(it)) },
            stringResource(byUnit(R.string.metric_per_km, R.string.metric_per_mile)),
            coverage(stats.kmPlatformIds, byUnit(R.string.hint_km, R.string.hint_miles)),
        ),
    )
    if (tiles.all { it.value == null }) {
        // Nothing to show yet: one line instead of five empty tiles.
        OutlinedCard(modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.metrics_empty),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(tiles.take(3), tiles.drop(3)).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile -> MetricTile(tile, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun MetricTile(tile: Tile, modifier: Modifier = Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(
                tile.value ?: "—",
                style = MaterialTheme.typography.titleLarge.tabular(),
                color = if (tile.value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                // "12 h 48 min" in a third of the width: shrink rather than cut off.
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = MaterialTheme.typography.titleLarge.fontSize),
            )
            Text(tile.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            tile.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (tile.value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

/** Donut of income per app with a legend (name, amount, share). */
@Composable
fun PlatformSplit(totals: List<PlatformTotal>, money: MoneyFormat, modifier: Modifier = Modifier) {
    val sum = totals.sumOf { it.totalMinor }.takeIf { it > 0 } ?: return
    val percent = NumberFormat.getPercentInstance(currentLocale())
    val colors = totals.map { platformChartColor(it.colorArgb) }
    val gap = MaterialTheme.colorScheme.surface
    val large = isLargeText()
    ChartCard(stringResource(R.string.split_by_app), modifier) {
        // With large text the legend goes under the donut, full width, so names aren't broken ("Ube / r").
        val layout: @Composable (@Composable () -> Unit, @Composable () -> Unit) -> Unit = { donut, legend ->
            if (large) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { donut(); legend() }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) { donut(); Spacer(Modifier.width(20.dp)); legend() }
            }
        }
        layout({
            Canvas(Modifier.size(96.dp)) {
                val stroke = 20.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                var startAngle = -90f
                totals.forEachIndexed { i, t ->
                    val sweep = 360f * t.totalMinor / sum
                    drawArc(colors[i], startAngle, sweep, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                    // 2dp surface gap between segments.
                    if (totals.size > 1) {
                        drawArc(gap, startAngle - 0.8f, 1.6f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke + 2))
                    }
                    startAngle += sweep
                }
            }
        }, {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                totals.forEachIndexed { i, t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i]))
                        Spacer(Modifier.width(8.dp))
                        Text(t.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money.format(t.totalMinor), style = MaterialTheme.typography.bodyMedium.tabular())
                            Text(
                                percent.format(t.totalMinor.toDouble() / sum),
                                style = MaterialTheme.typography.bodySmall.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        })
    }
}

private enum class ActivityMetric { MONEY, TRIPS }

/**
 * Money (stacked per platform) or trips per day, Uber Driver style: a summary line, labelled days, a dashed
 * average line, and one selected day whose figures show above the chart (tap a bar to move it).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DailyActivity(
    days: List<DayIncome>,
    platforms: List<PlatformEntity>,
    money: MoneyFormat,
    dates: DateFormats,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    /** Bars are months (long periods such as "All"). */
    monthly: Boolean = false,
) {
    val hasMoney = days.any { it.totalMinor > 0 }
    val hasTrips = days.any { it.tripCount > 0 }
    if (!hasMoney && !hasTrips) return
    var metric by remember(hasMoney) { mutableStateOf(if (hasMoney) ActivityMetric.MONEY else ActivityMetric.TRIPS) }
    fun value(d: DayIncome): Long = if (metric == ActivityMetric.MONEY) d.totalMinor else d.tripCount.toLong()
    val resources = LocalResources.current
    // Money, or "21 trips" with the language's plural form (also used while drawing, so not composable).
    fun format(v: Long): String =
        if (metric == ActivityMetric.MONEY) money.format(v) else resources.getQuantityString(R.plurals.trip_count, v.toInt(), v.toInt())

    val driven = days.indices.filter { value(days[it]) > 0 }
    var selected by remember(days, metric) { mutableStateOf(driven.lastOrNull()) }
    val max = days.maxOf { value(it) }.coerceAtLeast(1)
    val average = if (driven.isEmpty()) 0L else driven.sumOf { value(days[it]) } / driven.size
    val best = driven.maxByOrNull { value(days[it]) }

    val locale = currentLocale()
    val order = platforms.map { it.id }
    val colorOf = platforms.associate { it.id to platformChartColor(it.colorArgb) }
    val names = platforms.associate { it.id to it.name }
    val tripsColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val averageColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val selectedLabelStyle = labelStyle.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val weekdayNames = remember(locale) {
        java.time.DayOfWeek.entries.associateWith { it.getDisplayName(TextStyle.SHORT, locale).trimEnd('.').take(3) }
    }
    val monthFormat = remember(locale) { java.time.format.DateTimeFormatter.ofPattern("LLL", locale) }
    fun bucketName(date: LocalDate) = if (monthly) dates.period(Period.Month.containing(date)) else dates.day(date)
    fun bucketShort(date: LocalDate) = if (monthly) dates.period(Period.Month.containing(date)) else dates.shortDay(date)
    // Which bars get a label under the axis: months (every third when there are many), all days of a week,
    // otherwise every seventh day.
    val labelled: Map<Int, String> = days.indices.mapNotNull { i ->
        val d = days[i].date
        when {
            monthly -> if (days.size <= 12 || i % 3 == 0) i to d.toJavaLocalDate().format(monthFormat).trimEnd('.') else null
            days.size <= 7 -> i to (weekdayNames[java.time.DayOfWeek.of(d.dayOfWeek.isoDayNumber)] ?: "")
            d.day in setOf(1, 8, 15, 22, 29) && days.first().date.day == 1 -> i to d.day.toString()
            days.first().date.day != 1 && i % 7 == 0 -> i to dates.shortDay(d)
            else -> null
        }
    }.toMap()
    val shown = platforms.filter { p -> days.any { (it.byPlatform[p.id] ?: 0) > 0 } }

    ChartCard(stringResource(if (monthly) R.string.monthly_activity else R.string.daily_activity), modifier) {
        if (hasMoney && hasTrips) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                listOf(ActivityMetric.MONEY to R.string.activity_money, ActivityMetric.TRIPS to R.string.activity_trips).forEachIndexed { index, (m, label) ->
                    ToggleButton(
                        checked = metric == m,
                        onCheckedChange = { metric = m },
                        modifier = Modifier.weight(1f),
                        shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    ) { Text(stringResource(label)) }
                }
            }
        }
        // Summary: how many days, the best one, the average per day driven.
        Text(
            buildString {
                append(pluralStringResource(if (monthly) R.plurals.months_driven else R.plurals.days_driven, driven.size, driven.size))
                if (best != null) append(" · ").append(stringResource(R.string.best_day, bucketShort(days[best].date), format(value(days[best]))))
                if (driven.size > 1) {
                    append(" · ").append(stringResource(if (monthly) R.string.average_per_month else R.string.average_per_day, format(average)))
                }
            },
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The selected day, above the chart.
        val pick = selected?.let { days[it] }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(pick?.let { bucketName(it.date) } ?: stringResource(R.string.tap_a_bar), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (pick != null) Text(format(value(pick)), style = MaterialTheme.typography.titleLarge.tabular())
        }
        // TalkBack: what the chart is, the selected day and its figure, and actions to move along the days
        // (the bars themselves are drawn, so they can't be focused one by one).
        val chartDescription = stringResource(if (monthly) R.string.chart_bars_month_description else R.string.chart_bars_description)
        val previousLabel = stringResource(if (monthly) R.string.chart_previous_month else R.string.chart_previous_day)
        val nextLabel = stringResource(if (monthly) R.string.chart_next_month else R.string.chart_next_day)
        val selectedState = pick?.let { bucketName(it.date) + ": " + format(value(it)) }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .semantics {
                    contentDescription = chartDescription
                    selectedState?.let { stateDescription = it }
                    val at = selected
                    customActions = listOfNotNull(
                        driven.lastOrNull { at == null || it < at }?.let { i -> CustomAccessibilityAction(previousLabel) { selected = i; true } },
                        driven.firstOrNull { at == null || it > at }?.let { i -> CustomAccessibilityAction(nextLabel) { selected = i; true } },
                    )
                }
                .pointerInput(days, metric) {
                    detectTapGestures { offset ->
                        val index = (offset.x / (size.width.toFloat() / days.size)).toInt().coerceIn(0, days.lastIndex)
                        if (value(days[index]) > 0) selected = index
                    }
                },
        ) {
            val axisSpace = 18.dp.toPx()
            val chartHeight = size.height - axisSpace
            val slot = size.width / days.size
            val gap = (slot * 0.25f).coerceIn(2.dp.toPx(), 8.dp.toPx())
            val barWidth = slot - gap
            drawLine(gridColor, Offset(0f, chartHeight), Offset(size.width, chartHeight), 1.dp.toPx())
            days.forEachIndexed { i, day ->
                val x = i * slot + gap / 2
                val faded = selected != null && selected != i
                val segments: List<Pair<Color, Long>> = if (metric == ActivityMetric.MONEY) {
                    order.mapNotNull { id -> day.byPlatform[id]?.takeIf { it > 0 }?.let { (colorOf[id] ?: gridColor) to it } }
                } else {
                    listOfNotNull(day.tripCount.toLong().takeIf { it > 0 }?.let { tripsColor to it })
                }
                var top = chartHeight
                // Estimated days (no screenshot) are drawn hollow, so they read differently without relying on colour.
                val estimated = metric == ActivityMetric.MONEY && day.estimatedMinor > 0 && day.estimatedMinor >= day.totalMinor
                segments.forEachIndexed { s, (color, v) ->
                    val h = chartHeight * v / max
                    val isTop = s == segments.lastIndex
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(
                        color = if (faded) lerp(color, surface, 0.55f) else color,
                        topLeft = if (estimated) Offset(x + stroke / 2, top - h + stroke / 2) else Offset(x, top - h),
                        size = if (estimated) Size(barWidth - stroke, (h - stroke).coerceAtLeast(0f)) else Size(barWidth, h),
                        cornerRadius = if (isTop) CornerRadius(4.dp.toPx().coerceAtMost(barWidth / 2)) else CornerRadius.Zero,
                        style = if (estimated) Stroke(stroke) else androidx.compose.ui.graphics.drawscope.Fill,
                    )
                    top -= h
                    if (!isTop) drawLine(surface, Offset(x, top), Offset(x + barWidth, top), 2.dp.toPx())
                }
                labelled[i]?.let { text ->
                    val layout = measurer.measure(text, if (selected == i) selectedLabelStyle else labelStyle)
                    val lx = (x + barWidth / 2 - layout.size.width / 2).coerceIn(0f, size.width - layout.size.width)
                    drawText(layout, topLeft = Offset(lx, chartHeight + 4.dp.toPx()))
                }
            }
            if (driven.size > 1) {
                val y = chartHeight - chartHeight * average / max
                drawLine(averageColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                val layout = measurer.measure(format(average), labelStyle)
                drawText(layout, topLeft = Offset(size.width - layout.size.width, (y - layout.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
            }
        }
        if (metric == ActivityMetric.MONEY && days.any { it.estimatedMinor > 0 }) {
            Text(
                stringResource(R.string.estimated_bars),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (metric == ActivityMetric.MONEY && shown.size > 1) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                shown.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(colorOf.getValue(p.id)))
                        Spacer(Modifier.width(8.dp))
                        Text(p.name, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (pick != null) {
            if (metric == ActivityMetric.MONEY && pick.byPlatform.size > 1) {
                pick.byPlatform.entries.sortedBy { order.indexOf(it.key) }.forEach { (id, v) ->
                    Text(
                        (names[id] ?: "") + " · " + money.format(v),
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // On the left, so the floating + button never covers it.
            TextButton(onClick = { onOpenDay(pick.date) }, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(if (monthly) R.string.open_month else R.string.open_day))
            }
        }
    }
}

/** Trip fares by weekday and 4-hour slot, one blue from light (little) to dark (most); busiest cell outlined. */
@Composable
fun BestTimeToDrive(heat: List<List<Long>>, money: MoneyFormat, modifier: Modifier = Modifier) {
    val max = heat.flatten().maxOrNull()?.takeIf { it > 0 } ?: return
    val locale = currentLocale()
    val dark = isDark()
    val low = if (dark) ChartColors.heatLowDark else ChartColors.heatLow
    val high = if (dark) ChartColors.heatHighDark else ChartColors.heatHigh
    val outline = MaterialTheme.colorScheme.onSurface
    val weekdays = remember(locale) {
        java.time.DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, locale).trimEnd('.').replaceFirstChar { c -> c.titlecase(locale) } }
    }
    val slots = listOf("00–04", "04–08", "08–12", "12–16", "16–20", "20–24")
    var picked by remember(heat) { mutableStateOf<Pair<Int, Int>?>(null) }
    // Full weekday names for TalkBack ("Monday", not "Mon").
    val weekdaysFull = remember(locale) {
        java.time.DayOfWeek.entries.map { it.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { c -> c.titlecase(locale) } }
    }
    val resources = LocalResources.current

    ChartCard(stringResource(R.string.best_time), modifier, info = stringResource(R.string.best_time_caption)) {
        Row {
            Spacer(Modifier.width(48.dp))
            weekdays.forEach { d ->
                Text(d, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        slots.forEachIndexed { slot, label ->
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(48.dp), style = MaterialTheme.typography.labelSmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                (0 until 7).forEach { day ->
                    val value = heat[day][slot]
                    val busiest = value == max
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 1.5.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (value == 0L) MaterialTheme.colorScheme.surface else lerp(low, high, (value.toFloat() / max).coerceIn(0.12f, 1f)))
                            .then(if (busiest) Modifier.border(2.dp, outline, RoundedCornerShape(4.dp)) else Modifier)
                            .then(if (value == 0L) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)) else Modifier)
                            .clickable { picked = day to slot }
                            .semantics {
                                val cell = resources.getString(R.string.heat_cell, weekdaysFull[day], slots[slot], money.format(value))
                                contentDescription = if (busiest) resources.getString(R.string.heat_cell_busiest, cell) else cell
                            },
                    )
                }
            }
        }
        picked?.let { (d, s) ->
            Text(
                stringResource(R.string.heat_cell, weekdays[d], slots[s], money.format(heat[d][s])),
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A card's title in the primary colour, with an optional ⓘ for what used to be a sentence on the card. */
@Composable
fun CardTitle(title: String, modifier: Modifier = Modifier, info: String? = null) {
    Row(modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        info?.let { InfoButton(it) }
    }
}

/** Cumulative income through the period, drawn faintly in the hero card. */
@Composable
fun Sparkline(days: List<DayIncome>, color: Color, modifier: Modifier = Modifier) {
    if (days.size < 2) return
    val cumulative = days.runningFold(0L) { acc, d -> acc + d.totalMinor }.drop(1)
    val max = cumulative.last().takeIf { it > 0 } ?: return
    Canvas(modifier) {
        val path = Path()
        cumulative.forEachIndexed { i, v ->
            val x = size.width * i / (cumulative.size - 1)
            val y = size.height - size.height * v / max
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun ChartCard(title: String, modifier: Modifier = Modifier, info: String? = null, content: @Composable () -> Unit) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            CardTitle(title, info = info)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
