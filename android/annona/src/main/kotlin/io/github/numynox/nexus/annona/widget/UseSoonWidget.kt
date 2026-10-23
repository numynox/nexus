package io.github.numynox.nexus.annona.widget

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
import androidx.glance.unit.ColorProvider as GlanceColor
import io.github.numynox.nexus.annona.AnnonaApplication
import io.github.numynox.nexus.annona.AppContainer
import io.github.numynox.nexus.annona.MainActivity
import io.github.numynox.nexus.annona.R
import io.github.numynox.nexus.annona.data.Urgency
import io.github.numynox.nexus.annona.data.expiryLabel
import io.github.numynox.nexus.annona.data.urgencyOf
import io.github.numynox.nexus.annona.ui.AnnonaPalette
import java.time.LocalDate
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/**
 * What to use up next, on the home screen: the soonest-expiring things in
 * stock, worded like the pantry's badges. The labels are worked out when the
 * widget draws, so "Tomorrow" turns into "Expires today" overnight even
 * without network; the list itself loads on Android's half-hourly update and
 * whenever the app goes to the background.
 */
class UseSoonWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as AnnonaApplication).container
        val state = UseSoonData.load(container)
        val dynamicColor = container.preferences.current().dynamicColor
        provideContent { UseSoonContent(state, dynamicColor = dynamicColor) }
    }

    companion object {
        /** Redraws every placed widget, e.g. once the app may have changed the stock. */
        suspend fun refresh(context: Context) = runCatching { UseSoonWidget().updateAll(context) }
    }
}

class UseSoonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UseSoonWidget()
}

/** One thing in stock with a date. */
@Serializable
data class SoonItem(val name: String, val expires: String) {
    val date: LocalDate get() = LocalDate.parse(expires)
}

sealed interface UseSoonState {
    data object SignedOut : UseSoonState
    /** Never loaded, and the network is not there now. */
    data object NotLoaded : UseSoonState
    /** Soonest first; more than any widget size shows. */
    data class Ready(val items: List<SoonItem>) : UseSoonState
}

object UseSoonData {
    /** More rows than the tallest widget holds. */
    private const val Kept = 12

    suspend fun load(container: AppContainer): UseSoonState {
        container.core.auth.awaitUserId() ?: return UseSoonState.SignedOut
        val fresh = runCatching {
            withTimeoutOrNull(20_000) {
                container.repository.activeItems()
                    .filter { it.expirationDate != null }
                    .sortedBy { it.expirationDate }
                    .take(Kept)
                    .map { SoonItem(it.displayName, it.expirationDate.toString()) }
            }
        }.getOrNull()
        if (fresh != null) {
            container.preferences.setWidgetItems(fresh)
            return UseSoonState.Ready(fresh)
        }
        return container.preferences.widgetItems()?.let(UseSoonState::Ready) ?: UseSoonState.NotLoaded
    }
}

private val WidgetColors = ColorProviders(light = AnnonaPalette.light, dark = AnnonaPalette.dark)

/** The pantry badge colours: past its date, and due this week. */
private val Expired = ColorProvider(day = Color(0xFFBA1A1A), night = Color(0xFFFFB4AB))
private val Soon = ColorProvider(day = Color(0xFF874D00), night = Color(0xFFFFB870))

@Composable
fun UseSoonContent(state: UseSoonState, today: LocalDate = LocalDate.now(), dynamicColor: Boolean = false) {
    GlanceTheme(colors = if (dynamicColor) GlanceTheme.colors else WidgetColors) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            when (state) {
                UseSoonState.SignedOut -> Message("Sign in to Annona to see what to use up")
                UseSoonState.NotLoaded -> Message("Waiting for a connection")
                is UseSoonState.Ready -> UseSoon(state.items, today)
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

private fun colorOf(urgency: Urgency): GlanceColor? = when (urgency) {
    Urgency.Expired -> Expired
    Urgency.Today, Urgency.Week -> Soon
    else -> null
}

@Composable
private fun UseSoon(items: List<SoonItem>, today: LocalDate) {
    val expired = items.count { it.date.isBefore(today) }
    val week = items.count { !it.date.isBefore(today) && !it.date.isAfter(today.plusDays(7)) }
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Logo()
        Spacer(GlanceModifier.width(8.dp))
        Text(
            "Use soon",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.defaultWeight(),
        )
        val summary = when {
            expired > 0 -> "$expired expired"
            week > 0 -> "$week this week"
            else -> null
        }
        summary?.let {
            Text(it, style = TextStyle(color = if (expired > 0) Expired else Soon, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
    }
    if (items.isEmpty()) {
        Spacer(GlanceModifier.height(8.dp))
        Text("Nothing with a date in stock", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
        return
    }
    // As many rows as the widget is tall: a header, then about 22 dp a row.
    val rows = ((LocalSize.current.height.value - 24 - 30) / 22).toInt().coerceIn(1, items.size)
    Spacer(GlanceModifier.height(6.dp))
    items.take(rows).forEach { item ->
        val urgency = urgencyOf(item.date, today)
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.name,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                expiryLabel(item.date, today),
                maxLines = 1,
                style = TextStyle(
                    color = colorOf(urgency) ?: GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = if (colorOf(urgency) != null) FontWeight.Medium else FontWeight.Normal,
                ),
            )
        }
    }
}
