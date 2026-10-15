package app.ridetracker.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Box
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.TextAlign
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColor
import app.ridetracker.AppContainer
import app.ridetracker.MainActivity
import app.ridetracker.R
import app.ridetracker.RideTrackerApplication
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.TargetCalculator
import app.ridetracker.shared.domain.TargetProgress
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.resolveCurrency
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** How the month stands, for the pill's colour. */
private enum class GoalTone { ON_TRACK, BEHIND, AHEAD, DONE }

/** What the widget shows, already worded; [progress] is null without a target. */
private data class GoalState(
    /** "October target". */
    val title: String,
    val progress: TargetProgress?,
    /** What's done so far, in whole units ("RON 2,160"). */
    val amount: String,
    /** "of RON 3,000". */
    val of: String,
    /** One short pill: "RON 256/day · 15 days", "Behind RON 507", "Done · +RON 660"; or the "set a target" text. */
    val pill: String,
    val tone: GoalTone,
    /** The pill for narrow widgets: only the amount ("RON 79/day", "Behind RON 507"). */
    val pillShort: String = pill,
)

/**
 * Home-screen widget with this month's target (owner, 15 Oct 2026): how far you are, what each driving day left
 * needs, and the pace. The same numbers as Home's target card. Tap opens the app (the target page when none is set).
 * Small: the percentage and the bar; wider or taller: the amounts, per day and pace too.
 */
class GoalWidget : GlanceAppWidget() {

