package app.ridetracker.ui

import androidx.annotation.StringRes
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
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import app.ridetracker.ui.common.LocalBottomBarSpace
import app.ridetracker.ui.common.LocalOpenMenu
import app.ridetracker.ui.entry.EntryScreen
import app.ridetracker.ui.expense.ExpenseScreen
import app.ridetracker.ui.money.MoneyScreen
import app.ridetracker.ui.overview.OverviewScreen
import app.ridetracker.ui.placeholder.ComingSoonScreen
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
    val currentTab = Tab.entries.firstOrNull { it.route == backStack?.destination?.route }
    val barSpace = if (currentTab != null) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + FloatingBarHeight
    } else {
        0.dp
    }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBottomBarSpace provides barSpace, LocalOpenMenu provides { nav.navigate("menu") }) {
            NavHost(navController = nav, startDestination = Tab.HOME.route) {
                composable(Tab.HOME.route) { OverviewScreen(onAddEntry = { nav.navigate("entry") }, onAddExpense = { nav.navigate("expense") }) }
                composable(Tab.TRIPS.route) {
                    ComingSoonScreen(R.string.nav_trips, Icons.Outlined.Route, R.string.trips_coming_soon)
                }
                composable(Tab.MONEY.route) {
                    MoneyScreen(
                        onAddIncome = { nav.navigate("entry") },
                        onEditIncome = { id -> nav.navigate("entry?id=$id") },
                        onAddExpense = { nav.navigate("expense") },
                        onEditExpense = { id -> nav.navigate("expense?id=$id") },
                    )
                }
                composable(Tab.VEHICLE.route) {
                    VehicleScreen(onEditVehicle = { nav.navigate("vehicle/edit") })
                }
                composable("menu") {
                    MenuScreen(
                        onClose = { nav.popBackStack() },
                        onManagePlatforms = { nav.navigate("platforms") },
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
                composable("platforms") { PlatformsScreen(onBack = { nav.popBackStack() }) }
            }
        }
        if (currentTab != null) {
            FloatingNavBar(
                current = currentTab,
                onSelect = { nav.switchTab(it) },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
            )
        }
    }
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
private fun FloatingNavBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.animateContentSize(),
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    ) {
        Tab.entries.forEach { tab ->
            val label = stringResource(tab.label)
            if (tab == current) {
                FilledTonalButton(onClick = { onSelect(tab) }) {
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
