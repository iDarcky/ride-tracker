package app.ridetracker.ui.wrapped

import app.ridetracker.ui.common.label
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.key
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import kotlinx.datetime.plus
import kotlinx.datetime.daysUntil
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ridetracker.AppContainer
import app.ridetracker.R
import app.ridetracker.shared.domain.DateRange
import app.ridetracker.shared.domain.HomeStatsCalculator
import app.ridetracker.shared.domain.Period
import app.ridetracker.shared.domain.Wrapped
import app.ridetracker.shared.domain.WrappedCalculator
import app.ridetracker.ui.common.DateFormats
import app.ridetracker.ui.common.MoneyFormat
import app.ridetracker.ui.common.container
import app.ridetracker.ui.common.currentLocale
import app.ridetracker.ui.common.distanceFormat
import app.ridetracker.ui.common.resolveCurrency
import app.ridetracker.ui.common.tabular
import app.ridetracker.ui.report.shareImage
import app.ridetracker.ui.theme.onPositiveContainerColor
import app.ridetracker.ui.theme.positiveContainerColor
import java.text.NumberFormat
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

data class WrappedUiState(
    val wrapped: Wrapped? = null,
    val currencyCode: String? = null,
    /** The period is still running, so it's compared with the same days of the previous one. */
    val sameDays: Boolean = false,
    val loading: Boolean = true,
)

class WrappedViewModel(private val range: DateRange, private val yearly: Boolean, private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(WrappedUiState())
    val state: StateFlow<WrappedUiState> = _state

    init {
        viewModelScope.launch {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val previousWhole = if (yearly) WrappedCalculator.yearRange(range.start.year - 1)
            else (Period.Month.containing(range.start).previous() as Period.Month).range
            // A month or year still running is compared with the same days of the previous one, like Home does.
            val running = today < range.endInclusive
            val previous = if (!running) previousWhole else {
                val days = range.start.daysUntil(today)
                DateRange(previousWhole.start, previousWhole.start.plus(DatePeriod(days = days)).coerceAtMost(previousWhole.endInclusive))
            }
            val entries = c.incomeRepository.observeEntryDetails(range).first()
            val stats = HomeStatsCalculator.compute(
                range, entries, c.incomeRepository.observeLines(range).first(),
                c.importRepository.observeTrips(range).first(), c.importRepository.observeSummaries(range).first(),
            )
            val previousIncome = c.incomeRepository.getEntries(previous).takeIf { it.isNotEmpty() }?.sumOf { it.amountMinor }
            val previousExpenses = c.expenseRepository.getInRange(previous).takeIf { it.isNotEmpty() }?.sumOf { it.amountMinor }
            val wrapped = WrappedCalculator.compute(
                range, yearly, entries, c.expenseRepository.getInRange(range), previousIncome, previousExpenses, stats,
                c.incomeRepository.observePlatforms().first(), c.settingsRepository.target.first(), today,
            )
            _state.value = WrappedUiState(wrapped, c.settingsRepository.settings.first().currencyCode, sameDays = running, loading = false)
        }
    }
}

/**
 * One story card: a label, the big figure, a line under it. [countMinor] makes a money figure count up from 0 as the
 * card appears (formatted by [format]).
 */
private data class Story(
    val label: String,
    val big: String,
    val sub: String?,
    val palette: Int,
    val countMinor: Long? = null,
    val format: ((Long) -> String)? = null,
)

