package io.github.numynox.nexus.vibilia.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.numynox.nexus.vibilia.ui.logbook.EditorSheetHost
import io.github.numynox.nexus.vibilia.ui.logbook.LogbookScreen
import io.github.numynox.nexus.vibilia.ui.prices.PricesScreen
import io.github.numynox.nexus.vibilia.ui.settings.SettingsScreen
import io.github.numynox.nexus.vibilia.ui.settings.VehicleEditorScreen
import io.github.numynox.nexus.vibilia.ui.statistics.StatisticsScreen
import kotlinx.serialization.Serializable

@Serializable data object PricesRoute
@Serializable data object LogbookRoute
@Serializable data object StatisticsRoute
@Serializable data object SettingsRoute

/** A vehicle's details, members and Bluetooth; [carId] null adds a new one. */
@Serializable data class VehicleRoute(val carId: String? = null)

private data class TopLevel(val route: Any, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

// The web app's sidebar, regrouped: prices (history and nearby), the log of
// refuels and expenses, statistics, settings.
private val TopLevels = listOf(
    TopLevel(PricesRoute, "Prices", Icons.Outlined.LocalGasStation, Icons.Rounded.LocalGasStation),
    TopLevel(LogbookRoute, "Logbook", Icons.Outlined.Timeline, Icons.Rounded.Timeline),
    TopLevel(StatisticsRoute, "Statistics", Icons.Outlined.Insights, Icons.Rounded.Insights),
    TopLevel(SettingsRoute, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

@Composable
fun MainScaffold(app: AppViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val onTopLevel = destination == null || TopLevels.any { destination.hasRoute(it.route::class) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevels.forEach { item ->
                val selected = destination?.hierarchy?.any { it.hasRoute(item.route::class) } == true
                item(
                    selected = selected,
                    onClick = {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                    label = { Text(item.label) },
                )
            }
        },
        layoutType = if (onTopLevel) {
            NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())
        } else {
            NavigationSuiteType.None
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = PricesRoute,
            enterTransition = { fadeIn(tween(260, delayMillis = 60)) + slideInVertically(tween(320)) { it / 20 } },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(260, delayMillis = 60)) },
            popExitTransition = { fadeOut(tween(120)) },
        ) {
            composable<PricesRoute> { PricesScreen(app) }
            composable<LogbookRoute> { LogbookScreen(app) }
            composable<StatisticsRoute> { StatisticsScreen(app) }
            composable<SettingsRoute> {
                SettingsScreen(app, onOpenVehicle = { carId -> navController.navigate(VehicleRoute(carId)) })
            }
            composable<VehicleRoute>(
                enterTransition = { slideInHorizontally(tween(380)) { it / 3 } + fadeIn(tween(260)) },
                popExitTransition = { slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(200)) },
            ) { entry ->
                VehicleEditorScreen(app, carId = entry.toRoute<VehicleRoute>().carId, onBack = { navController.popBackStack() })
            }
        }
    }

    EditorSheetHost(app)
}
