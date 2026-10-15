package app.ridetracker.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import app.ridetracker.MainActivity
import app.ridetracker.R

/**
 * Quick actions on the home screen (owner, 15 Oct 2026): Fuel (a new expense already set to fuel), Import, Expense and
 * Income, the same icons as the + menu. One button per cell of width, in this order: 4×1 shows all four, 3×1 the first
 * three, 2×1 two, 1×1 one. Two cells high or more, they wrap into a grid (2×2 shows all four).
 */
class QuickWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    private data class Action(val label: Int, val icon: Int, val route: String)

    private val actions = listOf(
        Action(R.string.widget_quick_fuel, R.drawable.ic_widget_fuel, "expense?category=fuel"),
        Action(R.string.widget_quick_import, R.drawable.ic_widget_import, "import"),
        Action(R.string.widget_quick_expense, R.drawable.ic_widget_expense, "expense"),
        Action(R.string.widget_quick_income, R.drawable.ic_widget_income, "entry"),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { GlanceTheme(colors = widgetColors()) { Content() } }
    }

    @Composable
    private fun Content() {
        val size = LocalSize.current
        // A launcher cell is roughly 70–100 dp; one button per cell of width, two rows once there's room for them.
        val columns = (size.width.value / CELL_DP).toInt().coerceIn(1, actions.size)
        val rows = if (size.height.value >= TWO_ROWS_DP && columns < actions.size) 2 else 1
        val shown = actions.take(columns * rows)
        val perRow = (shown.size + rows - 1) / rows
        // A single cell has no room for "Combustibil": the icon alone (TalkBack still reads the name).
        val labels = shown.size > 1
        Box(
            GlanceModifier.fillMaxSize().appWidgetBackground().background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(28.dp).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(GlanceModifier.fillMaxSize()) {
                shown.chunked(perRow).forEach { row ->
                    Row(GlanceModifier.defaultWeight().fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        row.forEach { Button(it, GlanceModifier.defaultWeight(), labels) }
                    }
                }
            }
        }
    }

    @Composable
    private fun Button(action: Action, modifier: GlanceModifier, label: Boolean) {
        val context = LocalContext.current
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_ROUTE, action.route)
            // Distinct data per button, so each gets its own launch (extras alone would be merged).
            .setData("ridetracker://quick/${action.route}".toUri())
        Column(
            // Height only: fillMaxSize would override the weight and let the first button take the whole row.
            modifier.fillMaxHeight().padding(4.dp).cornerRadius(20.dp).clickable(actionStartActivity(intent)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                GlanceModifier.size(40.dp).background(GlanceTheme.colors.primaryContainer).cornerRadius(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    ImageProvider(action.icon),
                    contentDescription = if (label) null else context.getString(action.label),
                    modifier = GlanceModifier.size(22.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                )
            }
            if (!label) return@Column
            Spacer(GlanceModifier.height(4.dp))
            Text(
                context.getString(action.label),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
    }
}

/** Width of one button: a launcher cell, give or take. */
private const val CELL_DP = 85f

/** Height from which the buttons wrap into two rows (two cells). */
private const val TWO_ROWS_DP = 130f

class QuickWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickWidget()
}
