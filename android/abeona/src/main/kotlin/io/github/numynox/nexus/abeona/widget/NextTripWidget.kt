package io.github.numynox.nexus.abeona.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import io.github.numynox.nexus.abeona.AbeonaApplication
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.MainActivity
import io.github.numynox.nexus.abeona.R
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.changes
import io.github.numynox.nexus.abeona.domain.departureDelay
import io.github.numynox.nexus.abeona.domain.platformChanged
import io.github.numynox.nexus.abeona.domain.worstConfidence
import io.github.numynox.nexus.abeona.ui.AbeonaColors
import io.github.numynox.nexus.abeona.ui.AbeonaPalette
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.common.confidenceSummary
import java.time.Instant
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/**
 * The next departure on the home screen: time, delay, platform, route and how
 * the changes look. It loads for itself (decisions/2026-10-23-home-screen-widgets.md)
 * and keeps the last good trip, so it still shows the platform in a tunnel.
 * Delays and confidence are worked out when it draws, from the stored trip.
 */
class NextTripWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as AbeonaApplication).container
        val state = NextTripData.load(container)
        val dynamicColor = container.preferences.current().dynamicColor
        provideContent { NextTripContent(state, dynamicColor = dynamicColor) }
    }

    companion object {
        /** Redraws every placed widget, e.g. after a live refresh. */
        suspend fun refresh(context: Context) = runCatching { NextTripWidget().updateAll(context) }
    }
}

class NextTripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextTripWidget()
}

/** The trip as the widget last saw it, without its route line and stopovers. */
@Serializable
data class NextTripSnapshot(val trip: Trip, val savedAt: String)

sealed interface NextTripState {
    data object SignedOut : NextTripState
    /** Never loaded, and the network is not there now. */
    data object NotLoaded : NextTripState
    data object NoTrips : NextTripState
    data class Ready(val trip: Trip) : NextTripState
}

object NextTripData {
    suspend fun load(container: AppContainer, now: Instant = Instant.now()): NextTripState {
        container.core.auth.awaitUserId() ?: return NextTripState.SignedOut
        val trips = runCatching { withTimeoutOrNull(20_000) { container.trips.upcoming().value } }.getOrNull()
        if (trips != null) {
            val next = trips.firstOrNull { it.expectedArrival.isAfter(now) }
            container.preferences.setWidgetSnapshot(
                next?.let { trip -> NextTripSnapshot(trip.copy(legs = trip.legs.map { it.copy(polyline = null, stopovers = emptyList()) }), now.toString()) },
            )
            return next?.let(NextTripState::Ready) ?: NextTripState.NoTrips
        }
        val stored = container.preferences.widgetSnapshot()?.trip ?: return NextTripState.NotLoaded
        return if (stored.expectedArrival.isAfter(now)) NextTripState.Ready(stored) else NextTripState.NoTrips
    }
}

private val WidgetColors = ColorProviders(light = AbeonaPalette.light, dark = AbeonaPalette.dark)
private val Amber = ColorProvider(day = AbeonaColors.Amber, night = AbeonaColors.Amber)
private val OnAmber = ColorProvider(day = AbeonaColors.Ink, night = AbeonaColors.Ink)

@Composable
fun NextTripContent(state: NextTripState, now: Instant = Instant.now(), dynamicColor: Boolean = false) {
    val context = LocalContext.current
    val tripId = (state as? NextTripState.Ready)?.trip?.id
    GlanceTheme(colors = if (dynamicColor) GlanceTheme.colors else WidgetColors) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(24.dp)
                .background(GlanceTheme.colors.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable(actionStartActivity(MainActivity.intentFor(context, tripId))),
        ) {
            when (state) {
                NextTripState.SignedOut -> Message("Sign in to Abeona to see your next train")
                NextTripState.NotLoaded -> Message("Waiting for a connection")
                NextTripState.NoTrips -> Message("No trips planned")
                is NextTripState.Ready -> NextTrip(state.trip, now)
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo()
            Spacer(GlanceModifier.width(10.dp))
            Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp))
        }
    }
}

@Composable
private fun Logo() {
    Image(
        ImageProvider(R.drawable.ic_notification),
        contentDescription = null,
        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
        modifier = GlanceModifier.size(18.dp),
    )
}

@Composable
private fun NextTrip(trip: Trip, now: Instant) {
    val first = trip.legs.firstOrNull { !it.isWalk }
    val delay = first?.let(::departureDelay) ?: 0
    val platform = first?.from?.shownPlatform
    val moved = first?.let { platformChanged(it.from.plannedPlatform, it.from.platform) } == true
    val tall = LocalSize.current.height.value >= 140
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Logo()
        Spacer(GlanceModifier.width(8.dp))
        Text(
            "Next trip",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(TripFormat.day(trip.departureAt), style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
    Spacer(GlanceModifier.height(if (tall) 8.dp else 4.dp))
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // The new time, and beside it the minutes late above the planned time struck through (glossary: delay).
        Text(
            TripFormat.time(if (delay > 0) first?.from?.expectedAt else first?.from?.plannedAt ?: trip.departureAt),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = if (tall) 34.sp else 28.sp, fontWeight = FontWeight.Bold),
        )
        if (delay > 0) {
            Spacer(GlanceModifier.width(8.dp))
            Column {
                Text("+$delay", style = TextStyle(color = GlanceTheme.colors.tertiary, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                Text(
                    TripFormat.time(first?.from?.plannedAt),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, textDecoration = TextDecoration.LineThrough),
                )
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        if (platform != null) {
            Column(
                GlanceModifier
                    .background(if (moved) Amber else GlanceTheme.colors.secondaryContainer)
                    .cornerRadius(12.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val ink = if (moved) OnAmber else GlanceTheme.colors.onSecondaryContainer
                Text("Pl.", style = TextStyle(color = ink, fontSize = 11.sp))
                Text(platform, style = TextStyle(color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
    Spacer(GlanceModifier.height(4.dp))
    Text(
        "${trip.originName} → ${trip.destinationName}",
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    )
    if (!tall) return
    Text(
        listOfNotNull(first?.label, confidenceSummary(changes(trip.legs), worstConfidence(trip.legs))).joinToString(" · "),
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
    )
}
