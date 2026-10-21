package io.github.numynox.nexus.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A grey shape where content will be, breathing gently while it loads.
 * Placeholders say "something is coming" without claiming what: unlike an
 * empty state, they cannot turn out to be wrong once the data arrives.
 */
@Composable
fun PlaceholderBlock(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 14.dp,
    shape: Shape = CircleShape,
) {
    val pulse = rememberInfiniteTransition(label = "placeholder")
    val alpha by pulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "alpha")
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape),
    )
}

/**
 * A card-shaped placeholder: a short label line, a heading and [lines] of
 * text — the generic stand-in for a card of figures or a list row.
 */
@Composable
fun PlaceholderCard(modifier: Modifier = Modifier, lines: Int = 2, height: Dp? = null) {
    NexusCard(modifier.fillMaxWidth().semantics { contentDescription = "Loading" }) {
        Column(
            Modifier.then(if (height != null) Modifier.height(height) else Modifier),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlaceholderBlock(width = 96.dp, height = 12.dp)
            PlaceholderBlock(Modifier.fillMaxWidth(0.7f), height = 22.dp)
            repeat(lines) { index -> PlaceholderBlock(Modifier.fillMaxWidth(if (index == lines - 1) 0.55f else 1f)) }
        }
    }
}

/** [count] placeholder cards one under the other, for a list that is on its way. */
@Composable
fun PlaceholderList(count: Int = 3, modifier: Modifier = Modifier, lines: Int = 2) {
    Column(modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) { PlaceholderCard(lines = lines) }
    }
}
