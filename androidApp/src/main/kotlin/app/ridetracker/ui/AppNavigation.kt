package app.ridetracker.ui

import androidx.compose.foundation.border
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.ridetracker.R
import app.ridetracker.RideTrackerApplication
import app.ridetracker.ui.importing.ImportScreen
import app.ridetracker.ui.common.LocalBottomBarSpace
import app.ridetracker.ui.common.LocalHaze
import app.ridetracker.ui.common.rememberGlassStyle
import app.ridetracker.ui.common.LocalTabReselects
import app.ridetracker.ui.money.PlatformIncomeScreen
import kotlinx.datetime.LocalDate
import app.ridetracker.ui.common.LocalOpenMenu
import app.ridetracker.ui.entry.EntryScreen
import app.ridetracker.ui.expense.ExpenseScreen
import app.ridetracker.ui.expense.RecurringEditScreen
import app.ridetracker.ui.expense.RecurringListScreen
import app.ridetracker.ui.money.MoneyScreen
import app.ridetracker.ui.overview.OverviewScreen
import app.ridetracker.ui.trips.TripDetailsScreen
import app.ridetracker.ui.trips.TripsScreen
import app.ridetracker.ui.platforms.PlatformsScreen
import app.ridetracker.ui.menu.MenuScreen
import app.ridetracker.ui.settings.ExportScreen
import app.ridetracker.ui.settings.SettingsChoicePage
import app.ridetracker.ui.settings.SettingsPage
import app.ridetracker.ui.settings.SettingsScreen
import app.ridetracker.ui.settings.YourDataScreen
import app.ridetracker.ui.vehicle.VehicleEditScreen
import app.ridetracker.ui.vehicle.VehicleScreen

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("home", R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    MONEY("money", R.string.nav_money, Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet),
    TRIPS("trips", R.string.nav_trips, Icons.Outlined.Route, Icons.Filled.Route),
    VEHICLE("vehicle", R.string.nav_vehicle, Icons.Outlined.DirectionsCar, Icons.Filled.DirectionsCar),
}

/** Height of the floating bar plus its margins. */
private val FloatingBarHeight = 64.dp + 16.dp + 8.dp