/**
 * A month or a year, wrapped (owner, 15 Oct 2026: "like Spotify Wrapped"): full-screen story cards, one highlight
 * each, in the app's colours with a Material 3 Expressive shape; tap right for the next, left for the previous. The
 * last card sums it up and shares as an image (for Threads). Cards the data doesn't support are left out.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WrappedScreen(
    start: LocalDate,
    end: LocalDate,
    yearly: Boolean,
    onClose: () -> Unit,
    viewModel: WrappedViewModel = viewModel { WrappedViewModel(DateRange(start, end), yearly, container) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wrapped = state.wrapped
    if (state.loading || wrapped == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            LoadingIndicator(color = MaterialTheme.colorScheme.onPrimary)
        }
        return
    }
    val locale = currentLocale()
    val money = remember(state.currencyCode, locale) { MoneyFormat(resolveCurrency(state.currencyCode), locale, wholeUnits = true) }
    val dates = remember(locale) { DateFormats(locale) }
    val distance = distanceFormat()
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 0 } }
    val periodName = if (yearly) start.year.toString() else monthName(start, locale)
    val previousName = if (yearly) (start.year - 1).toString() else monthName(start.minus(DatePeriod(months = 1)), locale)

    val stories = buildList {
        add(Story(stringResource(R.string.wrapped_your, periodName).replaceFirstChar { it.titlecase(locale) }, stringResource(R.string.wrapped_wrapped),
            pluralStringResource(R.plurals.wrapped_days, wrapped.daysDriven, wrapped.daysDriven), 0))
        val keptLabel = stringResource(if (wrapped.expensesMinor > 0) R.string.wrapped_kept else R.string.wrapped_earned)
        val change = wrapped.keptChange?.let {
            val res = when {
                state.sameDays && it >= 0 -> R.string.wrapped_more_than_same_days
                state.sameDays -> R.string.wrapped_less_than_same_days
                it >= 0 -> R.string.wrapped_more_than
                else -> R.string.wrapped_less_than
            }
            stringResource(res, percent.format(abs(it)), previousName)
        }
        add(Story(keptLabel, money.format(wrapped.keptMinor), change, 1, wrapped.keptMinor, money::format))
        // Where the money went: the biggest category and its share, then the next two.
        wrapped.expenseCategories.firstOrNull()?.let { top ->
            val share = percent.format(top.totalMinor.toDouble() / wrapped.expensesMinor)
            val next = wrapped.expenseCategories.drop(1).take(2)
            val nextLine = next.map { "${stringResource(it.category.label)} ${money.format(it.totalMinor)}" }.joinToString(" · ")
            add(
                Story(
                    stringResource(R.string.wrapped_expenses),
                    stringResource(top.category.label),
                    stringResource(R.string.wrapped_expense_share, money.format(top.totalMinor), share) + if (nextLine.isNotEmpty()) "\n$nextLine" else "",
                    5,
                ),
            )
        }
        wrapped.bestDay?.let { add(Story(stringResource(R.string.wrapped_best_day), money.format(wrapped.bestDayMinor), dates.day(it), 2, wrapped.bestDayMinor, money::format)) }
        wrapped.bestWeekday?.let {
            val name = java.time.DayOfWeek.of(it.ordinal + 1).getDisplayName(TextStyle.FULL, locale).replaceFirstChar { c -> c.titlecase(locale) }
            add(Story(stringResource(R.string.wrapped_best_weekday), name, stringResource(R.string.wrapped_on_average, money.format(wrapped.bestWeekdayAverageMinor)), 3))
        }
        wrapped.bestMonth?.let { add(Story(stringResource(R.string.wrapped_best_month), monthName(it.range.start, locale).replaceFirstChar { c -> c.titlecase(locale) }, money.format(wrapped.bestMonthMinor), 4)) }
        if (wrapped.perHourMinor != null && wrapped.onlineMinutes != null) {
            val hours = (wrapped.onlineMinutes!! / 60.0).roundToInt()
            add(Story(stringResource(R.string.wrapped_per_hour), money.format(wrapped.perHourMinor!!), pluralStringResource(R.plurals.wrapped_hours, hours, hours), 3, wrapped.perHourMinor, money::format))
        }
        if (wrapped.trips > 0) {
            add(Story(stringResource(R.string.wrapped_rides), NumberFormat.getIntegerInstance(locale).format(wrapped.trips),
                wrapped.distanceMeters?.let { stringResource(R.string.wrapped_driven, distance.meters(it)) }, 0))
        }
        if (wrapped.apps.size > 1) {
            val top = wrapped.apps.first()
            add(Story(stringResource(R.string.wrapped_apps), "${top.name} ${percent.format(top.share)}",
                wrapped.apps.drop(1).joinToString(" · ") { "${it.name} ${percent.format(it.share)}" }, 1))
        }
        wrapped.target?.let {
            add(Story(stringResource(if (it.reached) R.string.wrapped_target_reached else R.string.wrapped_target),
                percent.format(it.achievedMinor.toDouble() / it.targetMinor),
                stringResource(R.string.wrapped_target_of, money.format(it.achievedMinor), money.format(it.targetMinor)), 2))
        }
        if (wrapped.yearly && wrapped.monthsWithTarget > 0) {
            add(Story(stringResource(R.string.wrapped_targets_reached), "${wrapped.monthsTargetReached} / ${wrapped.monthsWithTarget}",
                pluralStringResource(R.plurals.wrapped_months, wrapped.monthsWithTarget, wrapped.monthsWithTarget), 2))
        }
    }
    val pages = stories.size + 1 // + the summary
    var page by rememberSaveable { mutableIntStateOf(0) }
    val palettes = palettes()
    var summaryColor by rememberSaveable { mutableIntStateOf(0) }

    // Auto-advance like Spotify: each card fills its bar in a few seconds, then the next shows; holding pauses.
    // Off with TalkBack (a screen-reader user needs time) and on the summary card.
    val context = LocalContext.current
    val talkBack = remember {
        (context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager).isTouchExplorationEnabled
    }
    var paused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(page) { progress.snapTo(0f) }
    LaunchedEffect(page, paused) {
        if (talkBack || paused || page >= pages - 1) return@LaunchedEffect
        val left = ((1f - progress.value) * STORY_MILLIS).toInt()
        progress.animateTo(1f, tween(left, easing = LinearEasing))
        page++
    }

    val previousLabel = stringResource(R.string.wrapped_previous)
    val nextLabel = stringResource(R.string.wrapped_next)
    Box(Modifier.fillMaxSize()) {
        // Taps and holds underneath the card (its text lets them through; the summary's buttons don't): left third
        // back, the rest forward; holding anywhere pauses.
        Box(
            Modifier.fillMaxSize()
                .pointerInput(pages) {
                    detectTapGestures(
                        onPress = {
                            paused = true
                            tryAwaitRelease()
                            paused = false
                        },
                        onTap = { offset ->
                            if (offset.x < size.width / 3f) { if (page > 0) page-- } else if (page < pages - 1) page++
                        },
                    )
                }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(previousLabel) { if (page > 0) { page--; true } else false },
                        CustomAccessibilityAction(nextLabel) { if (page < pages - 1) { page++; true } else false },
                    )
                },
        )
        if (page < stories.size) {
            // Keyed by page, so each card's entrance plays when it appears.
            key(page) { StoryCard(stories[page], palettes[stories[page].palette % palettes.size], shapeFor(page), StoryLayout.entries[page % StoryLayout.entries.size]) }
        } else {
            SummaryCard(wrapped, periodName, money, percent, palettes, summaryColor, onColor = { summaryColor = it })
        }
        val onColor = if (page < stories.size) palettes[stories[page].palette % palettes.size].second else palettes[summaryColor].second
        Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Story progress: done cards full, the current one filling, the rest faint.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(pages) { i ->
                    val fill = when {
                        i < page -> 1f
                        i == page -> if (talkBack || page == pages - 1) 1f else progress.value
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(onColor.copy(alpha = 0.3f))) {
                        Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(onColor.copy(alpha = 0.9f)))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.report_brand), color = onColor.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = onColor) }
            }
        }
    }
}

/** How long each card stays before the next (Spotify's are about this long). */
private const val STORY_MILLIS = 5_000

