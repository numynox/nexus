package io.github.numynox.nexus.annona.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.numynox.nexus.annona.ui.add.AddScreen
import io.github.numynox.nexus.annona.ui.add.ConsumeScreen
import io.github.numynox.nexus.annona.ui.add.ScanScreen
import io.github.numynox.nexus.annona.ui.pantry.PantryScreen
import io.github.numynox.nexus.annona.ui.places.PlacesScreen
import io.github.numynox.nexus.annona.ui.products.ProductScreen
import io.github.numynox.nexus.annona.ui.products.ProductsScreen
import io.github.numynox.nexus.annona.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

/** What the scanner is for: stocking up, or using up (glossary: Consume). */
enum class ScanMode { Add, Consume }

@Serializable data object PantryRoute : NavKey
@Serializable data object ProductsRoute : NavKey
@Serializable data object PlacesRoute : NavKey
@Serializable data object SettingsRoute : NavKey

@Serializable data class ScanRoute(val mode: ScanMode) : NavKey

/** Adding stock: of a known product, for a barcode, or a new product typed by [name]. */
@Serializable data class AddRoute(val productId: Long? = null, val ean: String? = null, val name: String? = null) : NavKey
@Serializable data class ConsumeRoute(val productId: Long) : NavKey
@Serializable data class ProductRoute(val productId: Long) : NavKey

/** Where the app can be sent from a screen. */
class Navigator(private val push: (NavKey) -> Unit, private val replace: (NavKey) -> Unit, val back: () -> Unit) {
    fun scan(mode: ScanMode) = push(ScanRoute(mode))
    fun add(productId: Long? = null, ean: String? = null, name: String? = null, fromScanner: Boolean = false) =
        AddRoute(productId, ean, name).let { if (fromScanner) replace(it) else push(it) }
    fun consume(productId: Long, fromScanner: Boolean = false) = ConsumeRoute(productId).let { if (fromScanner) replace(it) else push(it) }
    fun product(productId: Long) = push(ProductRoute(productId))
}

private data class TopLevel(val route: NavKey, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

// The web's sidebar, regrouped: the pantry (dashboard and what to use up),
// the product catalogue, stock by place, settings (categories and locations
// live there). Scanning is a button on every tab, not a page.
private val TopLevels = listOf(
    TopLevel(PantryRoute, "Pantry", Icons.Outlined.Kitchen, Icons.Rounded.Kitchen),
    TopLevel(ProductsRoute, "Products", Icons.Outlined.ShoppingBasket, Icons.Rounded.ShoppingBasket),
    TopLevel(PlacesRoute, "Places", Icons.Outlined.Place, Icons.Rounded.Place),
    TopLevel(SettingsRoute, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

@Composable
fun MainScaffold(app: AppViewModel, shortcut: MutableStateFlow<ScanMode?>) {
    val backStack = rememberNavBackStack(PantryRoute)
    val top = backStack.lastOrNull()
    val currentTab = backStack.lastOrNull { key -> TopLevels.any { it.route == key } } ?: PantryRoute
    val onTopLevel = TopLevels.any { it.route == top }

    fun open(route: NavKey) {
        if (top == route) return
        backStack.clear()
        backStack.add(PantryRoute)
        if (route != PantryRoute) backStack.add(route)
    }

    val navigator = Navigator(
        push = { backStack.add(it) },
        // The scanner hands over to what it found; Back then skips it.
        replace = { key ->
            if (backStack.lastOrNull() is ScanRoute) backStack.removeLastOrNull()
            backStack.add(key)
        },
        back = { backStack.removeLastOrNull() },
    )

    val pending by shortcut.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let { mode ->
            shortcut.value = null
            navigator.scan(mode)
        }
    }

    // Confirmations ("Added 2 × Milk") outlive the screen that caused them,
    // so they are shown here, above whichever screen is current.
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
                    entry<PantryRoute> { PantryScreen(app, navigator) }
                    entry<ProductsRoute> { ProductsScreen(app, navigator) }
                    entry<PlacesRoute> { PlacesScreen(app, navigator) }
                    entry<SettingsRoute> { SettingsScreen(app) }
                    entry<ScanRoute>(metadata = sideways) { key -> ScanScreen(app, key.mode, navigator) }
                    entry<AddRoute>(metadata = sideways) { key -> AddScreen(app, key, navigator) }
                    entry<ConsumeRoute>(metadata = sideways) { key -> ConsumeScreen(app, key.productId, navigator) }
                    entry<ProductRoute>(metadata = sideways) { key -> ProductScreen(app, key.productId, navigator) }
                },
            )
        }
        SnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (onTopLevel) 96.dp else 88.dp),
        )
    }
}
