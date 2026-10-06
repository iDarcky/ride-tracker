package app.ridetracker.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.ridetracker.R
import app.ridetracker.ui.entry.EntryScreen
import app.ridetracker.ui.more.MoreScreen
import app.ridetracker.ui.overview.OverviewScreen
import app.ridetracker.ui.placeholder.ComingSoonScreen
import app.ridetracker.ui.platforms.PlatformsScreen

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    OVERVIEW("overview", R.string.nav_overview, Icons.Outlined.Insights, Icons.Filled.Insights),
    TRIPS("trips", R.string.nav_trips, Icons.Outlined.Route, Icons.Filled.Route),
    VEHICLE("vehicle", R.string.nav_vehicle, Icons.Outlined.DirectionsCar, Icons.Filled.DirectionsCar),
    EXPENSES("expenses", R.string.nav_expenses, Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
    MORE("more", R.string.nav_more, Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz),
}

/** Top-level screens share a navigation bar; detail screens (add income, apps) cover it. */
@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        val selected = tab == currentTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.OVERVIEW.route,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Tab.OVERVIEW.route) {
                OverviewScreen(
                    onAddEntry = { nav.navigate("entry") },
                    onEditEntry = { id -> nav.navigate("entry?id=$id") },
                )
            }
            composable(Tab.TRIPS.route) {
                ComingSoonScreen(R.string.nav_trips, Icons.Outlined.Route, R.string.trips_coming_soon)
            }
            composable(Tab.VEHICLE.route) {
                ComingSoonScreen(R.string.nav_vehicle, Icons.Outlined.DirectionsCar, R.string.vehicle_coming_soon)
            }
            composable(Tab.EXPENSES.route) {
                ComingSoonScreen(R.string.nav_expenses, Icons.AutoMirrored.Outlined.ReceiptLong, R.string.expenses_coming_soon)
            }
            composable(Tab.MORE.route) { MoreScreen(onManagePlatforms = { nav.navigate("platforms") }) }
            composable(
                "entry?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) {
                EntryScreen(onDone = { nav.popBackStack() }, onManagePlatforms = { nav.navigate("platforms") })
            }
            composable("platforms") { PlatformsScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
