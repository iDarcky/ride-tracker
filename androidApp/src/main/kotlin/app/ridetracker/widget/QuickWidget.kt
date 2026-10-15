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
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import app.ridetracker.MainActivity
import app.ridetracker.R
import app.ridetracker.ui.theme.DarkColors
import app.ridetracker.ui.theme.LightColors

/**
 * Quick actions on the home screen (owner, 15 Oct 2026): Fuel (a new expense already set to fuel), Import, Expense and
 * Income, the same icons as the + menu. A row when wide (4×1), a 2×2 grid when square.
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
        provideContent { GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors)) { Content() } }
    }

    @Composable
    private fun Content() {
        val size = LocalSize.current
        val grid = size.width < size.height * 2f
        Box(
            GlanceModifier.fillMaxSize().appWidgetBackground().background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(28.dp).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (grid) {
                Column(GlanceModifier.fillMaxSize()) {
                    actions.chunked(2).forEach { pair ->
                        Row(GlanceModifier.defaultWeight().fillMaxWidth()) { pair.forEach { Button(it, GlanceModifier.defaultWeight()) } }
                    }
                }
            } else {
                Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    actions.forEach { Button(it, GlanceModifier.defaultWeight()) }
                }
            }
        }
    }

    @Composable
    private fun Button(action: Action, modifier: GlanceModifier) {
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
                    contentDescription = null,
                    modifier = GlanceModifier.size(22.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(
                context.getString(action.label),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
    }
}

class QuickWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickWidget()
}
