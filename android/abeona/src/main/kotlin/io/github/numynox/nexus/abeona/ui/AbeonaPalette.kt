package io.github.numynox.nexus.abeona.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.numynox.nexus.core.ui.theme.NexusPalette

/**
 * Abeona's colours (abeona-design.md §3): a burgundy for the brand, the
 * current step and the primary action, on warm paper with ink for text.
 * Green, gold and red are only ever status ([ok], [watch], [bad]) and always
 * come with an icon; nothing else is coloured.
 */
object AbeonaColors {
    /**
     * Burgundy as a fill: the primary button, rail progress, the state disc.
     * It is dark enough to be the text colour too, so fill and [Brand] are one.
     */
    val Burgundy = Color(0xFF8C1D40)
    val BurgundyDark = Color(0xFFF08CA8)
    val OnBurgundy = Color(0xFFFFFFFF)
    val OnBurgundyDark = Color(0xFF3D0A1A)

    /** Burgundy as text and icons on a surface. */
    val Brand = Burgundy
    val BrandSoft = Color(0xFFF7E1E7)
    val BrandSoftDark = Color(0xFF452033)

    val Paper = Color(0xFFFBF7F2)
    val PaperDark = Color(0xFF1A1512)
    val Card = Color(0xFFFFFFFF)
    val CardDark = Color(0xFF26201C)
    val Well = Color(0xFFF4EEE6)
    val WellDark = Color(0xFF322A24)
    val Ink = Color(0xFF2A211C)
    val InkDark = Color(0xFFF3EAE2)
    val Muted = Color(0xFF6B5F57)
    val MutedDark = Color(0xFFB5A79C)

    /** The one thing that changed (a platform) and the one thing that broke, as fills. */
    val WatchFill = Color(0xFFF2C230)
    val OnWatchFill = Color(0xFF3A2A00)
    val BadFill = Color(0xFFC4372B)
}

/** A surface colour and what is written on it. */
data class Tone(val container: Color, val content: Color)

/**
 * A status colour: [soft] for chips and banners (the default), [fill] for the
 * one thing on the screen that broke or changed. [ok] has no fill of its own.
 */
data class Status(val soft: Tone, val fill: Tone)

private val isDark: Boolean
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** On time, a safe change, covered by the ticket, paid. */
val ok: Status
    @Composable @ReadOnlyComposable get() {
        val soft = if (isDark) Tone(Color(0xFF1D4634), Color(0xFFA6E3C4)) else Tone(Color(0xFFDDF0E6), Color(0xFF1B5A40))
        return Status(soft, soft)
    }

/** A delay, a tight change, a changed platform, the binding lifted, a deadline near. Never red. */
val watch: Status
    @Composable @ReadOnlyComposable get() = Status(
        soft = if (isDark) Tone(Color(0xFF4A3A00), Color(0xFFFFE08A)) else Tone(Color(0xFFFCEFC2), Color(0xFF6B4D00)),
        fill = Tone(AbeonaColors.WatchFill, AbeonaColors.OnWatchFill),
    )

/** A ride that does not run, a change that cannot be made or is at risk, a ticket that does not cover. */
val bad: Status
    @Composable @ReadOnlyComposable get() = Status(
        soft = if (isDark) Tone(Color(0xFF5C1A12), Color(0xFFFFC2B8)) else Tone(Color(0xFFFBDAD5), Color(0xFF9E241A)),
        fill = Tone(AbeonaColors.BadFill, Color.White),
    )

/**
 * Burgundy as a fill with what goes on it. With the wallpaper's colours
 * chosen instead of Abeona's, the scheme's own primary.
 */
val burgundy: Tone
    @Composable @ReadOnlyComposable get() {
        val scheme = MaterialTheme.colorScheme
        return when (scheme.primary) {
            AbeonaPalette.light.primary -> Tone(AbeonaColors.Burgundy, AbeonaColors.OnBurgundy)
            AbeonaPalette.dark.primary -> Tone(AbeonaColors.BurgundyDark, AbeonaColors.OnBurgundyDark)
            else -> Tone(scheme.primary, scheme.onPrimary)
        }
    }

/** Burgundy as text and icons on a surface: the eyebrow, the countdown, links. */
val brand: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

/** The live card, selected chips. */
val brandSoft: Tone
    @Composable @ReadOnlyComposable get() = Tone(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)

/** Insets, platform boxes, quiet chips. */
val well: Tone
    @Composable @ReadOnlyComposable get() = Tone(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)

/** Raised surfaces. */
val card: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerLow

/** Secondary text and icons. */
val muted: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** Hairlines: ink at 12 % (14 % on dark). */
val hairline: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.14f else 0.12f)

