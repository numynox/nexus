package io.github.numynox.nexus.abeona.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.numynox.nexus.core.ui.theme.NexusPalette

/**
 * Abeona's brand colours, SJ-inspired: a calm railway green on an airy
 * off-white, ink for text, mint and sky for surfaces, amber for anything that
 * needs attention (a delay, a tight change), and a muted brick for the rare
 * real problem — never walls of red.
 */
object AbeonaColors {
    val Green = Color(0xFF1F6F5C)
    val Ink = Color(0xFF12313A)
    val Mint = Color(0xFFE8F3EE)
    val Sky = Color(0xFFCFE6F2)
    val Amber = Color(0xFFF2B84B)
    val Danger = Color(0xFFC4553B)
    val Background = Color(0xFFF7F8F5)
}

/** Amber with ink on it: delays, changed platforms, tight changes. Readable in both themes. */
data class Attention(val container: Color, val content: Color)

val attention: Attention
    @Composable @ReadOnlyComposable get() = Attention(AbeonaColors.Amber, AbeonaColors.Ink)

/** The brick red for cancellations and changes at risk, as a small fill — not as text on large areas. */
val danger: Attention
    @Composable @ReadOnlyComposable get() =
        if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Attention(Color(0xFFE38B74), Color(0xFF3A0E05))
        else Attention(AbeonaColors.Danger, Color.White)

/** Calm confirmation: mint with green, for "Safe" and "on time". */
val calm: Attention
    @Composable @ReadOnlyComposable get() = Attention(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)

val AbeonaPalette = NexusPalette(
    light = lightColorScheme(
        primary = AbeonaColors.Green,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCDEBDF),
        onPrimaryContainer = Color(0xFF0A3F33),
        inversePrimary = Color(0xFF8AD5BC),
        secondary = Color(0xFF3E6370),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = AbeonaColors.Sky,
        onSecondaryContainer = AbeonaColors.Ink,
        tertiary = Color(0xFF7A5900),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFCE3A9),
        onTertiaryContainer = Color(0xFF3F2E00),
        background = AbeonaColors.Background,
        onBackground = AbeonaColors.Ink,
        surface = AbeonaColors.Background,
        onSurface = AbeonaColors.Ink,
        surfaceVariant = Color(0xFFDCE5E0),
        onSurfaceVariant = Color(0xFF41524F),
        surfaceTint = AbeonaColors.Green,
        inverseSurface = AbeonaColors.Ink,
        inverseOnSurface = Color(0xFFEAF2EE),
        error = AbeonaColors.Danger,
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFADBD2),
        onErrorContainer = Color(0xFF5C1A0D),
        outline = Color(0xFF6F7E7A),
        outlineVariant = Color(0xFFC3CEC9),
        scrim = Color(0xFF000000),
        surfaceBright = AbeonaColors.Background,
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF0F5F1),
        surfaceContainer = AbeonaColors.Mint,
        surfaceContainerHigh = Color(0xFFE0ECE6),
        surfaceContainerHighest = Color(0xFFD7E4DE),
        surfaceDim = Color(0xFFD7DDD9),
    ),
    dark = darkColorScheme(
        primary = Color(0xFF8AD5BC),
        onPrimary = Color(0xFF00382B),
        primaryContainer = Color(0xFF145244),
        onPrimaryContainer = Color(0xFFCDEBDF),
        inversePrimary = AbeonaColors.Green,
        secondary = Color(0xFFAFCCD8),
        onSecondary = Color(0xFF16323C),
        secondaryContainer = Color(0xFF26434D),
        onSecondaryContainer = AbeonaColors.Sky,
        tertiary = AbeonaColors.Amber,
        onTertiary = Color(0xFF402D00),
        tertiaryContainer = Color(0xFF5C4300),
        onTertiaryContainer = Color(0xFFFFDFA0),
        background = Color(0xFF0E1A1D),
        onBackground = Color(0xFFDCE8E4),
        surface = Color(0xFF0E1A1D),
        onSurface = Color(0xFFDCE8E4),
        surfaceVariant = Color(0xFF3F4E4B),
        onSurfaceVariant = Color(0xFFBFCBC6),
        surfaceTint = Color(0xFF8AD5BC),
        inverseSurface = Color(0xFFDCE8E4),
        inverseOnSurface = AbeonaColors.Ink,
        error = Color(0xFFF0A08C),
        onError = Color(0xFF5C1A0D),
        errorContainer = Color(0xFF7A2E1C),
        onErrorContainer = Color(0xFFFADBD2),
        outline = Color(0xFF89958F),
        outlineVariant = Color(0xFF3F4E4B),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF34413F),
        surfaceContainerLowest = Color(0xFF091315),
        surfaceContainerLow = Color(0xFF142226),
        surfaceContainer = Color(0xFF18282C),
        surfaceContainerHigh = Color(0xFF203236),
        surfaceContainerHighest = Color(0xFF2A3C40),
        surfaceDim = Color(0xFF0E1A1D),
    ),
)