/** Where a card puts its words and its shape, so cards in a row don't all look the same. */
private enum class StoryLayout { TOP_START, BOTTOM_START, CENTER, TOP_END }

@Composable
private fun StoryCard(story: Story, colors: Pair<Color, Color>, shape: Shape, layout: StoryLayout) {
    val (container, content) = colors
    // Entrance: the words rise and fade in, a money figure counts up; the shape turns slowly the whole time.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    val count = remember { Animatable(0f) }
    LaunchedEffect(Unit) { count.animateTo(1f, tween(1_100, delayMillis = 150, easing = FastOutSlowInEasing)) }
    val spin by rememberInfiniteTransition(label = "shape").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing)), label = "shape turn",
    )
    val bigText = story.countMinor?.let { target -> story.format?.invoke((target * count.value).toLong()) } ?: story.big
    BoxWithConstraints(Modifier.fillMaxSize().background(container)) {
        // The Expressive shape, big and half off the screen, in the corner opposite the words.
        val (shapeX, shapeY) = when (layout) {
            StoryLayout.TOP_START -> maxWidth * 0.35f to maxHeight * 0.55f
            StoryLayout.BOTTOM_START -> maxWidth * 0.30f to -maxWidth * 0.35f
            StoryLayout.CENTER -> -maxWidth * 0.05f to maxHeight * 0.5f - maxWidth * 0.55f
            StoryLayout.TOP_END -> -maxWidth * 0.45f to maxHeight * 0.58f
        }
        Box(
            Modifier.size(maxWidth * 1.1f).offset(x = shapeX, y = shapeY).rotate((if (layout == StoryLayout.CENTER) 0f else 18f) + spin)
                .clip(shape).background(content.copy(alpha = if (layout == StoryLayout.CENTER) 0.12f else 0.10f)),
        )
        val vertical = when (layout) {
            StoryLayout.TOP_START, StoryLayout.TOP_END -> Arrangement.Top
            StoryLayout.BOTTOM_START -> Arrangement.Bottom
            StoryLayout.CENTER -> Arrangement.Center
        }
        val horizontal = when (layout) {
            StoryLayout.TOP_END -> Alignment.End
            StoryLayout.CENTER -> Alignment.CenterHorizontally
            else -> Alignment.Start
        }
        val align = when (layout) {
            StoryLayout.TOP_END -> TextAlign.End
            StoryLayout.CENTER -> TextAlign.Center
            else -> TextAlign.Start
        }
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp, vertical = 112.dp)
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * 48.dp.toPx()
                },
            verticalArrangement = vertical,
            horizontalAlignment = horizontal,
        ) {
            val big = @Composable {
                Text(
                    bigText,
                    color = content,
                    style = MaterialTheme.typography.displayLarge.tabular(),
                    fontWeight = FontWeight.Bold,
                    textAlign = align,
                    maxLines = 2,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(maxFontSize = MaterialTheme.typography.displayLarge.fontSize),
                )
            }
            // Right-aligned cards lead with the number; the others with the label.
            if (layout == StoryLayout.TOP_END) {
                big()
                Spacer(Modifier.height(12.dp))
                Text(story.label, color = content, style = MaterialTheme.typography.headlineSmall, textAlign = align)
            } else {
                Text(story.label, color = content, style = MaterialTheme.typography.headlineSmall, textAlign = align)
                Spacer(Modifier.height(12.dp))
                big()
            }
            story.sub?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = content.copy(alpha = 0.85f), style = MaterialTheme.typography.titleLarge, textAlign = align)
            }
        }
    }
}