    // Laid out for its exact size, so the ring and the number grow with the widget instead of leaving empty space.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        provideContent {
            GlanceTheme(colors = widgetColors()) { Content(state) }
        }
    }

    @Composable
    private fun Content(state: GoalState) {
        val context = androidx.glance.LocalContext.current
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (state.progress == null) putExtra(MainActivity.EXTRA_OPEN_TARGET, true) }
        val size = LocalSize.current
        val big = size.height >= MEDIUM.height
        val progress = state.progress
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(28.dp)
                .padding(if (big) 16.dp else 12.dp)
                .clickable(actionStartActivity(open)),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (progress == null) {
                Column {
                    Text(state.title, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    Spacer(GlanceModifier.height(4.dp))
                    Text(state.pill, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 2)
                }
                return@Box
            }
            // One layout per shape: square or tall stacks the ring over the number; wide puts them side by side.
            // Text sizes come from the room actually left, so the number never gets cut ("RON 12,160").
            val pad = if (big) 16.dp else 12.dp
            val innerW = size.width - pad * 2
            val innerH = size.height - pad * 2
            val stacked = size.width < size.height * 1.25f && size.height >= 150.dp
            if (stacked) {
                val ring = minOf(innerH * 0.5f, innerW * 0.7f).coerceIn(48.dp, 150.dp)
                Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                    Ring(progress, ring, true)
                    Spacer(GlanceModifier.height(8.dp))
                    Text(
                        state.amount,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = fit(state.amount, innerW, 40f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                        maxLines = 1,
                    )
                    Text(state.of, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center), maxLines = 1)
                    if (innerH - ring >= 110.dp) {
                        Spacer(GlanceModifier.height(8.dp))
                        Pill(if (state.pill.length * 6.5f + 20f > innerW.value) state.pillShort else state.pill, state.tone)
                    }
                }
                return@Box
            }
            // Side by side: the ring fills the height (up to ~38% of the width), the words take the rest.
            val ring = minOf(innerH, size.width * 0.38f).coerceIn(32.dp, 168.dp)
            val gap = if (big) 16.dp else 12.dp
            // Wide and short: the pill goes on the right when there's room for it.
            val pillBeside = !big && innerW - ring - gap >= 260.dp
            val textW = innerW - ring - gap - (if (pillBeside) 130.dp else 0.dp)
            val numberSize = fit(state.amount, textW, if (big) 44f else 26f)
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Ring(progress, ring, big)
                Spacer(GlanceModifier.width(gap))
                Column(GlanceModifier.defaultWeight()) {
                    if (big && innerH >= 120.dp) {
                        Text(state.title, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                    Text(state.amount, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = numberSize, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(state.of, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp), maxLines = 1)
                    if (big) {
                        Spacer(GlanceModifier.height(8.dp))
                        // About 6.5 dp per character at the pill's size: the short version when the full one won't fit.
                        Pill(if (state.pill.length * 6.5f + 20f > textW.value) state.pillShort else state.pill, state.tone, large = innerH >= 150.dp && textW >= 180.dp)
                    }
                }
                if (pillBeside) {
                    Spacer(GlanceModifier.width(8.dp))
                    Pill(state.pill, state.tone)
                }
            }
        }
    }

    /**
     * The biggest bold size (up to [maxSp]) at which [text] fits in [width]: bold digits and letters are about 0.6 of
     * the font size wide.
     */
    private fun fit(text: String, width: androidx.compose.ui.unit.Dp, maxSp: Float): androidx.compose.ui.unit.TextUnit {
        val sp = (width.value / (text.length.coerceAtLeast(1) * 0.6f)).coerceIn(14f, maxSp)
        return sp.sp
    }

    /** Material 3 Expressive style progress ring with the percentage inside, tinted from the theme. */
    @Composable
    private fun Ring(progress: TargetProgress, size: androidx.compose.ui.unit.Dp, big: Boolean) {
        val context = androidx.glance.LocalContext.current
        val px = (size.value * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        val stroke = px * (if (big) 0.11f else 0.14f)
        Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(RingBitmaps.ring(px, stroke, 1f)),
                contentDescription = null,
                modifier = GlanceModifier.size(size),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.secondaryContainer),
            )
            if (progress.fraction > 0f) {
                Image(
                    provider = ImageProvider(RingBitmaps.ring(px, stroke, progress.fraction)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(size),
                    colorFilter = ColorFilter.tint(if (progress.reached) positive else GlanceTheme.colors.primary),
                )
            }
            Text(
                "${(progress.fraction * 100).roundToInt()}%",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = when {
                    size >= 140.dp -> 26.sp
                    size >= 100.dp -> 22.sp
                    size >= 64.dp -> 16.sp
                    else -> 12.sp
                },
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun Pill(text: String, tone: GoalTone, large: Boolean = false) {
        val (container, content) = when (tone) {
            GoalTone.BEHIND -> GlanceTheme.colors.errorContainer to GlanceTheme.colors.onErrorContainer
            GoalTone.AHEAD, GoalTone.DONE -> positiveContainer to onPositiveContainer
            GoalTone.ON_TRACK -> GlanceTheme.colors.primaryContainer to GlanceTheme.colors.onPrimaryContainer
        }
        Box(
            GlanceModifier.background(container).cornerRadius(50.dp)
                .padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 6.dp else 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = TextStyle(color = content, fontSize = if (large) 14.sp else 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }

    private suspend fun load(context: Context): GoalState {
        val container = (context.applicationContext as RideTrackerApplication).container
        val locale = Locale.getDefault()
        val currency = resolveCurrency(container.settingsRepository.settings.first().currencyCode)
        val money = MoneyFormat(currency, locale, wholeUnits = true)
        val month = Period.Month.containing(Clock.System.todayIn(TimeZone.currentSystemDefault()))
        val monthName = java.time.Month.of(month.range.start.month.ordinal + 1).getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, locale) // "October", "octombrie"
        val title = context.getString(R.string.widget_goal_title, monthName)
        val progress = progressNow(container).first()
            ?: return GoalState(title, null, "", "", context.getString(R.string.target_suggest), GoalTone.ON_TRACK)
        val pace = progress.paceMinor
        val (pill, tone) = when {
            progress.reached -> context.getString(R.string.widget_goal_done, money.format(progress.achievedMinor - progress.targetMinor)) to GoalTone.DONE
            progress.finished -> context.getString(R.string.target_missed, money.format(progress.remainingMinor)) to GoalTone.BEHIND
            pace != null && pace < 0 -> context.getString(R.string.widget_goal_behind, money.format(-pace)) to GoalTone.BEHIND
            pace != null && pace > 0 && progress.perDayMinor == null -> context.getString(R.string.widget_goal_ahead, money.format(pace)) to GoalTone.AHEAD
            progress.perDayMinor != null -> context.resources.getQuantityString(
                R.plurals.widget_goal_per_day, progress.drivingDaysLeft, money.format(progress.perDayMinor!!), progress.drivingDaysLeft,
            ) to (if ((pace ?: 0) > 0) GoalTone.AHEAD else GoalTone.ON_TRACK)
            else -> context.getString(R.string.target_on_pace) to GoalTone.ON_TRACK
        }
        return GoalState(
            title = title,
            progress = progress,
            amount = money.format(progress.achievedMinor),
            of = context.getString(R.string.widget_goal_of, money.format(progress.targetMinor)),
            pill = pill,
            tone = tone,
            pillShort = progress.perDayMinor?.takeIf { !progress.reached && tone != GoalTone.BEHIND }
                ?.let { context.getString(R.string.widget_goal_per_day_short, money.format(it)) } ?: pill,
        )
    }

    companion object {
        private val MEDIUM = DpSize(180.dp, 110.dp)


        /** The theme's "money up" green (positiveColor() and its container), light and dark. */
        private val positive: GlanceColor = ColorProvider(day = Color(0xFF146C3E), night = Color(0xFF7EE2A8))
        private val positiveContainer: GlanceColor = ColorProvider(day = Color(0xFFA6F4C0), night = Color(0xFF00522B))
        private val onPositiveContainer: GlanceColor = ColorProvider(day = Color(0xFF00522B), night = Color(0xFFA6F4C0))

        /** This month's target progress, as Home computes it; null without a target. Checks for a new month every 15 minutes. */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun progressNow(container: AppContainer): Flow<TargetProgress?> {
            val months = flow {
                while (true) {
                    emit(Clock.System.todayIn(TimeZone.currentSystemDefault()))
                    delay(15 * 60_000L)
                }
            }.distinctUntilChanged()
            return combine(months, container.settingsRepository.target) { today, target -> today to target }
                .flatMapLatest { (today, target) ->
                    val month = Period.Month.containing(today)
                    val amount = target.targetFor(month) ?: return@flatMapLatest flowOf(null)
                    combine(container.incomeRepository.observeTotals(month.range), container.expenseRepository.observeInRange(month.range)) { totals, expenses ->
                        val achieved = TargetCalculator.achieved(target.basis, totals.sumOf { it.totalMinor }, expenses.sumOf { it.amountMinor })
                        TargetCalculator.progress(month, amount, achieved, target.drivingDays, today)
                    }
                }
        }

        /** Redraws the widgets whenever the month's numbers, the target or the day change (while the app runs). */
        @OptIn(FlowPreview::class)
        fun watch(context: Context, container: AppContainer, scope: CoroutineScope) {
            scope.launch {
                progressNow(container).distinctUntilChanged().drop(1).debounce(1_000).collect {
                    GoalWidget().updateAll(context)
                }
            }
        }
    }
}

/** White ring arcs (round ends, from 12 o'clock) that the widget tints with theme colours. */
private object RingBitmaps {
    fun ring(sizePx: Int, strokePx: Float, fraction: Float): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            color = android.graphics.Color.WHITE
        }
        val inset = strokePx / 2
        val oval = RectF(inset, inset, sizePx - inset, sizePx - inset)
        if (fraction >= 1f) canvas.drawOval(oval, paint) else canvas.drawArc(oval, -90f, 360f * fraction.coerceAtLeast(0.02f), false, paint)
        return bitmap
    }
}

class GoalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoalWidget()
}
