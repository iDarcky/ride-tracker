package app.ridetracker.ui.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

/** Gross split into what you kept, what the apps took and what you spent: one bar, then the figures. */
@Composable
fun MoneyBreakdown(stats: HomeStats, expenseMinor: Long, tracksExpenses: Boolean, money: MoneyFormat, modifier: Modifier = Modifier) {
    val fees = -stats.feesMinor
    val kept = stats.grossMinor - fees - expenseMinor
    Column(modifier) {
        if (stats.grossMinor > 0 && kept >= 0) {
            val kc = keptColor()
            val fc = feesColor()
            val ec = expensesColor()
            Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(kept to kc, fees to fc, expenseMinor to ec).filter { it.first > 0 }.forEach { (value, color) ->
                    Box(Modifier.weight(value.toFloat()).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth()) {
            Figure(stringResource(R.string.gross), money.format(stats.grossMinor), null, Modifier.weight(1f))
            Figure(stringResource(R.string.fees), money.format(-fees), feesColor(), Modifier.weight(1f))
            if (tracksExpenses) Figure(stringResource(R.string.nav_expenses), money.format(-expenseMinor), expensesColor(), Modifier.weight(1f))
            Figure(stringResource(if (tracksExpenses) R.string.kept else R.string.earned), money.format(kept), keptColor(), Modifier.weight(1f))
        }
        val parts = buildList {
            add(stringResource(R.string.gross_fares, money.format(stats.faresMinor)))
            if (stats.bonusesAndTipsMinor != 0L) add(stringResource(R.string.gross_bonuses, money.format(stats.bonusesAndTipsMinor)))
            if (stats.otherIncomeMinor != 0L) add(stringResource(R.string.gross_other, money.format(stats.otherIncomeMinor)))
        }
        Text(
            stringResource(R.string.gross_is, parts.joinToString(" + ")),
            modifier = Modifier.padding(top = 8.dp),
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

/** Label with a colour dot (identity), value in text colour. */
@Composable
private fun Figure(label: String, value: String, dot: Color?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(value, style = MaterialTheme.typography.titleSmall.tabular(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Trips, average fare, hours, per hour, km, per km: only the ones some report provides. */
@Composable
fun Metrics(stats: HomeStats, money: MoneyFormat, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val tiles = buildList {
        if (stats.tripCount > 0) {
            add(Triple(stats.tripCount.toString(), stringResource(R.string.metric_trips), null))
            stats.averageFareMinor?.let { add(Triple(money.format(it), stringResource(R.string.metric_average_fare), null)) }
        }
        stats.onlineMinutes?.let { minutes ->
            add(Triple(stringResource(R.string.hours_minutes, minutes / 60, minutes % 60), stringResource(R.string.metric_online), null))
        }
        stats.perHourMinor?.let { add(Triple(money.format(it), stringResource(R.string.metric_per_hour), null)) }
        stats.distanceMeters?.let { add(Triple(stringResource(R.string.km_value, "%.0f".format(locale, it / 1000.0)), stringResource(R.string.metric_km), null)) }
        stats.perKmMinor?.let { add(Triple(money.format(it), stringResource(R.string.metric_per_km), null)) }
    }
    if (tiles.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Three per row, or two when that avoids a lonely tile and gives amounts room.
        val perRow = if (tiles.size % 3 == 0) 3 else 2
        tiles.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (value, label, _) ->
                    OutlinedCard(Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(value, style = MaterialTheme.typography.titleLarge.tabular(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (stats.tripCount > 0 && stats.distanceMeters == null) {
            Text(
                stringResource(R.string.metrics_source_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

/**
 * Income per day, stacked per app (Uber Driver style): tap a bar to see that day's figures and open it.
 * The tallest day is marked with a dashed line and its amount.
 */
@Composable
fun DailyActivity(
    days: List<DayIncome>,
    platforms: List<PlatformEntity>,
    money: MoneyFormat,
    dates: DateFormats,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val max = days.maxOfOrNull { it.totalMinor }?.takeIf { it > 0 } ?: return
    val order = platforms.map { it.id }
    val colorOf = platforms.associate { it.id to platformChartColor(it.colorArgb) }
    val names = platforms.associate { it.id to it.name }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val dim = MaterialTheme.colorScheme.surface
    var selected by remember(days) { mutableStateOf<Int?>(null) }
    val shown = platforms.filter { p -> days.any { (it.byPlatform[p.id] ?: 0) > 0 } }

    ChartCard(stringResource(R.string.daily_activity), modifier) {
        Text(
            money.format(max),
            style = MaterialTheme.typography.labelSmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(days) {
                    detectTapGestures { offset ->
                        val index = (offset.x / (size.width.toFloat() / days.size)).toInt().coerceIn(0, days.lastIndex)
                        selected = if (selected == index || days[index].totalMinor == 0L) null else index
                    }
                },
        ) {
            val slot = size.width / days.size
            val gap = (slot * 0.2f).coerceIn(2.dp.toPx(), 6.dp.toPx())
            val barWidth = slot - gap
            drawLine(gridColor, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            days.forEachIndexed { i, day ->
                var top = size.height
                val x = i * slot + gap / 2
                val segments = order.mapNotNull { id -> day.byPlatform[id]?.takeIf { it > 0 }?.let { id to it } }
                segments.forEachIndexed { s, (id, value) ->
                    val h = size.height * value / max
                    val color = colorOf[id] ?: gridColor
                    val faded = selected != null && selected != i
                    val isTop = s == segments.lastIndex
                    drawRoundRect(
                        color = if (faded) lerp(color, dim, 0.6f) else color,
                        topLeft = Offset(x, top - h),
                        size = Size(barWidth, h),
                        cornerRadius = if (isTop) CornerRadius(4.dp.toPx().coerceAtMost(barWidth / 2)) else CornerRadius.Zero,
                    )
                    top -= h
                    // 2dp surface gap between stacked segments.
                    if (!isTop) drawLine(dim, Offset(x, top), Offset(x + barWidth, top), 2.dp.toPx())
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(dates.shortDay(days.first().date), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(dates.shortDay(days.last().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (shown.size > 1) {
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
        val pick = selected?.let { days[it] }
        if (pick == null) {
            Text(
                stringResource(R.string.tap_a_bar),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dates.day(pick.date), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(money.format(pick.totalMinor), style = MaterialTheme.typography.titleMedium.tabular())
            }
            pick.byPlatform.entries.sortedBy { order.indexOf(it.key) }.forEach { (id, value) ->
                Text(
                    (names[id] ?: "") + " · " + money.format(value),
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
