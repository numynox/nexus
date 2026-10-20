package io.github.numynox.nexus.noctua.ui.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Refresh by pulling up past the last article — the web's gesture
 * (decisions/2026-09-06-refresh-at-the-end-of-the-feed.md). Not the top of the
 * list: a refresh retakes the seen snapshot, so the articles already scrolled
 * past disappear, and scrolling back up to reread one must never do that by
 * accident. And the moment to ask for more is when you have run out.
 */
class EndPull(private val threshold: Float, private val onRefresh: () -> Unit) : NestedScrollConnection {
    /** How far the list has been dragged up past its end, in pixels. */
    var distance by mutableFloatStateOf(0f)
        private set

    val fraction: Float get() = (distance / threshold).coerceIn(0f, 1f)

    // Dragged up (negative y) with nothing left to scroll: the list is at its end.
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
        distance = (distance - available.y).coerceAtMost(threshold * 1.5f)
        return Offset(0f, available.y)
    }

    // Dragging back down first takes back the pull, before the list scrolls.
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || distance <= 0f || available.y <= 0f) return Offset.Zero
        val taken = min(distance, available.y)
        distance -= taken
        return Offset(0f, taken)
    }

    // The finger lifted: far enough is a refresh, either way the pull lets go.
    override suspend fun onPreFling(available: Velocity): Velocity {
        val pulled = distance
        if (pulled >= threshold) onRefresh()
        distance = 0f
        return if (pulled > 0f) available else Velocity.Zero
    }
}

/**
 * The end of the feed: a full screen tall, like the web's panel, so the last
 * article can scroll all the way past the top and count as seen — a shorter
 * one would leave the progress short of done for good.
 */
@Composable
fun EndOfFeed(
    empty: Boolean,
    pull: EndPull,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lift by animateFloatAsState(pull.fraction, label = "pull")
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .graphicsLayer { rotationZ = lift * 180f }
                .background(MaterialTheme.colorScheme.secondaryContainer, MaterialShapes.Cookie9Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            when {
                refreshing -> LoadingIndicator(Modifier.size(48.dp))
                lift > 0f -> {
                    CircularProgressIndicator(
                        progress = { lift },
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.size(56.dp).graphicsLayer { rotationZ = -lift * 180f },
                    )
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                else -> Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(if (empty) "Nothing new" else "That's everything", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                pull.fraction >= 1f -> "Let go to refresh"
                empty -> "Every article here has been seen. Pull up or refresh for what came in since."
                else -> "Pull up to fetch what came in since and clear what you've seen."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onRefresh, enabled = !refreshing) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Refresh", modifier = Modifier.padding(start = 8.dp))
        }
    }
}