/**
 * The one type scale (abeona-design.md §4), named as on the web (`t-*`).
 * Figures are tabular everywhere. [meta] is written in [muted], [label] in
 * capitals; the caller passes the colour and the case.
 */
object AbeonaType {
    private const val TABULAR = "tnum"

    /** The countdown; one per screen. */
    val numeral = TextStyle(fontSize = 48.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp, fontFeatureSettings = TABULAR)

    /** The time that goes with the headline. */
    val time = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR)

    /** The answer to the moment's question; one per screen. */
    val headline = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR)

    /** Card titles, a stop's name in a row that matters. */
    val title = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR)

    /** Rows, sentences. */
    val body = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = TABULAR)

    /** Supporting facts, captions. */
    val meta = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR)

    /** The live card's state line and column heads only. */
    val label = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontFeatureSettings = TABULAR)
}

val AbeonaPalette = NexusPalette(
    light = lightColorScheme(
        primary = AbeonaColors.Brand,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = AbeonaColors.BrandSoft,
        onPrimaryContainer = AbeonaColors.Ink,
        inversePrimary = AbeonaColors.BurgundyDark,
        secondary = AbeonaColors.Muted,
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = AbeonaColors.Well,
        onSecondaryContainer = AbeonaColors.Ink,
        tertiary = Color(0xFF6B4D00),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFCEFC2),
        onTertiaryContainer = Color(0xFF6B4D00),
        background = AbeonaColors.Paper,
        onBackground = AbeonaColors.Ink,
        surface = AbeonaColors.Paper,
        onSurface = AbeonaColors.Ink,
        surfaceVariant = AbeonaColors.Well,
        onSurfaceVariant = AbeonaColors.Muted,
        surfaceTint = AbeonaColors.Brand,
        inverseSurface = AbeonaColors.Ink,
        inverseOnSurface = AbeonaColors.Paper,
        error = AbeonaColors.BadFill,
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFBDAD5),
        onErrorContainer = Color(0xFF9E241A),
        outline = Color(0xFF8D8178),
        // Ink at 12 % on paper.
        outlineVariant = Color(0xFFE2DDD8),
        scrim = Color(0xFF000000),
        surfaceBright = AbeonaColors.Paper,
        surfaceContainerLowest = AbeonaColors.Card,
        surfaceContainerLow = AbeonaColors.Card,
        surfaceContainer = Color(0xFFF8F3EC),
        surfaceContainerHigh = AbeonaColors.Well,
        surfaceContainerHighest = Color(0xFFEEE7DE),
        surfaceDim = Color(0xFFE9E2D9),
    ),
    dark = darkColorScheme(
        primary = AbeonaColors.BurgundyDark,
        onPrimary = AbeonaColors.OnBurgundyDark,
        primaryContainer = AbeonaColors.BrandSoftDark,
        onPrimaryContainer = AbeonaColors.InkDark,
        inversePrimary = AbeonaColors.Brand,
        secondary = AbeonaColors.MutedDark,
        onSecondary = AbeonaColors.PaperDark,
        secondaryContainer = AbeonaColors.WellDark,
        onSecondaryContainer = AbeonaColors.InkDark,
        tertiary = Color(0xFFFFE08A),
        onTertiary = Color(0xFF3A2A00),
        tertiaryContainer = Color(0xFF4A3A00),
        onTertiaryContainer = Color(0xFFFFE08A),
        background = AbeonaColors.PaperDark,
        onBackground = AbeonaColors.InkDark,
        surface = AbeonaColors.PaperDark,
        onSurface = AbeonaColors.InkDark,
        surfaceVariant = AbeonaColors.WellDark,
        onSurfaceVariant = AbeonaColors.MutedDark,
        surfaceTint = AbeonaColors.BurgundyDark,
        inverseSurface = AbeonaColors.InkDark,
        inverseOnSurface = AbeonaColors.Ink,
        error = Color(0xFFFFC2B8),
        onError = Color(0xFF5C1A12),
        errorContainer = Color(0xFF5C1A12),
        onErrorContainer = Color(0xFFFFC2B8),
        outline = Color(0xFF8A7D73),
        // Ink at 14 % on paper.
        outlineVariant = Color(0xFF38332F),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF3A312A),
        surfaceContainerLowest = Color(0xFF14100E),
        surfaceContainerLow = AbeonaColors.CardDark,
        surfaceContainer = Color(0xFF2B2420),
        surfaceContainerHigh = AbeonaColors.WellDark,
        surfaceContainerHighest = Color(0xFF3A312A),
        surfaceDim = AbeonaColors.PaperDark,
    ),
)
