package io.github.numynox.nexus.vibilia.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode

import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
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
import androidx.glance.text.TextStyle
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.vibilia.AppContainer
import io.github.numynox.nexus.vibilia.MainActivity
import io.github.numynox.nexus.vibilia.R
import io.github.numynox.nexus.vibilia.VibiliaApplication
import io.github.numynox.nexus.vibilia.reminders.RefuelReminders
import io.github.numynox.nexus.vibilia.tracking.localDrivingHoursAfter
import io.github.numynox.nexus.vibilia.ui.VibiliaPalette
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/**
 * The selected car's tank on the home screen, and where fuel is cheapest
 * right now — the two things the prices screen is opened for. Loads fresh
 * when Android asks (every half hour, and when the app goes to the
 * background), and shows the last good load when there is no network.
 */
class FuelWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as VibiliaApplication).container
        val state = FuelWidgetData.load(container)
        val dynamicColor = container.preferences.current().dynamicColor
        provideContent { FuelWidgetContent(state, dynamicColor) }
    }

    companion object {
        /** Redraws every placed widget, e.g. once the app may have changed what it shows. */
        suspend fun refresh(context: Context) = runCatching { FuelWidget().updateAll(context) }
    }
}

class FuelWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FuelWidget()
}

/** What the widget last loaded, kept for when the next load fails. */
@Serializable
data class FuelSnapshot(
    val carName: String,
    /** Null for a car without a tank size, whose level is unknown. */
    val fillPercent: Int? = null,
    val litersLeft: Double? = null,
    val rangeKm: Double? = null,
    /** Under the refuel reminder's threshold, or dry within a day. */
    val low: Boolean = false,
    val fuelType: String,
    val station: String? = null,
    val price: Double? = null,
    val loadedAt: Long,
)

sealed interface FuelWidgetState {
    data object SignedOut : FuelWidgetState
    data object NoCar : FuelWidgetState
    /** Never loaded, and the network is not there now. */
    data object NotLoaded : FuelWidgetState
    data class Ready(val snapshot: FuelSnapshot) : FuelWidgetState
}

object FuelWidgetData {
    suspend fun load(container: AppContainer, now: Instant = Instant.now()): FuelWidgetState {
        container.auth.awaitUserId() ?: return FuelWidgetState.SignedOut
        val fresh = runCatching { withTimeoutOrNull(20_000) { fetch(container, now) } }.getOrNull()
        if (fresh != null) {
            fresh.snapshot?.let { container.preferences.setWidgetSnapshot(it) }
            return fresh.snapshot?.let(FuelWidgetState::Ready) ?: FuelWidgetState.NoCar
        }
        return container.preferences.widgetSnapshot()?.let(FuelWidgetState::Ready) ?: FuelWidgetState.NotLoaded
    }

    private class Fetched(val snapshot: FuelSnapshot?)

    private suspend fun fetch(container: AppContainer, now: Instant): Fetched {
        val settings = container.preferences.current()
        val cars = container.cars.cars()
        // The app's own choice: the selected car, or the newest when that is gone.
        val car = cars.firstOrNull { it.id == settings.selectedCarId } ?: cars.firstOrNull() ?: return Fetched(null)
        val level = if ((car.tankCapacity ?: 0.0) > 0) {
            container.statistics.fuelLevel(car.id, car) { container.logbook.refuels(car.id) }?.let { level ->
                val tracker = container.driveTracking.tracker.state.first()
                level.plusLocalDriving(tracker.localDrivingHoursAfter(car.id, level.lastRefuelAt, now))
            }
        } else {
            null
        }
        // Prices are a nicety beside the tank: without them the widget still shows it.
        val cheapest = runCatching {
            container.prices.currentPrices(settings.fuelType)
                .filter { it.effectivePrice != null }
                .minByOrNull { it.effectivePrice!! }
        }.getOrNull()
        return Fetched(
            FuelSnapshot(
                carName = car.name,
                fillPercent = level?.let { (it.fillRatio * 100).toInt() },
                litersLeft = level?.remainingLiters,
                rangeKm = level?.rangeKm,
                low = level?.let { RefuelReminders.isLow(it, settings.refuelReminderPercent, now) } ?: false,
                fuelType = settings.fuelType,
                station = cheapest?.let { "${it.displayName} ${it.displayPlace}".trim() },
                price = cheapest?.effectivePrice,
                loadedAt = now.toEpochMilli(),
            ),
        )
    }
}

private val WidgetColors = ColorProviders(light = VibiliaPalette.light, dark = VibiliaPalette.dark)

/** The orange of a low tank, as the app's gauge draws it. */
private val Low = ColorProvider(day = Color(0xFF874D00), night = Color(0xFFFFB870))

@Composable
fun FuelWidgetContent(state: FuelWidgetState, dynamicColor: Boolean = false) {
    GlanceTheme(colors = if (dynamicColor) GlanceTheme.colors else WidgetColors) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                FuelWidgetState.SignedOut -> Message("Sign in to Vibilia to see your tank")
                FuelWidgetState.NoCar -> Message("Add a car in Vibilia to see its tank")
                FuelWidgetState.NotLoaded -> Message("Waiting for a connection")
                is FuelWidgetState.Ready -> Tank(state.snapshot)
            }
        }
    }
}

/** Fresh data needs no time; an old price says how old it is. */
private fun staleness(snapshot: FuelSnapshot, now: Instant): String {
    val loaded = Instant.ofEpochMilli(snapshot.loadedAt)
    return if (Duration.between(loaded, now) > Duration.ofHours(1)) " · as of ${NexusFormat.time(loaded)}" else ""
}

@Composable
private fun Message(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Logo()
        Spacer(GlanceModifier.width(10.dp))
        Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp))
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
private fun Tank(snapshot: FuelSnapshot, now: Instant = Instant.now()) {
    val tall = LocalSize.current.height >= 100.dp
    val accent = if (snapshot.low) Low else GlanceTheme.colors.primary
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Logo()
        Spacer(GlanceModifier.width(8.dp))
        Text(
            snapshot.carName,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.defaultWeight(),
        )
        snapshot.fillPercent?.let {
            Text("$it %", style = TextStyle(color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold))
        }
    }
    snapshot.fillPercent?.let { percent ->
        Spacer(GlanceModifier.height(8.dp))
        LinearProgressIndicator(
            progress = percent / 100f,
            color = accent,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
            modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
        )
    }
    val tank = listOfNotNull(
        snapshot.litersLeft?.let { "${NexusFormat.liters(it, 0)} left" },
        snapshot.rangeKm?.let { "${NexusFormat.km(it)} range" },
    ).joinToString(" · ")
    if (tall && tank.isNotEmpty()) {
        Spacer(GlanceModifier.height(8.dp))
        Text(tank, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
    }
    val price = snapshot.price?.let { price ->
        "${snapshot.fuelType} ${NexusFormat.number(price, 3)} €/L" +
            (snapshot.station?.let { " at $it" } ?: "") +
            staleness(snapshot, now)
    }
    when {
        price != null -> {
            Spacer(GlanceModifier.height(if (tall) 2.dp else 6.dp))
            Text(price, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
        }
        !tall && tank.isNotEmpty() -> {
            Spacer(GlanceModifier.height(6.dp))
            Text(tank, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
        }
    }
}
