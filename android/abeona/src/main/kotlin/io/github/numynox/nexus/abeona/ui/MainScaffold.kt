package io.github.numynox.nexus.abeona.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.numynox.nexus.abeona.ui.bahncards.BahnCardScreen
import io.github.numynox.nexus.abeona.ui.claims.ClaimsScreen
import io.github.numynox.nexus.abeona.ui.intake.IncomingShare
import io.github.numynox.nexus.abeona.ui.review.ReviewScreen
import io.github.numynox.nexus.abeona.ui.search.SearchScreen
import io.github.numynox.nexus.abeona.ui.settings.SettingsScreen
import io.github.numynox.nexus.abeona.ui.trip.TripScreen
import io.github.numynox.nexus.abeona.ui.trips.PastTripsScreen
import io.github.numynox.nexus.abeona.ui.trips.TripsScreen
import io.github.numynox.nexus.abeona.ui.wallet.TicketScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

@Serializable data object TripsRoute : NavKey
@Serializable data object SearchRoute : NavKey
@Serializable data object ReviewRoute : NavKey
@Serializable data object SettingsRoute : NavKey

@Serializable data class TripRoute(val tripId: String) : NavKey
@Serializable data class TicketRoute(val tripId: String) : NavKey
@Serializable data class BahnCardRoute(val cardId: String) : NavKey
@Serializable data object ClaimsRoute : NavKey
@Serializable data object PastTripsRoute : NavKey

/** Where the app can be sent from a screen. */
class Navigator(
    private val push: (NavKey) -> Unit,
    private val switchTab: (NavKey) -> Unit,
    private val replace: (NavKey) -> Unit,
    val back: () -> Unit,
) {
    fun trip(id: String) = push(TripRoute(id))
    fun ticket(tripId: String) = push(TicketRoute(tripId))
    fun search() = switchTab(SearchRoute)
    fun review() = switchTab(ReviewRoute)
    fun settings() = switchTab(SettingsRoute)
    fun bahnCard(cardId: String) = push(BahnCardRoute(cardId))
    fun claims() = push(ClaimsRoute)
    fun pastTrips() = push(PastTripsRoute)

    /** After saving a search result: the trip, with Back going to the trips list. */
    fun savedTrip(id: String) {
        switchTab(TripsRoute)
        push(TripRoute(id))
    }

    /** Replaces the current screen, e.g. a deleted trip with the list. */
    fun replaceWith(route: NavKey) = replace(route)
}

private data class TopLevel(val route: NavKey, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

// Trips is home: the next ride first. Search plans a new one; Review is the
// year in rails; Settings holds the household and BahnCards.
private val TopLevels = listOf(
    TopLevel(TripsRoute, "Trips", Icons.Outlined.Train, Icons.Rounded.Train),
    TopLevel(SearchRoute, "Search", Icons.Outlined.Search, Icons.Rounded.Search),
    TopLevel(ReviewRoute, "Review", Icons.Outlined.AutoAwesome, Icons.Rounded.AutoAwesome),
    TopLevel(SettingsRoute, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

@Composable
fun MainScaffold(app: AppViewModel, openTrip: MutableStateFlow<String?>, incoming: MutableStateFlow<IncomingShare?>) {
    val backStack = rememberNavBackStack(TripsRoute)
    val top = backStack.lastOrNull()
    val currentTab = backStack.lastOrNull { key -> TopLevels.any { it.route == key } } ?: TripsRoute
    val onTopLevel = TopLevels.any { it.route == top }

    fun open(route: NavKey) {
        if (top == route) return
        backStack.clear()
        backStack.add(TripsRoute)
        if (route != TripsRoute) backStack.add(route)
    }

    val navigator = Navigator(
        push = { backStack.add(it) },
        switchTab = { open(it) },
        replace = { key ->
            backStack.removeLastOrNull()
            if (backStack.lastOrNull() != key) backStack.add(key)
        },
        back = { backStack.removeLastOrNull() },
    )

    val pending by openTrip.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let { id ->
            openTrip.value = null
            open(TripsRoute)
            navigator.trip(id)
        }
    }

    // A share from another app goes to the search screen, which reads it (intake).
    val share by incoming.collectAsStateWithLifecycle()
    LaunchedEffect(share) {
        share?.let {
            incoming.value = null
            app.share.value = it
            open(SearchRoute)
        }
    }

    // Confirmations outlive the screen that caused them, so they are shown
    // here, above whichever screen is current.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(app) { app.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
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
            layoutType = if (onTopLevel) NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo()) else NavigationSuiteType.None,
        ) {
            val sideways = NavDisplay.transitionSpec {
                (slideInHorizontally(tween(380)) { it / 3 } + fadeIn(tween(260))) togetherWith fadeOut(tween(120))
            } + NavDisplay.popTransitionSpec {
                fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(200)))
            }
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                transitionSpec = {
                    (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(tween(320)) { it / 20 }) togetherWith fadeOut(tween(120))
                },
                popTransitionSpec = { fadeIn(tween(260, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
                predictivePopTransitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
                entryProvider = entryProvider {
                    entry<TripsRoute> { TripsScreen(app, navigator) }
                    entry<SearchRoute> { SearchScreen(app, navigator) }
                    entry<ReviewRoute> { ReviewScreen(app, navigator) }
                    entry<SettingsRoute> { SettingsScreen(app, navigator) }
                    entry<TripRoute>(metadata = sideways) { key -> TripScreen(app, key.tripId, navigator) }
                    entry<TicketRoute>(metadata = sideways) { key -> TicketScreen(app, key.tripId, navigator) }
                    entry<BahnCardRoute>(metadata = sideways) { key -> BahnCardScreen(app, key.cardId, navigator) }
                    entry<ClaimsRoute>(metadata = sideways) { ClaimsScreen(app, navigator) }
                    entry<PastTripsRoute>(metadata = sideways) { PastTripsScreen(app, navigator) }
                },
            )
        }
        SnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (onTopLevel) 96.dp else 88.dp),
        )
    }
}