/** The last card: the period on one image, in a colour of the driver's choice, with Share. */
@Composable
private fun SummaryCard(
    wrapped: Wrapped,
    periodName: String,
    money: MoneyFormat,
    percent: NumberFormat,
    palettes: List<Pair<Color, Color>>,
    selected: Int,
    onColor: (Int) -> Unit,
) {
    val (container, content) = palettes[selected]
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val shareName = stringResource(R.string.wrapped_share_name, periodName)
    Column(
        Modifier.fillMaxSize().background(container).safeDrawingPadding().padding(horizontal = 20.dp, vertical = 72.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // What gets shared: this block on the card's own colour (solid, so the image has no see-through corners).
        Column(
            Modifier.fillMaxWidth()
                .drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }
                .background(container)
                .clip(RoundedCornerShape(32.dp)).background(content.copy(alpha = 0.08f)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.wrapped_summary_title, periodName).replaceFirstChar { it.titlecase() }, color = content, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            val tiles = buildList {
                add(stringResource(if (wrapped.expensesMinor > 0) R.string.wrapped_kept else R.string.wrapped_earned) to money.format(wrapped.keptMinor))
                add(stringResource(R.string.wrapped_days_label) to wrapped.daysDriven.toString())
                if (wrapped.bestDay != null) add(stringResource(R.string.wrapped_best_day) to money.format(wrapped.bestDayMinor))
                if (wrapped.perHourMinor != null) add(stringResource(R.string.wrapped_per_hour) to money.format(wrapped.perHourMinor!!))
                else if (wrapped.trips > 0) add(stringResource(R.string.wrapped_rides) to wrapped.trips.toString())
            }
            tiles.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(label, color = content.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                            Text(value, color = content, style = MaterialTheme.typography.headlineSmall.tabular(), fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (wrapped.apps.size > 1) {
                Text(wrapped.apps.joinToString(" · ") { "${it.name} ${percent.format(it.share)}" }, color = content, style = MaterialTheme.typography.bodyLarge)
            }
            Text(stringResource(R.string.wrapped_made_with), color = content.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(20.dp))
        // The card's colour, from the app's palette.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            palettes.forEachIndexed { i, (bg, fg) ->
                val label = stringResource(R.string.wrapped_colour, i + 1)
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(bg)
                        .border(if (i == selected) 3.dp else 1.dp, if (i == selected) content else fg.copy(alpha = 0.4f), CircleShape)
                        .clickable(onClickLabel = label) { onColor(i) }
                        .semantics { contentDescription = label; this.selected = i == selected },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                scope.launch {
                    val bitmap: Bitmap = layer.toImageBitmap().asAndroidBitmap()
                    shareImage(context, bitmap, shareName)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = content, contentColor = container),
        ) {
            Icon(Icons.Outlined.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.wrapped_share))
        }
    }
}

/** Bold pairs from the app's palette: container and the colour on it. */
@Composable
private fun palettes(): List<Pair<Color, Color>> {
    val c = MaterialTheme.colorScheme
    return listOf(
        c.primary to c.onPrimary,
        c.tertiaryContainer to c.onTertiaryContainer,
        positiveContainerColor() to onPositiveContainerColor(),
        c.primaryContainer to c.onPrimaryContainer,
        c.inverseSurface to c.inverseOnSurface,
        c.secondaryContainer to c.onSecondaryContainer,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun shapeFor(page: Int): Shape {
    val shapes = listOf(
        MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.SoftBurst,
        MaterialShapes.Cookie6Sided, MaterialShapes.Flower, MaterialShapes.Puffy, MaterialShapes.Gem,
    )
    return shapes[page % shapes.size].toShape()
}

private fun monthName(date: LocalDate, locale: java.util.Locale): String =
    java.time.Month.of(date.month.ordinal + 1).getDisplayName(TextStyle.FULL_STANDALONE, locale)