/** Main tabs share a floating navigation bar; everything else (menu, settings, add income) opens over it. */
@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val sharedFiles = (LocalContext.current.applicationContext as RideTrackerApplication).container.sharedFiles
    val shared by sharedFiles.collectAsState()
    // Files shared to the app open the Import screen, which takes them from sharedFiles.
    LaunchedEffect(shared) {
        if (shared.isNotEmpty() && backStack?.destination?.route != "import") nav.navigate("import")
    }
    val currentTab = Tab.entries.firstOrNull { it.route == backStack?.destination?.route }
    val reselects = remember { mutableStateMapOf<Tab, Int>() }
    val haze = rememberHazeState()
    val barSpace = if (currentTab != null) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + FloatingBarHeight
    } else {
        0.dp
    }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalHaze provides haze, LocalBottomBarSpace provides barSpace, LocalOpenMenu provides { nav.navigate("menu") }) {
            // Tabs switch instantly; opening and closing a screen uses Material's shared axis X (as in Settings),
            // which the predictive back gesture follows under the finger.
            NavHost(
                navController = nav,
                startDestination = Tab.HOME.route,
                // The floating bar blurs whatever scrolls under it.
                modifier = Modifier.hazeSource(haze),
                enterTransition = { if (betweenTabs()) EnterTransition.None else SharedAxis.enter(forward = true) },
                exitTransition = { if (betweenTabs()) ExitTransition.None else SharedAxis.exit(forward = true) },
                popEnterTransition = { if (betweenTabs()) EnterTransition.None else SharedAxis.enter(forward = false) },
                popExitTransition = { if (betweenTabs()) ExitTransition.None else SharedAxis.exit(forward = false) },
            ) {
                composable(Tab.HOME.route) { ReselectScope(reselects[Tab.HOME] ?: 0) { OverviewScreen(onImport = { nav.navigate("import") }) } }
                composable(Tab.TRIPS.route) { ReselectScope(reselects[Tab.TRIPS] ?: 0) {
                    TripsScreen(onOpenTrip = { id -> nav.navigate("trip/$id") }, onImport = { nav.navigate("import") })
                } }
                composable(Tab.MONEY.route) { ReselectScope(reselects[Tab.MONEY] ?: 0) {
                    MoneyScreen(
                        onAddIncome = { nav.navigate("entry") },
                        onEditIncome = { id -> nav.navigate("entry?id=$id") },
                        onAddExpense = { nav.navigate("expense") },
                        onEditExpense = { id -> nav.navigate("expense?id=$id") },
                        onOpenRecurring = { nav.navigate("recurring") },
                        onOpenPlatform = { id, month -> nav.navigate("platform-income/$id/${month.range.start.toEpochDays()}") },
                    )
                } }
                composable(Tab.VEHICLE.route) { ReselectScope(reselects[Tab.VEHICLE] ?: 0) {
                    VehicleScreen(onEditVehicle = { nav.navigate("vehicle/edit") })
                } }
                composable("menu") {
                    MenuScreen(
                        onClose = { nav.popBackStack() },
                        onManagePlatforms = { nav.navigate("platforms") },
                        onImport = { nav.navigate("import") },
                        onYourData = { nav.navigate("data") },
                        onSettings = { nav.navigate("settings") },
                    )
                }
                composable("settings") {
                    SettingsScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(it.route) })
                }
                SettingsPage.entries.forEach { page ->
                    composable(page.route) {
                        SettingsChoicePage(page, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(it.route) })
                    }
                }
                composable("data") { YourDataScreen(onBack = { nav.popBackStack() }, onExport = { nav.navigate("export") }) }
                composable("export") { ExportScreen(onBack = { nav.popBackStack() }) }
                composable("vehicle/edit") { VehicleEditScreen(onDone = { nav.popBackStack() }) }
                composable(
                    "entry?id={id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                ) {
                    EntryScreen(onDone = { nav.popBackStack() }, onManagePlatforms = { nav.navigate("platforms") })
                }
                composable(
                    "expense?id={id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                ) {
                    ExpenseScreen(onDone = { nav.popBackStack() })
                }
                composable("recurring") {
                    RecurringListScreen(onBack = { nav.popBackStack() }, onEdit = { id -> nav.navigate("recurring/$id") })
                }
                composable("recurring/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    RecurringEditScreen(ruleId = entry.arguments?.getLong("id") ?: -1L, onDone = { nav.popBackStack() })
                }
                composable("import") { ImportScreen(onDone = { nav.popBackStack() }, sharedFiles = sharedFiles) }
                composable("trip/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    TripDetailsScreen(tripId = entry.arguments?.getLong("id") ?: -1L, onBack = { nav.popBackStack() })
                }
                composable("platforms") { PlatformsScreen(onBack = { nav.popBackStack() }) }
                composable(
                    "platform-income/{id}/{month}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("month") { type = NavType.LongType }),
                ) { entry ->
                    PlatformIncomeScreen(
                        platformId = entry.arguments?.getLong("id") ?: -1L,
                        monthStart = LocalDate.fromEpochDays(entry.arguments?.getLong("month") ?: 0L),
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
        if (currentTab != null) {
            // The + is a circle exactly as tall as the bar.
            val density = LocalDensity.current
            var barHeight by remember { mutableStateOf(48.dp) }
            BarWithAddButton(
                buttonSize = barHeight,
                bar = {
                    FloatingNavBar(
                        current = currentTab,
                        haze = haze,
                        onSelect = { if (it == currentTab) reselects[it] = (reselects[it] ?: 0) + 1 else nav.switchTab(it) },
                        modifier = Modifier.onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                    )
                },
                addButton = {
                    AddMenu(
                        size = barHeight,
                        onAddIncome = { nav.navigate("entry") },
                        onAddExpense = { nav.navigate("expense") },
                        onImport = { nav.navigate("import") },
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp),
            )
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs(): Boolean =
    Tab.entries.any { it.route == initialState.destination.route } && Tab.entries.any { it.route == targetState.destination.route }

/** Material shared axis X: the new screen slides in a little from the side it comes from while the old one fades. */
private object SharedAxis {
    private val decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    private const val DURATION = 300
    private const val SHIFT = 0.1f // of the screen width, about 30-40 dp

    fun enter(forward: Boolean): EnterTransition =
        slideInHorizontally(tween(DURATION, easing = decelerate)) { width -> ((if (forward) SHIFT else -SHIFT) * width).toInt() } +
            fadeIn(tween(DURATION / 2, delayMillis = DURATION / 4, easing = decelerate))

    fun exit(forward: Boolean): ExitTransition =
        slideOutHorizontally(tween(DURATION, easing = decelerate)) { width -> ((if (forward) -SHIFT else SHIFT) * width).toInt() } +
            fadeOut(tween(DURATION / 4, easing = accelerate))
}

@Composable
private fun ReselectScope(reselects: Int, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTabReselects provides reselects, content = content)
}

private fun NavHostController.switchTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Google Photos-style floating bar: the current tab shows icon + label, the others only an icon. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingNavBar(current: Tab, haze: HazeState, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    // Frosted glass (see Glass.kt) with a hairline outline instead of a grey container.
    val glass = rememberGlassStyle()
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier
            .animateContentSize()
            .clip(CircleShape)
            .hazeBlur(HazeInput.Sources(haze), glass)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(toolbarContainerColor = Color.Transparent),
    ) {
        Tab.entries.forEach { tab ->
            val label = stringResource(tab.label)
            if (tab == current) {
                FilledTonalButton(
                    onClick = { onSelect(tab) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Icon(tab.selectedIcon, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label, maxLines = 1)
                }
            } else {
                IconButton(onClick = { onSelect(tab) }) {
                    Icon(tab.icon, contentDescription = label)
                }
            }
        }
    }
}

/** Space between the bar and the + button, as in Google Photos' bar and search button. */
private val BarButtonGap = 8.dp

/** Material's FAB menu keeps this margin around its button, to the right and below. */
private val FabMenuMargin = 16.dp

/**
 * The floating bar and the + button side by side, centred together. The + menu opens upwards from its button
 * without moving the bar; the open menu's items may reach over the bar.
 */
@Composable
private fun BarWithAddButton(
    buttonSize: Dp,
    bar: @Composable () -> Unit,
    addButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(bar, addButton), modifier = modifier) { (barMeasurables, addMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val barPlaceable = barMeasurables.first().measure(loose)
        val addPlaceable = addMeasurables.first().measure(loose)
        val gap = BarButtonGap.roundToPx()
        val button = buttonSize.roundToPx()
        val margin = FabMenuMargin.roundToPx()
        // The menu's bottom margin hangs below the bar's line, into the space under the bar.
        val height = maxOf(barPlaceable.height, addPlaceable.height - margin)
        layout(constraints.maxWidth, height) {
            val barX = (constraints.maxWidth - (barPlaceable.width + gap + button)) / 2
            val barY = height - barPlaceable.height
            barPlaceable.place(barX, barY)
            // The menu's button sits at its bottom right: line it up beside the bar, centred on the bar's height.
            val buttonRight = barX + barPlaceable.width + gap + button
            val buttonBottom = barY + barPlaceable.height - (barPlaceable.height - button) / 2
            addPlaceable.place(buttonRight + margin - addPlaceable.width, buttonBottom + margin - addPlaceable.height)
        }
    }
}

/** M3 Expressive FAB menu next to the bar on every tab: one blue button, three actions (import, add expense, add income). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddMenu(size: Dp, onAddIncome: () -> Unit, onAddExpense: () -> Unit, onImport: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { expanded = it },
                containerColor = ToggleFloatingActionButtonDefaults.containerColor(
                    initialColor = MaterialTheme.colorScheme.primary,
                    finalColor = MaterialTheme.colorScheme.primary,
                ),
                // A circle as tall as the bar, like the search button next to Google Photos' bar.
                containerSize = ToggleFloatingActionButtonDefaults.containerSize(initialSize = size),
                containerCornerRadius = ToggleFloatingActionButtonDefaults.containerCornerRadius(initialSize = size / 2),
            ) {
                Icon(
                    if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = stringResource(if (expanded) R.string.close else R.string.add_income),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                onImport()
            },
            icon = { Icon(Icons.Outlined.UploadFile, contentDescription = null) },
            text = { Text(stringResource(R.string.import_title)) },
        )
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                onAddExpense()
            },
            icon = { Icon(Icons.Filled.Remove, contentDescription = null) },
            text = { Text(stringResource(R.string.add_expense)) },
        )
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                onAddIncome()
            },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.add_income)) },
        )
    }
}
