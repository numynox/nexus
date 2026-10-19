package io.github.numynox.nexus.core.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** How an app follows light and dark. Device-local, like the web apps' theme. */
enum class ThemeMode { System, Light, Dark }

/** An app's own colours, generated from its logo colour (see each app's palette). */
@Immutable
data class NexusPalette(val light: ColorScheme, val dark: ColorScheme)

/** Semantic colours Material 3 does not name: a full tank, a low one. */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

private val LightExtended = ExtendedColors(
    success = Color(0xFF2E7D32),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFB8F0B9),
    onSuccessContainer = Color(0xFF002106),
    warning = Color(0xFF8C5000),
    warningContainer = Color(0xFFFFDCBE),
    onWarningContainer = Color(0xFF2D1600),
)

private val DarkExtended = ExtendedColors(
    success = Color(0xFF7ED987),
    onSuccess = Color(0xFF00390F),
    successContainer = Color(0xFF00531A),
    onSuccessContainer = Color(0xFFB8F0B9),
    warning = Color(0xFFFFB870),
    warningContainer = Color(0xFF6B3B00),
    onWarningContainer = Color(0xFFFFDCBE),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

object NexusThemeDefaults {
    val extendedColors: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}

private val NexusShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Material's type scale, with headlines a step heavier for the Expressive look. */
private val NexusTypography: Typography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Tabular figures, so prices and counters do not jitter as their digits change. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * The theme every Nexus app wraps itself in: Material 3 Expressive with its
 * expressive motion scheme, the app's own palette or the wallpaper's colours,
 * and colour changes that ease in rather than snap.
 */
@Composable
fun NexusTheme(
    palette: NexusPalette,
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val target = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> palette.dark
        else -> palette.light
    }

    SystemBarsFollow(dark)

    CompositionLocalProvider(LocalExtendedColors provides if (dark) DarkExtended else LightExtended) {
        MaterialExpressiveTheme(
            colorScheme = target.animated(),
            motionScheme = MotionScheme.expressive(),
            shapes = NexusShapes,
            typography = NexusTypography,
            content = content,
        )
    }
}

/** Status and navigation bar icons follow the app's theme, not the system's. */
@Composable
private fun SystemBarsFollow(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    LaunchedEffect(activity, dark) {
        val style = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

@Composable
private fun ColorScheme.animated(): ColorScheme {
    @Composable
    fun Color.ease(): Color = animateColorAsState(this, tween(durationMillis = 450), label = "scheme").value

    return copy(
        primary = primary.ease(),
        onPrimary = onPrimary.ease(),
        primaryContainer = primaryContainer.ease(),
        onPrimaryContainer = onPrimaryContainer.ease(),
        secondary = secondary.ease(),
        onSecondary = onSecondary.ease(),
        secondaryContainer = secondaryContainer.ease(),
        onSecondaryContainer = onSecondaryContainer.ease(),
        tertiary = tertiary.ease(),
        onTertiary = onTertiary.ease(),
        tertiaryContainer = tertiaryContainer.ease(),
        onTertiaryContainer = onTertiaryContainer.ease(),
        background = background.ease(),
        onBackground = onBackground.ease(),
        surface = surface.ease(),
        onSurface = onSurface.ease(),
        surfaceVariant = surfaceVariant.ease(),
        onSurfaceVariant = onSurfaceVariant.ease(),
        surfaceContainerLowest = surfaceContainerLowest.ease(),
        surfaceContainerLow = surfaceContainerLow.ease(),
        surfaceContainer = surfaceContainer.ease(),
        surfaceContainerHigh = surfaceContainerHigh.ease(),
        surfaceContainerHighest = surfaceContainerHighest.ease(),
        outline = outline.ease(),
        outlineVariant = outlineVariant.ease(),
    )
}
