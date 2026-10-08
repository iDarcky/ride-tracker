package app.ridetracker.ui.overview

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.pluralStringResource
import kotlinx.datetime.isoDayNumber
import app.ridetracker.R
import app.ridetracker.shared.data.PlatformEntity
import app.ridetracker.shared.data.PlatformTotal
import app.ridetracker.shared.domain.DayIncome
import app.ridetracker.shared.domain.HomeStats
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.tabular
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

/** Smallest share a segment gets on screen, so its label below always fits (the % shown stays exact). */
private const val MIN_SEGMENT_SHARE = 0.34f

/**
 * Gross split into kept, platform fees and expenses: one bar, and each segment's label sits directly under
 * it (tick in the segment's colour), so there is no separate legend to match up.
 */
@Composable
fun MoneyBreakdown(stats: HomeStats, expenseMinor: Long, tracksExpenses: Boolean, money: MoneyFormat, modifier: Modifier = Modifier) {
    val fees = -stats.feesMinor
    val kept = stats.grossMinor - fees - expenseMinor
    val percent = NumberFormat.getPercentInstance(currentLocale())
    data class Part(val label: String, val amount: Long, val shown: Long, val color: Color)
    val parts = buildList {
        add(Part(stringResource(if (tracksExpenses) R.string.kept else R.string.earned), kept, kept, keptColor()))
        if (fees > 0) add(Part(stringResource(R.string.fees), fees, -fees, feesColor()))
        if (expenseMinor > 0) add(Part(stringResource(R.string.nav_expenses), expenseMinor, -expenseMinor, expensesColor()))
    }
    Column(modifier) {
        if (stats.grossMinor > 0 && kept >= 0) {
            val raw = parts.map { it.amount.toFloat() / stats.grossMinor }
            val weights = raw.map { if (it > 0f) maxOf(it, MIN_SEGMENT_SHARE) else 0f }
            val drawn = parts.indices.filter { weights[it] > 0f }
            Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                drawn.forEach { i ->
                    Box(Modifier.weight(weights[i]).height(10.dp).clip(RoundedCornerShape(5.dp)).background(parts[i].color))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                drawn.forEach { i ->
                    SegmentLabel(
                        label = parts[i].label + " · " + percent.format(raw[i]),
                        value = money.format(parts[i].shown),
                        color = parts[i].color,
                        modifier = Modifier.weight(weights[i]),
                    )
                }
            }
        } else {
            // Spent more than earned: no bar, just the figures.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                parts.forEach { SegmentLabel(it.label, money.format(it.shown), it.color, Modifier.weight(1f)) }
            }
        }
        val gross = buildList {
            add(stringResource(R.string.gross_fares, money.format(stats.faresMinor)))
            if (stats.bonusesAndTipsMinor != 0L) add(stringResource(R.string.gross_bonuses, money.format(stats.bonusesAndTipsMinor)))
            if (stats.otherIncomeMinor != 0L) add(stringResource(R.string.gross_other, money.format(stats.otherIncomeMinor)))
        }
        Text(
            stringResource(R.string.of_gross, money.format(stats.grossMinor), gross.joinToString(" + ")),
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!stats.feesKnownForAll) {
            Text(
                stringResource(R.string.fees_partial),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A coloured tick on the left ties the label to the segment above it. */
@Composable
private fun SegmentLabel(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(color))
        Column(Modifier.padding(start = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleSmall.tabular(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
    val km = stats.distanceMeters?.let { stringResource(R.string.km_value, "%.0f".format(locale, it / 1000.0)) }
    val tiles = listOf(
        Tile(stats.tripCount.takeIf { it > 0 }?.toString(), stringResource(R.string.metric_trips), coverage(stats.tripPlatformIds, R.string.hint_trips)),
        Tile(km, stringResource(R.string.metric_km), coverage(stats.kmPlatformIds, R.string.hint_km)),
        Tile(hours, stringResource(R.string.metric_online), coverage(stats.hourPlatformIds, R.string.hint_hours)),
        Tile(stats.perHourMinor?.let { money.format(it) }, stringResource(R.string.metric_per_hour, money.symbol), coverage(stats.hourPlatformIds, R.string.hint_hours)),
        Tile(stats.perKmMinor?.let { money.format(it) }, stringResource(R.string.metric_per_km, money.symbol), coverage(stats.kmPlatformIds, R.string.hint_km)),
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
                overflow = TextOverflow.Ellipsis,
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
    ChartCard(stringResource(R.string.split_by_app), modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }
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
) {
    val hasMoney = days.any { it.totalMinor > 0 }
    val hasTrips = days.any { it.tripCount > 0 }
    if (!hasMoney && !hasTrips) return
    var metric by remember(hasMoney) { mutableStateOf(if (hasMoney) ActivityMetric.MONEY else ActivityMetric.TRIPS) }
    fun value(d: DayIncome): Long = if (metric == ActivityMetric.MONEY) d.totalMinor else d.tripCount.toLong()
    val resources = LocalContext.current.resources
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
    // Which days get a label under the axis: all of them in a week, otherwise every seventh.
    val labelled: Map<Int, String> = days.indices.mapNotNull { i ->
        val d = days[i].date
        when {
            days.size <= 7 -> i to (weekdayNames[java.time.DayOfWeek.of(d.dayOfWeek.isoDayNumber)] ?: "")
            d.day in setOf(1, 8, 15, 22, 29) && days.first().date.day == 1 -> i to d.day.toString()
            days.first().date.day != 1 && i % 7 == 0 -> i to dates.shortDay(d)
            else -> null
        }
    }.toMap()
    val shown = platforms.filter { p -> days.any { (it.byPlatform[p.id] ?: 0) > 0 } }

    ChartCard(stringResource(R.string.daily_activity), modifier) {
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
                append(pluralStringResource(R.plurals.days_driven, driven.size, driven.size))
                if (best != null) append(" · ").append(stringResource(R.string.best_day, dates.shortDay(days[best].date), format(value(days[best]))))
                if (driven.size > 1) append(" · ").append(stringResource(R.string.average_per_day, format(average)))
            },
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The selected day, above the chart.
        val pick = selected?.let { days[it] }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(pick?.let { dates.day(it.date) } ?: stringResource(R.string.tap_a_bar), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (pick != null) Text(format(value(pick)), style = MaterialTheme.typography.titleLarge.tabular())
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
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
                segments.forEachIndexed { s, (color, v) ->
                    val h = chartHeight * v / max
                    val isTop = s == segments.lastIndex
                    drawRoundRect(
                        color = if (faded) lerp(color, surface, 0.55f) else color,
                        topLeft = Offset(x, top - h),
                        size = Size(barWidth, h),
                        cornerRadius = if (isTop) CornerRadius(4.dp.toPx().coerceAtMost(barWidth / 2)) else CornerRadius.Zero,
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
        if (metric == ActivityMetric.MONEY && shown.size > 1) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                shown.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(colorOf.getValue(p.id)))
                        Spacer(Modifier.width(6.dp))
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
                Text(stringResource(R.string.open_day))
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

    ChartCard(stringResource(R.string.best_time), modifier) {
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
                            .clickable { picked = day to slot },
                    )
                }
            }
        }
        val text = picked?.let { (d, s) -> stringResource(R.string.heat_cell, weekdays[d], slots[s], money.format(heat[d][s])) }
            ?: stringResource(R.string.best_time_caption)
        Text(text, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun ChartCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
