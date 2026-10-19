package io.github.numynox.nexus.noctua.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.numynox.nexus.noctua.ui.feed.FeedScreen
import io.github.numynox.nexus.noctua.ui.library.LibraryScreen
import io.github.numynox.nexus.noctua.ui.settings.SectionEditorScreen
import io.github.numynox.nexus.noctua.ui.settings.SettingsScreen
import io.github.numynox.nexus.noctua.ui.statistics.StatisticsScreen
import kotlinx.serialization.Serializable

@Serializable data object FeedRoute : NavKey
@Serializable data object LibraryRoute : NavKey
@Serializable data object StatisticsRoute : NavKey
@Serializable data object SettingsRoute : NavKey

/** One section's name, icon and feeds. */
@Serializable data class SectionRoute(val sectionId: Long) : NavKey

private data class TopLevel(val route: NavKey, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

// The web app's sidebar, regrouped for a phone: the feed (sections become
// chips at its top), the library of starred and read articles, statistics,
// settings (where sections are managed).
private val TopLevels = listOf(
    TopLevel(FeedRoute, "Feed", Icons.Outlined.ViewAgenda, Icons.Rounded.ViewAgenda),
    TopLevel(LibraryRoute, "Library", Icons.Outlined.Bookmarks, Icons.Rounded.Bookmarks),
    TopLevel(StatisticsRoute, "Statistics", Icons.Outlined.Insights, Icons.Rounded.Insights),
    TopLevel(SettingsRoute, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

/**
 * Navigation 3, as in Vibilia: a tab switch resets the back stack to the feed
 * plus that tab, so Back returns to the feed and then leaves the app.
 */
@Composable
fun MainScaffold(app: AppViewModel) {
    val backStack = rememberNavBackStack(FeedRoute)
    val top = backStack.lastOrNull()
    val currentTab = backStack.lastOrNull { key -> TopLevels.any { it.route == key } } ?: FeedRoute
    val onTopLevel = TopLevels.any { it.route == top }

    fun open(route: NavKey) {
        if (top == route) return
        backStack.clear()
        backStack.add(FeedRoute)
        if (route != FeedRoute) backStack.add(route)
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
                entry<FeedRoute> { FeedScreen(app, onManageSections = { open(SettingsRoute) }) }
                entry<LibraryRoute> { LibraryScreen(app) }
                entry<StatisticsRoute> { StatisticsScreen() }
                entry<SettingsRoute> { SettingsScreen(app, onOpenSection = { backStack.add(SectionRoute(it)) }) }
                entry<SectionRoute>(
                    metadata = NavDisplay.transitionSpec {
                        (slideInHorizontally(tween(380)) { it / 3 } + fadeIn(tween(260))) togetherWith fadeOut(tween(120))
                    } + NavDisplay.popTransitionSpec {
                        fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(200)))
                    },
                ) { key ->
                    SectionEditorScreen(app, sectionId = key.sectionId, onBack = { backStack.removeLastOrNull() })
                }
            },
        )
    }
}
