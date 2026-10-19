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
import androidx.compose.animation.togetherWith
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.numynox.nexus.vibilia.ui.logbook.EditorSheetHost
import io.github.numynox.nexus.vibilia.ui.logbook.LogbookScreen
import io.github.numynox.nexus.vibilia.ui.prices.PricesScreen
import io.github.numynox.nexus.vibilia.ui.settings.SettingsScreen
import io.github.numynox.nexus.vibilia.ui.settings.VehicleEditorScreen
import io.github.numynox.nexus.vibilia.ui.statistics.StatisticsScreen
import kotlinx.serialization.Serializable

@Serializable data object PricesRoute : NavKey
@Serializable data object LogbookRoute : NavKey
@Serializable data object StatisticsRoute : NavKey
@Serializable data object SettingsRoute : NavKey

/** A vehicle's details, members and Bluetooth; [carId] null adds a new one. */
@Serializable data class VehicleRoute(val carId: String? = null) : NavKey

private data class TopLevel(val route: NavKey, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

// The web app's sidebar, regrouped: prices (history and nearby), the log of
// refuels and expenses, statistics, settings.
private val TopLevels = listOf(
    TopLevel(PricesRoute, "Prices", Icons.Outlined.LocalGasStation, Icons.Rounded.LocalGasStation),
    TopLevel(LogbookRoute, "Logbook", Icons.Outlined.Timeline, Icons.Rounded.Timeline),
    TopLevel(StatisticsRoute, "Statistics", Icons.Outlined.Insights, Icons.Rounded.Insights),
    TopLevel(SettingsRoute, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

/**
 * Navigation 3: the back stack is a plain list of keys. A tab switch resets it
 * to Prices plus that tab, so Back from any tab returns to Prices and then
 * leaves the app — the usual Android pattern for a bottom bar.
 */
@Composable
fun MainScaffold(app: AppViewModel) {
    val backStack = rememberNavBackStack(PricesRoute)
    val top = backStack.lastOrNull()
    val currentTab = backStack.lastOrNull { key -> TopLevels.any { it.route == key } } ?: PricesRoute
    val onTopLevel = TopLevels.any { it.route == top }

    fun open(route: NavKey) {
        if (top == route) return
        backStack.clear()
        backStack.add(PricesRoute)
        if (route != PricesRoute) backStack.add(route)
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevels.forEach { item ->
                val selected = item.route == currentTab
                item(
                    selected = selected,
                    onClick = { open(item.route) },
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
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            transitionSpec = {
                (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(tween(320)) { it / 20 }) togetherWith fadeOut(tween(120))
            },
            popTransitionSpec = { fadeIn(tween(260, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
            predictivePopTransitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
            entryProvider = entryProvider {
                entry<PricesRoute> { PricesScreen(app) }
                entry<LogbookRoute> { LogbookScreen(app) }
                entry<StatisticsRoute> { StatisticsScreen(app) }
                entry<SettingsRoute> { SettingsScreen(app, onOpenVehicle = { carId -> backStack.add(VehicleRoute(carId)) }) }
                entry<VehicleRoute>(
                    metadata = NavDisplay.transitionSpec {
                        (slideInHorizontally(tween(380)) { it / 3 } + fadeIn(tween(260))) togetherWith fadeOut(tween(120))
                    } + NavDisplay.popTransitionSpec {
                        fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(200)))
                    },
                ) { key ->
                    VehicleEditorScreen(app, carId = key.carId, onBack = { backStack.removeLastOrNull() })
                }
            },
        )
    }

    EditorSheetHost(app)
}
