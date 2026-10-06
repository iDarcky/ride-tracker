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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
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
import app.ridetracker.ui.entry.EntryScreen
import app.ridetracker.ui.money.MoneyScreen
import app.ridetracker.ui.overview.OverviewScreen
import app.ridetracker.ui.placeholder.ComingSoonScreen
import app.ridetracker.ui.platforms.PlatformsScreen
import app.ridetracker.ui.settings.SettingsScreen

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("home", R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    TRIPS("trips", R.string.nav_trips, Icons.Outlined.Route, Icons.Filled.Route),
    MONEY("money", R.string.nav_money, Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet),
    VEHICLE("vehicle", R.string.nav_vehicle, Icons.Outlined.DirectionsCar, Icons.Filled.DirectionsCar),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** Height of the floating bar plus its margins. */
private val FloatingBarHeight = 64.dp + 16.dp + 8.dp

/** Top-level screens share a floating navigation bar; detail screens (add income, apps) cover it. */
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
        CompositionLocalProvider(LocalBottomBarSpace provides barSpace) {
            NavHost(navController = nav, startDestination = Tab.HOME.route) {
                composable(Tab.HOME.route) { OverviewScreen(onAddEntry = { nav.navigate("entry") }) }
                composable(Tab.TRIPS.route) {
                    ComingSoonScreen(R.string.nav_trips, Icons.Outlined.Route, R.string.trips_coming_soon)
                }
                composable(Tab.MONEY.route) {
                    MoneyScreen(
                        onAddIncome = { nav.navigate("entry") },
                        onEditIncome = { id -> nav.navigate("entry?id=$id") },
                    )
                }
                composable(Tab.VEHICLE.route) {
                    ComingSoonScreen(R.string.nav_vehicle, Icons.Outlined.DirectionsCar, R.string.vehicle_coming_soon)
                }
                composable(Tab.SETTINGS.route) { SettingsScreen(onManagePlatforms = { nav.navigate("platforms") }) }
                composable(
                    "entry?id={id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                ) {
                    EntryScreen(onDone = { nav.popBackStack() }, onManagePlatforms = { nav.navigate("platforms") })
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
